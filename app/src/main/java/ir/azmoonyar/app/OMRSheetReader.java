package ir.azmoonyar.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EXPERIMENTAL OpenCV-backed main scoring path.
 *
 * This branch intentionally routes the scanner through OpenCvBubbleAnalyzer
 * instead of BubbleDarknessAnalyzer so the user can compare real-sheet results.
 * The frozen reference branch remains unchanged.
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
     *                        in the canonical printed template coordinate system
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

        if (!OpenCvBubbleAnalyzer.initialize()) {
            throw new IllegalStateException("موتور OpenCV راه‌اندازی نشد؛ اسکن متوقف شد تا پاسخ حدسی تولید نشود.");
        }

        List<QuestionResult> results = new ArrayList<>();
        for (int q = 0; q < bubbleCentersMm.length; q++) {
            double[][] centers = bubbleCentersMm[q];
            if (centers == null || centers.length != 4) {
                throw new IllegalArgumentException("هر سؤال باید چهار مختصات حباب داشته باشد.");
            }

            double[] centerX = new double[4];
            double[] centerY = new double[4];
            double[] radii = new double[4];
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

                centerX[option] = center.x;
                centerY[option] = center.y;
                radii[option] = (radiusX + radiusY) * 0.5;
            }

            // OpenCV scores are raw dark-pixel ratios from adaptive Gaussian thresholding.
            // They are intentionally fed into the same classifier for this first comparison.
            double[] scores = OpenCvBubbleAnalyzer.scores(
                    luminance, imageWidth, imageHeight, centerX, centerY, radii);
            results.add(new QuestionResult(q, scores, classifier.classify(scores)));
        }
        return Collections.unmodifiableList(results);
    }
}
