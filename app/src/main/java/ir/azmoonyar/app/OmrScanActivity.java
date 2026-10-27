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
 * Isolated live-camera stage for answer-sheet alignment.
 * It deliberately does not save grades: reliable sheet rectification and bubble
 * coordinate calibration must be completed before automatic scoring is enabled.
 */
public final class OmrScanActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 8402;
    private PreviewView previewView;
    private TextView statusView;
    private ExecutorService cameraExecutor;
    private volatile long lastStatusUpdate;
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
        guidance.setText("برگه را کامل داخل کادر دوربین بگیرید و از نور یکنواخت استفاده کنید. این مرحله فقط هم‌ترازی اولیه را بررسی می‌کند؛ هنوز نمره‌ای ذخیره نمی‌شود.");
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
                        .setTargetResolution(new android.util.Size(640, 480))
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                // ComponentActivity implements LifecycleOwner, as required by CameraX.
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
            int markerCount = countDarkCornerRegions(luma, width, height);
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastStatusUpdate > 350) {
                lastStatusUpdate = now;
                final String message = markerCount >= 4
                        ? "چهار ناحیهٔ تیره در گوشه‌ها دیده شد؛ برگه را ثابت نگه دارید."
                        : "در حال یافتن نشانه‌های گوشهٔ برگه…";
                updateStatus(message);
            }
        } catch (RuntimeException ignored) {
            updateStatus("کیفیت تصویر کافی نیست؛ فاصله و نور را تنظیم کنید.");
        } finally {
            image.close();
        }
    }

    /**
     * Coarse cue only: counts dark pixels in four image-corner windows, not
     * validated printed square markers. It must never be used for grading.
     */
    private int countDarkCornerRegions(byte[] yPlane, int width, int height) {
        int windowW = Math.max(12, width / 7);
        int windowH = Math.max(12, height / 7);
        int[][] boxes = {
                {0, 0, windowW, windowH},
                {width - windowW, 0, width, windowH},
                {0, height - windowH, windowW, height},
                {width - windowW, height - windowH, width, height}
        };
        int found = 0;
        for (int[] box : boxes) {
            int dark = 0, total = 0;
            for (int y = box[1]; y < box[3]; y += 2) {
                for (int x = box[0]; x < box[2]; x += 2) {
                    total++;
                    if ((yPlane[y * width + x] & 0xff) < 70) dark++;
                }
            }
            if (total > 0 && dark / (double) total > 0.12) found++;
        }
        return found;
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
