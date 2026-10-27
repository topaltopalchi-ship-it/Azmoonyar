package ir.azmoonyar.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

/** Conservative image-only detector for printed answer-table bounds and corner marks. */
final class ExternalSheetAutoDetector {
    static final class Detection {
        final RectF table;
        final int cornerMarks;
        final int componentPixels;
        Detection(RectF table, int cornerMarks, int componentPixels) {
            this.table = table;
            this.cornerMarks = cornerMarks;
            this.componentPixels = componentPixels;
        }
    }

    private ExternalSheetAutoDetector() {}

    static Detection detect(Bitmap source) {
        if (source == null || source.getWidth() < 200 || source.getHeight() < 200) return null;
        int scale = Math.max(1, Math.max(source.getWidth(), source.getHeight()) / 900);
        int w = source.getWidth() / scale, h = source.getHeight() / scale;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = source.getPixel(Math.min(source.getWidth()-1, x*scale),
                        Math.min(source.getHeight()-1, y*scale));
            }
        }
        boolean[] dark = new boolean[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int lum = (int)(0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p));
            dark[i] = lum < 145;
        }
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int bestArea = 0, bestL = 0, bestT = 0, bestR = 0, bestB = 0;
        for (int start = 0; start < dark.length; start++) {
            if (!dark[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start; seen[start] = true;
            int minX = start % w, maxX = minX, minY = start / w, maxY = minY, count = 0;
            while (head < tail) {
                int at = queue[head++], x = at % w, y = at / w;
                count++;
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (x > 0) tail = enqueue(at-1, dark, seen, queue, tail);
                if (x+1 < w) tail = enqueue(at+1, dark, seen, queue, tail);
                if (y > 0) tail = enqueue(at-w, dark, seen, queue, tail);
                if (y+1 < h) tail = enqueue(at+w, dark, seen, queue, tail);
            }
            int bw = maxX-minX+1, bh = maxY-minY+1;
            double wf = bw/(double)w, hf = bh/(double)h, ratio = bw/(double)bh;
            // Printed table rules join into a large connected component. Ignore text, QR and page edges.
            if (count > bestArea && wf >= 0.15 && wf <= 0.70 && hf >= 0.08 && hf <= 0.45
                    && ratio >= 0.65 && ratio <= 2.2
                    && minX > w*0.08 && maxX < w*0.96 && minY > h*0.08 && maxY < h*0.88) {
                bestArea = count; bestL = minX; bestT = minY; bestR = maxX+1; bestB = maxY+1;
            }
        }
        if (bestArea < 800) return null;
        int marks = countCornerMarks(dark, w, h);
        float padX = 2f / w, padY = 2f / h;
        RectF bounds = new RectF(Math.max(0, bestL/(float)w-padX),
                Math.max(0, bestT/(float)h-padY), Math.min(1, bestR/(float)w+padX),
                Math.min(1, bestB/(float)h+padY));
        return new Detection(bounds, marks, bestArea);
    }

    private static int enqueue(int index, boolean[] dark, boolean[] seen, int[] queue, int tail) {
        if (dark[index] && !seen[index]) {
            seen[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int countCornerMarks(boolean[] dark, int w, int h) {
        int count = 0;
        int[][] regions = {
            {0, 0, w/4, h/4}, {w*3/4, 0, w, h/4},
            {0, h*3/4, w/4, h}, {w*3/4, h*3/4, w, h}
        };
        for (int[] r : regions) {
            int hits = 0;
            for (int y=r[1]; y<r[3]; y++) for (int x=r[0]; x<r[2]; x++)
                if (dark[y*w+x]) hits++;
            double fraction = hits / (double)((r[2]-r[0])*(r[3]-r[1]));
            if (fraction > 0.0015 && fraction < 0.08) count++;
        }
        return count;
    }
}
