package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test public void detectsOneMarkedOption() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.12, 0.81, 0.18, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(1, r.optionIndex);
    }

    @Test public void belowOrAtDayThresholdIsBlank() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.04, 0.20, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, r.status);
        assertEquals(-1, r.optionIndex);
    }

    @Test public void closeTwoDayMarksGoToReview() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, r.status);
    }

    @Test public void secondMarkAtDayThresholdIsNotCounted() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.29, 0.20, 0.08, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void differenceOfPointOneSelectsDarkerOption() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.42, 0.32, 0.08, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void differenceBelowPointOneGoesToReview() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.251, 0.250, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, r.status);
    }

    @Test public void threeMarkedOptionsGoToReview() {
        OMRAnswerClassifier.Result r = classifier.classify(new double[]{0.51, 0.41, 0.31, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, r.status);
    }

    @Test public void autoSelectsDayWhenMostScoresExceedPointTwo() {
        OMRAnswerClassifier selected = OMRAnswerClassifier.forSheetScores(new double[][]{
                {0.25, 0.24, 0.22, 0.21}, {0.23, 0.22, 0.21, 0.19}
        });
        assertEquals(OMRAnswerClassifier.LightMode.DAY, selected.getLightMode());
        assertEquals(0.20, selected.getMarkThreshold(), 0.000001);
    }

    @Test public void autoSelectsNightWhenMostScoresExceedPointOneButNotPointTwo() {
        OMRAnswerClassifier selected = OMRAnswerClassifier.forSheetScores(new double[][]{
                {0.19, 0.18, 0.15, 0.14}, {0.19, 0.17, 0.15, 0.11}
        });
        assertEquals(OMRAnswerClassifier.LightMode.NIGHT, selected.getLightMode());
        assertEquals(0.10, selected.getMarkThreshold(), 0.000001);
    }

    @Test public void nightModeRecognizesScoresAbovePointOne() {
        OMRAnswerClassifier night = OMRAnswerClassifier.forSheetScores(new double[][]{
                {0.19, 0.18, 0.15, 0.14}, {0.19, 0.17, 0.15, 0.11}
        });
        OMRAnswerClassifier.Result r = night.classify(new double[]{0.18, 0.05, 0.03, 0.01});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, r.status);
        assertEquals(0, r.optionIndex);
    }

    @Test public void rejectsInvalidThresholds() {
        try { new OMRAnswerClassifier(-0.1, 0.0, 0.1); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new OMRAnswerClassifier(0.5, 0.6, 0.1); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsWrongNumberOfBubbles() {
        try { classifier.classify(new double[]{0.1, 0.2, 0.3}); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsScoresOutsideRange() {
        try { classifier.classify(new double[]{0.1, 0.2, 1.1, 0.3}); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
