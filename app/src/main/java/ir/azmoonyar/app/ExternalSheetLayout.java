package ir.azmoonyar.app;

/**
 * Normalized bubble geometry for common external answer-sheet layouts.
 * Coordinates are fractions of the source image, not guessed page dimensions.
 */
public final class ExternalSheetLayout {
    private ExternalSheetLayout() {}

    /**
     * Returns {x,y} in normalized image coordinates.
     * For double-column sheets, the first half of questions is in the right
     * column (RTL reading order); remaining questions are in the left column.
     * "manual" currently uses the single-grid model after the teacher narrows
     * the bounds to the relevant regular table.
     */
    public static double[] bubbleCenter(int questionIndex, int optionIndex,
            int questions, int options, String layout,
            double left, double top, double right, double bottom) {
        if (questions < 1 || questionIndex < 0 || questionIndex >= questions
                || options < 2 || optionIndex < 0 || optionIndex >= options
                || !finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                || left < 0 || top < 0 || right > 1 || bottom > 1
                || right <= left || bottom <= top) {
            throw new IllegalArgumentException("مشخصات هندسهٔ پاسخنامه معتبر نیست.");
        }
        if ("double".equals(layout)) {
            int firstColumnQuestions = (questions + 1) / 2;
            boolean rightColumn = questionIndex < firstColumnQuestions;
            int row = rightColumn ? questionIndex : questionIndex - firstColumnQuestions;
            int columnQuestions = rightColumn ? firstColumnQuestions : questions - firstColumnQuestions;
            if (columnQuestions < 1) columnQuestions = 1;
            double half = (right - left) / 2.0;
            double x = rightColumn
                    ? left + half + (optionIndex + 0.5) * half / options
                    : left + (optionIndex + 0.5) * half / options;
            double y = top + (row + 0.5) * (bottom - top) / columnQuestions;
            return new double[]{x, y};
        }
        double x = left + (optionIndex + 0.5) * (right - left) / options;
        double y = top + (questionIndex + 0.5) * (bottom - top) / questions;
        return new double[]{x, y};
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
