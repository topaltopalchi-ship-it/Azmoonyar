package ir.azmoonyar.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OMRSheetReaderTest {
    @Test public void readsStrongFillAndLeavesBlankBubblesUnmarked() {
        int width = 120, height = 60;
        byte[] image = new byte[width * height];
        java.util.Arrays.fill(image, (byte) 230);
        double[][][] centers = {{{20, 30}, {45, 30}, {70, 30}, {95, 30}}};
        for (int y = 24; y <= 36; y++) {
            for (int x = 14; x <= 26; x++) {
                int dx = x - 20, dy = y - 30;
                if (dx * dx + dy * dy <= 5 * 5) image[y * width + x] = 30;
            }
        }
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(0, 0),
                new SheetPerspectiveTransform.Point(width - 1, 0),
                new SheetPerspectiveTransform.Point(width - 1, height - 1),
                new SheetPerspectiveTransform.Point(0, height - 1),
                width - 1, height - 1);
        List<OMRSheetReader.QuestionResult> result = new OMRSheetReader().read(
                image, width, height, transform, centers, 8);
        assertEquals(1, result.size());
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.get(0).classification.status);
        assertEquals(0, result.get(0).classification.optionIndex);
        assertTrue(result.get(0).darkness[0] > result.get(0).darkness[1] + 0.25);
    }
    @Test public void readsUsingCanonicalPrintedTemplateCoordinates() {
        int width = 840, height = 1188;
        byte[] image = new byte[width * height];
        java.util.Arrays.fill(image, (byte) 230);
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(0, 0),
                new SheetPerspectiveTransform.Point(width - 1, 0),
                new SheetPerspectiveTransform.Point(width - 1, height - 1),
                new SheetPerspectiveTransform.Point(0, height - 1),
                OmrTemplateLayout.PAGE_WIDTH_MM, OmrTemplateLayout.PAGE_HEIGHT_MM);

        // Simulate a strongly filled first option of the first question.
        SheetPerspectiveTransform.Point selected = transform.toImage(
                centers[0][0][0], centers[0][0][1]);
        int cx = (int) Math.round(selected.x);
        int cy = (int) Math.round(selected.y);
        for (int y = cy - 4; y <= cy + 4; y++) {
            for (int x = cx - 4; x <= cx + 4; x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= 16) {
                    image[y * width + x] = 25;
                }
            }
        }

        List<OMRSheetReader.QuestionResult> result = new OMRSheetReader().read(
                image, width, height, transform, centers, 1.35);
        assertEquals(100, result.size());
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.get(0).classification.status);
        assertEquals(0, result.get(0).classification.optionIndex);
    }


    @Test public void samplesRealOvalDimensionsConsistentlyAcrossRows() {
        final int width = 1000, height = 1400;
        final double scale = 4.5;
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(20, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20 + 297 * scale),
                new SheetPerspectiveTransform.Point(20, 20 + 297 * scale),
                210, 297);
        double[][][] centers = {
                {{45, 55}, {60, 55}, {75, 55}, {90, 55}},
                {{45, 95}, {60, 95}, {75, 95}, {90, 95}}
        };
        byte[] photo = new byte[width * height];
        java.util.Arrays.fill(photo, (byte) 245);

        // Fill A on the first row and D on the second with equal elliptical marks.
        int[] selectedOptions = {0, 3};
        for (int q = 0; q < centers.length; q++) {
            SheetPerspectiveTransform.Point p = transform.toImage(
                    centers[q][selectedOptions[q]][0], centers[q][selectedOptions[q]][1]);
            int cx = (int) Math.round(p.x), cy = (int) Math.round(p.y);
            for (int y = cy - 8; y <= cy + 8; y++) {
                for (int x = cx - 10; x <= cx + 10; x++) {
                    double nx = (x - cx) / 9.0, ny = (y - cy) / 7.0;
                    if (nx * nx + ny * ny <= 1.0) photo[y * width + x] = 55;
                }
            }
        }

        List<OMRSheetReader.QuestionResult> result = new OMRSheetReader().read(
                photo, width, height, transform, centers, 2.15, 1.70);
        assertEquals(2, result.size());
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.get(0).classification.status);
        assertEquals(0, result.get(0).classification.optionIndex);
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.get(1).classification.status);
        assertEquals(3, result.get(1).classification.optionIndex);
        assertTrue("Equivalent oval marks on different rows should have comparable scores",
                Math.abs(result.get(0).classification.strongestScore
                        - result.get(1).classification.strongestScore) < 0.08);
    }

    @Test public void rejectsEmptyOrOversizedPageInsteadOfReturningEmptyResults() {
        byte[] image = new byte[120 * 60];
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(0, 0),
                new SheetPerspectiveTransform.Point(119, 0),
                new SheetPerspectiveTransform.Point(119, 59),
                new SheetPerspectiveTransform.Point(0, 59), 119, 59);
        try {
            new OMRSheetReader().read(image, 120, 60, transform, new double[0][][], 8);
            fail("An empty answer page must not be accepted");
        } catch (IllegalArgumentException expected) {
            // Expected: empty results could otherwise be mistaken for a successful scan.
        }
        try {
            new OMRSheetReader().read(image, 120, 60, transform, new double[101][4][2], 8);
            fail("A page larger than the supported 100 questions must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: this reader handles one page, with at most 100 questions.
        }
    }
}
