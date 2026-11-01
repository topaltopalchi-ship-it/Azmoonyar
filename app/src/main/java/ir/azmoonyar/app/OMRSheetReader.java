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

    /** Backward-compatible circular sampling overload for older callers and tests. */
    public List<QuestionResult> read(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[][][] bubbleCentersMm,
            double bubbleRadiusMm) {
        return read(luminance, imageWidth, imageHeight, transform, bubbleCentersMm,
                bubbleRadiusMm, bubbleRadiusMm);
    }

    /**
     * Reads the printed oval using its actual horizontal and vertical semi-axes.
     * The current A4 form prints 4.3 x 3.4 mm bubbles, hence 2.15 x 1.70 mm.
     */
    public List<QuestionResult> read(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[][][] bubbleCentersMm,
            double bubbleRadiusXmm, double bubbleRadiusYmm) {
        if (luminance == null || imageWidth <= 0 || imageHeight <= 0
                || (long) imageWidth * imageHeight > luminance.length
                || transform == null || bubbleCentersMm == null
                || bubbleCentersMm.length < 1 || bubbleCentersMm.length > 100
                || !validRadius(bubbleRadiusXmm) || !validRadius(bubbleRadiusYmm)) {
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
                        Math.min(transform.getPageWidth(), x + bubbleRadiusXmm), y);
                SheetPerspectiveTransform.Point edgeY = transform.toImage(
                        x, Math.min(transform.getPageHeight(), y + bubbleRadiusYmm));
                // Pass the full projected basis vectors, not only their lengths.
                // The page can be rotated or sheared in the camera image.
                scores[option] = BubbleDarknessAnalyzer.scoreProjectedEllipse(luminance,
                        imageWidth, imageHeight, center.x, center.y,
                        edgeX.x - center.x, edgeX.y - center.y,
                        edgeY.x - center.x, edgeY.y - center.y);
            }
            results.add(new QuestionResult(q, scores, classifier.classify(scores)));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Two-stage perforated-mask read. First inspect the four physical apertures
     * independently. If one or more show a mark, classify those aperture scores.
     * Only when all apertures are blank do we inspect complete bubbles to resolve
     * faint, misplaced, erased, or otherwise uncertain marks.
     */
    public List<QuestionResult> readWithAnswerMask(byte[] luminance, int imageWidth,
            int imageHeight, SheetPerspectiveTransform transform,
            double[][][] bubbleCentersMm, double bubbleRadiusXmm, double bubbleRadiusYmm) {
        if (luminance == null || imageWidth <= 0 || imageHeight <= 0
                || (long) imageWidth * imageHeight > luminance.length
                || transform == null || bubbleCentersMm == null
                || bubbleCentersMm.length < 1 || bubbleCentersMm.length > 100
                || !validRadius(bubbleRadiusXmm) || !validRadius(bubbleRadiusYmm)) {
            throw new IllegalArgumentException("تصویر یا مشخصات قالب پاسخنامه معتبر نیست.");
        }
        List<QuestionResult> results = new ArrayList<>();
        for (int q = 0; q < bubbleCentersMm.length; q++) {
            double[][] centers = bubbleCentersMm[q];
            if (centers == null || centers.length != 4) {
                throw new IllegalArgumentException("هر سؤال باید چهار مختصات حباب داشته باشد.");
            }
            double[] apertureScores = new double[4];
            for (int option = 0; option < 4; option++) {
                apertureScores[option] = scoreAperture(luminance, imageWidth, imageHeight,
                        transform, centers[option], bubbleRadiusXmm, bubbleRadiusYmm);
            }
            OMRAnswerClassifier.Result firstPass = classifier.classify(apertureScores);
            double[] scores = apertureScores;
            OMRAnswerClassifier.Result classification = firstPass;
            if (firstPass.status == OMRAnswerClassifier.Status.BLANK) {
                // A blank aperture is not automatically a wrong answer. Inspect all
                // four complete bubbles only in this second-stage review.
                scores = new double[4];
                for (int option = 0; option < 4; option++) {
                    scores[option] = scoreOption(luminance, imageWidth, imageHeight,
                            transform, centers[option], bubbleRadiusXmm, bubbleRadiusYmm);
                }
                classification = classifier.classify(scores);
            }
            results.add(new QuestionResult(q, scores, classification));
        }
        return Collections.unmodifiableList(results);
    }

    /** Compatibility overload: answer key is deliberately not used to locate marks. */
    public List<QuestionResult> readWithAnswerMask(byte[] luminance, int imageWidth,
            int imageHeight, SheetPerspectiveTransform transform,
            double[][][] bubbleCentersMm, double bubbleRadiusXmm,
            double bubbleRadiusYmm, String answerKey) {
        if (answerKey == null || bubbleCentersMm == null
                || answerKey.length() < bubbleCentersMm.length) {
            throw new IllegalArgumentException("کلید پاسخ برای همهٔ سؤال‌ها موجود نیست.");
        }
        return readWithAnswerMask(luminance, imageWidth, imageHeight, transform,
                bubbleCentersMm, bubbleRadiusXmm, bubbleRadiusYmm);
    }

    private static double scoreAperture(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[] centerMm,
            double radiusXmm, double radiusYmm) {
        if (centerMm == null || centerMm.length != 2) {
            throw new IllegalArgumentException("مختصات حباب باید شامل x و y باشد.");
        }
        double x=centerMm[0], y=centerMm[1];
        SheetPerspectiveTransform.Point center=transform.toImage(x,y);
        SheetPerspectiveTransform.Point edgeX=transform.toImage(
                Math.min(transform.getPageWidth(),x+radiusXmm),y);
        SheetPerspectiveTransform.Point edgeY=transform.toImage(
                x,Math.min(transform.getPageHeight(),y+radiusYmm));
        return BubbleDarknessAnalyzer.scoreProjectedAperture(luminance,imageWidth,imageHeight,
                center.x,center.y,edgeX.x-center.x,edgeX.y-center.y,
                edgeY.x-center.x,edgeY.y-center.y);
    }

    private static double scoreOption(byte[] luminance, int imageWidth, int imageHeight,
            SheetPerspectiveTransform transform, double[] centerMm,
            double radiusXmm, double radiusYmm) {
        if (centerMm == null || centerMm.length != 2) {
            throw new IllegalArgumentException("مختصات حباب باید شامل x و y باشد.");
        }
        double x = centerMm[0], y = centerMm[1];
        SheetPerspectiveTransform.Point center = transform.toImage(x, y);
        SheetPerspectiveTransform.Point edgeX = transform.toImage(
                Math.min(transform.getPageWidth(), x + radiusXmm), y);
        SheetPerspectiveTransform.Point edgeY = transform.toImage(
                x, Math.min(transform.getPageHeight(), y + radiusYmm));
        return BubbleDarknessAnalyzer.scoreProjectedEllipse(luminance,
                imageWidth, imageHeight, center.x, center.y,
                edgeX.x - center.x, edgeX.y - center.y,
                edgeY.x - center.x, edgeY.y - center.y);
    }

    private static boolean validRadius(double radius) {
        return radius >= 1 && !Double.isNaN(radius) && !Double.isInfinite(radius);
    }
}
