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
     * Elliptical sampling for printed oval bubbles. radiusX and radiusY are the
     * local image-space semi-axes derived from the sheet perspective transform.
     */
    public static double score(byte[] luminance, int width, int height,
                               double centerX, double centerY,
                               double radiusX, double radiusY) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(radiusX) || !finite(radiusY)
                || !finite(centerX) || !finite(centerY)
                || radiusX < 2 || radiusY < 2 || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        // Sample a larger central ellipse while staying clear of the printed outline.
        double innerScale = 0.68;
        // Keep the local paper ring compact so it does not reach neighboring bubbles.
        double ringInnerScale = 1.12;
        double ringOuterScale = 1.35;
        double innerX = radiusX * innerScale, innerY = radiusY * innerScale;
        double ringInX = radiusX * ringInnerScale, ringInY = radiusY * ringInnerScale;
        double ringOutX = radiusX * ringOuterScale, ringOutY = radiusY * ringOuterScale;
        double maxExtentX = ringOutX, maxExtentY = ringOutY;
        // Do not score a partially visible bubble: clipping the reference ring at
        // an image edge can make an off-sheet bubble look confidently blank/filled.
        if (centerX - maxExtentX < 0 || centerY - maxExtentY < 0
                || centerX + maxExtentX >= width || centerY + maxExtentY >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }

        int minX = Math.max(0, (int) Math.floor(centerX - maxExtentX));
        int maxX = Math.min(width - 1, (int) Math.ceil(centerX + maxExtentX));
        int minY = Math.max(0, (int) Math.floor(centerY - maxExtentY));
        int maxY = Math.min(height - 1, (int) Math.ceil(centerY + maxExtentY));

        long innerSum = 0;
        int innerCount = 0;
        long paperSum = 0;
        int paperCount = 0;
        for (int y = minY; y <= maxY; y++) {
            double ny = (y - centerY) / radiusY;
            for (int x = minX; x <= maxX; x++) {
                double nx = (x - centerX) / radiusX;
                double normalizedDistance = nx * nx + ny * ny;
                int value = luminance[y * width + x] & 0xff;
                if (normalizedDistance <= innerScale * innerScale) {
                    innerSum += value;
                    innerCount++;
                } else if (normalizedDistance >= ringInnerScale * ringInnerScale
                        && normalizedDistance <= ringOuterScale * ringOuterScale) {
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

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
