package ir.azmoonyar.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class OMRCalibrationTest {
    private static double[] coords(int count, boolean x) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = x ? ((i % 8) < 4 ? 0.25 : 0.75)
                    : ((i / 8) < 4 ? 0.25 : 0.75);
        }
        return values;
    }

    @Test public void uniformBlankSheetCanReachGreenAfterOffset() {
        double[] darkness = new double[64];
        java.util.Arrays.fill(darkness, 0.03);
        OMRCalibration.Result result = OMRCalibration.evaluate(
                darkness, 0.03, coords(64, true), coords(64, false));
        assertEquals(OMRCalibration.Status.CALIBRATED, result.status);
        assertEquals(100.0, result.qualityIndex, 0.001);
        assertEquals(0.03, result.recommendedOffset, 0.001);
    }

    @Test public void spatialLightingGradientCannotBeMadeGreenByGlobalOffset() {
        double[] darkness = new double[64];
        for (int i = 0; i < darkness.length; i++) {
            int quadrant = (i / 16);
            darkness[i] = 0.02 + quadrant * 0.03;
        }
        OMRCalibration.Result result = OMRCalibration.evaluate(
                darkness, 0.065, coords(64, true), coords(64, false));
        assertNotEquals(OMRCalibration.Status.CALIBRATED, result.status);
        assertTrue(result.regionalMedianRange > 0.05);
    }

    @Test public void wrongOffsetDoesNotShowGreen() {
        double[] darkness = new double[64];
        java.util.Arrays.fill(darkness, 0.04);
        OMRCalibration.Result result = OMRCalibration.evaluate(
                darkness, 0.0, coords(64, true), coords(64, false));
        assertNotEquals(OMRCalibration.Status.CALIBRATED, result.status);
    }

    @Test public void tooFewSamplesAreRejected() {
        OMRCalibration.Result result = OMRCalibration.evaluate(
                new double[]{0.01, 0.02, 0.01, 0.02},
                0.01, new double[]{0.1, 0.2, 0.8, 0.9},
                new double[]{0.1, 0.2, 0.8, 0.9});
        assertEquals(OMRCalibration.Status.INVALID, result.status);
    }
}
