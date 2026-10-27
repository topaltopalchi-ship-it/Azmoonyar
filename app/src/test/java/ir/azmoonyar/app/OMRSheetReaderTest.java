package ir.azmoonyar.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

}
