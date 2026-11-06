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
    public void darknessMustBeStrictlyAboveTwoTenthsToCountAsMarked() {
        OMRAnswerClassifier.Result atThreshold =
                classifier.classify(new double[]{0.20, 0.02, 0.01, 0.00});
        assertEquals(OMRAnswerClassifier.Status.BLANK, atThreshold.status);

        OMRAnswerClassifier.Result aboveThreshold =
                classifier.classify(new double[]{0.2001, 0.02, 0.01, 0.00});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, aboveThreshold.status);
        assertEquals(0, aboveThreshold.optionIndex);
    }

    @Test
    public void detectsBlankQuestion() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.04, 0.09, 0.10, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void twoQualifyingMarkedBubblesGoToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void twoQualifyingMarksWithLessThanOneTenthDifferenceGoToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.251, 0.250, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
        org.junit.Assert.assertEquals(0.251, result.strongestScore, 0.000001);
    }

    @Test
    public void twoQualifyingMarksWithSmallDifferenceGoToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.500, 0.499, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
        org.junit.Assert.assertEquals(0.500, result.strongestScore, 0.000001);
    }

    @Test
    public void detectsModerateRealPhotoMarkWhenClearlyStronger() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.00, 0.00, 0.08, 0.40});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(3, result.optionIndex);
    }

    @Test
    public void nearBlankLowContrastIsNotGuessedAsAnswered() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.00, 0.10, 0.08});
        // Every bubble is at or below the configured 0.20 threshold.
        // Selecting the darkest one would turn background noise into a student answer.
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
        org.junit.Assert.assertEquals(0.10, result.strongestScore, 0.000001);
    }

    @Test
    public void exactTieAboveThresholdIsSentForReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.21, 0.21, 0.10, 0.05});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void rejectsThresholdsOutsideNormalizedRange() {
        try {
            new OMRAnswerClassifier(-0.1, 0.0, 0.1);
            fail("Expected invalid thresholds to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            new OMRAnswerClassifier(0.5, 0.6, 0.1);
            fail("Expected blank threshold above mark threshold to be rejected");
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
