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
        assertEquals(0.81, result.strongestScore, 0.000001);
    }

    @Test
    public void detectsBlankQuestion() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.04, 0.09, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void sendsMultipleMarkedBubblesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void sendsCloseVisibleBubbleScoresToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.251, 0.250, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void sendsTwoStrongCloseBubblesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.500, 0.499, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void clearlyDarkerOptionStillWinsOverWeakSecondaryScore() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.500, 0.20, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void exactTwelvePointGapGoesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.52, 0.40, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void gapOfThirteenPointsStillGoesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.521, 0.400, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void detectsModerateRealPhotoMarkWhenClearlyStronger() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.00, 0.00, 0.08, 0.40});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(3, result.optionIndex);
    }

    @Test
    public void acceptsClearlyDominantMarkAtTwentyFourPercent() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.24, 0.00, 0.00, 0.00});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void scoreAboveTwelvePercentWinsWhenGapExceedsFourteenPoints() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.23, 0.00, 0.00, 0.00});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void exactFourteenPointGapGoesToReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.30, 0.16, 0.00, 0.00});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void nearPaperWhiteScoresRemainBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.00, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
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


    @Test
    public void calibratedThresholdResolvesProvidedQuestionsOneThroughSix() {
        double[][] samples = {
                {0.40, 0.12, 0.12, 0.09}, // Q1 A
                {0.25, 0.12, 0.14, 0.14}, // Q2 A
                {0.13, 0.12, 0.19, 0.13}, // Q3 C
                {0.12, 0.11, 0.17, 0.14}, // Q4 C
                {0.13, 0.13, 0.14, 0.17}, // Q5 D
                {0.11, 0.07, 0.27, 0.14}  // Q6 C
        };
        int[] expected = {0, 0, 2, 2, 3, 2};
        for (int i = 0; i < samples.length; i++) {
            OMRAnswerClassifier.Result result = classifier.classify(samples[i]);
            assertEquals("Question " + (i + 1), OMRAnswerClassifier.Status.ANSWERED, result.status);
            assertEquals("Question " + (i + 1), expected[i], result.optionIndex);
        }
    }
}
