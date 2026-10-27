package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OmrTemplateLayoutTest {
    private static final double EPSILON = 0.0001;

    @Test public void mapsFirstQuestionToFirstColumnAndOptionsLeftToRight() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        assertEquals(100, centers.length);
        assertEquals(173.8625, centers[0][0][0], EPSILON);
        assertEquals(155.2875, centers[0][1][0], EPSILON);
        assertEquals(136.7125, centers[0][2][0], EPSILON);
        assertEquals(118.1375, centers[0][3][0], EPSILON);
        assertEquals(56.575, centers[0][0][1], EPSILON);
    }

    @Test public void mapsQuestionFiftyAndFiftyOneToBottomAndTopOfTheirColumns() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        assertEquals(274.625, centers[49][0][1], EPSILON);
        assertEquals(78.8625, centers[50][0][0], EPSILON);
        assertEquals(56.575, centers[50][0][1], EPSILON);
    }

    @Test public void returnsOnlyQuestionsPresentOnFinalPartialPage() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(123, 1);
        assertEquals(23, centers.length);
        assertEquals(78.8625, centers[0][0][0], EPSILON);
        assertEquals(56.575, centers[0][0][1], EPSILON);
        assertEquals(154.475, centers[22][0][1], EPSILON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPageOutsidePrintedQuestionRange() {
        OmrTemplateLayout.bubbleCentersForPage(20, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidQuestionCount() {
        OmrTemplateLayout.bubbleCentersForPage(0, 0);
    }
}
