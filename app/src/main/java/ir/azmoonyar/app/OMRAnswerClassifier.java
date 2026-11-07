package ir.azmoonyar.app;

/**
 * Classifies four normalized bubble-darkness scores.
 * Scores above 0.20 are considered marked. A close double mark is reviewed
 * only when the difference between the two marked options is below 0.10.
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
        this(0.20, 0.20, 0.10);
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

        int strongestIndex = -1;
        int markedCount = 0;
        double strongest = -1.0;
        double second = -1.0;
        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }
            if (score > markThreshold) {
                markedCount++;
            }
            if (score > strongest) {
                second = strongest;
                strongest = score;
                strongestIndex = i;
            } else if (score > second) {
                second = score;
            }
        }

        if (markedCount == 0 || strongest <= blankThreshold) {
            return new Result(Status.BLANK, -1, 0.0, Math.max(0.0, strongest));
        }

        double margin = strongest - Math.max(0.0, second);
        if (markedCount >= 3) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        if (markedCount == 2 && margin < minimumMargin) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        return new Result(Status.ANSWERED, strongestIndex, margin, strongest);
    }
}
