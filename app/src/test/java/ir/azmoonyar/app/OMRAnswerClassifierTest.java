package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier day = new OMRAnswerClassifier();

    @Test public void oneAboveDayThresholdIsAnswer() {
        OMRAnswerClassifier.Result r=day.classify(new double[]{0.12,0.81,0.18,0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED,r.status); assertEquals(1,r.optionIndex);
    }
    @Test public void dayAtOrBelowPointTwoIsBlank() {
        assertEquals(OMRAnswerClassifier.Status.BLANK,day.classify(new double[]{0.04,0.20,0.11,0.06}).status);
    }
    @Test public void closeTwoDayMarksReview() {
        assertEquals(OMRAnswerClassifier.Status.REVIEW,day.classify(new double[]{0.71,0.15,0.68,0.09}).status);
    }
    @Test public void exactPointOneMarginChoosesDarkest() {
        OMRAnswerClassifier.Result r=day.classify(new double[]{0.42,0.32,0.08,0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED,r.status); assertEquals(0,r.optionIndex);
    }
    @Test public void threeMarkedOptionsReview() {
        assertEquals(OMRAnswerClassifier.Status.REVIEW,day.classify(new double[]{0.51,0.41,0.31,0.10}).status);
    }
    @Test public void averageAbovePointTwoSelectsDay() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.25,0.24,0.22,0.21}});
        assertEquals(OMRAnswerClassifier.LightMode.DAY,c.getLightMode());
        assertEquals(0.20,c.getMarkThreshold(),0.000001);
    }
    @Test public void averageBetweenPointOneAndPointTwoSelectsNight() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.19,0.18,0.15,0.14}});
        assertEquals(OMRAnswerClassifier.LightMode.NIGHT,c.getLightMode());
        assertEquals(0.10,c.getMarkThreshold(),0.000001);
    }
    @Test public void averageAtPointTwoSelectsNight() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.20,0.20,0.20,0.20}});
        assertEquals(OMRAnswerClassifier.LightMode.NIGHT,c.getLightMode());
    }
    @Test public void averageAtOrBelowPointOneDefaultsNight() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.10,0.10,0.10,0.10}});
        assertEquals(OMRAnswerClassifier.LightMode.NIGHT,c.getLightMode());
    }
    @Test public void nightThresholdsClassifySingleMarkAndBlank() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.19,0.18,0.15,0.14}});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED,c.classify(new double[]{0.18,0.05,0.03,0.01}).status);
        assertEquals(OMRAnswerClassifier.Status.BLANK,c.classify(new double[]{0.10,0.09,0.03,0.01}).status);
    }
    @Test public void nightDoubleMarkGapUnderPointOneReview() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.19,0.18,0.15,0.14}});
        assertEquals(OMRAnswerClassifier.Status.REVIEW,c.classify(new double[]{0.19,0.15,0.03,0.01}).status);
    }
    @Test public void nightDoubleMarkGapPointOneSelectsDarker() {
        OMRAnswerClassifier c=OMRAnswerClassifier.forSheetScores(new double[][]{{0.19,0.18,0.15,0.14}});
        OMRAnswerClassifier.Result r=c.classify(new double[]{0.25,0.15,0.03,0.01});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED,r.status); assertEquals(0,r.optionIndex);
    }
    @Test public void rejectsWrongBubbleCountAndInvalidScores() {
        try { day.classify(new double[]{0.1,0.2,0.3}); fail(); } catch(IllegalArgumentException expected) {}
        try { day.classify(new double[]{0.1,0.2,1.1,0.3}); fail(); } catch(IllegalArgumentException expected) {}
    }
}
