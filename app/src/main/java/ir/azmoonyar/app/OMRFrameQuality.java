package ir.azmoonyar.app;

/** Image sharpness checks for burst frames, independent of Android bitmap APIs. */
public final class OMRFrameQuality {
    private OMRFrameQuality() {}

    /**
     * Variance of the 4-neighbour discrete Laplacian on 8-bit luma.
     * Low values indicate weak high-frequency detail (often motion/defocus blur).
     */
    public static double laplacianVariance(byte[] luma, int width, int height) {
        if (luma == null || width < 3 || height < 3 || (long) width * height != luma.length)
            throw new IllegalArgumentException("Invalid luma frame dimensions.");
        long count = 0;
        double sum = 0.0, sumSquares = 0.0;
        // Subsample large camera images to keep the burst quality gate inexpensive.
        int step = Math.max(1, Math.max(width, height) / 1200);
        for (int y = 1; y < height - 1; y += step) {
            int row = y * width;
            for (int x = 1; x < width - 1; x += step) {
                int center = luma[row + x] & 0xff;
                int laplacian = (luma[row - width + x] & 0xff)
                        + (luma[row + width + x] & 0xff)
                        + (luma[row + x - 1] & 0xff)
                        + (luma[row + x + 1] & 0xff) - 4 * center;
                sum += laplacian;
                sumSquares += (double) laplacian * laplacian;
                count++;
            }
        }
        if (count == 0) return 0.0;
        double mean = sum / count;
        return Math.max(0.0, sumSquares / count - mean * mean);
    }
}
