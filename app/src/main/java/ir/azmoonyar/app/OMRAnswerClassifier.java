package ir.azmoonyar.app;

/**
 * Altay Noor sensitivity: every positive darkness score is ink evidence.
 * Exactly one positive option is accepted; two or more positive options are
 * sent to review as multiple marks. Only four zero scores are blank.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final double MARK_THRESHOLD = 0.0;
    public static final double REVIEW_GAP = 0.0;
    public static final double ANSWER_GAP = 0.0;

    public static final class Result {
        public final Status status;
        public final int optionIndex;
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

    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        if (markThreshold < 0 || markThreshold > 1 || blankThreshold < 0
                || blankThreshold > markThreshold || minimumMargin < 0 || minimumMargin > 1) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
    }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4)
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");

        int markedCount = 0;
        int markedIndex = -1;
        double strongest = 0.0;
        double second = 0.0;

        for (int i = 0; i < 4; i++) {
            double score = darkness[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1)
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");

            if (score > 0.0) {
                markedCount++;
                if (score > strongest) {
                    second = strongest;
                    strongest = score;
                    markedIndex = i;
                } else if (score > second) {
                    second = score;
                }
            }
        }

        if (markedCount == 0)
            return new Result(Status.BLANK, -1, 0.0, 0.0);

        if (markedCount >= 2)
            return new Result(Status.MULTIPLE_MARKS, -1, strongest - second, strongest);

        return new Result(Status.ANSWERED, markedIndex, strongest, strongest);
    }
}
