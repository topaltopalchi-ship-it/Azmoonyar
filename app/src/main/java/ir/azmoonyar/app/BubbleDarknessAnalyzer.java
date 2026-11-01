package ir.azmoonyar.app;

/**
 * Color-independent bubble measurement for OMR.
 *
 * Uses an elliptical ROI in image space so a printed oval remains correctly
 * sampled after perspective projection. The reference ring is local to each
 * bubble, reducing sensitivity to broad illumination gradients.
 */
public final class BubbleDarknessAnalyzer {
    private BubbleDarknessAnalyzer() {}

    /** Backward-compatible circular measurement. */
    public static double score(byte[] luminance, int width, int height,
                               double centerX, double centerY, double radius) {
        return scoreEllipse(luminance, width, height, centerX, centerY, radius, radius);
    }

    /**
     * Measures local relative darkness inside an ellipse.
     * radiusX and radiusY are the projected semi-axis lengths in image pixels.
     */
    public static double scoreEllipse(byte[] luminance, int width, int height,
            double centerX, double centerY, double radiusX, double radiusY) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(radiusX) || !finite(radiusY) || !finite(centerX) || !finite(centerY)
                || radiusX < 2 || radiusY < 2 || centerX < 0 || centerY < 0
                || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        double maxRadius = Math.max(radiusX, radiusY);
        int ringOuter = Math.max(2, (int) Math.ceil(maxRadius * 1.55));
        if (centerX - ringOuter < 0 || centerY - ringOuter < 0
                || centerX + ringOuter >= width || centerY + ringOuter >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }

        int minX = Math.max(0, (int) Math.floor(centerX - ringOuter));
        int maxX = Math.min(width - 1, (int) Math.ceil(centerX + ringOuter));
        int minY = Math.max(0, (int) Math.floor(centerY - ringOuter));
        int maxY = Math.min(height - 1, (int) Math.ceil(centerY + ringOuter));

        long innerSum = 0, paperSum = 0;
        int innerCount = 0, paperCount = 0;
        double innerLimit = 0.58 * 0.58;
        double ringInner = 1.18 * 1.18;
        double ringOuterNorm = 1.55 * 1.55;

        for (int y = minY; y <= maxY; y++) {
            double dy = (y - centerY) / radiusY;
            for (int x = minX; x <= maxX; x++) {
                double dx = (x - centerX) / radiusX;
                double normalized = dx * dx + dy * dy;
                int value = luminance[y * width + x] & 0xff;
                if (normalized <= innerLimit) {
                    innerSum += value;
                    innerCount++;
                } else if (normalized >= ringInner && normalized <= ringOuterNorm) {
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
