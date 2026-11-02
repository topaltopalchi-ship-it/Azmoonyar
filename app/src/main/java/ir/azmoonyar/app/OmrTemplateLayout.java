package ir.azmoonyar.app;

/**
 * Canonical bubble centers for the printed AZY-OMR-100-v1 A4 template.
 * Coordinates mirror the four-column CSS in MainActivity.printStandardAnswerSheets().
 */
public final class OmrTemplateLayout {
    public static final String TEMPLATE_ID = "AZY-OMR-100-v1";
    public static final double PAGE_WIDTH_MM = 210.0;
    public static final double PAGE_HEIGHT_MM = 297.0;
    public static final int QUESTIONS_PER_PAGE = 100;
    public static final int QUESTIONS_PER_COLUMN = 25;
    public static final int OPTIONS_PER_QUESTION = 4;

    public static final double NEW_MARKER_SIZE_MM = 16.0;
    public static final double NEW_MARKER_CENTER_INSET_MM = 13.0;
    public static final double LEGACY_MARKER_CENTER_INSET_MM = 6.5;

    private static final double PAGE_SIDE_PADDING_MM = 12.0;
    private static final double COLUMN_GAP_MM = 3.0;
    private static final double COLUMN_BORDER_MM = 0.25;
    private static final double COLUMN_PADDING_MM = 1.0;
    private static final double COLUMN_VERTICAL_PADDING_MM = 1.0;
    private static final double NUMBER_COLUMN_MM = 7.0;
    private static final double COLUMNS_TOP_MM = 44.0;
    private static final double TABLE_HEADER_MM = 7.0;
    private static final double TABLE_ROW_MM = 8.3;

    // Per-question alignment adjustments (millimeters).
    // Positive X moves detection centers right; positive Y moves them down.
    private static final double SHIFT_DOWN_MM = 1.5;
    private static final double SHIFT_SLIGHTLY_UP_MM = -0.8;
    private static final double SHIFT_UP_MM = -1.5;
    private static final double SHIFT_RIGHT_MM = 1.5;

    private OmrTemplateLayout() {}

    /**
     * Returns four [x,y] bubble centers per question in millimeters from the
     * page top-left, matching the printed four-column, 25-rows-per-column form.
     */
    public static double[][][] bubbleCentersForPage(int totalQuestions, int pageIndex) {
        if (totalQuestions < 1 || totalQuestions > 100) {
            throw new IllegalArgumentException("تعداد سؤال باید بین ۱ تا ۱۰۰ باشد.");
        }
        int pageCount = (totalQuestions + QUESTIONS_PER_PAGE - 1) / QUESTIONS_PER_PAGE;
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("شماره صفحهٔ پاسخنامه معتبر نیست.");
        }

        int questionsOnPage = Math.min(QUESTIONS_PER_PAGE,
                totalQuestions - pageIndex * QUESTIONS_PER_PAGE);
        double[][][] centers = new double[questionsOnPage][OPTIONS_PER_QUESTION][2];

        // .sheet has 12 mm side padding; .cols uses four equal columns and 3 mm gaps.
        double columnWidth = (PAGE_WIDTH_MM - 2 * PAGE_SIDE_PADDING_MM
                - 3 * COLUMN_GAP_MM) / 4.0;
        double tableWidth = columnWidth - 2 * COLUMN_BORDER_MM - 2 * COLUMN_PADDING_MM;
        double printedOptionCellWidth = (tableWidth - NUMBER_COLUMN_MM) / OPTIONS_PER_QUESTION;
        double firstBubbleCenterY = COLUMNS_TOP_MM + COLUMN_BORDER_MM + COLUMN_VERTICAL_PADDING_MM
                + TABLE_HEADER_MM + TABLE_ROW_MM / 2.0;

        for (int question = 0; question < questionsOnPage; question++) {
            int column = question / QUESTIONS_PER_COLUMN;
            int row = question % QUESTIONS_PER_COLUMN;
            double yShift = verticalShiftForQuestion(questionNumber);
            double columnLeft = PAGE_SIDE_PADDING_MM + column * (columnWidth + COLUMN_GAP_MM);
            double tableLeft = columnLeft + COLUMN_BORDER_MM + COLUMN_PADDING_MM;
            for (int option = 0; option < OPTIONS_PER_QUESTION; option++) {
                double xShift = horizontalShiftForQuestion(questionNumber)
                        + horizontalShiftForQuestionOption(questionNumber, option);
                // Printed table is LTR: question number, then A, B, C, D.
                centers[question][option][0] = tableLeft + NUMBER_COLUMN_MM
                        + (option + 0.5) * printedOptionCellWidth;
                centers[question][option][1] = firstBubbleCenterY + row * TABLE_ROW_MM + yShift;
            }
        }
        return centers;
    }

    private static double verticalShiftForQuestion(int questionNumber) {
        if (questionNumber >= 1 && questionNumber <= 12) {
            return SHIFT_DOWN_MM;
        }
        if (questionNumber >= 26 && questionNumber <= 36) {
            return SHIFT_DOWN_MM;
        }
        if (questionNumber >= 44 && questionNumber <= 50) {
            return SHIFT_SLIGHTLY_UP_MM;
        }
        if (questionNumber >= 51 && questionNumber <= 59) {
            return SHIFT_DOWN_MM;
        }
        if (questionNumber >= 68 && questionNumber <= 75) {
            return SHIFT_UP_MM;
        }
        if (questionNumber >= 87 && questionNumber <= 100) {
            return SHIFT_UP_MM;
        }
        return 0.0;
    }

    private static double horizontalShiftForQuestion(int questionNumber) {
        return (questionNumber >= 1 && questionNumber <= 12) ? SHIFT_RIGHT_MM : 0.0;
    }

    // Red A bubbles in the first column (questions 12–20) need a further
    // 1.5 mm rightward calibration based on the supplied scan.
    private static double horizontalShiftForQuestionOption(int questionNumber, int option) {
        if (questionNumber >= 1 && questionNumber <= 3) {
            return SHIFT_RIGHT_MM; // all four colored bubbles, additional 1.5 mm right
        }
        return (option == 0 && questionNumber >= 12 && questionNumber <= 20)
                ? SHIFT_RIGHT_MM : 0.0;
    }
}
