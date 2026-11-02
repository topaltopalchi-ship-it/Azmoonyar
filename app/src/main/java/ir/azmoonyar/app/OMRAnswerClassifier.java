package ir.azmoonyar.app;

/**
 * Classifies four normalized darkness scores (0..1) as percent-like 0..100 evidence.
 * A score above 20 is considered ink evidence; an option wins only when its
 * darkness exceeds the runner-up by more than 20 points. Weak separation is reviewed.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final double MARK_THRESHOLD = 0.20;
    public static final double REVIEW_GAP = 0.10;
    public static final double ANSWER_GAP = 0.20;

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
        int strongestIndex = -1;
        double strongest = -1.0, second = -1.0;
        for (int i = 0; i < 4; i++) {
            double score = darkness[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1)
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            if (score > strongest) {
                second = strongest;
                strongest = score;
                strongestIndex = i;
            } else if (score > second) {
                second = score;
            }
        }
        if (strongest <= MARK_THRESHOLD)
            return new Result(Status.BLANK, -1, 0.0, Math.max(0.0, strongest));
        double margin = strongest - Math.max(0.0, second);
        if (margin > ANSWER_GAP)
            return new Result(Status.ANSWERED, strongestIndex, margin, strongest);
        // Darkness above 20 with a difference below 10 is specifically ambiguous.
        // The 10..20 gap also remains review-only: a confident answer requires >20.
        return new Result(Status.REVIEW, -1, margin, strongest);
    }
}
