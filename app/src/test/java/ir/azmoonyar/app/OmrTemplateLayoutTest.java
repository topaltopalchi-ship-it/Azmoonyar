package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OmrTemplateLayoutTest {
    private static final double EPSILON = 0.0001;

    @Test public void mapsFirstQuestionToFirstColumnAndOptionsLeftToRight() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        assertEquals(100, centers.length);
        // Coordinates follow the actual HTML print layout: four question columns,
        // each with A-D bubble cells laid out left-to-right.
        assertEquals(24.59375, centers[0][0][0], EPSILON);
        assertEquals(33.28125, centers[0][1][0], EPSILON);
        assertEquals(41.96875, centers[0][2][0], EPSILON);
        assertEquals(50.65625, centers[0][3][0], EPSILON);
        assertEquals(56.4, centers[0][0][1], EPSILON);
    }

    @Test public void mapsQuestionFiftyAndFiftyOneToTopOfThirdColumnAndNextRow() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        assertEquals(255.6, centers[49][0][1], EPSILON);
        assertEquals(119.09375, centers[50][0][0], EPSILON);
        assertEquals(56.4, centers[50][0][1], EPSILON);
    }

    @Test public void returnsOnlyQuestionsRequestedForVariableLengthSheet() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(23, 0);
        assertEquals(23, centers.length);
        assertEquals(24.59375, centers[0][0][0], EPSILON);
        assertEquals(56.4, centers[0][0][1], EPSILON);
        assertEquals(239.0, centers[22][0][1], EPSILON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPageOutsidePrintedQuestionRange() {
        OmrTemplateLayout.bubbleCentersForPage(20, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreThanOneHundredQuestions() {
        OmrTemplateLayout.bubbleCentersForPage(101, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidQuestionCount() {
        OmrTemplateLayout.bubbleCentersForPage(0, 0);
    }
}
