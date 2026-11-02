package ir.azmoonyar.app;

/**
 * Sensitivity-oriented classifier for four locally normalized bubble-darkness measurements.
 *
 * Input values must be calibrated darkness scores in [0, 1], where 0 is paper-white
 * and 1 is fully dark. Geometry detection, perspective correction, local background
 * normalization and image-quality checks must happen before calling this class.
 * Weak or ambiguous evidence is sent for review instead of guessed.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final class Result {
        public final Status status;
        /** Zero-based option index for ANSWERED, otherwise -1. */
        public final int optionIndex;
        /** Difference between strongest and second-strongest bubble scores. */
        public final double margin;
        /** Strongest normalized darkness score, unchanged from the input scale [0, 1]. */
        public final double strongestScore;

        private Result(Status status, int optionIndex, double margin, double strongestScore) {
            this.status = status;
            this.optionIndex = optionIndex;
            this.margin = margin;
            this.strongestScore = strongestScore;
        }
    }

    private final double markThreshold;
    private final double blankThreshold;
    private final double minimumMargin;

    public OMRAnswerClassifier() {
        // Sensitivity mode: every score strictly above zero is a detected mark.
        // A single marked option is accepted; multiple marked options go to review.
        this(0.0, 0.0, 0.0);
    }

    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        if (blankThreshold < 0 || blankThreshold > 1 || markThreshold < 0 || markThreshold > 1
                || blankThreshold > markThreshold || minimumMargin < 0 || minimumMargin > 1) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
        this.markThreshold = markThreshold;
        this.blankThreshold = blankThreshold;
        this.minimumMargin = minimumMargin;
    }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }
        double[] scores = darkness.clone();
        for (double score : scores) {
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }
        }

        int strongestIndex = -1;
        int markedOptions = 0;
        double strongest = 0.0;
        double second = 0.0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > 0.0) {
                markedOptions++;
                if (scores[i] > strongest) {
                    second = strongest;
                    strongest = scores[i];
                    strongestIndex = i;
                } else if (scores[i] > second) {
                    second = scores[i];
                }
            }
        }

        if (markedOptions == 0) {
            return new Result(Status.BLANK, -1, 0.0, 0.0);
        }
        if (markedOptions >= 2) {
            return new Result(Status.REVIEW, -1, strongest - second, strongest);
        }
        return new Result(Status.ANSWERED, strongestIndex, strongest, strongest);
    }
}
