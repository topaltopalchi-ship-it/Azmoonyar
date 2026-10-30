package ir.azmoonyar.app;

/**
 * Selects the strongest recorded bubble mark.
 *
 * A bubble is considered marked when its normalized darkness reaches
 * markThreshold. If one or more options are marked, the option with the
 * highest score wins. A question is blank only when no option reaches
 * that threshold. An exact tie for the highest score is sent to review.
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

    public OMRAnswerClassifier() {
        this(0.28);
    }

    public OMRAnswerClassifier(double markThreshold) {
        if (Double.isNaN(markThreshold) || Double.isInfinite(markThreshold)
                || markThreshold < 0 || markThreshold > 1) {
            throw new IllegalArgumentException("آستانهٔ ثبت علامت معتبر نیست.");
        }
        this.markThreshold = markThreshold;
    }

    /** Legacy constructor retained for source compatibility; only markThreshold is used. */
    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double minimumMargin) {
        this(markThreshold);
        if (Double.isNaN(blankThreshold) || Double.isInfinite(blankThreshold)
                || blankThreshold < 0 || blankThreshold > 1
                || Double.isNaN(minimumMargin) || Double.isInfinite(minimumMargin)
                || minimumMargin < 0 || minimumMargin > 1) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
    }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }
        double strongest = -1;
        double second = -1;
        int strongestIndex = -1;
        int markedOptions = 0;
        boolean tiedForStrongest = false;

        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }
            if (score >= markThreshold) markedOptions++;

            if (score > strongest) {
                second = strongest;
                strongest = score;
                strongestIndex = i;
                tiedForStrongest = false;
            } else if (score == strongest) {
                tiedForStrongest = true;
            } else if (score > second) {
                second = score;
            }
        }

        if (markedOptions == 0) {
            return new Result(Status.BLANK, -1, 0, Math.max(0, strongest));
        }

        double margin = strongest - Math.max(0, second);
        if (tiedForStrongest) {
            return new Result(Status.REVIEW, -1, margin, strongest);
        }
        return new Result(Status.ANSWERED, strongestIndex, margin, strongest);
    }
}
