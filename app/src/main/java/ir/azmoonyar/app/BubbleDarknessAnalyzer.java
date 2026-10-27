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
                || radius < 2 || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        int innerRadius = Math.max(1, (int) Math.floor(radius * 0.58));
        int ringInner = Math.max(innerRadius + 1, (int) Math.ceil(radius * 1.18));
        int ringOuter = Math.max(ringInner + 1, (int) Math.ceil(radius * 1.55));
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
            int dy = y - (int) Math.round(centerY);
            for (int x = minX; x <= maxX; x++) {
                int dx = x - (int) Math.round(centerX);
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
        return Math.max(0.0, Math.min(1.0, relativeDarkness));
    }
}
