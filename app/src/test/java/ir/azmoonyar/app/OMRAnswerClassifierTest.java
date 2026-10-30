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
    public void choosesHigherScoreWhenTwoOptionsAreMarked() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void choosesHighestAmongThreeMarkedOptions() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.42, 0.63, 0.31, 0.12});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void blankOnlyWhenNoOptionHasMarkScore() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.04, 0.09, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void treatsWeakScoresBelowMarkThresholdAsBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.25, 0.18, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
    }

    @Test
    public void exactTieForHighestMarkedOptionGoesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.71, 0.09});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void acceptsSingleModerateMarkWithoutMarginRequirement() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.00, 0.00, 0.08, 0.40});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(3, result.optionIndex);
    }

    @Test
    public void rejectsInvalidThreshold() {
        try {
            new OMRAnswerClassifier(1.1);
            fail("Expected invalid threshold to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void rejectsWrongNumberOfBubbles() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 0.3});
            fail("Expected invalid input to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void rejectsScoresOutsideRange() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 1.1, 0.3});
            fail("Expected invalid score to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
