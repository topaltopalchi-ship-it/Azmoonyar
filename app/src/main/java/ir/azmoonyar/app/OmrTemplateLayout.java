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

    // Exact outer dimensions from MainActivity.printStandardAnswerSheets():
    // .bubble{width:5.2mm;height:4.1mm}. Reader expects semi-axes.
    public static final double BUBBLE_HALF_WIDTH_MM = 2.6;
    public static final double BUBBLE_HALF_HEIGHT_MM = 2.05;

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
    // Geometry below must exactly mirror printStandardAnswerSheets():
    // .col has a 0.25 mm border and 1 mm padding; the table header is 7 mm
    // and each answer row is 8.3 mm. These values mirror the print CSS exactly.

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
        // The printed HTML explicitly reserves 7 mm for the question number.
        // The four answer cells share the remaining table width equally.
        // Keep this formula in sync with the colgroup in MainActivity's print HTML.
        double printedOptionCellWidth = (tableWidth - NUMBER_COLUMN_MM) / OPTIONS_PER_QUESTION;
        double firstBubbleCenterY = COLUMNS_TOP_MM + COLUMN_BORDER_MM + COLUMN_VERTICAL_PADDING_MM
                + TABLE_HEADER_MM + TABLE_ROW_MM / 2.0;

        for (int question = 0; question < questionsOnPage; question++) {
            int column = question / QUESTIONS_PER_COLUMN;
            int row = question % QUESTIONS_PER_COLUMN;
            double columnLeft = PAGE_SIDE_PADDING_MM + column * (columnWidth + COLUMN_GAP_MM);
            double tableLeft = columnLeft + COLUMN_BORDER_MM + COLUMN_PADDING_MM;
            for (int option = 0; option < OPTIONS_PER_QUESTION; option++) {
                // Printed table is LTR: question number, then A, B, C, D.
                centers[question][option][0] = tableLeft + NUMBER_COLUMN_MM
                        + (option + 0.5) * printedOptionCellWidth;
                centers[question][option][1] = firstBubbleCenterY + row * TABLE_ROW_MM;
            }
        }
        return centers;
    }
}
