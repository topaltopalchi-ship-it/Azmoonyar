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

    public OMRSheetReader() {
        this(new OMRAnswerClassifier());
    }

    public OMRSheetReader(OMRAnswerClassifier classifier) {
        if (classifier == null) throw new IllegalArgumentException("طبقه‌بند پاسخ لازم است.");
        this.classifier = classifier;
    }

    /**
     * @param bubbleCentersMm each question contains exactly four [x,y] centers
     *                        in the canonical printed page coordinate system
     * @param bubbleRadiusMm printed bubble radius in the same coordinate units
     */
    public List<QuestionResult> read(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[][][] bubbleCentersMm,
            double bubbleRadiusMm) {
        return read(luminance, imageWidth, imageHeight, transform, bubbleCentersMm,
                bubbleRadiusMm, bubbleRadiusMm);
    }

    /** Reads bubbles using independent horizontal and vertical ellipse radii in mm. */
    public List<QuestionResult> read(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[][][] bubbleCentersMm,
            double bubbleRadiusXmm, double bubbleRadiusYmm) {
        if (luminance == null || imageWidth <= 0 || imageHeight <= 0
                || (long) imageWidth * imageHeight > luminance.length
                || transform == null || bubbleCentersMm == null
                || bubbleCentersMm.length < 1 || bubbleCentersMm.length > 100
                || !finite(bubbleRadiusXmm) || !finite(bubbleRadiusYmm)
                || bubbleRadiusXmm < 1 || bubbleRadiusYmm < 1) {
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
                SheetPerspectiveTransform.Point left = transform.toImage(x - bubbleRadiusXmm, y);
                SheetPerspectiveTransform.Point right = transform.toImage(x + bubbleRadiusXmm, y);
                SheetPerspectiveTransform.Point top = transform.toImage(x, y - bubbleRadiusYmm);
                SheetPerspectiveTransform.Point bottom = transform.toImage(x, y + bubbleRadiusYmm);
                double radiusX = (Math.hypot(left.x-center.x,left.y-center.y)
                        + Math.hypot(right.x-center.x,right.y-center.y)) * 0.5;
                double radiusY = (Math.hypot(top.x-center.x,top.y-center.y)
                        + Math.hypot(bottom.x-center.x,bottom.y-center.y)) * 0.5;
                scores[option] = BubbleDarknessAnalyzer.score(luminance,
                        imageWidth, imageHeight, center.x, center.y, radiusX, radiusY);
            }
            results.add(new QuestionResult(q, scores, classifier.classify(scores)));
        }
        return Collections.unmodifiableList(results);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

}
