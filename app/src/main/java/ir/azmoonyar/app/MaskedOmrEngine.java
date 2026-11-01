package ir.azmoonyar.app;

/**
 * Decision core for the Masked OMR workflow.
 *
 * The caller must first rectify both the white calibration sheet and the answer
 * sheet into the same canonical A4 coordinate system. Raw darkness values are
 * normalized to [0,1], with 0=white and 1=black. The calibration mean is
 * subtracted before applying the preserved Altay threshold.
 *
 * This class deliberately does not guess image geometry or invent a threshold.
 */
public final class MaskedOmrEngine {
    public static final String VERSION_NAME = "نسخه نقابدار";
    public static final String PREF_CALIBRATION_MEAN = "masked_omr_calibration_mean";
    public static final String PREF_CALIBRATION_TIME = "masked_omr_calibration_time";
    public static final String PREF_CALIBRATION_TEMPLATE = "masked_omr_calibration_template";

    public enum Status { CORRECT, WRONG, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final class Calibration {
        public final double meanDarkness;
        public final long capturedAtMillis;
        public final String templateId;

        private Calibration(double meanDarkness, long capturedAtMillis, String templateId) {
            this.meanDarkness = meanDarkness;
            this.capturedAtMillis = capturedAtMillis;
            this.templateId = templateId;
        }
    }

    public static final class Decision {
        public final Status status;
        public final int detectedOption; // -1 when blank/review/multiple
        public final double[] correctedScores;

        private Decision(Status status, int detectedOption, double[] correctedScores) {
            this.status = status;
            this.detectedOption = detectedOption;
            this.correctedScores = correctedScores.clone();
        }
    }

    private MaskedOmrEngine() {}

    /**
     * Builds a calibration from every valid page pixel. The caller must pass
     * pixels belonging to the A4 paper only (not desk/background). Sampling may
     * be strided uniformly for memory and CPU efficiency.
     */
    public static Calibration calibrate(byte[] luminance, int width, int height,
            int stride, String templateId, long capturedAtMillis) {
        if (luminance == null || width <= 0 || height <= 0
                || (long) width * height > luminance.length || stride < 1
                || templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("تصویر کالیبراسیون معتبر نیست.");
        }
        long sum = 0;
        long count = 0;
        for (int y = 0; y < height; y += stride) {
            int row = y * width;
            for (int x = 0; x < width; x += stride) {
                sum += luminance[row + x] & 0xff;
                count++;
            }
        }
        if (count == 0) throw new IllegalArgumentException("نمونهٔ کالیبراسیون خالی است.");
        // Convert luminance to darkness: white=0, black=1.
        double mean = 1.0 - (sum / (double) count) / 255.0;
        return new Calibration(clamp01(mean), capturedAtMillis, templateId);
    }

    /**
     * Applies a white-sheet baseline to four bubble scores and classifies one
     * question. The correct option is checked first (mask aperture). If it is
     * not marked, all remaining options are checked to distinguish WRONG from
     * BLANK. Multiple marks and near-threshold evidence are never guessed.
     *
     * threshold must be the verified Altay threshold expressed in the same
     * normalized score domain; it is intentionally supplied by the caller.
     */
    public static Decision classify(double[] rawDarkness, int correctOption,
            Calibration calibration, double threshold, double reviewBand) {
        if (rawDarkness == null || rawDarkness.length != 4 || correctOption < 0
                || correctOption > 3 || calibration == null
                || !Double.isFinite(threshold) || threshold <= 0 || threshold >= 1
                || !Double.isFinite(reviewBand) || reviewBand < 0 || reviewBand >= threshold) {
            throw new IllegalArgumentException("پارامترهای تشخیص نقاب‌دار معتبر نیستند.");
        }
        double[] corrected = new double[4];
        int markedCount = 0;
        int markedIndex = -1;
        boolean uncertain = false;
        for (int i = 0; i < 4; i++) {
            double raw = rawDarkness[i];
            if (!Double.isFinite(raw) || raw < 0 || raw > 1) {
                throw new IllegalArgumentException("تیرگی حباب باید بین صفر و یک باشد.");
            }
            corrected[i] = clamp01(raw - calibration.meanDarkness);
            if (corrected[i] >= threshold) {
                markedCount++;
                markedIndex = i;
            } else if (corrected[i] >= threshold - reviewBand) {
                uncertain = true;
            }
        }
        if (markedCount > 1) return new Decision(Status.MULTIPLE_MARKS, -1, corrected);
        if (uncertain) return new Decision(Status.REVIEW, -1, corrected);
        if (markedCount == 0) return new Decision(Status.BLANK, -1, corrected);
        if (markedIndex == correctOption) return new Decision(Status.CORRECT, markedIndex, corrected);
        return new Decision(Status.WRONG, markedIndex, corrected);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
