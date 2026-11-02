package ir.azmoonyar.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Automatic blank-sheet baseline by four page regions. */
public final class AutomaticRegionalCalibration {
    public static final class Result {
        public final double[] offsets;
        public final double regionalSpread;
        public final double medianAbsoluteDeviation;
        public final double quality;
        public final boolean acceptable;

        private Result(double[] offsets, double spread, double mad, double quality,
                       boolean acceptable) {
            this.offsets = offsets.clone();
            this.regionalSpread = spread;
            this.medianAbsoluteDeviation = mad;
            this.quality = quality;
            this.acceptable = acceptable;
        }
    }

    private AutomaticRegionalCalibration() {}

    public static Result evaluate(double[] blankScores, double[] x, double[] y) {
        if (blankScores == null || x == null || y == null || blankScores.length < 32
                || x.length != blankScores.length || y.length != blankScores.length) {
            throw new IllegalArgumentException("نمونه‌های کالیبراسیون کافی نیست.");
        }
        @SuppressWarnings("unchecked")
        List<Double>[] regions = new List[] {
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        };
        for (int i = 0; i < blankScores.length; i++) {
            if (!finite(blankScores[i]) || blankScores[i] < 0 || blankScores[i] > 1
                    || !finite(x[i]) || !finite(y[i])
                    || x[i] < 0 || x[i] > 1 || y[i] < 0 || y[i] > 1) {
                throw new IllegalArgumentException("دادهٔ کالیبراسیون نامعتبر است.");
            }
            int region = (y[i] >= .5 ? 2 : 0) + (x[i] >= .5 ? 1 : 0);
            regions[region].add(blankScores[i]);
        }
        double[] medians = new double[4];
        double[] all = blankScores.clone();
        for (int r = 0; r < 4; r++) {
            if (regions[r].size() < 4) throw new IllegalArgumentException("هر ناحیه باید نمونهٔ کافی داشته باشد.");
            medians[r] = median(toArray(regions[r]));
        }
        double globalMedian = median(all);
        double[] deviations = new double[all.length];
        for (int i = 0; i < all.length; i++) deviations[i] = Math.abs(all[i] - globalMedian);
        double mad = median(deviations);
        double min = Math.min(Math.min(medians[0], medians[1]), Math.min(medians[2], medians[3]));
        double max = Math.max(Math.max(medians[0], medians[1]), Math.max(medians[2], medians[3]));
        double spread = max - min;
        // Only compensate the regional baseline above the global blank baseline.
        // This preserves a genuine mark as a local contrast increase.
        double[] offsets = new double[4];
        for (int r = 0; r < 4; r++) offsets[r] = Math.max(0, Math.min(.12, medians[r] - globalMedian));
        double quality = 100 * Math.max(0, Math.min(1, 1 - (spread * 4 + mad * 6)));
        boolean acceptable = spread <= .08 && mad <= .035 && quality >= 65;
        return new Result(offsets, spread, mad, quality, acceptable);
    }

    private static double[] toArray(List<Double> values) {
        double[] a = new double[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i);
        return a;
    }
    private static double median(double[] a) {
        double[] s = a.clone(); Arrays.sort(s);
        int m = s.length / 2;
        return s.length % 2 == 0 ? (s[m - 1] + s[m]) / 2 : s[m];
    }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
}
