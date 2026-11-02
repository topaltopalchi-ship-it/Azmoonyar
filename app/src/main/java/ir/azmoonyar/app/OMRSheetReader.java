package ir.azmoonyar.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Samples four bubbles per question after a caller has validated the sheet
 * registration marks and supplied bubble centers in the printed template's
 * coordinate system. No default coordinates are guessed here.
 */
public final class OMRSheetReader {
    public static final class QuestionResult {
        public final int questionIndex;
        public final double[] darkness;
        public final OMRAnswerClassifier.Result classification;

        private QuestionResult(int questionIndex, double[] darkness,
                               OMRAnswerClassifier.Result classification) {
            this.questionIndex = questionIndex;
            this.darkness = darkness.clone();
            this.classification = classification;
        }
    }

    private final OMRAnswerClassifier classifier;
    private final double calibrationOffset;
    private final double[] regionalOffsets;

    public OMRSheetReader() { this(new OMRAnswerClassifier(), 0.0, null); }
    public OMRSheetReader(double offset) { this(new OMRAnswerClassifier(), offset, null); }
    public OMRSheetReader(double offset, double[] regionalOffsets) { this(new OMRAnswerClassifier(), offset, regionalOffsets); }
    public OMRSheetReader(OMRAnswerClassifier classifier) { this(classifier, 0.0, null); }

    public OMRSheetReader(OMRAnswerClassifier classifier, double offset) { this(classifier, offset, null); }

    public OMRSheetReader(OMRAnswerClassifier classifier, double offset, double[] regionalOffsets) {
        if (classifier == null) throw new IllegalArgumentException("طبقه‌بند پاسخ لازم است.");
        if (Double.isNaN(offset) || Double.isInfinite(offset) || offset < 0.0 || offset > 1.0)
            throw new IllegalArgumentException("اصلاح کالیبراسیون معتبر نیست.");
        if (regionalOffsets != null && regionalOffsets.length != 4)
            throw new IllegalArgumentException("چهار مقدار اصلاح منطقه‌ای لازم است.");
        if (regionalOffsets != null) {
            for (double value : regionalOffsets) {
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 0.12)
                    throw new IllegalArgumentException("اصلاح منطقه‌ای معتبر نیست.");
            }
        }
        this.classifier = classifier;
        this.calibrationOffset = offset;
        this.regionalOffsets = regionalOffsets == null ? null : regionalOffsets.clone();
    }

    /**
     * @param bubbleCentersMm each question contains exactly four [x,y] centers
     *                        in the canonical printed page coordinate system
     * @param bubbleRadiusMm printed bubble radius in the same coordinate units
     */
    public List<QuestionResult> read(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[][][] bubbleCentersMm,
            double bubbleRadiusMm) {
        if (luminance == null || imageWidth <= 0 || imageHeight <= 0
                || (long) imageWidth * imageHeight > luminance.length
                || transform == null || bubbleCentersMm == null
                || bubbleCentersMm.length < 1 || bubbleCentersMm.length > 100
                || bubbleRadiusMm < 1
                || Double.isNaN(bubbleRadiusMm) || Double.isInfinite(bubbleRadiusMm)) {
            throw new IllegalArgumentException("تصویر یا مشخصات قالب پاسخنامه معتبر نیست.");
        }

        List<QuestionResult> results = new ArrayList<>();
        for (int q = 0; q < bubbleCentersMm.length; q++) {
            double[][] centers = bubbleCentersMm[q];
            if (centers == null || centers.length != 4) {
                throw new IllegalArgumentException("هر سؤال باید چهار مختصات حباب داشته باشد.");
            }
            double[] scores = new double[4];
            for (int option = 0; option < 4; option++) {
                if (centers[option] == null || centers[option].length != 2) {
                    throw new IllegalArgumentException("مختصات حباب باید شامل x و y باشد.");
                }
                double x = centers[option][0], y = centers[option][1];
                SheetPerspectiveTransform.Point center = transform.toImage(x, y);
                SheetPerspectiveTransform.Point edgeX = transform.toImage(
                        Math.min(transform.getPageWidth(), x + bubbleRadiusMm), y);
                SheetPerspectiveTransform.Point edgeY = transform.toImage(
                        x, Math.min(transform.getPageHeight(), y + bubbleRadiusMm));
                double radiusX = Math.hypot(edgeX.x - center.x, edgeX.y - center.y);
                double radiusY = Math.hypot(edgeY.x - center.x, edgeY.y - center.y);
                double radiusPixels = (radiusX + radiusY) * 0.5;
                double rawScore = BubbleDarknessAnalyzer.score(luminance,
                        imageWidth, imageHeight, center.x, center.y, radiusPixels);
                int region = (y / transform.getPageHeight() >= 0.5 ? 2 : 0)
                        + (x / transform.getPageWidth() >= 0.5 ? 1 : 0);
                double regionalOffset = regionalOffsets == null ? 0.0 : regionalOffsets[region];
                scores[option] = Math.max(0.0, rawScore - calibrationOffset - regionalOffset);
            }
            results.add(new QuestionResult(q, scores, classifier.classify(scores)));
        }
        return Collections.unmodifiableList(results);
    }
}
