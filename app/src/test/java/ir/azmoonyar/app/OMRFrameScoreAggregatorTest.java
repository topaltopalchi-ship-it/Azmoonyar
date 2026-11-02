package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class OMRFrameScoreAggregatorTest {
    @Test public void medianRejectsOneExtremeFrame() {
        assertEquals(0.72, OMRFrameScoreAggregator.median(new double[]{0.70, 0.72, 0.73, 0.05, 0.71}), 0.0001);
    }

    @Test public void evenFrameCountUsesMeanOfMiddlePair() {
        assertEquals(0.50, OMRFrameScoreAggregator.median(new double[]{0.2, 0.8}), 0.0001);
    }

    @Test public void rejectsInvalidScores() {
        try {
            OMRFrameScoreAggregator.median(new double[]{0.2, Double.NaN});
            fail("Expected invalid score to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Scores"));
        }
    }
    @Test public void conflictingClearFrameAnswersRequireReview() {
        OMRAnswerClassifier classifier = new OMRAnswerClassifier();
        java.util.List<OMRSheetReader.QuestionResult> first = java.util.Arrays.asList(
                new OMRSheetReader.QuestionResult(0, new double[]{0.80, 0.10, 0.08, 0.07},
                        classifier.classify(new double[]{0.80, 0.10, 0.08, 0.07})));
        java.util.List<OMRSheetReader.QuestionResult> second = java.util.Arrays.asList(
                new OMRSheetReader.QuestionResult(0, new double[]{0.10, 0.82, 0.08, 0.07},
                        classifier.classify(new double[]{0.10, 0.82, 0.08, 0.07})));
        java.util.List<OMRFrameScoreAggregator.AggregatedQuestion> result =
                OMRFrameScoreAggregator.aggregate(java.util.Arrays.asList(first, second), classifier);
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.get(0).classification.status);
        assertEquals(-1, result.get(0).classification.optionIndex);
    }

    @Test public void laplacianSharpnessRejectsUniformSoftFrame() {
        byte[] soft = new byte[64 * 64];
        java.util.Arrays.fill(soft, (byte) 128);
        byte[] sharp = new byte[64 * 64];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            sharp[y * 64 + x] = (byte) (((x + y) & 1) == 0 ? 0 : 255);
        assertEquals(0.0, OMRFrameQuality.laplacianVariance(soft, 64, 64), 0.0001);
        assertTrue(OMRFrameQuality.laplacianVariance(sharp, 64, 64) > 24.0);
    }

}
