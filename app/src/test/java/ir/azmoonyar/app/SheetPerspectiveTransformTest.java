package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SheetPerspectiveTransformTest {
    @Test public void mapsCanonicalRectangleWithoutDistortion() {
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(20, 30),
                new SheetPerspectiveTransform.Point(220, 30),
                new SheetPerspectiveTransform.Point(220, 330),
                new SheetPerspectiveTransform.Point(20, 330),
                200, 300);
        SheetPerspectiveTransform.Point p = transform.toImage(50, 150);
        assertEquals(70.0, p.x, 1e-6);
        assertEquals(180.0, p.y, 1e-6);
    }

    @Test public void mapsAllFourCornersUnderPerspective() {
        SheetPerspectiveTransform.Point tl = new SheetPerspectiveTransform.Point(40, 20);
        SheetPerspectiveTransform.Point tr = new SheetPerspectiveTransform.Point(300, 45);
        SheetPerspectiveTransform.Point br = new SheetPerspectiveTransform.Point(270, 420);
        SheetPerspectiveTransform.Point bl = new SheetPerspectiveTransform.Point(15, 370);
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                tl, tr, br, bl, 210, 297);
        assertPoint(tl, transform.toImage(0, 0));
        assertPoint(tr, transform.toImage(210, 0));
        assertPoint(br, transform.toImage(210, 297));
        assertPoint(bl, transform.toImage(0, 297));
    }

    @Test public void rejectsDegenerateCorners() {
        try {
            SheetPerspectiveTransform.fromPageCorners(
                    new SheetPerspectiveTransform.Point(0, 0),
                    new SheetPerspectiveTransform.Point(10, 0),
                    new SheetPerspectiveTransform.Point(20, 0),
                    new SheetPerspectiveTransform.Point(30, 0), 210, 297);
            fail("Expected degenerate corners to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: four collinear points cannot define a page transform.
        }
    }


    @Test public void mapsFromRegistrationMarkCenters() {
        SheetPerspectiveTransform.Point[] template = {
                new SheetPerspectiveTransform.Point(6.5, 6.5),
                new SheetPerspectiveTransform.Point(203.5, 6.5),
                new SheetPerspectiveTransform.Point(203.5, 290.5),
                new SheetPerspectiveTransform.Point(6.5, 290.5)
        };
        SheetPerspectiveTransform.Point[] image = {
                new SheetPerspectiveTransform.Point(40, 25),
                new SheetPerspectiveTransform.Point(430, 55),
                new SheetPerspectiveTransform.Point(390, 600),
                new SheetPerspectiveTransform.Point(15, 560)
        };
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromControlPoints(
                template, image, 210, 297);
        for (int i = 0; i < 4; i++) {
            SheetPerspectiveTransform.Point actual = transform.toImage(
                    template[i].x, template[i].y);
            assertPoint(image[i], actual);
        }
    }

    private static void assertPoint(SheetPerspectiveTransform.Point expected,
                                    SheetPerspectiveTransform.Point actual) {
        assertEquals(expected.x, actual.x, 1e-5);
        assertEquals(expected.y, actual.y, 1e-5);
    }
}
