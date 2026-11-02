package ir.azmoonyar.app;

/**
 * Evaluates illumination uniformity across a blank registered answer sheet.
 * Input values are paper-surface darkness (0 = bright, 1 = dark), ordered:
 * top-left, top-right, bottom-left, bottom-right.
 */
public final class SheetLightPlacementGuide {
    public static final class Result {
        public final double spread;
        public final double average;
        public final int darkestRegion;
        public final int brightestRegion;
        public final boolean suitable;
        public final String message;

        private Result(double spread, double average, int darkestRegion,
                       int brightestRegion, boolean suitable, String message) {
            this.spread = spread;
            this.average = average;
            this.darkestRegion = darkestRegion;
            this.brightestRegion = brightestRegion;
            this.suitable = suitable;
            this.message = message;
        }
    }

    private SheetLightPlacementGuide() {}

    public static Result evaluate(double[] darkness) {
        if (darkness == null || darkness.length != 4) {
            throw new IllegalArgumentException("چهار نمونهٔ نور لازم است.");
        }
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        int darkest = 0, brightest = 0;
        for (int i = 0; i < darkness.length; i++) {
            double value = darkness[i];
            if (Double.isNaN(value) || Double.isInfinite(value)
                    || value < 0 || value > 1) {
                throw new IllegalArgumentException("مقدار روشنایی نامعتبر است.");
            }
            sum += value;
            if (value > max) { max = value; darkest = i; }
            if (value < min) { min = value; brightest = i; }
        }
        double spread = max - min;
        double average = sum / 4.0;
        boolean suitable = spread <= 0.055;
        String[] names = {"بالا چپ", "بالا راست", "پایین چپ", "پایین راست"};
        String[] moves = {
                "کمی به سمت پایین و راست",
                "کمی به سمت پایین و چپ",
                "کمی به سمت بالا و راست",
                "کمی به سمت بالا و چپ"
        };
        String message;
        if (suitable) {
            message = String.format(java.util.Locale.US,
                    "نور یکنواخت و مناسب است (اختلاف %.1f%%). برگه را ثابت نگه دارید.",
                    spread * 100.0);
        } else {
            message = String.format(java.util.Locale.US,
                    "سایه/تیرگی در ناحیهٔ %s بیشتر است (اختلاف %.1f%%). برگه را %s جابه‌جا کنید و دوباره بسنجید.",
                    names[darkest], spread * 100.0, moves[darkest]);
        }
        return new Result(spread, average, darkest, brightest, suitable, message);
    }
}
