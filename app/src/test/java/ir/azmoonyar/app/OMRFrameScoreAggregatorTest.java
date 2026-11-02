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

}
