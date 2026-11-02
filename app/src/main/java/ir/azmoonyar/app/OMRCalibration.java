package ir.azmoonyar.app;

import java.util.Arrays;

/**
 * Robust statistics for blank-sheet OMR calibration.
 *
 * Scores are darkness values in [0,1] sampled from the same template regions
 * that will later be used for bubbles. This class does not infer sheet geometry
 * and must only be used after the caller has mapped those regions correctly.
 */
public final class OMRCalibration {
    public enum Status { CALIBRATED, ADJUST, INVALID }

    public static final class Result {
        public final Status status;
        public final double medianDarkness;
        public final double medianAbsoluteDeviation;
        public final double recommendedOffset;
        public final int sampleCount;

        private Result(Status status, double medianDarkness,
                       double medianAbsoluteDeviation, double recommendedOffset,
                       int sampleCount) {
            this.status = status;
            this.medianDarkness = medianDarkness;
            this.medianAbsoluteDeviation = medianAbsoluteDeviation;
            this.recommendedOffset = recommendedOffset;
            this.sampleCount = sampleCount;
        }
    }

    private OMRCalibration() {}

    /**
     * Evaluates blank reference samples and recommends a correction that moves
     * their median darkness toward zero. Offset is expressed in the same score
     * units as the darkness input; caller should apply it before classification.
     */
    public static Result evaluate(double[] blankDarkness, double manualOffset) {
        if (blankDarkness == null || blankDarkness.length < 8
                || !finite(manualOffset)) {
            return new Result(Status.INVALID, Double.NaN, Double.NaN, 0.0,
                    blankDarkness == null ? 0 : blankDarkness.length);
        }

        double[] samples = blankDarkness.clone();
        for (double sample : samples) {
            if (!finite(sample) || sample < 0.0 || sample > 1.0) {
                return new Result(Status.INVALID, Double.NaN, Double.NaN, 0.0,
                        samples.length);
            }
        }

        double median = median(samples);
        double[] deviations = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            deviations[i] = Math.abs(samples[i] - median);
        }
        double mad = median(deviations);
        // A positive darkness score on blank paper should be subtracted.
        double recommended = clamp(median, -1.0, 1.0);
        double correctedMedian = median - manualOffset;

        // Green is based on both central tendency and spread, never the slider
        // position alone. Thresholds are normalized score units and should be
        // validated with real device captures before release.
        Status status = Math.abs(correctedMedian) <= 0.015 && mad <= 0.02
                ? Status.CALIBRATED
                : (Math.abs(correctedMedian) <= 0.05 && mad <= 0.06
                    ? Status.ADJUST : Status.ADJUST);
        return new Result(status, median, mad, recommended, samples.length);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) * 0.5
                : sorted[middle];
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
