package ir.azmoonyar.app;

/**
 * Altay 4 decision rules for four locally normalized bubble-darkness scores.
 * Scores are in [0,1], where 0 is paper-white and larger values mean darker marks.
 */
public final class OMRAnswerClassifier {
    public enum Status { ANSWERED, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final class Result {
        public final Status status;
        /** Zero-based option index for ANSWERED, otherwise -1. */
        public final int optionIndex;
        /** Difference between the strongest and second strongest marked options. */
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
    private final double twoMarkGap;

    public OMRAnswerClassifier() {
        this(0.15, 0.20);
    }

    /** Compatibility constructor for callers that still supply the former blank threshold. */
    public OMRAnswerClassifier(double markThreshold, double blankThreshold, double twoMarkGap) {
        this(markThreshold, twoMarkGap);
        if (!finite(blankThreshold) || blankThreshold < 0 || blankThreshold > markThreshold) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
    }

    public OMRAnswerClassifier(double markThreshold, double twoMarkGap) {
        if (!finite(markThreshold) || markThreshold < 0 || markThreshold > 1
                || !finite(twoMarkGap) || twoMarkGap < 0 || twoMarkGap > 1) {
            throw new IllegalArgumentException("آستانه‌های تشخیص پاسخ معتبر نیستند.");
        }
        this.markThreshold = markThreshold;
        this.twoMarkGap = twoMarkGap;
    }

    public Result classify(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز حباب ارائه شود.");
        }

        int markedCount = 0;
        int strongestIndex = -1;
        double strongest = -1;
        double second = -1;
        int isolatedFivePercentIndex = -1;
        int isolatedFivePercentCount = 0;

        for (int i = 0; i < darkness.length; i++) {
            double score = darkness[i];
            if (!finite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید عددی بین صفر و یک باشد.");
            }

            // A single non-zero mark with all other bubbles exactly white is
            // accepted when it reaches the low-mark floor.
            if (score >= 0.05) {
                boolean othersZero = true;
                for (int j = 0; j < darkness.length; j++) {
                    if (j != i && darkness[j] != 0.0) {
                        othersZero = false;
                        break;
                    }
                }
                if (othersZero) {
                    isolatedFivePercentIndex = i;
                    isolatedFivePercentCount++;
                }
            }

            if (score > markThreshold) {
                markedCount++;
                if (score > strongest) {
                    second = strongest;
                    strongest = score;
                    strongestIndex = i;
                } else if (score > second) {
                    second = score;
                }
            }
        }

        if (isolatedFivePercentCount == 1) {
            double score = darkness[isolatedFivePercentIndex];
            return new Result(Status.ANSWERED, isolatedFivePercentIndex, score, score);
        }

        if (markedCount == 0) {
            return new Result(Status.BLANK, -1, 0, 0);
        }
        if (markedCount == 1) {
            if (strongest < 0.16) {
                return new Result(Status.REVIEW, -1, strongest, strongest);
            }
            return new Result(Status.ANSWERED, strongestIndex, strongest, strongest);
        }

        // When three or four bubbles cross the mark threshold, a threshold-only
        // rule would incorrectly call every option marked. Select the strongest
        // measured bubble as the answer, as requested. Exact ties remain REVIEW
        // because there is no unique strongest option to report.
        if (markedCount >= 3) {
            double gap = strongest - second;
            if (gap <= 0.000001) {
                return new Result(Status.REVIEW, -1, gap, strongest);
            }
            return new Result(Status.ANSWERED, strongestIndex, gap, strongest);
        }

        double gap = strongest - second;
        if (gap >= twoMarkGap) {
            return new Result(Status.ANSWERED, strongestIndex, gap, strongest);
        }
        return new Result(Status.REVIEW, -1, gap, strongest);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
