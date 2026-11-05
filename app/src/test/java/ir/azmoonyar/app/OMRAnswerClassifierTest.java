package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test
    public void clearlyDarkerBubbleIsSelected() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.128, 0.326, 0.113, 0.129});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void gapBelowOneTenthNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.40, 0.301, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void gapExactlyOneTenthNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.50, 0.40, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void gapAboveOneTenthSelectsDarkerBubble() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.51, 0.40, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void exactZeroScoresAreBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.0, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void allScoresBelowPointOneAreBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.01, 0.05, 0.099, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void scoreExactlyPointOneIsNotWhite() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.10, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void twoCloseScoresBelowPointTwoDoNotTriggerReview() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.19, 0.15, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void twoCloseScoresOnlyTriggerReviewWhenBothExceedPointTwo() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.31, 0.25, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void secondScoreExactlyPointTwoDoesNotTriggerDoubleMarkReview() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.30, 0.20, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void twoQualifyingMarksWithGapAbovePointOneSelectDarkerOption() {
        OMRAnswerClassifier.Result result = classifier.classify(
                new double[]{0.45, 0.30, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void exactTieNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.333, 0.333, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
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
