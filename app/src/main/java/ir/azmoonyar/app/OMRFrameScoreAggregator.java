package ir.azmoonyar.app;

/** Median-based combination for independently scored camera frames. */
public final class OMRFrameScoreAggregator {
    private OMRFrameScoreAggregator() {}

    public static double median(double[] values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("At least one frame is required.");
        double[] sorted = values.clone();
        for (double value : sorted) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Scores must be between zero and one.");
            }
        }
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }
}
