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


    @Test public void bubbleLayoutMatchesPrintedFourColumnA4Geometry() {
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(100, 0);
        assertEquals(100, centers.length);

        // CSS: 12 mm side margins, four columns with 3 mm gaps,
        // 0.25 mm column borders, 1 mm padding, 7 mm number cell,
        // 8.3 mm answer rows and a 7 mm header.
        assertEquals(24.59375, centers[0][0][0], 1e-5);
        assertEquals(33.28125, centers[0][1][0], 1e-5);
        assertEquals(56.4, centers[0][0][1], 1e-5);
        assertEquals(56.4 + 24 * 8.3, centers[24][0][1], 1e-5);
        assertEquals(24.59375 + 3 * (44.25 + 3.0), centers[75][0][0], 1e-5);
    }

    @Test public void rejectsQuestionCountOutsidePrintedTemplateCapacity() {
        try {
            OmrTemplateLayout.bubbleCentersForPage(101, 0);
            fail("Expected more than 100 questions to be rejected for one page");
        } catch (IllegalArgumentException expected) {
            // Expected: this template is a single A4 page with 100 bubbles rows.
        }
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


    @Test public void rejectsCrossedCornerOrder() {
        try {
            SheetPerspectiveTransform.fromPageCorners(
                    new SheetPerspectiveTransform.Point(10, 10),
                    new SheetPerspectiveTransform.Point(200, 10),
                    new SheetPerspectiveTransform.Point(10, 200),
                    new SheetPerspectiveTransform.Point(200, 200),
                    210, 297);
            fail("Expected crossed corner order to be rejected");
        } catch (IllegalArgumentException expected) {
            // Wrongly ordered marker candidates must not be accepted as a page.
        }
    }

    private static void assertPoint(SheetPerspectiveTransform.Point expected,
                                    SheetPerspectiveTransform.Point actual) {
        assertEquals(expected.x, actual.x, 1e-5);
        assertEquals(expected.y, actual.y, 1e-5);
    }
}
