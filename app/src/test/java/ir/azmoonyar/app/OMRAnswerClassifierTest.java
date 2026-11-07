package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test public void oneScoreAbovePointTwoIsCorrect() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.12, 0.81, 0.18, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(1, r.optionIndex);
    }

    @Test public void allScoresBelowPointTwoAreBlank() {
        assertEquals(OMRAnswerClassifier.Status.BLANK,
                classifier.classify(new double[]{0.04, 0.19, 0.11, 0.06}).status);
    }

    @Test public void exactPointTwoIsBlank() {
        assertEquals(OMRAnswerClassifier.Status.BLANK,
                classifier.classify(new double[]{0.20, 0.12, 0.08, 0.01}).status);
    }

    @Test public void twoMarksWithGapLessThanPointOneGoToReview() {
        assertEquals(OMRAnswerClassifier.Status.REVIEW,
                classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09}).status);
    }

    @Test public void twoMarksWithGapGreaterThanPointOneChooseDarker() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.42, 0.31, 0.08, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void exactPointOneGapChoosesDarker() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.42, 0.32, 0.08, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void secondBubbleAtOrBelowPointTwoDoesNotCountAsSecondMark() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.42, 0.20, 0.08, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void moreThanTwoMarkedOptionsGoToReview() {
        assertEquals(OMRAnswerClassifier.Status.REVIEW,
                classifier.classify(new double[]{0.51, 0.41, 0.31, 0.10}).status);
    }

    @Test public void diagnosticModesDoNotChangeFixedThresholds() {
        OMRAnswerClassifier c = OMRAnswerClassifier.forSheetScores(new double[][]{{0.19, 0.18, 0.15, 0.14}});
        assertEquals(0.20, c.getMarkThreshold(), 0.000001);
        assertEquals(0.20, c.getBlankThreshold(), 0.000001);
        assertEquals(0.10, c.getMinimumMargin(), 0.000001);
        assertEquals(OMRAnswerClassifier.Status.BLANK,
                c.classify(new double[]{0.19, 0.18, 0.15, 0.14}).status);
    }

    @Test public void rejectsWrongBubbleCountAndInvalidScores() {
        try { classifier.classify(new double[]{0.1, 0.2, 0.3}); fail(); }
        catch (IllegalArgumentException expected) {}
        try { classifier.classify(new double[]{0.1, 0.2, 1.1, 0.3}); fail(); }
        catch (IllegalArgumentException expected) {}
    }
}
