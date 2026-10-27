package ir.azmoonyar.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Button;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * Live-camera alignment cue only. Corner-window detection is intentionally not
 * treated as validated marker geometry or as permission to score a sheet.
 */
public final class OmrScanActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 8402;
    private PreviewView previewView;
    private TextView statusView;
    private ExecutorService cameraExecutor;
    private volatile long lastStatusUpdate;
    private volatile long lastFrameAnalysis;
    private volatile boolean finished;
    private String participantName = "";
    private int stableMarkerFrames, missingMarkerFrames;
    private volatile boolean sheetConsumed, waitingForNextName;
    private PersianVosk voiceRecognizer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 27, 114));
        getWindow().setNavigationBarColor(Color.rgb(7, 27, 114));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(245, 248, 255));

        TextView title = new TextView(this);
        title.setText("اسکن پاسخنامه");
        title.setTextColor(Color.rgb(7, 40, 130));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(12, 16, 12, 12);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        page.addView(previewView, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setText("دوربین برای یافتن چهار مربع گوشه آماده می‌شود…");
        statusView.setTextColor(Color.rgb(30, 41, 59));
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(16, 12, 16, 12);
        page.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        TextView guidance = new TextView(this);
        guidance.setText("برگه را کامل داخل تصویر بگیرید. پس از تشخیص پایدار، اسکن خودکار انجام می‌شود؛ برای برگهٔ بعدی، برگهٔ قبلی را کنار ببرید.");
        guidance.setTextColor(Color.rgb(80, 90, 110));
        guidance.setTextSize(13);
        guidance.setGravity(Gravity.CENTER);
        guidance.setPadding(18, 4, 18, 14);
        page.addView(guidance, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);

        participantName = getIntent().getStringExtra("participant_name");
        if (participantName == null) participantName = "";
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1280, 960))
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis);
            } catch (Exception error) {
                updateStatus("راه‌اندازی دوربین ناموفق بود؛ مجوز دوربین و دستگاه را بررسی کنید.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        try {
            if (image.getFormat() != android.graphics.ImageFormat.YUV_420_888
                    || image.getPlanes().length == 0) return;
            // Avoid allocating image-sized buffers on every camera frame.
            // Keep the preview responsive while preserving enough pixels for the
            // small 3 mm outlined registration marks on the current A4 template.
            long frameNow = android.os.SystemClock.elapsedRealtime();
            if (frameNow - lastFrameAnalysis < 80) return;
            lastFrameAnalysis = frameNow;
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer().duplicate();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            byte[] luma = new byte[width * height];
            int base = buffer.position();
            for (int y = 0; y < height; y++) {
                int row = base + y * rowStride;
                for (int x = 0; x < width; x++) {
                    int index = row + x * pixelStride;
                    if (index >= 0 && index < buffer.limit()) {
                        luma[y * width + x] = buffer.get(index);
                    }
                }
            }
            int rotation = image.getImageInfo().getRotationDegrees();
            UprightLuma upright = rotateToUpright(luma, width, height, rotation);
            MarkerDetection detection = detectDarkCornerMarkers(
                    upright.pixels, upright.width, upright.height);
            int markerCount = detection.count;
            SheetPerspectiveTransform transform = null;
            if (markerCount == 4) {
                try {
                    // Existing template registration-mark centers.
                    transform = SheetPerspectiveTransform.fromControlPoints(
                            new SheetPerspectiveTransform.Point[]{
                                    new SheetPerspectiveTransform.Point(detection.markerSizeMm > 8 ? 13.0 : 6.5,
                                            detection.markerSizeMm > 8 ? 13.0 : 6.5),
                                    new SheetPerspectiveTransform.Point(detection.markerSizeMm > 8 ? 197.0 : 203.5,
                                            detection.markerSizeMm > 8 ? 13.0 : 6.5),
                                    new SheetPerspectiveTransform.Point(detection.markerSizeMm > 8 ? 197.0 : 203.5,
                                            detection.markerSizeMm > 8 ? 284.0 : 290.5),
                                    new SheetPerspectiveTransform.Point(detection.markerSizeMm > 8 ? 13.0 : 6.5,
                                            detection.markerSizeMm > 8 ? 284.0 : 290.5)
                            },
                            detection.points, OmrTemplateLayout.PAGE_WIDTH_MM,
                            OmrTemplateLayout.PAGE_HEIGHT_MM);
                } catch (IllegalArgumentException ignored) {
                    transform = null;
                }
            }
            if (transform != null && markerCount == 4) {
                stableMarkerFrames++;
                missingMarkerFrames = 0;
                if (!sheetConsumed && stableMarkerFrames >= 3) {
                    sheetConsumed = true;
                    processRecognizedSheet(upright.pixels, upright.width, upright.height, transform);
                }
            } else {
                stableMarkerFrames = 0;
                if (sheetConsumed && markerCount < 2) {
                    missingMarkerFrames++;
                    if (missingMarkerFrames >= 5 && !waitingForNextName) {
                        waitingForNextName = true;
                        showNextParticipantDialog();
                    }
                } else if (markerCount >= 2) {
                    missingMarkerFrames = 0;
                }
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastStatusUpdate > 350) {
                lastStatusUpdate = now;
                String message;
                if (transform != null) {
                    try {
                        // Diagnostic-only live sampling. No answers or scores are saved.
                        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(
                                OmrTemplateLayout.QUESTIONS_PER_PAGE, 0);
                        List<OMRSheetReader.QuestionResult> results =
                                new OMRSheetReader().read(upright.pixels, upright.width,
                                        upright.height, transform, centers, 1.0);
                        int answered = 0, blank = 0, review = 0, multiple = 0;
                        for (OMRSheetReader.QuestionResult result : results) {
                            switch (result.classification.status) {
                                case ANSWERED: answered++; break;
                                case BLANK: blank++; break;
                                case MULTIPLE_MARKS: multiple++; break;
                                case REVIEW: review++; break;
                            }
                        }
                        message = "خواندن آزمایشی حباب‌ها • پاسخ تشخیص‌داده‌شده: " + answered
                                + " • خالی: " + blank + " • بازبینی: " + review
                                + " • چندعلامتی: " + multiple + " — نمره ذخیره نمی‌شود.";
                    } catch (RuntimeException readError) {
                        message = "چهار گوشه پیدا شد، اما خواندن حباب‌ها پایدار نیست؛ برگه را صاف‌تر و با نور یکنواخت بگیرید.";
                    }
                } else if (markerCount >= 4) {
                    message = "نشانه‌های گوشه پیدا شدند، اما هندسهٔ برگه قابل اعتماد نیست؛ برگه را کامل و صاف داخل کادر بگیرید.";
                } else if (markerCount >= 2) {
                    message = "چند نشانهٔ مربعی پیدا شد؛ کل برگه را داخل کادر و با نور یکنواخت نگه دارید.";
                } else {
                    message = "در حال جست‌وجوی مربع‌های گوشه… چهار گوشهٔ برگه را داخل تصویر نگه دارید.";
                }
                updateStatus(message);
            }
        } catch (RuntimeException ignored) {
            updateStatus("کیفیت تصویر کافی نیست؛ فاصله و نور را تنظیم کنید.");
        } finally {
            image.close();
        }
    }

    private static final class UprightLuma {
        final byte[] pixels; final int width; final int height;
        UprightLuma(byte[] pixels, int width, int height) {
            this.pixels = pixels; this.width = width; this.height = height;
        }
    }

    private static final class MarkerDetection {
        final int count;
        // Ordered top-left, top-right, bottom-right, bottom-left.
        final SheetPerspectiveTransform.Point[] points;
        // Approximate physical marker size inferred from component size: old 3 mm
        // outlined marks remain supported alongside the new 16 mm solid marks.
        final double markerSizeMm;
        MarkerDetection(int count, SheetPerspectiveTransform.Point[] points, double markerSizeMm) {
            this.count = count; this.points = points; this.markerSizeMm = markerSizeMm;
        }
    }

    private UprightLuma rotateToUpright(byte[] source, int width, int height, int degrees) {
        int rotation = ((degrees % 360) + 360) % 360;
        if (rotation != 90 && rotation != 180 && rotation != 270) {
            return new UprightLuma(source, width, height);
        }
        int outWidth = (rotation == 180) ? width : height;
        int outHeight = (rotation == 180) ? height : width;
        byte[] output = new byte[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx, dy;
                if (rotation == 90) { dx = height - 1 - y; dy = x; }
                else if (rotation == 180) { dx = width - 1 - x; dy = height - 1 - y; }
                else { dx = y; dy = width - 1 - x; }
                output[dy * outWidth + dx] = source[y * width + x];
            }
        }
        return new UprightLuma(output, outWidth, outHeight);
    }

    /**
     * Looks for a compact, nearly-square dark component in each corner. Both filled marks and thin outlined squares are supported.
     * The returned points are candidate mark centers, not proof that the sheet
     * is a valid response form. Scoring stays disabled until layout calibration
     * and bubble sampling are validated against printed sheets.
     */
    private MarkerDetection detectDarkCornerMarkers(byte[] yPlane, int width, int height) {
        // Work on a half-resolution luma image. Accept both the legacy 3 mm
        // outlined marks and new 16 mm solid vector marks.
        final int sample = 2;
        final int gw = (width + sample - 1) / sample;
        final int gh = (height + sample - 1) / sample;
        final boolean[] visited = new boolean[gw * gh];
        final int[] queue = new int[gw * gh];
        final int cornerW = Math.max(12, gw / 4);
        final int cornerH = Math.max(12, gh / 4);
        final int[][] regions = {
                {0, 0, cornerW, cornerH},
                {gw - cornerW, 0, gw, cornerH},
                {gw - cornerW, gh - cornerH, gw, gh},
                {0, gh - cornerH, cornerW, gh}
        };
        SheetPerspectiveTransform.Point[] points = new SheetPerspectiveTransform.Point[4];
        double[] sizes = new double[4];
        int found = 0;
        int threshold = adaptiveDarkThreshold(yPlane, width, height);

        for (int corner = 0; corner < regions.length; corner++) {
            int[] region = regions[corner];
            int x0 = Math.max(0, region[0]), y0 = Math.max(0, region[1]);
            int x1 = Math.min(gw, region[2]), y1 = Math.min(gh, region[3]);
            double bestScore = -Double.MAX_VALUE;
            SheetPerspectiveTransform.Point bestPoint = null;
            double bestSize = 0;

            for (int sy = y0; sy < y1; sy++) {
                for (int sx = x0; sx < x1; sx++) {
                    int seed = sy * gw + sx;
                    if (visited[seed]) continue;
                    if (lumaAt(yPlane, width, height, sx, sy, sample) >= threshold) {
                        visited[seed] = true;
                        continue;
                    }
                    int head = 0, tail = 0;
                    queue[tail++] = seed;
                    visited[seed] = true;
                    int minX = sx, maxX = sx, minY = sy, maxY = sy, pixels = 0;
                    boolean tooLarge = false;
                    while (head < tail) {
                        int p = queue[head++];
                        int px = p % gw, py = p / gw;
                        pixels++;
                        minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                        minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                        if (pixels > 6000) { tooLarge = true; break; }
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = px + dx, ny = py + dy;
                                if (nx < x0 || nx >= x1 || ny < y0 || ny >= y1) continue;
                                int ni = ny * gw + nx;
                                if (visited[ni]) continue;
                                visited[ni] = true;
                                if (lumaAt(yPlane, width, height, nx, ny, sample) < threshold
                                        && tail < queue.length) queue[tail++] = ni;
                            }
                        }
                    }

                    int boxW = maxX - minX + 1, boxH = maxY - minY + 1;
                    double aspect = boxW / (double) Math.max(1, boxH);
                    double fill = pixels / (double) Math.max(1, boxW * boxH);
                    int size = Math.max(boxW, boxH);
                    // Marker geometry is scale tolerant: tiny outlined legacy marks
                    // and larger filled marks both pass, while thin text strokes do not.
                    boolean compactSquare = !tooLarge && pixels >= 8
                            && boxW >= 4 && boxH >= 4 && size <= Math.min(90, Math.max(20, gw / 5))
                            && aspect >= 0.68 && aspect <= 1.47
                            && fill >= 0.12;
                    if (!compactSquare) continue;

                    double estimatedMm = size * 210.0 / Math.max(1.0, gw);
                    boolean legacySize = estimatedMm >= 1.2 && estimatedMm <= 7.5;
                    boolean newSize = estimatedMm >= 7.5 && estimatedMm <= 25.0;
                    if (!legacySize && !newSize) continue;

                    // Prefer near-square, compact components and plausible marker size.
                    double sizePenalty = Math.abs(Math.log(Math.max(0.1, estimatedMm)
                            / (newSize ? 16.0 : 3.0)));
                    double score = 2.0 - Math.abs(1.0 - aspect) * 1.4
                            + Math.min(1.0, fill) * 0.5 - sizePenalty * 0.25;
                    if (score > bestScore) {
                        bestScore = score;
                        bestSize = estimatedMm;
                        bestPoint = new SheetPerspectiveTransform.Point(
                                (minX + maxX) * 0.5 * sample,
                                (minY + maxY) * 0.5 * sample);
                    }
                }
            }
            if (bestPoint != null) {
                points[corner] = bestPoint;
                sizes[corner] = bestSize;
                found++;
            }
        }

        double markerSizeMm = 0;
        if (found > 0) {
            double sum = 0;
            for (double size : sizes) if (size > 0) sum += size;
            markerSizeMm = sum / found;
        }
        return new MarkerDetection(found, points, markerSizeMm);
    }

    /** Estimate a local black/white split from sparse luma samples. */
    private int adaptiveDarkThreshold(byte[] yPlane, int width, int height) {
        long sum = 0;
        int count = 0;
        int stepX = Math.max(1, width / 48);
        int stepY = Math.max(1, height / 48);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                sum += yPlane[y * width + x] & 0xff;
                count++;
            }
        }
        int mean = count == 0 ? 128 : (int) (sum / count);
        // Keep the threshold permissive for shadows, but never so high that
        // ordinary mid-gray paper texture becomes a black registration mark.
        return Math.max(65, Math.min(145, mean - 35));
    }

    private int lumaAt(byte[] yPlane, int width, int height, int sampleX, int sampleY, int sample) {
        int x = Math.min(width - 1, sampleX * sample);
        int y = Math.min(height - 1, sampleY * sample);
        return yPlane[y * width + x] & 0xff;
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (!finished && statusView != null) statusView.setText(message);
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                updateStatus("برای اسکن پاسخنامه، اجازهٔ دسترسی به دوربین لازم است.");
            }
        }
    }

    @Override protected void onDestroy() {
        finished = true;
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        super.onDestroy();
    }
}