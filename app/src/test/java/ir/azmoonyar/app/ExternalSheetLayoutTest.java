package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExternalSheetLayoutTest {
    @Test public void singleColumnUsesEvenRowsAndOptions() {
        double[] first = ExternalSheetLayout.bubbleCenter(0, 0, 10, 4,
                "single", 0.1, 0.2, 0.9, 0.8);
        double[] last = ExternalSheetLayout.bubbleCenter(9, 3, 10, 4,
                "single", 0.1, 0.2, 0.9, 0.8);
        assertEquals(0.2, first[0], 1e-9);
        assertEquals(0.23, first[1], 1e-9);
        assertEquals(0.8, last[0], 1e-9);
        assertEquals(0.77, last[1], 1e-9);
    }

    @Test public void doubleColumnSplitsQuestionsAndStartsAtRight() {
        double[] q1 = ExternalSheetLayout.bubbleCenter(0, 0, 10, 4,
                "double", 0.1, 0.1, 0.9, 0.9);
        double[] q5 = ExternalSheetLayout.bubbleCenter(4, 0, 10, 4,
                "double", 0.1, 0.1, 0.9, 0.9);
        double[] q6 = ExternalSheetLayout.bubbleCenter(5, 0, 10, 4,
                "double", 0.1, 0.1, 0.9, 0.9);
        assertTrue("first question should be in right half", q1[0] > 0.5);
        assertTrue("question five should remain in right half", q5[0] > 0.5);
        assertTrue("question six should be in left half", q6[0] < 0.5);
        assertEquals(q1[1], q6[1], 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidBounds() {
        ExternalSheetLayout.bubbleCenter(0, 0, 10, 4,
                "single", 0.8, 0.2, 0.1, 0.9);
    }
}
