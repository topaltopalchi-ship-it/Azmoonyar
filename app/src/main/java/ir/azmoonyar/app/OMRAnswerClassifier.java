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
        /** Strongest normalized darkness score. */
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
        // Calibrated against the current phone-photo diagnostics: clearly stronger marks
        // Keep low-contrast marks from being guessed: values below 0.16 are blank,
        // a mark must reach 0.28, and the strongest bubble must lead by at least 0.24.
        // This preserves clearly dominant real-photo marks while routing borderline cases to review.
        this(0.28, 0.16, 0.24);
    }

    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        if (blankThreshold < 0 || blankThreshold > 1 || markThreshold < 0 || markThreshold > 1
                || blankThreshold >= markThreshold || minimumMargin < 0 || minimumMargin > 1) {
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

        int strongestIndex = 0;
        double strongest = scores[0];
        double second = -1;
        boolean tiedForStrongest = false;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > strongest) {
                second = strongest;
                strongest = scores[i];
                strongestIndex = i;
                tiedForStrongest = false;
            } else if (scores[i] == strongest) {
                second = strongest;
                tiedForStrongest = true;
            } else if (scores[i] > second) {
                second = scores[i];
            }
        }

        if (strongest <= blankThreshold) {
            return new Result(Status.BLANK, -1, margin, strongest);
        }

        // Two independently strong fills are a multiple mark. A second score
        // near the winner but below this level is ambiguous, not a second mark.
        if (second >= 0.50) {
            return new Result(Status.MULTIPLE_MARKS, -1, margin, strongest);
        }

        // Evidence between blank and mark thresholds, or an insufficient lead,
        // must be reviewed rather than promoted to an answer.
        if (strongest < markThreshold || margin < minimumMargin || tiedForStrongest) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        return new Result(Status.ANSWERED, strongestIndex, margin, strongest);
    }
}
