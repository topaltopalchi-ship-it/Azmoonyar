package ir.azmoonyar.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class OMRAndScoringTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test public void classifiesClearSingleMark() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.82, 0.08, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test public void classifiesBlankSheetQuestion() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.08, 0.10, 0.12, 0.05});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test public void closeDarkMarksAreSentToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.76, 0.70, 0.08, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test public void sendsAmbiguousMarkToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.48, 0.40, 0.09, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test public void rejectsInvalidDarknessScores() {
        try {
            classifier.classify(new double[]{0.2, 0.3, Double.NaN, 0.1});
            fail("Expected invalid darkness to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("بین صفر و یک"));
        }
    }

    @Test public void scorerCountsCorrectWrongAndBlankWhenAllAnswersAreDeterminate() {
        OMRAnswerClassifier.Result a = classifier.classify(new double[]{0.82, 0.08, 0.12, 0.10});
        OMRAnswerClassifier.Result wrongA = classifier.classify(new double[]{0.82, 0.08, 0.12, 0.10});
        OMRAnswerClassifier.Result blank = classifier.classify(new double[]{0.08, 0.10, 0.12, 0.05});
        OMRAnswerClassifier.Result wrongD = classifier.classify(new double[]{0.08, 0.10, 0.76, 0.10});

        ExamScorer.Result result = ExamScorer.score("ABCD",
                Arrays.asList(a, wrongA, blank, wrongD), 1.0, -0.25, 0.0);

        assertEquals(4, result.totalQuestions);
        assertEquals(1, result.correct);
        assertEquals(2, result.wrong);
        assertEquals(1, result.blank);
        assertEquals(0, result.multipleMarks);
        assertEquals(0, result.needsReview);
        assertEquals(0.5, result.earnedPoints, 0.0001);
        assertEquals(4.0, result.maximumPoints, 0.0001);
        assertEquals(12.5, result.percentage(), 0.0001);
    }

    @Test public void scorerDoesNotPublishPercentageWhileReviewIsNeeded() {
        OMRAnswerClassifier.Result review = classifier.classify(new double[]{0.48, 0.40, 0.09, 0.08});
        ExamScorer.Result result = ExamScorer.score("A", Arrays.asList(review), 1.0, -0.25, 0.0);
        assertEquals(1, result.needsReview);
        assertTrue(Double.isNaN(result.percentage()));
    }

    @Test public void marginRuleRunsBeforeChoosingTheDarkestOption() {
        // Both B and D are visibly marked. The darkest option wins only because
        // its score exceeds the runner-up by strictly more than 0.14.
        OMRAnswerClassifier.Result clearWinner =
                classifier.classify(new double[]{0.05, 0.72, 0.08, 0.51});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, clearWinner.status);
        assertEquals(1, clearWinner.optionIndex);
        assertEquals(0.21, clearWinner.margin, 0.000001);

        // At exactly 0.14, do not choose either option automatically.
        OMRAnswerClassifier.Result boundary =
                classifier.classify(new double[]{0.05, 0.30, 0.08, 0.16});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, boundary.status);
        assertEquals(-1, boundary.optionIndex);
        assertEquals(0.14, boundary.margin, 0.000001);
    }
}
