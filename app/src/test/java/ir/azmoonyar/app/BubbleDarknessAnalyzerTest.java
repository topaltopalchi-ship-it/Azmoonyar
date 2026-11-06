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
    public void detectsFillInsidePerspectiveScaledEllipticalBubble() {
        int width = 61, height = 51;
        byte[] image = new byte[width * height];
        java.util.Arrays.fill(image, (byte) 230);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double nx = (x - 30.0) / 12.0;
                double ny = (y - 25.0) / 8.0;
                if (nx * nx + ny * ny <= 0.82 * 0.82) {
                    image[y * width + x] = 30;
                }
            }
        }
        double score = BubbleDarknessAnalyzer.scoreEllipse(
                image, width, height, 30, 25, 12, 8);
        assertTrue("A filled perspective-scaled bubble should be detected", score > 0.35);
    }

    @Test
    public void printedOutlineOutsideFillRadiusDoesNotCountAsASelectedBubble() {
        int width = 61, height = 61;
        byte[] image = new byte[width * height];
        java.util.Arrays.fill(image, (byte) 230);
        // Simulate only the printed outline at radius 10 px. The supplied 9 px
        // sample radius is the usable interior and must not include that outline.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double distance = Math.hypot(x - 30.0, y - 30.0);
                if (distance >= 9.5 && distance <= 10.5) image[y * width + x] = 20;
            }
        }
        double score = BubbleDarknessAnalyzer.scoreEllipse(
                image, width, height, 30, 30, 9, 9);
        assertTrue("The printed circle outline alone must remain blank", score < 0.10);
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
        try {
            BubbleDarknessAnalyzer.score(image, 41, 41, 3, 20, 8);
            fail("A bubble with a clipped local reference ring must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: partially visible bubbles must go to review, not scoring.
        }
    }
}
