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
    public void detectsBlankQuestion() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.04, 0.09, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void flagsMultipleMarkedBubbles() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.MULTIPLE_MARKS, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void sendsWeakEvidenceToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.40, 0.18, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void sendsLowMarginSingleMarkToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.50, 0.39, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
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
    @Test
    public void rejectsNonFiniteThresholds() {
        assertInvalidThreshold(Double.NaN, 0.22, 0.12);
        assertInvalidThreshold(0.42, Double.NaN, 0.12);
        assertInvalidThreshold(0.42, 0.22, Double.NaN);
        assertInvalidThreshold(Double.POSITIVE_INFINITY, 0.22, 0.12);
    }

    private void assertInvalidThreshold(double mark, double blank, double margin) {
        try {
            new OMRAnswerClassifier(mark, blank, margin);
            fail("Expected invalid threshold to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

}
