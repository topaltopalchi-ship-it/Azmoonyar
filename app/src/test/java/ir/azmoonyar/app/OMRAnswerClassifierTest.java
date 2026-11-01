package ir.azmoonyar.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OMRAnswerClassifierTest {
    private final OMRAnswerClassifier classifier = new OMRAnswerClassifier();

    @Test
    public void detectsStrongSingleMark() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.12, 0.81, 0.18, 0.10});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void detectsBlankQuestion() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.04, 0.09, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void flagsMultipleMarkedBubbles() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.MULTIPLE_MARKS, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void closeLowContrastMarksAreSentForReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.251, 0.250, 0.12, 0.10});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void nearTieIsSentForReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.500, 0.499, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void detectsModerateRealPhotoMarkWhenClearlyStronger() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.00, 0.00, 0.08, 0.40});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(3, result.optionIndex);
    }

    @Test
    public void markBelowBlankThresholdRemainsBlank() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.02, 0.00, 0.10, 0.08});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
        assertEquals(-1, result.optionIndex);
    }

    @Test
    public void exactTieIsSentForReview() {
        OMRAnswerClassifier.Result result = classifier.classify(new double[]{0.20, 0.20, 0.10, 0.05});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void maskApertureMarkImmediatelyConfirmsKeyedAnswer() {
        OMRAnswerClassifier.Result result = classifier.classifyThroughMask(
                0.72, 2, new double[]{0.0, 0.0, 0.0, 0.0});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(2, result.optionIndex);
    }

    @Test
    public void missedMaskApertureFallsBackAndFindsWrongOption() {
        OMRAnswerClassifier.Result result = classifier.classifyThroughMask(
                0.08, 0, new double[]{0.08, 0.77, 0.10, 0.04});
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, result.status);
        assertEquals(1, result.optionIndex);
    }

    @Test
    public void missedMaskApertureAndFourEmptyBubblesRemainBlank() {
        OMRAnswerClassifier.Result result = classifier.classifyThroughMask(
                0.05, 3, new double[]{0.04, 0.09, 0.11, 0.06});
        assertEquals(OMRAnswerClassifier.Status.BLANK, result.status);
    }

    @Test
    public void missedMaskApertureAndTwoFilledOptionsAreMultipleMarks() {
        OMRAnswerClassifier.Result result = classifier.classifyThroughMask(
                0.06, 0, new double[]{0.71, 0.15, 0.68, 0.09});
        assertEquals(OMRAnswerClassifier.Status.MULTIPLE_MARKS, result.status);
    }

    @Test
    public void conflictingFullBubbleAndMaskEvidenceNeedsReview() {
        OMRAnswerClassifier.Result result = classifier.classifyThroughMask(
                0.10, 1, new double[]{0.10, 0.81, 0.08, 0.05});
        assertEquals(OMRAnswerClassifier.Status.REVIEW, result.status);
    }

    @Test
    public void rejectsThresholdsOutsideNormalizedRange() {
        try {
            new OMRAnswerClassifier(-0.1, 0.0, 0.1);
            fail("Expected invalid thresholds to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            new OMRAnswerClassifier(0.5, 0.6, 0.1);
            fail("Expected blank threshold above mark threshold to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }


    @Test
    public void sheetReaderFindsARealisticSingleFilledBubbleInSyntheticPhoto() {
        final int width = 1000, height = 1400;
        final double scale = 4.5;
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(20, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20 + 297 * scale),
                new SheetPerspectiveTransform.Point(20, 20 + 297 * scale),
                210, 297);
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(1, 0);
        byte[] photo = new byte[width * height];
        java.util.Arrays.fill(photo, (byte) 255);

        SheetPerspectiveTransform.Point center = transform.toImage(
                centers[0][2][0], centers[0][2][1]);
        int cx = (int) Math.round(center.x), cy = (int) Math.round(center.y);
        for (int y = cy - 5; y <= cy + 5; y++) {
            for (int x = cx - 5; x <= cx + 5; x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= 25) {
                    photo[y * width + x] = 0;
                }
            }
        }

        java.util.List<OMRSheetReader.QuestionResult> read =
                new OMRSheetReader().read(photo, width, height, transform, centers, 1.45);
        assertEquals(1, read.size());
        assertEquals(OMRAnswerClassifier.Status.ANSWERED, read.get(0).classification.status);
        assertEquals(2, read.get(0).classification.optionIndex);
    }

    @Test
    public void sheetReaderTreatsUnmarkedSyntheticPhotoAsBlank() {
        final int width = 1000, height = 1400;
        final double scale = 4.5;
        SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromPageCorners(
                new SheetPerspectiveTransform.Point(20, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20),
                new SheetPerspectiveTransform.Point(20 + 210 * scale, 20 + 297 * scale),
                new SheetPerspectiveTransform.Point(20, 20 + 297 * scale),
                210, 297);
        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(1, 0);
        byte[] photo = new byte[width * height];
        java.util.Arrays.fill(photo, (byte) 255);

        java.util.List<OMRSheetReader.QuestionResult> read =
                new OMRSheetReader().read(photo, width, height, transform, centers, 1.45);
        assertEquals(OMRAnswerClassifier.Status.BLANK, read.get(0).classification.status);
    }

    @Test
    public void rejectsWrongNumberOfBubbles() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 0.3});
            fail("Expected invalid input to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void rejectsScoresOutsideRange() {
        try {
            classifier.classify(new double[]{0.1, 0.2, 1.1, 0.3});
            fail("Expected invalid score to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
