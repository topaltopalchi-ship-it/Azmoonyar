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
}
