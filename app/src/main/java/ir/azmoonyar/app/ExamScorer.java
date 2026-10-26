package ir.azmoonyar.app;

import java.util.List;

/**
 * Pure-Java scoring rules for a fixed A-D answer key.
 * REVIEW responses are deliberately left unscored until manual review.
 */
public final class ExamScorer {
    private ExamScorer() {}

    public static final class Result {
        public final int totalQuestions, correct, wrong, blank, multipleMarks, needsReview;
        public final double earnedPoints, maximumPoints;

        private Result(int totalQuestions, int correct, int wrong, int blank,
                int multipleMarks, int needsReview, double earnedPoints, double maximumPoints) {
            this.totalQuestions = totalQuestions;
            this.correct = correct;
            this.wrong = wrong;
            this.blank = blank;
            this.multipleMarks = multipleMarks;
            this.needsReview = needsReview;
            this.earnedPoints = earnedPoints;
            this.maximumPoints = maximumPoints;
        }

        public boolean isComplete() { return needsReview == 0; }

        /** Returns NaN until every answer is determinate or if maximum points are zero. */
        public double percentage() {
            if (!isComplete() || maximumPoints == 0.0) return Double.NaN;
            return earnedPoints * 100.0 / maximumPoints;
        }
    }

    /**
     * Scores responses in question order. Key must contain one A-D character per response.
     * REVIEW responses remain unresolved and unscored; multiple marks count as wrong.
     */
    public static Result score(String answerKey, List<OMRAnswerClassifier.Result> responses,
            double correctPoints, double wrongPoints, double blankPoints) {
        if (answerKey == null || responses == null || answerKey.length() != responses.size()) {
            throw new IllegalArgumentException("کلید پاسخ و تعداد پاسخ‌ها باید برابر باشند.");
        }
        requireFinite(correctPoints, "نمره پاسخ صحیح");
        requireFinite(wrongPoints, "نمره پاسخ غلط");
        requireFinite(blankPoints, "نمره پاسخ خالی");
        if (correctPoints < 0 || wrongPoints > 0 || blankPoints < 0) {
            throw new IllegalArgumentException("نمره صحیح و خالی باید نامنفی و نمره غلط نامثبت باشد.");
        }

        int correct = 0, wrong = 0, blank = 0, multiple = 0, review = 0;
        double earned = 0.0;
        for (int i = 0; i < answerKey.length(); i++) {
            char expected = Character.toUpperCase(answerKey.charAt(i));
            if (expected < 'A' || expected > 'D') {
                throw new IllegalArgumentException("کلید سؤال " + (i + 1) + " باید بین A تا D باشد.");
            }
            OMRAnswerClassifier.Result response = responses.get(i);
            if (response == null || response.status == null) {
                throw new IllegalArgumentException("وضعیت پاسخ سؤال " + (i + 1) + " معتبر نیست.");
            }
            switch (response.status) {
                case ANSWERED:
                    if (response.optionIndex < 0 || response.optionIndex > 3) {
                        throw new IllegalArgumentException("گزینه پاسخ سؤال " + (i + 1) + " معتبر نیست.");
                    }
                    if (response.optionIndex == expected - 'A') {
                        correct++;
                        earned += correctPoints;
                    } else {
                        wrong++;
                        earned += wrongPoints;
                    }
                    break;
                case BLANK:
                    blank++;
                    earned += blankPoints;
                    break;
                case MULTIPLE_MARKS:
                    multiple++;
                    wrong++;
                    earned += wrongPoints;
                    break;
                case REVIEW:
                    review++;
                    break;
                default:
                    throw new IllegalArgumentException("وضعیت پاسخ ناشناخته است.");
            }
        }
        return new Result(answerKey.length(), correct, wrong, blank, multiple, review,
                earned, answerKey.length() * correctPoints);
    }

    private static void requireFinite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " باید عدد متناهی باشد.");
        }
    }
}
