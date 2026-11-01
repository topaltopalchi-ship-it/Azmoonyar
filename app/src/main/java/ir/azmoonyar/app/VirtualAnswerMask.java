package ir.azmoonyar.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/**
 * Builds the virtual white A4 answer-key mask.
 *
 * The mask is a display/inspection layer only. Its transparent answer holes
 * must never be fed into OMR scoring; scoring always samples the original
 * camera luminance buffer.
 */
public final class VirtualAnswerMask {
    private static final double HALF_BUBBLE_WIDTH_MM = 2.15;
    private static final double HALF_BUBBLE_HEIGHT_MM = 1.70;

    private VirtualAnswerMask() {}

    /**
     * @param width original camera image width
     * @param height original camera image height
     * @param transform canonical A4-to-camera projective transform
     * @param bubbleCenters canonical [question][A-D][x,y] centers in mm
     * @param answerKey one A-D character per question
     * @return ARGB bitmap: opaque white page with transparent holes at keyed bubbles
     */
    public static Bitmap render(int width, int height,
            SheetPerspectiveTransform transform, double[][][] bubbleCenters,
            String answerKey) {
        if (width <= 0 || height <= 0 || transform == null
                || bubbleCenters == null || bubbleCenters.length < 1
                || bubbleCenters.length > 100 || answerKey == null
                || answerKey.length() < bubbleCenters.length
                || (long) width * height > 16000000L) {
            throw new IllegalArgumentException("مشخصات نقاب پاسخنامه معتبر نیست.");
        }
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mask);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(0xFFFFFFFF);
        white.setStyle(Paint.Style.FILL);

        SheetPerspectiveTransform.Point tl = transform.toImage(0, 0);
        SheetPerspectiveTransform.Point tr = transform.toImage(
                OmrTemplateLayout.PAGE_WIDTH_MM, 0);
        SheetPerspectiveTransform.Point br = transform.toImage(
                OmrTemplateLayout.PAGE_WIDTH_MM, OmrTemplateLayout.PAGE_HEIGHT_MM);
        SheetPerspectiveTransform.Point bl = transform.toImage(
                0, OmrTemplateLayout.PAGE_HEIGHT_MM);
        Path page = new Path();
        page.moveTo((float) tl.x, (float) tl.y);
        page.lineTo((float) tr.x, (float) tr.y);
        page.lineTo((float) br.x, (float) br.y);
        page.lineTo((float) bl.x, (float) bl.y);
        page.close();
        canvas.drawPath(page, white);

        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setStyle(Paint.Style.FILL);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        for (int q = 0; q < bubbleCenters.length; q++) {
            if (bubbleCenters[q] == null || bubbleCenters[q].length != 4) {
                mask.recycle();
                throw new IllegalArgumentException("مختصات چهار گزینهٔ سؤال ناقص است.");
            }
            int correct = Character.toUpperCase(answerKey.charAt(q)) - 'A';
            if (correct < 0 || correct > 3) {
                mask.recycle();
                throw new IllegalArgumentException("کلید پاسخ باید فقط شامل A تا D باشد.");
            }
            double[] centerMm = bubbleCenters[q][correct];
            if (centerMm == null || centerMm.length != 2) {
                mask.recycle();
                throw new IllegalArgumentException("مختصات حباب پاسخ صحیح نامعتبر است.");
            }
            double x = centerMm[0], y = centerMm[1];
            SheetPerspectiveTransform.Point center = transform.toImage(x, y);
            SheetPerspectiveTransform.Point left = transform.toImage(
                    Math.max(0, x - HALF_BUBBLE_WIDTH_MM), y);
            SheetPerspectiveTransform.Point right = transform.toImage(
                    Math.min(OmrTemplateLayout.PAGE_WIDTH_MM, x + HALF_BUBBLE_WIDTH_MM), y);
            SheetPerspectiveTransform.Point top = transform.toImage(
                    x, Math.max(0, y - HALF_BUBBLE_HEIGHT_MM));
            SheetPerspectiveTransform.Point bottom = transform.toImage(
                    x, Math.min(OmrTemplateLayout.PAGE_HEIGHT_MM, y + HALF_BUBBLE_HEIGHT_MM));
            float rx = (float) ((Math.hypot(center.x-left.x, center.y-left.y)
                    + Math.hypot(right.x-center.x, right.y-center.y)) * 0.5);
            float ry = (float) ((Math.hypot(center.x-top.x, center.y-top.y)
                    + Math.hypot(bottom.x-center.x, bottom.y-center.y)) * 0.5);
            canvas.drawOval((float) center.x-rx, (float) center.y-ry,
                    (float) center.x+rx, (float) center.y+ry, clear);
        }
        clear.setXfermode(null);
        return mask;
    }
}
