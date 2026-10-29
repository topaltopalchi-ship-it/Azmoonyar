package ir.azmoonyar.app;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Experimental independent bubble score for diagnostics only.
 * Uses OpenCV adaptive thresholding and measures the fraction of dark pixels
 * inside the inner disk of each bubble. It does not replace production scoring.
 */
public final class OpenCvBubbleAnalyzer {
    private OpenCvBubbleAnalyzer() {}

    public static boolean initialize() {
        return OpenCVLoader.initLocal();
    }

    /**
     * Returns [A,B,C,D] scores in [0,1] using an adaptive threshold.
     * Centers and radius are in camera-image pixel coordinates.
     */
    public static double[] scores(byte[] luminance, int width, int height,
                                  double[] centerX, double[] centerY, double[] radius) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length
                || centerX == null || centerY == null || radius == null
                || centerX.length != 4 || centerY.length != 4 || radius.length != 4) {
            throw new IllegalArgumentException("تصویر یا مختصات حباب‌ها معتبر نیست.");
        }

        Mat gray = new Mat(height, width, CvType.CV_8UC1);
        Mat binary = new Mat();
        try {
            gray.put(0, 0, luminance);
            double meanRadius = 0;
            for (double r : radius) meanRadius += r;
            meanRadius /= 4.0;
            int blockSize = (int) Math.round(meanRadius * 2.6);
            blockSize = Math.max(15, Math.min(101, blockSize));
            if (blockSize % 2 == 0) blockSize++;
            if (blockSize >= Math.min(width, height)) {
                blockSize = Math.min(width, height) - 1;
                if (blockSize % 2 == 0) blockSize--;
            }
            if (blockSize < 3) throw new IllegalArgumentException("اندازهٔ تصویر برای آستانه‌گذاری کافی نیست.");

            Imgproc.adaptiveThreshold(gray, binary, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, blockSize, 7.0);

            byte[] thresholded = new byte[width * height];
            binary.get(0, 0, thresholded);
            double[] scores = new double[4];
            for (int i = 0; i < 4; i++) {
                if (!finite(centerX[i]) || !finite(centerY[i]) || !finite(radius[i])
                        || radius[i] < 2 || centerX[i] - radius[i] < 0
                        || centerY[i] - radius[i] < 0
                        || centerX[i] + radius[i] >= width
                        || centerY[i] + radius[i] >= height) {
                    throw new IllegalArgumentException("یکی از حباب‌ها کامل داخل تصویر نیست.");
                }
                double innerRadius = radius[i] * 0.58;
                double r2 = innerRadius * innerRadius;
                int x0 = (int) Math.floor(centerX[i] - innerRadius);
                int x1 = (int) Math.ceil(centerX[i] + innerRadius);
                int y0 = (int) Math.floor(centerY[i] - innerRadius);
                int y1 = (int) Math.ceil(centerY[i] + innerRadius);
                long dark = 0;
                long count = 0;
                for (int y = y0; y <= y1; y++) {
                    double dy = y - centerY[i];
                    for (int x = x0; x <= x1; x++) {
                        double dx = x - centerX[i];
                        if (dx * dx + dy * dy <= r2) {
                            if ((thresholded[y * width + x] & 0xff) == 0) dark++;
                            count++;
                        }
                    }
                }
                scores[i] = count == 0 ? 0 : dark / (double) count;
            }
            return scores;
        } finally {
            gray.release();
            binary.release();
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
