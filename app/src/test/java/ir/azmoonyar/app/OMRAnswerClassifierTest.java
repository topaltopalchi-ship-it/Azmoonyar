package ir.azmoonyar.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test
    public void detectsStrongSingleMark() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.12, 0.81, 0.18, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void detectsWhitePageWithOnlyTinyNoiseAsBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.004, 0.009, 0.011, 0.006});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void sendsFaintPossiblePencilMarkToReviewInsteadOfGuessing() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.00, 0.06, 0.01});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void flagsMultipleMarkedBubbles() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.MULTIPLE_MARKS, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void acceptsLowContrastMarkWhenItPassesTheConfiguredFloor() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.00, 0.10, 0.01});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(2, result.optionIndex);
        org.junit.Assert.assertEquals(0.20, result.strongestScore, 0.000001);
    }

    @Test
    public void exactTieIsSentForReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.20, 0.20, 0.10, 0.05});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void rejectsThresholdsOutsideNormalizedRange() {
        try {
            new OMRAnswerClassifier(-0.1, 0.0, 0.1);
            fail("Expected invalid thresholds to be rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            new OMRAnswerClassifier(0.5, 0.6, 0.1);
            fail("Expected blank threshold above mark threshold to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void rejectsWrongNumberOfBubbles() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 0.3});
            fail("Expected invalid input to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void rejectsScoresOutsideRange() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 1.1, 0.3});
            fail("Expected invalid score to be rejected");
        } catch (IllegalArgumentException expected) { }
    }
}
