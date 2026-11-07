package ir.azmoonyar.app;

/**
 * Normalized bubble-center coordinates for external answer-sheet layouts.
 * Bounds are normalized to the detected sheet: x0/y0 is the top-left
 * and x1/y1 is the bottom-right of the answer area.
 */
public final class ExternalSheetLayout {
    private ExternalSheetLayout() {}

    public static double[] bubbleCenter(
            int questionIndex,
            int optionIndex,
            int questionCount,
            int optionCount,
            String layout,
            double x0,
            double y0,
            double x1,
            double y1) {
        if (questionCount <= 0 || optionCount <= 0
                || questionIndex < 0 || questionIndex >= questionCount
                || optionIndex < 0 || optionIndex >= optionCount
                || !finite(x0) || !finite(y0) || !finite(x1) || !finite(y1)
                || x0 < 0 || y0 < 0 || x1 > 1 || y1 > 1
                || x0 >= x1 || y0 >= y1) {
            throw new IllegalArgumentException("Invalid answer-sheet bounds or indices");
        }
        if (layout == null || (!"single".equalsIgnoreCase(layout)
                && !"double".equalsIgnoreCase(layout))) {
            throw new IllegalArgumentException("Layout must be 'single' or 'double'");
        }

        double x;
        double y;
        if ("single".equalsIgnoreCase(layout)) {
            x = x0 + (optionIndex + 0.5) * (x1 - x0) / optionCount;
            y = y0 + (questionIndex + 0.5) * (y1 - y0) / questionCount;
        } else {
            int rightColumnQuestions = (questionCount + 1) / 2;
            int columnIndex = questionIndex < rightColumnQuestions ? 0 : 1;
            int rowIndex = questionIndex < rightColumnQuestions
                    ? questionIndex : questionIndex - rightColumnQuestions;
            int rowsInColumn = columnIndex == 0
                    ? rightColumnQuestions : questionCount - rightColumnQuestions;
            double halfWidth = (x1 - x0) / 2.0;
            double offset = (optionIndex + 0.5) * halfWidth / optionCount;
            // Numbering runs down the right column first, then down the left.
            x = columnIndex == 0 ? x1 - offset : x0 + offset;
            y = y0 + (rowIndex + 0.5) * (y1 - y0) / rowsInColumn;
        }
        return new double[] {x, y};
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
