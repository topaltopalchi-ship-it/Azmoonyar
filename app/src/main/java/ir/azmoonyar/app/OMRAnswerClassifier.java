package ir.azmoonyar.app;

/**
 * Conservative classifier for four locally normalized bubble-darkness scores.
 * Very weak page noise is blank; borderline evidence is reviewed; only a
 * sufficiently dark, clearly dominant bubble is accepted automatically.
 */
public final class OMRAnswerClassifier {
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
        // Deliberately separate definite blank, uncertain faint mark, and accepted mark.
        // These are conservative starting values; real marked/blank sheets must calibrate them.
        this(0.08, 0.025, 0.015);
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

        int strongestIndex = 0;
        double strongest = scores[0];
        double second = -1;
        boolean tiedForStrongest = false;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > strongest) {
                second = strongest;
                strongest = scores[i];
                strongestIndex = i;
                tiedForStrongest = false;
            } else if (scores[i] == strongest) {
                second = strongest;
                tiedForStrongest = true;
            } else if (scores[i] > second) {
                second = scores[i];
            }
        }

        double margin = strongest - Math.max(0, second);
        if (strongest <= blankThreshold) {
            return new Result(Status.BLANK, -1, margin, strongest);
        }
        if (tiedForStrongest || margin < minimumMargin) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }

        // Two substantial bubbles are not silently reduced to the darker one.
        if (second >= markThreshold) {
            return new Result(Status.MULTIPLE_MARKS, -1, margin, strongest);
        }
        if (strongest < markThreshold) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }
        return new Result(Status.ANSWERED, strongestIndex, margin, Math.min(1.0, strongest * 2.0));
    }
}
