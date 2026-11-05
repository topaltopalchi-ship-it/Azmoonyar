package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OMRAnswerClassifierSensitivityTest {
    @Test
    public void faintSingleMarkBelowPointOneIsWhite() {
        OMRAnswerClassifier.Result result =
                new OMRAnswerClassifier().classify(new double[]{0.001, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void twoQualifyingCloseMarkedOptionsNeedReview() {
        OMRAnswerClassifier.Result result =
                new OMRAnswerClassifier().classify(new double[]{0.62, 0.55, 0.03, 0.02});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void clearlyDarkerOptionWinsWhenSecondMarkIsBelowPointTwo() {
        OMRAnswerClassifier.Result result =
                new OMRAnswerClassifier().classify(new double[]{0.62, 0.19, 0.03, 0.02});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(0, result.optionIndex);
    }

    @Test
    public void closeTwoScoresNeedReview() {
        OMRAnswerClassifier.Result result =
                new OMRAnswerClassifier().classify(new double[]{0.34, 0.3399995, 0.02, 0.01});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void allZeroBubblesRemainBlank() {
        OMRAnswerClassifier.Result result =
                new OMRAnswerClassifier().classify(new double[]{0.0, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
    }
}
