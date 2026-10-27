package ir.azmoonyar.app;

/**
 * Projective mapping between the canonical printed page and camera-image pixels.
 *
 * Corner points must be the centers of the four registration marks, ordered
 * top-left, top-right, bottom-right, bottom-left. This class does not detect
 * marks itself and must not be used unless all four candidates are validated.
 */
public final class SheetPerspectiveTransform {
    public static final class Point {
        public final double x;
        public final double y;

        public Point(double x, double y) {
            if (!finite(x) || !finite(y)) {
                throw new IllegalArgumentException("مختصات نقطه معتبر نیست.");
            }
            this.x = x;
            this.y = y;
        }
    }

    private final double[] h;
    private final double pageWidth;
    private final double pageHeight;

    private SheetPerspectiveTransform(double[] coefficients, double pageWidth, double pageHeight) {
        this.h = coefficients;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
    }

    public static SheetPerspectiveTransform fromPageCorners(
            Point topLeft, Point topRight, Point bottomRight, Point bottomLeft,
            double pageWidth, double pageHeight) {
        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null
                || !finite(pageWidth) || !finite(pageHeight)
                || pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("گوشه‌ها یا ابعاد برگه معتبر نیستند.");
        }

        Point[] src = {
                new Point(0, 0), new Point(pageWidth, 0),
                new Point(pageWidth, pageHeight), new Point(0, pageHeight)
        };
        Point[] dst = {topLeft, topRight, bottomRight, bottomLeft};
        double[][] equations = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = src[i].x, y = src[i].y;
            double u = dst[i].x, v = dst[i].y;
            int r = i * 2;
            equations[r][0] = x;
            equations[r][1] = y;
            equations[r][2] = 1;
            equations[r][6] = -u * x;
            equations[r][7] = -u * y;
            equations[r][8] = u;

            equations[r + 1][3] = x;
            equations[r + 1][4] = y;
            equations[r + 1][5] = 1;
            equations[r + 1][6] = -v * x;
            equations[r + 1][7] = -v * y;
            equations[r + 1][8] = v;
        }

        double[] coefficients = solve(equations);
        // Reject nearly collapsed or self-crossing corner orderings. The
        // determinant is checked by solve(); this area check catches flat pages.
        double area = polygonArea(topLeft, topRight, bottomRight, bottomLeft);
        if (Math.abs(area) < 16.0) {
            throw new IllegalArgumentException("چهار گوشه برای اصلاح پرسپکتیو به‌اندازه کافی از هم فاصله ندارند.");
        }
        return new SheetPerspectiveTransform(coefficients, pageWidth, pageHeight);
    }

    /**
     * Builds a mapping from four known template coordinates (for example,
     * registration-mark centers) to their measured image coordinates.
     */
    public static SheetPerspectiveTransform fromControlPoints(
            Point[] templatePoints, Point[] imagePoints, double pageWidth, double pageHeight) {
        if (templatePoints == null || imagePoints == null
                || templatePoints.length != 4 || imagePoints.length != 4
                || !finite(pageWidth) || !finite(pageHeight)
                || pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("چهار نقطهٔ متناظر و ابعاد برگه لازم است.");
        }
        double[][] equations = new double[8][9];
        for (int i = 0; i < 4; i++) {
            Point src = templatePoints[i], dst = imagePoints[i];
            if (src == null || dst == null || src.x < 0 || src.y < 0
                    || src.x > pageWidth || src.y > pageHeight) {
                throw new IllegalArgumentException("نقطهٔ متناظر خارج از محدوده یا نامعتبر است.");
            }
            double x = src.x, y = src.y, u = dst.x, v = dst.y;
            int r = i * 2;
            equations[r][0] = x; equations[r][1] = y; equations[r][2] = 1;
            equations[r][6] = -u * x; equations[r][7] = -u * y; equations[r][8] = u;
            equations[r + 1][3] = x; equations[r + 1][4] = y; equations[r + 1][5] = 1;
            equations[r + 1][6] = -v * x; equations[r + 1][7] = -v * y;
            equations[r + 1][8] = v;
        }
        double[] coefficients = solve(equations);
        double sourceArea = polygonArea(templatePoints[0], templatePoints[1],
                templatePoints[2], templatePoints[3]);
        double imageArea = polygonArea(imagePoints[0], imagePoints[1],
                imagePoints[2], imagePoints[3]);
        if (Math.abs(sourceArea) < 1e-6 || Math.abs(imageArea) < 16.0) {
            throw new IllegalArgumentException("نقاط ثبت برای محاسبهٔ پرسپکتیو به‌اندازه کافی فاصله ندارند.");
        }
        return new SheetPerspectiveTransform(coefficients, pageWidth, pageHeight);
    }

    /** Maps a point in the printed template coordinate system into the image. */
    public Point toImage(double pageX, double pageY) {
        if (!finite(pageX) || !finite(pageY)
                || pageX < 0 || pageY < 0 || pageX > pageWidth || pageY > pageHeight) {
            throw new IllegalArgumentException("نقطه خارج از محدودهٔ برگه است.");
        }
        double denominator = h[6] * pageX + h[7] * pageY + 1.0;
        if (Math.abs(denominator) < 1e-10) {
            throw new IllegalArgumentException("تبدیل پرسپکتیو در این نقطه ناپایدار است.");
        }
        double x = (h[0] * pageX + h[1] * pageY + h[2]) / denominator;
        double y = (h[3] * pageX + h[4] * pageY + h[5]) / denominator;
        if (!finite(x) || !finite(y)) {
            throw new IllegalArgumentException("تبدیل پرسپکتیو نتیجهٔ نامعتبر تولید کرد.");
        }
        return new Point(x, y);
    }

    public double getPageWidth() { return pageWidth; }
    public double getPageHeight() { return pageHeight; }

    private static double[] solve(double[][] a) {
        final int n = 8;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row;
            }
            if (Math.abs(a[pivot][col]) < 1e-10) {
                throw new IllegalArgumentException("گوشه‌های برگه برای محاسبهٔ پرسپکتیو هم‌خط یا نامعتبرند.");
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;

            double divisor = a[col][col];
            for (int j = col; j <= n; j++) a[col][j] /= divisor;
            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = a[row][col];
                for (int j = col; j <= n; j++) a[row][j] -= factor * a[col][j];
            }
        }
        double[] result = new double[8];
        for (int i = 0; i < n; i++) result[i] = a[i][n];
        return result;
    }

    private static double polygonArea(Point a, Point b, Point c, Point d) {
        return (a.x * b.y - b.x * a.y) + (b.x * c.y - c.x * b.y)
                + (c.x * d.y - d.x * c.y) + (d.x * a.y - a.x * d.y);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
