package ir.azmoonyar.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.Intent;
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
import android.util.Base64;

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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continuous offline QR scanner. Frames are analyzed in memory and are never saved.
 * Accepted QR payloads: legacy AZY1/AZY2 and AZY3|Base64 exam|sheet-set ID|model|template|page|page count.
 * This activity registers sheet IDs only; OMR bubble reading/grading is not implemented yet.
 */
public class ScanActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 4102;
    private static final int STABLE_FRAMES_REQUIRED = 3;
    private static final int VOICE_NAME_REQUEST = 4103;
    private PersianVosk voiceRecognizer;

    private PreviewView previewView;
    private TextView statusView;
    private TextView countView;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private SharedPreferences prefs;
    private String examName;
    private String candidateValue = "";
    private int candidateFrames = 0;
    // Prevent repeated events while the same QR remains in the camera frame.
    private String lockedPayload = "";
    private boolean waitingForQrToLeave = false;
    private String latestSheetSetId = "";\n    private String pendingStudentName = "";
    private String latestModelId = "";
    private String latestTemplateId = "";
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
        guide.setText("نام شرکت‌کننده را با دکمهٔ میکروفون ثبت کن\nاین نسخه هنوز حباب‌های پاسخنامه را از تصویر نمی‌خواند");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(15);
        guide.setGravity(Gravity.CENTER);
        guide.setPadding(dp(10), dp(10), dp(10), dp(10));
        android.graphics.drawable.GradientDrawable guideBg = new android.graphics.drawable.GradientDrawable();
        guideBg.setColor(0xCC173A91);
        guideBg.setCornerRadius(dp(18));
        guideBg.setStroke(dp(1), 0x887DB7FF);
        guide.setBackground(guideBg);
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
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(Color.rgb(20, 35, 72));
        statusBg.setCornerRadius(dp(16));
        statusBg.setStroke(dp(1), Color.rgb(53, 85, 145));
        statusView.setBackground(statusBg);
        page.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        countView = new TextView(this);
        countView.setTextColor(Color.rgb(180, 225, 255));
        countView.setTextSize(14);
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(dp(8), 0, dp(8), dp(10));
        page.addView(countView, new LinearLayout.LayoutParams(-1, -2));
        refreshCount();

        Button voiceName = new Button(this);
        voiceName.setText("🎙 ثبت صوتی نام شرکت‌کننده");
        voiceName.setAllCaps(false);
        voiceName.setOnClickListener(v -> startVoiceNameCapture());
        page.addView(voiceName, new LinearLayout.LayoutParams(-1, dp(52)));

        Button close = new Button(this);
        close.setText("پایان اسکن");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setTextSize(16);
        close.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable closeBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(37, 99, 235), Color.rgb(124, 58, 237)});
        closeBg.setCornerRadius(dp(16));
        close.setBackground(closeBg);
        close.setElevation(dp(3));
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
            showStatus("QR لازم نیست؛ ثبت نام و پاسخ‌ها را از دکمه‌های پایین انجام بده.");
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
                waitingForQrToLeave = false;
                lockedPayload = "";
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
                // A shared questionnaire/answer-sheet QR is valid on every separate scan.
                // Debounce only the same QR while it continuously remains in view.
                if (!waitingForQrToLeave || !value.equals(lockedPayload)) {
                    waitingForQrToLeave = true;
                    lockedPayload = value;
                    runOnUiThread(() -> registerIfValid(value));
                }
            }
        } catch (Exception ignored) {
            // A single blurry or partially visible frame must not stop continuous scanning.
        } finally {
            image.close();
        }
    }

    private void registerIfValid(String payload) {
        if (isFinishing() || !scannerActive) return;
        String[] parts = payload.split("\\|", -1);
        if (parts.length == 7 && "AZY3".equals(parts[0])) {
            registerPageQr(parts);
            return;
        }
        if (parts.length != 3 || !("AZY1".equals(parts[0]) || "AZY2".equals(parts[0]))) {
            showStatus("QR دیده شد، اما قالب آزمون‌یار نیست.");
            return;
        }
        String decodedExamName;
        try {
            decodedExamName = "AZY2".equals(parts[0])
                    ? new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP),
                            java.nio.charset.StandardCharsets.UTF_8).trim()
                    : parts[1].trim();
        } catch (IllegalArgumentException badPayload) {
            showStatus("نام آزمون در QR معتبر نیست.");
            return;
        }
        if (!examName.equals(decodedExamName)) {
            showStatus("این پاسخنامه برای آزمون فعلی نیست.");
            return;
        }
        String sheetId = parts[2].trim();
        if (sheetId.isEmpty() || sheetId.length() > 100) {
            showStatus("شناسهٔ پاسخنامه معتبر نیست.");
            return;
        }
        latestSheetSetId = sheetId;
        latestModelId = "";
        latestTemplateId = "";
        registerUniqueId(examName + "::" + sheetId, "QR شناسایی شد؛ اکنون نام شرکت‌کننده را صوتی ثبت کن.");
    }

    private void registerPageQr(String[] parts) {
        String decodedExamName;
        try {
            decodedExamName = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException badPayload) {
            showStatus("نام آزمون در QR معتبر نیست.");
            return;
        }
        // QR identifies a printed variant by batch + model, not by student ID.
        // Do not reject it solely because the currently selected exam title differs.
        // Match it against the immutable variant snapshot saved when the forms were printed.
        String sheetSetId = parts[2].trim();
        String modelId = parts[3].trim();
        String templateId = parts[4].trim();
        int page;
        int pageCount;
        try {
            page = Integer.parseInt(parts[5]);
            pageCount = Integer.parseInt(parts[6]);
        } catch (NumberFormatException badPage) {
            showStatus("شماره صفحه در QR معتبر نیست.");
            return;
        }
        if (sheetSetId.isEmpty() || sheetSetId.length() > 100
                || !modelId.matches("[A-D]") || templateId.isEmpty()
                || pageCount < 1 || pageCount > 10 || page < 1 || page > pageCount) {
            showStatus("اطلاعات صفحه در QR معتبر نیست.");
            return;
        }
        String issuedKey = "issued_exam_" + sheetSetId + "_" + modelId;
        String savedVariant = prefs.getString(issuedKey, "");
        // Some imported/older batches only retain their immutable variant snapshot inside
        // saved_exam_batches_json. Recover it as the scanner's reference before rejecting QR.
        if (savedVariant.isEmpty()) {
            try {
                JSONArray batches = new JSONArray(prefs.getString("saved_exam_batches_json", "[]"));
                for (int b = 0; b < batches.length() && savedVariant.isEmpty(); b++) {
                    JSONObject batch = batches.optJSONObject(b);
                    JSONArray variants = batch == null ? null : batch.optJSONArray("variants");
                    if (variants == null) continue;
                    for (int v = 0; v < variants.length(); v++) {
                        JSONObject candidate = variants.optJSONObject(v);
                        if (candidate != null
                                && sheetSetId.equals(candidate.optString("sheet_set_id", ""))
                                && modelId.equals(candidate.optString("model_id", ""))) {
                            savedVariant = candidate.toString();
                            prefs.edit().putString(issuedKey, savedVariant).apply();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        if (savedVariant.isEmpty()) {
            showStatus("QR خوانده شد، اما نسخهٔ متناظر در مجموعه‌های ذخیره‌شده پیدا نشد. PDF باید با QR همین آزمون‌یار ثبت شده باشد.");
            return;
        }
        try {
            JSONObject variant = new JSONObject(savedVariant);
            if (!sheetSetId.equals(variant.optString("sheet_set_id", ""))
                    || !modelId.equals(variant.optString("model_id", ""))
                    || !templateId.equals(variant.optString("template_id", "T1"))) {
                showStatus("اطلاعات QR با نسخهٔ ذخیره‌شده مطابقت ندارد.");
                return;
            }
        } catch (Exception invalidSnapshot) {
            showStatus("اطلاعات نسخهٔ ذخیره‌شده قابل خواندن نیست؛ نسخه‌ها را دوباره چاپ کن.");
            return;
        }
        latestSheetSetId = sheetSetId;
        latestModelId = modelId;
        latestTemplateId = templateId;
        // Current print flow creates a new sheetSetId for each printed model batch.
        // This deduplicates repeated scans of the same page within that batch; it does not
        // identify individual students because the printed QR does not yet contain a student ID.
        Set<String> events = new HashSet<>(prefs.getStringSet("scanned_qr_events", Collections.emptySet()));
        // The same QR is printed on the question/answer pair for one page. Use a stable
        // page key so rescanning that same printed page does not inflate the scan count.
        // Each newly printed batch receives its own sheetSetId.
        String eventId = sheetSetId + "::" + modelId + "::" + templateId + "::" + page;
        if (events.contains(eventId)) {
            showStatus("این صفحه قبلاً ثبت شده است؛ اسکن تکراری شمرده نشد.");
            refreshCount();
            return;
        }
        events.add(eventId);
        prefs.edit()
                .putStringSet("scanned_qr_events", events)
                .putInt("scanned_sheet_count", events.size())
                .apply();
        showStatus("نسخهٔ " + modelId + " شناسایی شد (صفحه " + page + " از " + pageCount
                + "). شناسه صفحه ثبت شد؛ خواندن حباب‌ها و تصحیح خودکار هنوز فعال نیست.");
        refreshCount();
    }

    private void startVoiceNameCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_NAME_REQUEST);
            showStatus("درخواست اجازهٔ میکروفون ارسال شد…");
            return;
        }
        if (voiceRecognizer != null) voiceRecognizer.stop();
        voiceRecognizer = new PersianVosk();
        voiceRecognizer.start(this, new PersianVosk.Callback() {
            @Override public void onText(String name) {
                saveVoiceName(name);
                if (voiceRecognizer != null) voiceRecognizer.stop();
            }
            @Override public void onStatus(String status) { showStatus(status); }
        });
    }

    private void saveVoiceName(String name) {
        if (name == null || name.trim().isEmpty()) { showStatus("نام دریافت نشد؛ دوباره تلاش کن."); return; }
        try {
            JSONArray rows;
            try { rows = new JSONArray(prefs.getString("scanned_named_answer_sheets", "[]")); }
            catch (Exception e) { rows = new JSONArray(); }
            JSONObject row = new JSONObject();
            row.put("name", name.trim()); row.put("examName", examName);
            row.put("sheetSetId", latestSheetSetId); row.put("modelId", latestModelId);
            row.put("templateId", latestTemplateId); row.put("createdAt", System.currentTimeMillis());
            rows.put(row);
            prefs.edit().putString("scanned_named_answer_sheets", rows.toString()).apply();
            showStatus("نام «" + name.trim() + "» به نسخهٔ " + (latestModelId.isEmpty() ? "قدیمی" : latestModelId) + " متصل شد.");
        } catch (Exception e) { showStatus("ذخیرهٔ نام انجام نشد."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void registerUniqueId(String uniqueKey, String successMessage) {
        Set<String> saved = new HashSet<>(prefs.getStringSet("scanned_sheet_ids", Collections.emptySet()));
        if (saved.contains(uniqueKey)) {
            showStatus("این پاسخنامه/صفحه قبلاً ثبت شده؛ مورد تکراری نادیده گرفته شد.");
            refreshCount();
            return;
        }
        saved.add(uniqueKey);
        prefs.edit()
                .putStringSet("scanned_sheet_ids", saved)
                .putInt("scanned_sheet_count", saved.size())
                .apply();
        showStatus(successMessage);
        refreshCount();
    }

    private void refreshCount() {
        if (countView == null || prefs == null) return;
        Set<String> ids = prefs.getStringSet("scanned_qr_events", Collections.emptySet());
        countView.setText("آزمون: " + examName + "  |  رویدادهای اسکن QR: " + ids.size());
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
        if (requestCode == VOICE_NAME_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceNameCapture();
            } else {
                showStatus("برای ثبت صوتی نام شرکت‌کننده، مجوز میکروفون لازم است.");
            }
            return;
        }
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
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
        }
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }
}
