package ir.azmoonyar.app;

/**
 * Color-independent bubble measurement for OMR.
 *
 * Supply the camera's grayscale/luma plane (for CameraX YUV_420_888 this is
 * the Y plane). The score compares the inner area of a bubble with a nearby
 * paper-reference ring, so printed bubble outlines and uneven illumination
 * have less influence. It intentionally measures darkness, not ink hue.
 *
 * This is a measurement primitive, not a complete sheet detector: callers must
 * first locate the sheet, correct perspective, and map bubble centers/radii.
 */
public final class BubbleDarknessAnalyzer {
    private BubbleDarknessAnalyzer() {}

    /**
     * @param luminance grayscale/luma bytes, one byte per pixel
     * @param width image width
     * @param height image height
     * @param centerX bubble center x in image coordinates
     * @param centerY bubble center y in image coordinates
     * @param radius bubble radius in image pixels
     * @return normalized relative darkness in [0,1]; larger means more filled
     */
    public static double score(byte[] luminance, int width, int height,
                               double centerX, double centerY, double radius) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(radius) || !finite(centerX) || !finite(centerY)
                || radius < 2 || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        // The supplied radius is the usable bubble interior, not the outer
        // printed diameter. Sample all of it; keep the paper reference outside it.
        int innerRadius = Math.max(1, (int) Math.floor(radius));
        int ringInner = Math.max(innerRadius + 1, (int) Math.ceil(radius * 1.25));
        int ringOuter = Math.max(ringInner + 1, (int) Math.ceil(radius * 1.50));
        // Do not score a partially visible bubble: clipping the reference ring at
        // an image edge can make an off-sheet bubble look confidently blank/filled.
        if (centerX - ringOuter < 0 || centerY - ringOuter < 0
                || centerX + ringOuter >= width || centerY + ringOuter >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }

        int minX = Math.max(0, (int) Math.floor(centerX - ringOuter));
        int maxX = Math.min(width - 1, (int) Math.ceil(centerX + ringOuter));
        int minY = Math.max(0, (int) Math.floor(centerY - ringOuter));
        int maxY = Math.min(height - 1, (int) Math.ceil(centerY + ringOuter));

        long innerSum = 0;
        int innerCount = 0;
        long paperSum = 0;
        int paperCount = 0;
        double innerR2 = (double) innerRadius * innerRadius;
        double ringIn2 = (double) ringInner * ringInner;
        double ringOut2 = (double) ringOuter * ringOuter;

        for (int y = minY; y <= maxY; y++) {
            double dy = y - centerY;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double d2 = (double) dx * dx + (double) dy * dy;
                int value = luminance[y * width + x] & 0xff;
                if (d2 <= innerR2) {
                    innerSum += value;
                    innerCount++;
                } else if (d2 >= ringIn2 && d2 <= ringOut2) {
                    paperSum += value;
                    paperCount++;
                }
            }
        }

        if (innerCount < 4 || paperCount < 4) {
            throw new IllegalArgumentException("کیفیت تصویر برای سنجش حباب کافی نیست.");
        }

        double innerMean = innerSum / (double) innerCount;
        double paperMean = paperSum / (double) paperCount;
        // Relative local contrast is less sensitive to shadows than a fixed
        // global threshold. Clamp so the downstream classifier receives [0,1].
        double relativeDarkness = (paperMean - innerMean) / Math.max(48.0, paperMean);
        // Restore the previously tested mild gain for genuine low-contrast marks.
        // Keep near-zero paper noise untouched so blank bubbles are not promoted.
        if (relativeDarkness > 0.08) {
            relativeDarkness *= 1.15;
        }
        return Math.max(0.0, Math.min(1.0, relativeDarkness));
    }

    /**
     * Scores a printed circular bubble after perspective mapping. A physical
     * circle may appear as an ellipse in camera pixels, so preserve both mapped
     * radii instead of averaging them into a smaller/larger circular sample.
     */
    public static double scoreEllipse(byte[] luminance, int width, int height,
            double centerX, double centerY, double radiusX, double radiusY) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(radiusX) || !finite(radiusY)
                || !finite(centerX) || !finite(centerY)
                || radiusX < 2 || radiusY < 2
                || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        // radiusX/radiusY are the mapped fillable radii after removing the
        // printed outline. The fill score covers that full interior disk/ellipse.
        final double innerLimit = 1.0;
        final double ringInnerLimit = 1.25 * 1.25;
        final double ringOuterLimit = 1.50 * 1.50;
        int extentX = (int) Math.ceil(radiusX * 1.50);
        int extentY = (int) Math.ceil(radiusY * 1.50);
        if (centerX - extentX < 0 || centerY - extentY < 0
                || centerX + extentX >= width || centerY + extentY >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }

        int minX = (int) Math.floor(centerX - extentX);
        int maxX = (int) Math.ceil(centerX + extentX);
        int minY = (int) Math.floor(centerY - extentY);
        int maxY = (int) Math.ceil(centerY + extentY);
        long innerSum = 0;
        int innerCount = 0;
        long paperSum = 0;
        int paperCount = 0;
        double radiusX2 = radiusX * radiusX;
        double radiusY2 = radiusY * radiusY;

        for (int y = minY; y <= maxY; y++) {
            double dy = y - centerY;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double normalizedDistance2 = dx * dx / radiusX2 + dy * dy / radiusY2;
                int value = luminance[y * width + x] & 0xff;
                if (normalizedDistance2 <= innerLimit) {
                    innerSum += value;
                    innerCount++;
                } else if (normalizedDistance2 >= ringInnerLimit
                        && normalizedDistance2 <= ringOuterLimit) {
                    paperSum += value;
                    paperCount++;
                }
            }
        }

        if (innerCount < 4 || paperCount < 4) {
            throw new IllegalArgumentException("کیفیت تصویر برای سنجش حباب کافی نیست.");
        }
        double innerMean = innerSum / (double) innerCount;
        double paperMean = paperSum / (double) paperCount;
        double relativeDarkness = (paperMean - innerMean) / Math.max(48.0, paperMean);
        if (relativeDarkness > 0.08) relativeDarkness *= 1.15;
        return Math.max(0.0, Math.min(1.0, relativeDarkness));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
