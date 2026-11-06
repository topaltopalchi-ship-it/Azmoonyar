package ir.azmoonyar.app;

/**
 * Conservative classifier for four normalized bubble-darkness scores.
 * Scores must be measured after registration, perspective correction and local
 * paper-background normalization. Uncertain marks are sent to manual review.
 */
public final class OMRAnswerClassifier {
    public static final double WHITE_SCORE_THRESHOLD = 0.15;
    public static final double DOUBLE_MARK_PRIMARY_THRESHOLD = 0.705;
    public static final double DOUBLE_MARK_MIN_SCORE = 0.65;
    public static final double REVIEW_MARGIN_THRESHOLD = 0.10;

    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final class Result {
        public final Status status;
        /** Zero-based option index for ANSWERED, otherwise -1. */
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
        // Conservative defaults; real answer-sheet calibration can refine these later.
        this(0.25, WHITE_SCORE_THRESHOLD, REVIEW_MARGIN_THRESHOLD);
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

    /** Compatibility constructor for older callers that supplied a mark threshold and review gap. */
    @Deprecated
    public OMRAnswerClassifier(double markThreshold, double twoMarkGap) {
        this(markThreshold, Math.min(WHITE_SCORE_THRESHOLD, Math.max(0.0, markThreshold - 0.01)), twoMarkGap);
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

        int bestIndex = 0;
        double best = scores[0];
        double second = -1.0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > best) {
                second = best;
                best = scores[i];
                bestIndex = i;
            } else if (scores[i] > second) {
                second = scores[i];
            }
        }

        double runnerUp = Math.max(0.0, second);
        double margin = best - runnerUp;

        // No visible mark: keep the item blank.
        if (best <= blankThreshold) {
            return new Result(Status.BLANK, -1, margin, best);
        }

        // Two independently dark bubbles are a multiple response, not a guessed answer.
        if (best >= DOUBLE_MARK_PRIMARY_THRESHOLD && runnerUp >= DOUBLE_MARK_MIN_SCORE) {
            return new Result(Status.MULTIPLE_MARKS, -1, margin, best);
        }

        // A weak mark, a near-tie, or a close competitor above the blank floor
        // is not safe to grade automatically.
        if (best < markThreshold || margin <= 0.0
                || (runnerUp > blankThreshold && margin < minimumMargin)) {
            return new Result(Status.REVIEW, -1, margin, best);
        }

        return new Result(Status.ANSWERED, bestIndex, margin, best);
    }
}
