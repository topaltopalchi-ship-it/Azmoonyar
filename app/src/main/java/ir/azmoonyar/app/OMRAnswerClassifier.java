package ir.azmoonyar.app;

public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }
    public enum LightMode { DAY, NIGHT }

    /** Absolute white-bubble cutoff: scores <= 0.10 are always blank. */
    public static final double WHITE_BUBBLE_CUTOFF = 0.10;

    public static final class Result {
        public final Status status;
        public final int optionIndex;
        public final double margin;
        public final double strongestScore;
        private Result(Status status, int optionIndex, double margin, double strongestScore) {
            this.status = status; this.optionIndex = optionIndex;
            this.margin = margin; this.strongestScore = strongestScore;
        }
    }

    private final double markThreshold, blankThreshold, minimumMargin;
    private final LightMode lightMode;

    public OMRAnswerClassifier() { this(0.20, 0.20, 0.10, LightMode.DAY); }
    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        this(markThreshold, blankThreshold, minimumMargin, LightMode.DAY);
    }
    private OMRAnswerClassifier(double markThreshold, double blankThreshold,
                                double minimumMargin, LightMode lightMode) {
        if (blankThreshold < 0 || blankThreshold > 1 || markThreshold < 0 || markThreshold > 1
                || blankThreshold > markThreshold || minimumMargin < 0 || minimumMargin > 1
                || lightMode == null) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
        this.markThreshold = markThreshold; this.blankThreshold = blankThreshold;
        this.minimumMargin = minimumMargin; this.lightMode = lightMode;
    }

    /**
     * Chooses mode from the arithmetic mean of all four-option darkness scores
     * across the entire sheet. Mean > 0.20 => DAY; mean > 0.10 => NIGHT.
     * At or below 0.10 defaults to NIGHT (low-light-safe threshold).
     */
    public static OMRAnswerClassifier forSheetScores(double[][] allQuestionScores) {
        if (allQuestionScores == null || allQuestionScores.length == 0) {
            throw new IllegalArgumentException("امتیازهای پاسخنامه برای انتخاب حالت نور لازم است.");
        }
        double sum = 0.0;
        long total = 0;
        for (double[] question : allQuestionScores) {
            if (question == null || question.length != 4) {
                throw new IllegalArgumentException("هر سؤال باید چهار امتیاز حباب داشته باشد.");
            }
            for (double score : question) {
                if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                    throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
                }
                sum += score;
                total++;
            }
        }
        double average = sum / total;
        if (average > 0.20) return new OMRAnswerClassifier(0.20, 0.20, 0.10, LightMode.DAY);
        return new OMRAnswerClassifier(0.10, 0.10, 0.10, LightMode.NIGHT);
    }

    public LightMode getLightMode() { return lightMode; }
    public double getMarkThreshold() { return markThreshold; }
    public double getBlankThreshold() { return blankThreshold; }
    public double getMinimumMargin() { return minimumMargin; }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }
        int strongestIndex = -1, markedCount = 0;
        double strongest = -1.0, second = -1.0;
        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }
            if (score > markThreshold) markedCount++;
            if (score > strongest) { second = strongest; strongest = score; strongestIndex = i; }
            else if (score > second) second = score;
        }
        // Enforce the absolute white rule in both lighting modes.
        if (strongest <= WHITE_BUBBLE_CUTOFF || markedCount == 0 || strongest <= blankThreshold) {
            return new Result(Status.BLANK, -1, 0.0, Math.max(0.0, strongest));
        }
        double margin = strongest - Math.max(0.0, second);
        if (markedCount >= 3 || (markedCount == 2 && margin < minimumMargin)) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }
        return new Result(Status.ANSWERED, strongestIndex, margin, strongest);
    }
}
