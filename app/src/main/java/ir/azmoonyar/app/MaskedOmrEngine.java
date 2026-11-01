package ir.azmoonyar.app;

/**
 * Decision core for the Masked OMR workflow.
 *
 * Calibration is a blank copy of the SAME printed A4 answer-sheet template,
 * captured with the same camera setup and rectified into canonical A4 space.
 * It stores a local blank score for every bubble, not one page-wide darkness
 * value. This matches BubbleDarknessAnalyzer.score(), which is already a
 * local inner-vs-paper-ring contrast measurement.
 *
 * No threshold is invented here: callers must supply a threshold verified
 * against the known-good Altay test sheets.
 */
public final class MaskedOmrEngine {
    public static final String VERSION_NAME = "نسخه نقابدار";
    public static final String PREF_CALIBRATION_DATA = "masked_omr_calibration_data";
    public static final String PREF_CALIBRATION_TIME = "masked_omr_calibration_time";
    public static final String PREF_CALIBRATION_TEMPLATE = "masked_omr_calibration_template";

    public enum Status { CORRECT, WRONG, BLANK, MULTIPLE_MARKS, REVIEW }

    public static final class Calibration {
        public final double[][] blankScores;
        public final long capturedAtMillis;
        public final String templateId;

        private Calibration(double[][] scores, long time, String template) {
            blankScores = deepCopy(scores);
            capturedAtMillis = time;
            templateId = template;
        }

        public int questionCount() { return blankScores.length; }
    }

    public static final class Decision {
        public final Status status;
        public final int detectedOption;
        public final double[] correctedScores;

        private Decision(Status status, int option, double[] scores) {
            this.status = status;
            detectedOption = option;
            correctedScores = scores.clone();
        }
    }

    private MaskedOmrEngine() {}

    /**
     * Creates a per-bubble blank baseline from OMRSheetReader darkness scores.
     * Input shape is [question][A-D]. A calibration must contain the same
     * question count and template geometry as the later answer sheet.
     */
    public static Calibration calibrate(double[][] blankScores, String templateId,
            long capturedAtMillis) {
        validateMatrix(blankScores);
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("شناسهٔ قالب کالیبراسیون معتبر نیست.");
        }
        for (double[] row : blankScores) {
            for (double score : row) {
                if (!finite(score) || score < 0 || score > 1) {
                    throw new IllegalArgumentException("امتیاز کالیبراسیون باید بین صفر و یک باشد.");
                }
            }
        }
        return new Calibration(blankScores, capturedAtMillis, templateId);
    }

    /**
     * Corrects each option using its own local blank-template baseline.
     * The correct option is evaluated first conceptually, while all four
     * readings remain available to detect wrong and multiple marks.
     */
    public static Decision classify(double[] rawScores, int correctOption,
            int questionIndex, Calibration calibration, String templateId,
            double threshold, double reviewBand) {
        if (rawScores == null || rawScores.length != 4 || calibration == null
                || questionIndex < 0 || questionIndex >= calibration.questionCount()
                || correctOption < 0 || correctOption > 3
                || templateId == null || !templateId.equals(calibration.templateId)
                || !finite(threshold) || threshold <= 0 || threshold >= 1
                || !finite(reviewBand) || reviewBand < 0 || reviewBand >= threshold) {
            throw new IllegalArgumentException("کالیبراسیون یا پارامترهای تشخیص نقاب‌دار معتبر نیستند.");
        }

        double[] corrected = new double[4];
        int markedCount = 0;
        int markedIndex = -1;
        boolean uncertain = false;
        for (int option = 0; option < 4; option++) {
            double raw = rawScores[option];
            if (!finite(raw) || raw < 0 || raw > 1) {
                throw new IllegalArgumentException("امتیاز حباب باید بین صفر و یک باشد.");
            }
            corrected[option] = Math.max(0, raw - calibration.blankScores[questionIndex][option]);
            if (corrected[option] >= threshold) {
                markedCount++;
                markedIndex = option;
            } else if (corrected[option] >= threshold - reviewBand) {
                uncertain = true;
            }
        }

        if (markedCount > 1) return new Decision(Status.MULTIPLE_MARKS, -1, corrected);
        if (uncertain) return new Decision(Status.REVIEW, -1, corrected);
        if (markedCount == 0) return new Decision(Status.BLANK, -1, corrected);
        return new Decision(markedIndex == correctOption ? Status.CORRECT : Status.WRONG,
                markedIndex, corrected);
    }

    public static String encode(Calibration calibration) {
        if (calibration == null) throw new IllegalArgumentException("کالیبراسیون موجود نیست.");
        StringBuilder out = new StringBuilder();
        for (int q = 0; q < calibration.blankScores.length; q++) {
            if (q > 0) out.append(';');
            for (int o = 0; o < 4; o++) {
                if (o > 0) out.append(',');
                out.append(Double.toString(calibration.blankScores[q][o]));
            }
        }
        return out.toString();
    }

    public static Calibration decode(String encoded, String templateId, long capturedAtMillis) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("دادهٔ کالیبراسیون ذخیره نشده است.");
        }
        String[] questions = encoded.split(";", -1);
        if (questions.length < 1 || questions.length > 100) {
            throw new IllegalArgumentException("تعداد سؤال‌های کالیبراسیون معتبر نیست.");
        }
        double[][] values = new double[questions.length][4];
        for (int q = 0; q < questions.length; q++) {
            String[] options = questions[q].split(",", -1);
            if (options.length != 4) throw new IllegalArgumentException("دادهٔ حباب‌های کالیبراسیون ناقص است.");
            for (int o = 0; o < 4; o++) {
                try { values[q][o] = Double.parseDouble(options[o]); }
                catch (NumberFormatException error) {
                    throw new IllegalArgumentException("دادهٔ کالیبراسیون قابل خواندن نیست.");
                }
            }
        }
        return calibrate(values, templateId, capturedAtMillis);
    }

    private static void validateMatrix(double[][] values) {
        if (values == null || values.length < 1 || values.length > 100) {
            throw new IllegalArgumentException("تعداد سؤال‌های کالیبراسیون باید بین ۱ تا ۱۰۰ باشد.");
        }
        for (double[] row : values) {
            if (row == null || row.length != 4) {
                throw new IllegalArgumentException("برای هر سؤال باید چهار امتیاز کالیبراسیون وجود داشته باشد.");
            }
        }
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][4];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
