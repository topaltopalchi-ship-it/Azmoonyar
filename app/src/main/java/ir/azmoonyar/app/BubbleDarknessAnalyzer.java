package ir.azmoonyar.app;

/**
 * Color-independent bubble measurement for OMR.
 *
 * Samples in the projected page coordinate basis, preserving oval rotation
 * and skew caused by camera perspective. The local reference ring reduces
 * sensitivity to broad illumination gradients.
 */
public final class BubbleDarknessAnalyzer {
    private BubbleDarknessAnalyzer() {}

    /** Backward-compatible circular measurement. */
    public static double score(byte[] luminance, int width, int height,
            double centerX, double centerY, double radius) {
        return scoreEllipse(luminance, width, height, centerX, centerY, radius, radius);
    }

    /** Axis-aligned ellipse compatibility overload. */
    public static double scoreEllipse(byte[] luminance, int width, int height,
            double centerX, double centerY, double radiusX, double radiusY) {
        return scoreProjectedEllipse(luminance, width, height, centerX, centerY,
                radiusX, 0.0, 0.0, radiusY);
    }

    /**
     * Measures an ellipse whose two semi-axis vectors are expressed in image pixels.
     * Unlike an axis-aligned ellipse, this handles rotation and shear under perspective.
     */
    public static double scoreProjectedEllipse(byte[] luminance, int width, int height,
            double centerX, double centerY, double axisXx, double axisXy,
            double axisYx, double axisYy) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || !finite(centerX) || !finite(centerY)
                || !finite(axisXx) || !finite(axisXy)
                || !finite(axisYx) || !finite(axisYy)
                || centerX < 0 || centerY < 0 || centerX >= width || centerY >= height) {
            throw new IllegalArgumentException("ناحیهٔ حباب برای سنجش معتبر نیست.");
        }

        double determinant = axisXx * axisYy - axisXy * axisYx;
        double axisXLength = Math.hypot(axisXx, axisXy);
        double axisYLength = Math.hypot(axisYx, axisYy);
        if (axisXLength < 2 || axisYLength < 2
                || Math.abs(determinant) < axisXLength * axisYLength * 1e-4) {
            throw new IllegalArgumentException("هندسهٔ بیضی حباب ناپایدار است.");
        }

        // Bounding box of the outer reference ellipse in image coordinates.
        double extentX = 1.55 * (Math.abs(axisXx) + Math.abs(axisYx));
        double extentY = 1.55 * (Math.abs(axisXy) + Math.abs(axisYy));
        if (centerX - extentX < 0 || centerY - extentY < 0
                || centerX + extentX >= width || centerY + extentY >= height) {
            throw new IllegalArgumentException("حباب یا حلقهٔ مرجع آن کامل داخل تصویر نیست.");
        }
        int minX = (int) Math.floor(centerX - extentX);
        int maxX = (int) Math.ceil(centerX + extentX);
        int minY = (int) Math.floor(centerY - extentY);
        int maxY = (int) Math.ceil(centerY + extentY);

        long innerSum = 0, paperSum = 0;
        int innerCount = 0, paperCount = 0;
        double innerLimit = 0.58 * 0.58;
        double ringInner = 1.18 * 1.18;
        double ringOuterNorm = 1.55 * 1.55;

        for (int y = minY; y <= maxY; y++) {
            double dy = y - centerY;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                // Invert the 2x2 basis matrix to obtain normalized ellipse coordinates.
                double localX = (dx * axisYy - dy * axisYx) / determinant;
                double localY = (axisXx * dy - axisXy * dx) / determinant;
                double normalized = localX * localX + localY * localY;
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
