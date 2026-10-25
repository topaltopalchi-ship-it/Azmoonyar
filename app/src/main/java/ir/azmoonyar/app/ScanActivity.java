package ir.azmoonyar.app;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.BarcodeFormat;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continuous offline QR scanner. Frames are analyzed in memory and are never saved.
 * Accepted QR payload format: AZY1|exact exam name|unique sheet ID.
 * This activity registers sheet IDs only; OMR bubble reading/grading is not implemented yet.
 */
public class ScanActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 4102;
    private static final int STABLE_FRAMES_REQUIRED = 3;

    private PreviewView previewView;
    private TextView statusView;
    private TextView countView;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private SharedPreferences prefs;
    private String examName;
    private String candidateValue = "";
    private int candidateFrames = 0;
    private String lastStatus = "";
    private long lastStatusAt = 0L;
    private long lastAnalysisAt = 0L;
    private volatile boolean scannerActive = true;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
        examName = prefs.getString("exam_name", "").trim();
        cameraExecutor = Executors.newSingleThreadExecutor();
        getWindow().setStatusBarColor(Color.rgb(7, 27, 114));
        getWindow().setNavigationBarColor(Color.rgb(7, 27, 114));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buildUi();

        if (examName.isEmpty()) {
            showStatus("ابتدا در صفحهٔ اصلی آزمون را تعریف کن.");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(8, 18, 42));

        TextView title = new TextView(this);
        title.setText("اسکن زنده پاسخنامه");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(12), dp(12), dp(12), dp(12));
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout cameraFrame = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraFrame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        TextView guide = new TextView(this);
        guide.setText("کد QR پاسخنامه را داخل کادر نگه دار\nعکس گرفته یا ذخیره نمی‌شود");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(15);
        guide.setGravity(Gravity.CENTER);
        guide.setPadding(dp(10), dp(10), dp(10), dp(10));
        guide.setBackgroundColor(0x99071972);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        guideParams.setMargins(dp(18), dp(22), dp(18), 0);
        cameraFrame.addView(guide, guideParams);
        page.addView(cameraFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setText("در حال آماده‌سازی دوربین…");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(8));
        page.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        countView = new TextView(this);
        countView.setTextColor(Color.rgb(180, 225, 255));
        countView.setTextSize(14);
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(dp(8), 0, dp(8), dp(10));
        page.addView(countView, new LinearLayout.LayoutParams(-1, -2));
        refreshCount();

        Button close = new Button(this);
        close.setText("پایان اسکن");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.setMargins(dp(16), dp(4), dp(16), dp(14));
        page.addView(close, buttonParams);

        setContentView(page);
    }

    private void startCamera() {
        showStatus("دوربین آماده می‌شود…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                showStatus("راه‌اندازی دوربین ناموفق بود. مجوز و دسترسی دوربین را بررسی کن.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || isFinishing()) return;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(960, 540))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            showStatus("آمادهٔ اسکن پیوسته؛ کد QR را جلوی دوربین بگیر.");
        } catch (Exception e) {
            showStatus("اتصال دوربین ناموفق بود. برنامه را ببند و دوباره امتحان کن.");
        }
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        try {
            if (!scannerActive || image.getFormat() != android.graphics.ImageFormat.YUV_420_888) {
                return;
            }
            long frameNow = System.currentTimeMillis();
            if (frameNow - lastAnalysisAt < 160L) return;
            lastAnalysisAt = frameNow;
            ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
            ByteBuffer buffer = yPlane.getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowStride = yPlane.getRowStride();
            int pixelStride = yPlane.getPixelStride();
            byte[] luminance = new byte[width * height];
            for (int row = 0; row < height; row++) {
                int sourceRow = row * rowStride;
                int targetRow = row * width;
                for (int col = 0; col < width; col++) {
                    luminance[targetRow + col] = buffer.get(sourceRow + col * pixelStride);
                }
            }

            LuminanceSource source = new PlanarYUVLuminanceSource(
                    luminance, width, height, 0, 0, width, height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.FALSE);

            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);
            Result result;
            try {
                result = reader.decodeWithState(bitmap);
            } catch (NotFoundException notFound) {
                candidateValue = "";
                candidateFrames = 0;
                return;
            } finally {
                reader.reset();
            }

            String value = result.getText() == null ? "" : result.getText().trim();
            if (value.equals(candidateValue)) {
                candidateFrames++;
            } else {
                candidateValue = value;
                candidateFrames = 1;
            }
            if (candidateFrames >= STABLE_FRAMES_REQUIRED) {
                candidateFrames = 0;
                candidateValue = "";
                runOnUiThread(() -> registerIfValid(value));
            }
        } catch (Exception ignored) {
            // A single blurry or partially visible frame must not stop continuous scanning.
        } finally {
            image.close();
        }
    }

    private void registerIfValid(String payload) {
        if (isFinishing() || !scannerActive) return;
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3 || !"AZY1".equals(parts[0])) {
            showStatus("QR دیده شد، اما قالب آزمون‌یار نیست.");
            return;
        }
        if (!examName.equals(parts[1].trim())) {
            showStatus("این پاسخنامه برای آزمون فعلی نیست.");
            return;
        }
        String sheetId = parts[2].trim();
        if (sheetId.isEmpty() || sheetId.length() > 100) {
            showStatus("شناسهٔ پاسخنامه معتبر نیست.");
            return;
        }

        String uniqueKey = examName + "::" + sheetId;
        Set<String> saved = new HashSet<>(prefs.getStringSet("scanned_sheet_ids", Collections.emptySet()));
        if (saved.contains(uniqueKey)) {
            showStatus("این پاسخنامه قبلاً ثبت شده؛ مورد تکراری نادیده گرفته شد.");
            refreshCount();
            return;
        }
        saved.add(uniqueKey);
        prefs.edit()
                .putStringSet("scanned_sheet_ids", saved)
                .putInt("scanned_sheet_count", saved.size())
                .apply();
        showStatus("شناسهٔ پاسخنامه ثبت شد: " + sheetId + " — حباب‌ها هنوز خوانده نشده‌اند.");
        refreshCount();
    }

    private void refreshCount() {
        if (countView == null || prefs == null) return;
        Set<String> ids = prefs.getStringSet("scanned_sheet_ids", Collections.emptySet());
        countView.setText("آزمون: " + examName + "  |  شناسه‌های یکتای ثبت‌شده: " + ids.size());
    }

    private void showStatus(String message) {
        if (statusView == null) return;
        long now = System.currentTimeMillis();
        if (message.equals(lastStatus) && now - lastStatusAt < 1500L) return;
        lastStatus = message;
        lastStatusAt = now;
        runOnUiThread(() -> {
            if (statusView != null) statusView.setText(message);
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showStatus("برای اسکن زنده باید مجوز دوربین را فعال کنی.");
            }
        }
    }

    @Override protected void onDestroy() {
        scannerActive = false;
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }
}
