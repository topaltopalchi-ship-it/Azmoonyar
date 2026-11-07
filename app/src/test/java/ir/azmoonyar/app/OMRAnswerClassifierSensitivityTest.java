package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OMRAnswerClassifierSensitivityTest {
    @Test
    public void faintSingleMarkIsRecognizedAboveTheFixedThreshold() {
        OMRAnswerClassifier.Result result = new OMRAnswerClassifier().classify(new double[]{0.34, 0.04, 0.02, 0.03});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void scoreAboveOneTenthIsRecognized() {
        OMRAnswerClassifier.Result result = new OMRAnswerClassifier().classify(new double[]{0.18, 0.04, 0.02, 0.03});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void strongerOfTwoMarksWinsWhenGapIsGreaterThanOneTenth() {
        OMRAnswerClassifier.Result result = new OMRAnswerClassifier().classify(new double[]{0.62, 0.51, 0.03, 0.02});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void emptyBubblesRemainBlank() {
        OMRAnswerClassifier.Result result = new OMRAnswerClassifier().classify(new double[]{0.03, 0.04, 0.02, 0.01});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
    }
}
