package ir.azmoonyar.app;

/**
 * Classifies four bubble-darkness scores by direct comparison.
 * If the strongest and second strongest scores differ by 0.1 or less,
 * the answer is sent to manual review instead of being guessed.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    /** Scores displayed as 0.000 are treated as white to suppress camera/rounding noise. */
    public static final double WHITE_SCORE_EPSILON = 0.0005;

    /** Minimum required gap between the two darkest bubbles (10 percentage points). */
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

            // The UI displays three decimal places. Treat values that round to
            // 0.000 as white so sensor noise does not send an empty answer to review.
            if (score < WHITE_SCORE_EPSILON) {
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
        // Equality is intentionally included: a 0.10 gap is still ambiguous.
        if (gap <= REVIEW_MARGIN_THRESHOLD) {
            return new Result(Status.REVIEW, -1, gap, strongest);
        }

        return new Result(Status.ANSWERED, strongestIndex, gap, strongest);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
