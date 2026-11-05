package ir.azmoonyar.app;

/**
 * Classifies four bubble-darkness scores by direct comparison.
 * No fixed darkness threshold is applied: the darkest bubble wins.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

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

    /**
     * Compatibility constructor for older callers. Threshold arguments are
     * intentionally ignored; classification no longer uses fixed thresholds.
     */
    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double twoMarkGap) {}

    /**
     * Compatibility constructor for older callers. Threshold arguments are
     * intentionally ignored; classification no longer uses fixed thresholds.
     */
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

            if (score > strongest) {
                second = strongest;
                strongest = score;
                strongestIndex = i;
            } else if (score > second) {
                second = score;
            }
        }

        // Only four exactly-zero measurements are considered blank at this stage.
        // Low non-zero values are still compared; ring/coordinate calibration follows separately.
        if (strongest == 0.0) {
            return new Result(Status.BLANK, -1, 0.0, 0.0);
        }

        double gap = strongest - second;
        if (gap <= 0.000001) {
            return new Result(Status.REVIEW, -1, gap, strongest);
        }

        return new Result(Status.ANSWERED, strongestIndex, gap, strongest);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
