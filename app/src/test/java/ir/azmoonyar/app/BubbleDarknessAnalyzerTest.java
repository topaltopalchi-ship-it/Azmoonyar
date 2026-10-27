package ir.azmoonyar.app;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BubbleDarknessAnalyzerTest {
    @Test
    public void detectsFilledInteriorAgainstLocalPaperReference() {
        int width = 41;
        int height = 41;
        byte[] blank = new byte[width * height];
        byte[] filled = new byte[width * height];
        java.util.Arrays.fill(blank, (byte) 230);
        java.util.Arrays.fill(filled, (byte) 230);

        // Darken only the inner area; keep the printed outline/reference ring
        // unchanged. The luma input deliberately contains no ink-color data.
        for (int y = 14; y <= 26; y++) {
            for (int x = 14; x <= 26; x++) {
                int dx = x - 20;
                int dy = y - 20;
                if (dx * dx + dy * dy <= 5 * 5) filled[y * width + x] = 35;
            }
        }

        double blankScore = BubbleDarknessAnalyzer.score(blank, width, height, 20, 20, 8);
        double filledScore = BubbleDarknessAnalyzer.score(filled, width, height, 20, 20, 8);
        assertTrue("An empty bubble should have a low score", blankScore < 0.10);
        assertTrue("A filled bubble should score darker than an empty bubble",
                filledScore > blankScore + 0.35);
    }
    @Test
    public void rejectsNonFiniteGeometryInsteadOfReturningMisleadingScore() {
        byte[] image = new byte[41 * 41];
        java.util.Arrays.fill(image, (byte) 230);
        try {
            BubbleDarknessAnalyzer.score(image, 41, 41, Double.NaN, 20, 8);
            fail("NaN center must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: invalid geometry must never look like a valid measurement.
        }
        try {
            BubbleDarknessAnalyzer.score(image, 41, 41, 20, 20, Double.POSITIVE_INFINITY);
            fail("Infinite radius must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
