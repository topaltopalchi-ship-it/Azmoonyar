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
        return score(luminance, width, height, centerX, centerY, radius, radius);
    }

    /**
     * Scores the interior of an elliptical printed bubble. The outline is
     * deliberately excluded; the nearby reference ring follows the ellipse.
     */
    public static double score(byte[] luminance, int width, int height,
                               double centerX, double centerY, double radiusX, double radiusY) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(radiusX) || !finite(radiusY)
                || !finite(centerX) || !finite(centerY)
                || radiusX < 2 || radiusY < 2 || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        double innerScale = 0.58, ringInnerScale = 1.18, ringOuterScale = 1.55;
        int extentX = (int) Math.ceil(radiusX * ringOuterScale);
        int extentY = (int) Math.ceil(radiusY * ringOuterScale);
        if (centerX - extentX < 0 || centerY - extentY < 0
                || centerX + extentX >= width || centerY + extentY >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }

        long innerSum = 0, paperSum = 0;
        int innerCount = 0, paperCount = 0;
        for (int y = (int) Math.floor(centerY - extentY);
             y <= (int) Math.ceil(centerY + extentY); y++) {
            double ny = (y - centerY) / radiusY;
            for (int x = (int) Math.floor(centerX - extentX);
                 x <= (int) Math.ceil(centerX + extentX); x++) {
                double nx = (x - centerX) / radiusX;
                double normalizedRadius = Math.sqrt(nx * nx + ny * ny);
                int value = luminance[y * width + x] & 0xff;
                if (normalizedRadius <= innerScale) {
                    innerSum += value;
                    innerCount++;
                } else if (normalizedRadius >= ringInnerScale
                        && normalizedRadius <= ringOuterScale) {
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
