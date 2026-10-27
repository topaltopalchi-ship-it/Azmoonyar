package ir.azmoonyar.app;

/**
 * Canonical bubble centers for the printed AZY-OMR-100-v1 A4 template.
 *
 * Keep these dimensions synchronized with printStandardAnswerSheets() in
 * MainActivity. The print CSS deliberately fixes the vertical positions and
 * table column widths so camera sampling does not depend on text flow.
 */
public final class OmrTemplateLayout {
    public static final String TEMPLATE_ID = "AZY-OMR-100-v1";
    public static final double PAGE_WIDTH_MM = 210.0;
    public static final double PAGE_HEIGHT_MM = 297.0;
    public static final int QUESTIONS_PER_PAGE = 100;
    public static final int QUESTIONS_PER_COLUMN = 50;
    public static final int OPTIONS_PER_QUESTION = 4;

    // New print generation uses solid 16 mm markers; scanner also accepts
    // legacy 3 mm outlined markers at the old center coordinates.
    public static final double NEW_MARKER_SIZE_MM = 16.0;
    public static final double NEW_MARKER_CENTER_INSET_MM = 13.0;
    public static final double LEGACY_MARKER_CENTER_INSET_MM = 6.5;

    private static final double PAGE_SIDE_PADDING_MM = 12.0;
    private static final double COLUMN_GAP_MM = 4.0;
    private static final double COLUMN_BORDER_MM = 0.35;
    private static final double COLUMN_HORIZONTAL_PADDING_MM = 1.5;
    private static final double NUMBER_COLUMN_MM = 13.0;
    private static final double COLUMNS_TOP_MM = 46.0;
    private static final double TABLE_HEADER_MM = 6.0;
    private static final double TABLE_ROW_MM = 4.45;
    private static final double COLUMN_TOP_BORDER_MM = 0.35;
    private static final double COLUMN_TOP_PADDING_MM = 2.0;

    private OmrTemplateLayout() {}

    /**
     * Returns bubble centers for one printed page, in question order. Each
     * question contains four [x,y] centers in millimeters from the page's
     * top-left corner. A final partial page returns only its actual questions.
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

        double columnWidth = (PAGE_WIDTH_MM - 2 * PAGE_SIDE_PADDING_MM - COLUMN_GAP_MM) / 2.0;
        double contentLeftInset = COLUMN_BORDER_MM + COLUMN_HORIZONTAL_PADDING_MM;
        double contentWidth = columnWidth - 2 * contentLeftInset;
        double optionWidth = (contentWidth - NUMBER_COLUMN_MM) / OPTIONS_PER_QUESTION;
        // The print page is RTL, so the first grid child (questions 1-50)
        // is the right-hand column; questions 51-100 occupy the left column.
        double firstColumnLeft = PAGE_SIDE_PADDING_MM + columnWidth + COLUMN_GAP_MM;
        double secondColumnLeft = PAGE_SIDE_PADDING_MM;
        double firstBubbleCenterY = COLUMNS_TOP_MM + COLUMN_TOP_BORDER_MM
                + COLUMN_TOP_PADDING_MM + TABLE_HEADER_MM + TABLE_ROW_MM / 2.0;

        for (int question = 0; question < questionsOnPage; question++) {
            int column = question / QUESTIONS_PER_COLUMN;
            int row = question % QUESTIONS_PER_COLUMN;
            double columnLeft = column == 0 ? firstColumnLeft : secondColumnLeft;
            double contentLeft = columnLeft + contentLeftInset;
            for (int option = 0; option < OPTIONS_PER_QUESTION; option++) {
                // The table is RTL: the question-number column is at the
                // right, and A/B/C/D progress from right to left.
                centers[question][option][0] = contentLeft + contentWidth
                        - NUMBER_COLUMN_MM - (option + 0.5) * optionWidth;
                centers[question][option][1] = firstBubbleCenterY + row * TABLE_ROW_MM;
            }
        }
        return centers;
    }
}
