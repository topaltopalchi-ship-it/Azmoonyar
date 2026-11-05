package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test
    public void darkestBubbleWinsWithSeveralNonzeroScores() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.128, 0.226, 0.113, 0.129});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void darkestBubbleWinsWithoutFixedThreshold() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.04, 0.08, 0.01});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(2, result.optionIndex);
    }

    @Test
    public void exactZeroScoresAreBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.0, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void exactTieNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.133, 0.133, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void nearTieNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.251, 0.2509995, 0.12, 0.10});
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
}
