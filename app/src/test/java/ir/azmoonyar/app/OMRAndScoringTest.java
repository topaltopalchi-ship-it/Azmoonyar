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

    @Test public void onlyAllZeroQuestionIsBlankWithoutThreshold() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.0, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test public void closeDarkMarksAreSentToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.7600000, 0.7599995, 0.08, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test public void sendsAmbiguousMarkToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.4800000, 0.4799995, 0.09, 0.08});
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
        OMRAnswerClassifier.Result blank = classifier.classify(new double[]{0.0, 0.0, 0.0, 0.0});
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
        OMRAnswerClassifier.Result review = classifier.classify(new double[]{0.4800000, 0.4799995, 0.09, 0.08});
        ExamScorer.Result result = ExamScorer.score("A", Arrays.asList(review), 1.0, -0.25, 0.0);
        assertEquals(1, result.needsReview);
        assertTrue(Double.isNaN(result.percentage()));
    }

    @Test public void darkestBubbleWinsAndNearTieNeedsReview() {
        OMRAnswerClassifier.Result clearWinner =
                classifier.classify(new double[]{0.05, 0.72, 0.08, 0.51});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, clearWinner.status);
        assertEquals(1, clearWinner.optionIndex);
        assertEquals(0.21, clearWinner.margin, 0.000001);

        OMRAnswerClassifier.Result tie =
                classifier.classify(new double[]{0.05, 0.30, 0.08, 0.2999995});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, tie.status);
        assertEquals(-1, tie.optionIndex);
    }
}
