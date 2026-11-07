package ir.azmoonyar.app;

public final class OMRAnswerClassifier {
    /** Scores must be strictly above 0.10 to count as a marked bubble. */
    public static final double MARK_THRESHOLD = 0.10;
    /** Two marked bubbles with a gap <= 0.10 require manual review. */
    public static final double REVIEW_GAP_THRESHOLD = 0.10;
    public static final double WHITE_SCORE_THRESHOLD = MARK_THRESHOLD;
    public static final double DOUBLE_MARK_PRIMARY_THRESHOLD = MARK_THRESHOLD;
    public static final double DOUBLE_MARK_MIN_SCORE = MARK_THRESHOLD;
    public static final double REVIEW_MARGIN_THRESHOLD = REVIEW_GAP_THRESHOLD;
    public static final double NEAR_TIE_REVIEW_EPSILON = 0.001;

    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }
    public enum LightMode { DAY, NIGHT }

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

    private final LightMode lightMode;
    public OMRAnswerClassifier() { this(LightMode.DAY); }

    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        this(LightMode.DAY);
        if (Double.isNaN(markThreshold) || Double.isNaN(blankThreshold)
                || Double.isNaN(minimumMargin)) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
    }

    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double twoMarkGap) {
        this(markThreshold, 0.0, twoMarkGap);
    }

    private OMRAnswerClassifier(LightMode lightMode) { this.lightMode = lightMode; }

    public static OMRAnswerClassifier forSheetScores(double[][] allQuestionScores) {
        if (allQuestionScores == null || allQuestionScores.length == 0) {
            throw new IllegalArgumentException("امتیازهای پاسخنامه برای انتخاب حالت نور لازم است.");
        }
        double sum = 0.0;
        long count = 0;
        for (double[] question : allQuestionScores) {
            if (question == null || question.length != 4) {
                throw new IllegalArgumentException("هر سؤال باید چهار امتیاز حباب داشته باشد.");
            }
            for (double score : question) {
                validateScore(score);
                sum += score;
                count++;
            }
        }
        return new OMRAnswerClassifier(sum / count > MARK_THRESHOLD ? LightMode.DAY : LightMode.NIGHT);
    }

    public LightMode getLightMode() { return lightMode; }
    public double getMarkThreshold() { return MARK_THRESHOLD; }
    public double getBlankThreshold() { return MARK_THRESHOLD; }
    public double getMinimumMargin() { return REVIEW_GAP_THRESHOLD; }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }
        int bestIndex = -1;
        double best = -1.0;
        double second = -1.0;
        int markedCount = 0;
        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            validateScore(score);
            if (score > MARK_THRESHOLD) markedCount++;
            if (score > best) {
                second = best;
                best = score;
                bestIndex = i;
            } else if (score > second) {
                second = score;
            }
        }
        double margin = best - Math.max(0.0, second);
        if (best <= MARK_THRESHOLD) return new Result(Status.BLANK, -1, margin, best);
        if (markedCount > 2) return new Result(Status.REVIEW, -1, margin, best);
        if (markedCount == 2) {
            if (margin <= REVIEW_GAP_THRESHOLD) return new Result(Status.REVIEW, -1, margin, best);
            return new Result(Status.ANSWERED, bestIndex, margin, best);
        }
        return new Result(Status.ANSWERED, bestIndex, margin, best);
    }

    private static void validateScore(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
        }
    }
}