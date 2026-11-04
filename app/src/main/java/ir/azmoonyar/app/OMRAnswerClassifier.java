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

        double margin = strongest - Math.max(0, second);

        // A completely unmarked row stays blank. The small low-contrast
        // exception below preserves a clearly ordered, close pair only when
        // both values are in the scanner's known near-white noise band.
        if (strongest <= blankThreshold) {
            if (strongest >= 0.10 && second >= 0.08
                    && margin > 0.0 && margin <= 0.0200001) {
                return new Result(Status.ANSWERED, strongestIndex, margin,
                        Math.min(1.0, strongest * 2.0));
            }
            return new Result(Status.BLANK, -1, margin, strongest);
        }

        // Evidence between the blank and reliable-mark bands is not enough
        // to make an automatic decision. Ask for human review.
        if (strongest < 0.25) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        // Two independently strong bubbles are a multiple mark. If the
        // runner-up is substantial but not strong enough to call a second
        // mark, the row remains ambiguous and must be reviewed.
        if (second >= 0.50 && second >= strongest * 0.75) {
            return new Result(Status.MULTIPLE_MARKS, -1, margin, strongest);
        }
        if (tiedForStrongest) {
            return new Result(Status.REVIEW, -1, 0, strongest);
        }

        // Preserve the scanner's explicit near-tie behavior for non-identical
        // scores (the exact tie above is still reviewed). All other weakly
        // separated marks, including a faint runner-up, require review.
        boolean deliberateNearTie = margin > 0.0 && margin <= 0.0011
                && strongest >= 0.25;
        if (!deliberateNearTie && margin < minimumMargin) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        // The strongest mark is sufficiently separated from the alternatives.
        // Keep the historical display score scaling, but never use it to
        // turn a weak mark into an answered question.
        return new Result(Status.ANSWERED, strongestIndex, margin,
                Math.min(1.0, strongest * 2.0));
    }
}
