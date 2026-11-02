package ir.azmoonyar.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Blank-reference calibration statistics for normalized OMR darkness scores.
 * Coordinates are normalized page coordinates in [0,1] and are used to compare
 * the four page quadrants. This class never changes raw measurements.
 */
public final class OMRCalibration {
    public enum Status { CALIBRATED, ADJUST, BAD, INVALID }

    public static final class Result {
        public final Status status;
        public final double medianDarkness;
        public final double medianAbsoluteDeviation;
        public final double regionalMedianRange;
        public final double recommendedOffset;
        public final double appliedOffset;
        public final double correctedMedian;
        public final double qualityIndex;
        public final int sampleCount;

        private Result(Status status, double medianDarkness, double mad,
                double regionalMedianRange, double recommendedOffset,
                double appliedOffset, double correctedMedian,
                double qualityIndex, int sampleCount) {
            this.status = status;
            this.medianDarkness = medianDarkness;
            this.medianAbsoluteDeviation = mad;
            this.regionalMedianRange = regionalMedianRange;
            this.recommendedOffset = recommendedOffset;
            this.appliedOffset = appliedOffset;
            this.correctedMedian = correctedMedian;
            this.qualityIndex = qualityIndex;
            this.sampleCount = sampleCount;
        }
    }

    private OMRCalibration() {}

    public static Result evaluate(double[] darkness, double manualOffset,
            double[] normalizedX, double[] normalizedY) {
        int count = darkness == null ? 0 : darkness.length;
        if (count < 32 || normalizedX == null || normalizedY == null
                || normalizedX.length != count || normalizedY.length != count
                || !finite(manualOffset) || manualOffset < 0.0 || manualOffset > 1.0) {
            return invalid(count);
        }

        double[] samples = darkness.clone();
        @SuppressWarnings("unchecked")
        List<Double>[] regions = new List[]{new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>()};
        for (int i = 0; i < count; i++) {
            if (!finite(samples[i]) || samples[i] < 0.0 || samples[i] > 1.0
                    || !finite(normalizedX[i]) || !finite(normalizedY[i])
                    || normalizedX[i] < 0.0 || normalizedX[i] > 1.0
                    || normalizedY[i] < 0.0 || normalizedY[i] > 1.0) {
                return invalid(count);
            }
            int col = normalizedX[i] >= 0.5 ? 1 : 0;
            int row = normalizedY[i] >= 0.5 ? 1 : 0;
            regions[row * 2 + col].add(samples[i]);
        }

        double median = median(samples);
        double[] deviations = new double[count];
        for (int i = 0; i < count; i++) deviations[i] = Math.abs(samples[i] - median);
        double mad = median(deviations);

        double minRegionMedian = Double.POSITIVE_INFINITY;
        double maxRegionMedian = Double.NEGATIVE_INFINITY;
        int populatedRegions = 0;
        for (List<Double> region : regions) {
            if (region.size() < 4) continue;
            double regionMedian = median(toArray(region));
            minRegionMedian = Math.min(minRegionMedian, regionMedian);
            maxRegionMedian = Math.max(maxRegionMedian, regionMedian);
            populatedRegions++;
        }
        if (populatedRegions < 2) return invalid(count);
        double regionRange = maxRegionMedian - minRegionMedian;

        double correctedMedian = median - manualOffset;
        double centralScore = clamp01(1.0 - Math.abs(correctedMedian) / 0.05);
        double spreadScore = clamp01(1.0 - (1.4826 * mad) / 0.08);
        double regionalScore = clamp01(1.0 - regionRange / 0.10);
        double cqi = 100.0 * (0.45 * centralScore
                + 0.35 * spreadScore + 0.20 * regionalScore);

        Status status;
        if (Math.abs(correctedMedian) <= 0.015 && 1.4826 * mad <= 0.02
                && regionRange <= 0.025 && cqi >= 95.0) {
            status = Status.CALIBRATED;
        } else if (cqi >= 75.0) {
            status = Status.ADJUST;
        } else {
            status = Status.BAD;
        }
        return new Result(status, median, mad, regionRange,
                clamp(median, 0.0, 1.0), manualOffset,
                correctedMedian, cqi, count);
    }

    private static Result invalid(int count) {
        return new Result(Status.INVALID, Double.NaN, Double.NaN, Double.NaN,
                0.0, 0.0, Double.NaN, 0.0, count);
    }

    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) * 0.5
                : sorted[middle];
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
