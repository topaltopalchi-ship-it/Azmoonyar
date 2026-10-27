package ir.azmoonyar.app;

import android.Manifest;
import android.content.pm.PackageManager;
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
        guidance.setText("برگه را تا حد امکان کامل داخل تصویر بگیرید؛ مربع‌های گوشه باید واضح باشند. تشخیص فعلی راهنمای اولیه است و هنوز نمره‌ای ذخیره نمی‌شود.");
        guidance.setTextColor(Color.rgb(80, 90, 110));
        guidance.setTextSize(13);
        guidance.setGravity(Gravity.CENTER);
        guidance.setPadding(18, 4, 18, 14);
        page.addView(guidance, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);

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
            boolean perspectiveReady = false;
            if (markerCount == 4) {
                try {
                    // Registration mark centers from the existing paired-sheet template:
                    // 5 mm square marks with their centers 6.5 mm from each page edge.
                    SheetPerspectiveTransform.fromControlPoints(
                            new SheetPerspectiveTransform.Point[]{
                                    new SheetPerspectiveTransform.Point(6.5, 6.5),
                                    new SheetPerspectiveTransform.Point(203.5, 6.5),
                                    new SheetPerspectiveTransform.Point(203.5, 290.5),
                                    new SheetPerspectiveTransform.Point(6.5, 290.5)
                            },
                            detection.points, 210.0, 297.0);
                    perspectiveReady = true;
                } catch (IllegalArgumentException ignored) {
                    perspectiveReady = false;
                }
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastStatusUpdate > 220) {
                lastStatusUpdate = now;
                final String message;
                if (perspectiveReady) {
                    message = "چهار نشانه پیدا شد و هندسهٔ پرسپکتیو برگه قابل محاسبه است؛ تشخیص پاسخ هنوز فعال نیست."; 
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
        MarkerDetection(int count, SheetPerspectiveTransform.Point[] points) {
            this.count = count; this.points = points;
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
     * Looks for a compact, filled, nearly-square dark component in each corner.
     * The returned points are candidate mark centers, not proof that the sheet
     * is a valid response form. Scoring stays disabled until layout calibration
     * and bubble sampling are validated against printed sheets.
     */
    private MarkerDetection detectDarkCornerMarkers(byte[] yPlane, int width, int height) {
        final int sample = 2;
        final int gw = (width + sample - 1) / sample;
        final int gh = (height + sample - 1) / sample;
        final boolean[] visited = new boolean[gw * gh];
        final int[] queue = new int[gw * gh];
        final int cornerW = Math.max(8, gw / 4);
        final int cornerH = Math.max(8, gh / 4);
        // Ordered TL, TR, BR, BL to match the projective transform.
        final int[][] regions = {
                {0, 0, cornerW, cornerH},
                {gw - cornerW, 0, gw, cornerH},
                {gw - cornerW, gh - cornerH, gw, gh},
                {0, gh - cornerH, cornerW, gh}
        };
        SheetPerspectiveTransform.Point[] points = new SheetPerspectiveTransform.Point[4];
        int foundCorners = 0;

        for (int corner = 0; corner < regions.length; corner++) {
            int[] region = regions[corner];
            int x0 = Math.max(0, region[0]), y0 = Math.max(0, region[1]);
            int x1 = Math.min(gw, region[2]), y1 = Math.min(gh, region[3]);
            double bestArea = -1;
            SheetPerspectiveTransform.Point bestPoint = null;

            for (int sy = y0; sy < y1; sy++) {
                for (int sx = x0; sx < x1; sx++) {
                    int seed = sy * gw + sx;
                    if (visited[seed]) continue;
                    if (lumaAt(yPlane, width, height, sx, sy, sample) >= 120) {
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
                        if (pixels > 1800) {
                            tooLarge = true;
                            break;
                        }
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = px + dx, ny = py + dy;
                                if (nx < x0 || nx >= x1 || ny < y0 || ny >= y1) continue;
                                int ni = ny * gw + nx;
                                if (visited[ni]) continue;
                                visited[ni] = true;
                                if (lumaAt(yPlane, width, height, nx, ny, sample) < 120
                                        && tail < queue.length) queue[tail++] = ni;
                            }
                        }
                    }
                    int boxW = maxX - minX + 1, boxH = maxY - minY + 1;
                    double aspect = boxW / (double) Math.max(1, boxH);
                    double fill = pixels / (double) (boxW * boxH);
                    boolean square = !tooLarge && pixels >= 10 && boxW >= 4 && boxH >= 4
                            && boxW <= 20 && boxH <= 20
                            && aspect >= 0.68 && aspect <= 1.47 && fill >= 0.42;
                    double area = boxW * (double) boxH;
                    if (square && area > bestArea) {
                        bestArea = area;
                        bestPoint = new SheetPerspectiveTransform.Point(
                                (minX + maxX) * 0.5 * sample,
                                (minY + maxY) * 0.5 * sample);
                    }
                }
            }
            if (bestPoint != null) {
                points[corner] = bestPoint;
                foundCorners++;
            }
        }
        return new MarkerDetection(foundCorners, points);
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