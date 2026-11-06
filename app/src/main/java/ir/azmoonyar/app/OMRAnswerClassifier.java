package ir.azmoonyar.app;

/**
 * Classifies four normalized bubble-darkness scores.
 * Scores at or below 0.10 are treated as zero (unmarked).
 * Two remaining scores trigger manual review only when their difference is < 0.10.
 */
public final class OMRAnswerClassifier {
    public static final double WHITE_SCORE_THRESHOLD = 0.10;
    public static final double ANSWER_SCORE_THRESHOLD = 0.10;
    public static final double REVIEW_MARGIN_THRESHOLD = 0.10;

    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

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

    private final double markThreshold;
    private final double blankThreshold;
    private final double minimumMargin;

    public OMRAnswerClassifier() {
        this(ANSWER_SCORE_THRESHOLD, WHITE_SCORE_THRESHOLD, REVIEW_MARGIN_THRESHOLD);
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

    /** Compatibility constructor for older callers. */
    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double reviewGap) {
        this(markThreshold, Math.min(WHITE_SCORE_THRESHOLD, markThreshold), reviewGap);
    }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }

        double[] scores = darkness.clone();
        for (int i = 0; i < scores.length; i++) {
            double score = scores[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }
            // Treat all values <= 0.10 as zero/no mark before ranking.
            if (score <= blankThreshold) scores[i] = 0.0;
        }

        int bestIndex = -1;
        double best = 0.0;
        double second = 0.0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > best) {
                second = best;
                best = scores[i];
                bestIndex = i;
            } else if (scores[i] > second) {
                second = scores[i];
            }
        }

        double margin = best - second;
        if (best <= markThreshold) {
            return new Result(Status.BLANK, -1, margin, best);
        }

        // Review only if BOTH leading bubbles exceed 0.10 and their gap is < 0.10.
        if (second > markThreshold && margin < minimumMargin) {
            return new Result(Status.REVIEW, -1, margin, best);
        }

        // Otherwise the darkest qualifying bubble is the selected answer.
        return new Result(Status.ANSWERED, bestIndex, margin, best);
    }
}
