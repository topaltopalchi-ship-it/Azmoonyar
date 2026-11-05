package ir.azmoonyar.app;

/**
 * Classifies four bubble-darkness scores by direct comparison.
 * If the strongest and second strongest scores differ by 0.1 or less,
 * the answer is sent to manual review instead of being guessed.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    /** Scores strictly below 0.1 are white / unmarked. */
    public static final double WHITE_SCORE_THRESHOLD = 0.1;

    /** Both leading bubbles must be strictly above 0.2 to trigger double-mark review. */
    public static final double DOUBLE_MARK_MIN_SCORE = 0.2;

    /** Minimum required gap between two qualifying marked bubbles (10 percentage points). */
    public static final double REVIEW_MARGIN_THRESHOLD = 0.1;

    public static final class Result {
        public final Status status;
        /** Zero-based option index for ANSWERED, otherwise -1. */
        public final int optionIndex;
        /** Difference between the strongest and second strongest scores. */
        public final double margin;
        public final double strongestScore;

        private Result(Status status, int optionIndex, double margin, double strongestScore) {
            this.status = status;
            this.optionIndex = optionIndex;
            this.margin = margin;
            this.strongestScore = strongestScore;
        }
    }

    public OMRAnswerClassifier() {}

    /** Compatibility constructor for older callers; the review margin is fixed at 0.1. */
    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double twoMarkGap) {}

    /** Compatibility constructor for older callers; the review margin is fixed at 0.1. */
    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double twoMarkGap) {}

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }

        int strongestIndex = 0;
        double strongest = -1.0;
        double second = -1.0;

        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            if (!finite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }

            // Any score strictly below 0.1 is treated as white.
            if (score < WHITE_SCORE_THRESHOLD) {
                score = 0.0;
            }

            if (score > strongest) {
                second = strongest;
                strongest = score;
                strongestIndex = i;
            } else if (score > second) {
                second = score;
            }
        }

        if (strongest == 0.0) {
            return new Result(Status.BLANK, -1, 0.0, 0.0);
        }

        double gap = strongest - second;
        // A double-mark review is allowed only if BOTH leading scores exceed 0.2.
        // A weak secondary score must not cause review, even when the gap is small.
        if (strongest > DOUBLE_MARK_MIN_SCORE
                && second > DOUBLE_MARK_MIN_SCORE
                && gap <= REVIEW_MARGIN_THRESHOLD) {
            return new Result(Status.REVIEW, -1, gap, strongest);
        }

        return new Result(Status.ANSWERED, strongestIndex, gap, strongest);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
