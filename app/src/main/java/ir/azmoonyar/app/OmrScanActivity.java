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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
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
    private static final int AUDIO_PERMISSION_REQUEST = 8403;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private TextView statusView;
    private TextView lightGuideView;
    private SheetGuideOverlay sheetGuideOverlay;
    private ExecutorService cameraExecutor;
    private volatile long lastStatusUpdate;
    private volatile long lastFrameAnalysis;
    private volatile boolean finished;
    private String participantName = "";
    private volatile int stableMarkerFrames;
    private volatile int stableLightFrames;
    private volatile double currentLightSpread = Double.POSITIVE_INFINITY;
    private static final double READY_LIGHT_SPREAD = 14.0;
    private int missingMarkerFrames;
    private static final int REQUIRED_STABLE_MARKER_FRAMES = 6;
    private volatile boolean sheetConsumed, waitingForNextName;
    private volatile String lastSheetResultMessage = "";
    private PersianVosk voiceRecognizer;
    private volatile boolean voiceCommandListening;
    private volatile boolean voiceCaptureRequested;
    private volatile byte[] diagnosticLuma;
    private volatile int diagnosticWidth, diagnosticHeight;
    private volatile SheetPerspectiveTransform diagnosticTransform;
    private volatile SheetPerspectiveTransform.Point[] diagnosticMarkerPoints;
    private volatile double diagnosticMarkerSizeMm;
    private volatile double[][][] diagnosticCenters;
    private volatile long lastDiagnosticFrameSaved;

    /** Draws an A4 portrait placement guide over the live camera preview. */
    private static final class SheetGuideOverlay extends View {
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SheetGuideOverlay(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            framePaint.setColor(Color.rgb(255, 170, 40));
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(4f * getResources().getDisplayMetrics().density);
            framePaint.setStrokeCap(Paint.Cap.ROUND);
            framePaint.setStrokeJoin(Paint.Join.ROUND);
            shadePaint.setColor(Color.argb(210, 255, 170, 40));
            shadePaint.setStyle(Paint.Style.STROKE);
            shadePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        }

        void setReady(boolean ready) {
            int color = ready ? Color.rgb(40, 220, 105) : Color.rgb(255, 170, 40);
            framePaint.setColor(color);
            shadePaint.setColor(Color.argb(210,
                    Color.red(color), Color.green(color), Color.blue(color)));
            postInvalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float viewW = getWidth(), viewH = getHeight();
            if (viewW <= 0 || viewH <= 0) return;

            // A4 ratio: width / height = 210 / 297. Leave room around all edges.
            float maxW = viewW * 0.88f;
            float maxH = viewH * 0.88f;
            float rectW = Math.min(maxW, maxH * (210f / 297f));
            float rectH = rectW * (297f / 210f);
            float left = (viewW - rectW) * 0.5f;
            float top = (viewH - rectH) * 0.5f;
            float right = left + rectW, bottom = top + rectH;

            framePaint.setStrokeWidth(2.5f * density);
            canvas.drawRect(left, top, right, bottom, framePaint);

            // Strong corner brackets make the intended sheet edges easy to align.
            float arm = Math.min(rectW, rectH) * 0.075f;
            framePaint.setStrokeWidth(5f * density);
            canvas.drawLine(left, top, left + arm, top, framePaint);
            canvas.drawLine(left, top, left, top + arm, framePaint);
            canvas.drawLine(right, top, right - arm, top, framePaint);
            canvas.drawLine(right, top, right, top + arm, framePaint);
            canvas.drawLine(left, bottom, left + arm, bottom, framePaint);
            canvas.drawLine(left, bottom, left, bottom - arm, framePaint);
            canvas.drawLine(right, bottom, right - arm, bottom, framePaint);
            canvas.drawLine(right, bottom, right, bottom - arm, framePaint);

            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(Color.WHITE);
            label.setTextSize(14f * density);
            label.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            label.setShadowLayer(3f * density, 0f, 1f * density, Color.BLACK);
            canvas.drawText("A4", left + 8f * density, top + 22f * density, label);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 27, 114));
        getWindow().setNavigationBarColor(Color.rgb(7, 27, 114));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(245, 248, 255));
        // Keep the last instruction above Android's bottom navigation area.
        int safeBottom = Math.round(28 * getResources().getDisplayMetrics().density);
        page.setPadding(0, 0, 0, safeBottom);

        TextView title = new TextView(this);
        title.setText("اسکن پاسخنامه");
        title.setTextColor(Color.rgb(7, 40, 130));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(12, 16, 12, 12);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));

        // Camera preview with a fixed A4 portrait guide overlaid on top.
        FrameLayout cameraFrame = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        cameraFrame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        sheetGuideOverlay = new SheetGuideOverlay(this);
        cameraFrame.addView(sheetGuideOverlay, new FrameLayout.LayoutParams(-1, -1));
        page.addView(cameraFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setText("دوربین برای یافتن چهار مربع گوشه آماده می‌شود…");
        statusView.setTextColor(Color.rgb(30, 41, 59));
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(16, 12, 16, 12);
        page.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        lightGuideView = new TextView(this);
        lightGuideView.setText("راهنمای یکنواختی نور: ابتدا برگه را کامل داخل کادر قرار دهید.");
        lightGuideView.setTextColor(Color.rgb(71, 85, 105));
        lightGuideView.setTextSize(14);
        lightGuideView.setGravity(Gravity.CENTER);
        lightGuideView.setPadding(14, 10, 14, 10);
        lightGuideView.setBackgroundColor(Color.rgb(226, 232, 240));
        page.addView(lightGuideView, new LinearLayout.LayoutParams(-1, -2));

        Button lightingButton = new Button(this);
        lightingButton.setTextSize(15);
        lightingButton.setAllCaps(false);
        updateLightingButtonLabel(lightingButton);
        lightingButton.setOnClickListener(v -> {
            final String[] modes = {"خودکار", "روز / نور زیاد", "شب / نور کم"};
            SharedPreferences prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
            String current = prefs.getString("omr_lighting_mode", "auto");
            int selected = "day".equals(current) ? 1 : ("night".equals(current) ? 2 : 0);
            new AlertDialog.Builder(this)
                    .setTitle("تنظیم نور برای تشخیص مربع‌ها")
                    .setSingleChoiceItems(modes, selected, (dialog, which) -> {
                        String mode = which == 1 ? "day" : (which == 2 ? "night" : "auto");
                        prefs.edit().putString("omr_lighting_mode", mode).apply();
                        updateLightingButtonLabel(lightingButton);
                        stableMarkerFrames = 0;
                        missingMarkerFrames = 0;
                        updateStatus(which == 1
                                ? "حالت روز فعال شد؛ پاسخنامه را ثابت نگه دارید تا مربع‌ها دوباره شناسایی شوند."
                                : which == 2
                                ? "حالت شب فعال شد؛ پاسخنامه را ثابت نگه دارید تا مربع‌ها دوباره شناسایی شوند."
                                : "تنظیم نور خودکار فعال شد؛ پاسخنامه را ثابت نگه دارید تا مربع‌ها دوباره شناسایی شوند.");
                        dialog.dismiss();
                    })
                    .setNegativeButton("بستن", null)
                    .show();
        });
        page.addView(lightingButton, new LinearLayout.LayoutParams(-1, -2));

        Button captureButton = new Button(this);
        captureButton.setText("عکس بگیر و تصحیح کن");
        captureButton.setTextSize(18);
        captureButton.setAllCaps(false);
        captureButton.setOnClickListener(v -> {
            if (sheetConsumed) {
                updateStatus("در حال پردازش همین برگه هستیم؛ لطفاً صبر کنید.");
                return;
            }
            if (imageCapture == null) {
                updateStatus("دوربین هنوز آماده نیست؛ چند لحظه صبر کنید.");
                return;
            }
            if (stableMarkerFrames < REQUIRED_STABLE_MARKER_FRAMES || stableLightFrames < REQUIRED_STABLE_MARKER_FRAMES) {
                updateStatus("چهار مربع گوشه هنوز پایدار نیستند؛ گوشی و برگه را ثابت نگه دارید تا پیام آمادگی ظاهر شود.");
                return;
            }
            sheetConsumed = true;
            captureRealPhotoAndCorrect();
        });
        page.addView(captureButton, new LinearLayout.LayoutParams(-1, -2));

        // Expose the coordinate diagnostic that was previously implemented but
        // had no UI entry point, so operators can verify bubble alignment on a live sheet.
        Button coordinateButton = new Button(this);
        coordinateButton.setText("نمایش حلقه‌های مختصات");
        coordinateButton.setTextSize(15);
        coordinateButton.setAllCaps(false);
        coordinateButton.setOnClickListener(v -> {
            byte[] luma = diagnosticLuma;
            int width = diagnosticWidth;
            int height = diagnosticHeight;
            SheetPerspectiveTransform transform = diagnosticTransform;
            SheetPerspectiveTransform.Point[] markers = diagnosticMarkerPoints;
            double markerSize = diagnosticMarkerSizeMm;
            double[][][] centers = diagnosticCenters;
            if (luma == null || width <= 0 || height <= 0 || transform == null
                    || markers == null || markers.length != 4 || centers == null) {
                updateStatus("ابتدا پاسخنامه را کامل داخل کادر دوربین نگه دارید تا چهار مربع گوشه شناسایی شوند.");
                return;
            }
            showBubbleCoordinateOverlay(luma, width, height, transform, centers, markers, markerSize);
        });
        page.addView(coordinateButton, new LinearLayout.LayoutParams(-1, -2));

        Button calibrationButton = new Button(this);
        calibrationButton.setText("کالیبراسیون تیرگی با برگهٔ خالی");
        calibrationButton.setAllCaps(false);
        calibrationButton.setOnClickListener(v -> showBlankSheetCalibration());
        page.addView(calibrationButton, new LinearLayout.LayoutParams(-1, -2));

        TextView guidance = new TextView(this);
        guidance.setText("پاسخنامه را کامل داخل کادر سبز قرار دهید؛ سپس دکمهٔ «عکس بگیر و تصحیح کن» را لمس کنید.");
        guidance.setTextColor(Color.rgb(51, 65, 85));
        guidance.setTextSize(13);
        guidance.setGravity(Gravity.CENTER);
        guidance.setBackgroundColor(Color.WHITE);
        guidance.setPadding(18, 8, 18, 10);
        page.addView(guidance, new LinearLayout.LayoutParams(-1, -2));

        // Diagnostic bubble overlays are intentionally removed from the normal scan screen.
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

    /**
     * Calibrates from the 400 template bubble locations on a blank, registered
     * answer sheet. A plain unmarked A4 cannot yet provide perspective anchors;
     * the four printed corner markers are required by the current scanner.
     */
    private void showBlankSheetCalibration() {
        byte[] luma = diagnosticLuma;
        int width = diagnosticWidth, height = diagnosticHeight;
        SheetPerspectiveTransform transform = diagnosticTransform;
        double[][][] centers = diagnosticCenters;
        if (luma == null || transform == null || centers == null || centers.length < 32
                || stableMarkerFrames < REQUIRED_STABLE_MARKER_FRAMES) {
            new AlertDialog.Builder(this).setTitle("کالیبراسیون خودکار نور")
                    .setMessage("پاسخنامهٔ قالب‌دار و خالی را کامل داخل کادر نگه دارید؛ چهار مربع گوشه باید پایدار شناسایی شوند.")
                    .setPositiveButton("متوجه شدم", null).show();
            return;
        }
        int count = 0;
        for (double[][] question : centers) if (question != null && question.length == 4) count += 4;
        double[] scores = new double[count], xs = new double[count], ys = new double[count];
        int index = 0;
        try {
            for (double[][] question : centers) {
                if (question == null || question.length != 4) continue;
                for (double[] mm : question) {
                    SheetPerspectiveTransform.Point p = transform.toImage(mm[0], mm[1]);
                    SheetPerspectiveTransform.Point ex = transform.toImage(
                            Math.min(transform.getPageWidth(), mm[0] + 1.45), mm[1]);
                    SheetPerspectiveTransform.Point ey = transform.toImage(
                            mm[0], Math.min(transform.getPageHeight(), mm[1] + 1.45));
                    double radius = (Math.hypot(ex.x - p.x, ex.y - p.y)
                            + Math.hypot(ey.x - p.x, ey.y - p.y)) * .5;
                    scores[index] = BubbleDarknessAnalyzer.score(luma, width, height, p.x, p.y, radius);
                    xs[index] = mm[0] / transform.getPageWidth();
                    ys[index] = mm[1] / transform.getPageHeight();
                    index++;
                }
            }
            AutomaticRegionalCalibration.Result result =
                    AutomaticRegionalCalibration.evaluate(scores, xs, ys);
            if (!result.acceptable) {
                showBlankSheetLightMap(luma, width, height, scores, xs, ys);
                new AlertDialog.Builder(this).setTitle("نور نامناسب")
                        .setMessage(String.format(java.util.Locale.US,
                                "یکنواختی نور کافی نیست (کیفیت %.0f از 100). برگه را به سطح روشن‌تر و بدون سایه منتقل کنید و دوباره کالیبره کنید.",
                                result.quality))
                        .setPositiveButton("متوجه شدم", null).show();
                return;
            }
            SharedPreferences prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
            JSONArray offsets = new JSONArray();
            for (double value : result.offsets) offsets.put(value);
            prefs.edit().putString("omr_regional_offsets", offsets.toString())
                    .putFloat("omr_regional_quality", (float) result.quality)
                    .putLong("omr_regional_saved_at", System.currentTimeMillis()).apply();
            showBlankSheetLightMap(luma, width, height, scores, xs, ys);
            new AlertDialog.Builder(this).setTitle("کالیبراسیون خودکار موفق")
                    .setMessage(String.format(java.util.Locale.US,
                            "کیفیت نور: %.0f از 100 — اصلاح موضعی چهار ناحیه ذخیره شد. علامت‌های مداد از معیار محلی جداگانه سنجیده می‌شوند.",
                            result.quality))
                    .setPositiveButton("بستن", null).show();
        } catch (org.json.JSONException jsonError) {
            new AlertDialog.Builder(this).setTitle("ذخیرهٔ کالیبراسیون ناموفق")
                    .setMessage("ذخیرهٔ تنظیمات اصلاح نور انجام نشد؛ دوباره تلاش کنید.")
                    .setPositiveButton("متوجه شدم", null).show();
        } catch (IllegalArgumentException badSample) {
            new AlertDialog.Builder(this).setTitle("کالیبراسیون انجام نشد")
                    .setMessage("نمونه‌برداری کامل نبود. پاسخنامه را صاف و بدون پوشش داخل کادر قرار دهید.")
                    .setPositiveButton("متوجه شدم", null).show();
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
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(98)
                        .build();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1280, 960))
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis, imageCapture);
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
            if (frameNow - lastFrameAnalysis < 55) return;
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
            if (markerCount < 4) {
                long plainCalibrationNow = android.os.SystemClock.elapsedRealtime();
                if (plainCalibrationNow - lastDiagnosticFrameSaved > 300) {
                    diagnosticLuma = upright.pixels.clone();
                    diagnosticWidth = upright.width;
                    diagnosticHeight = upright.height;
                    diagnosticTransform = null;
                    diagnosticMarkerPoints = null;
                    diagnosticCenters = null;
                    lastDiagnosticFrameSaved = plainCalibrationNow;
                }
            }
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
                long diagnosticNow = android.os.SystemClock.elapsedRealtime();
                if (diagnosticNow - lastDiagnosticFrameSaved > 300) {
                    diagnosticLuma = upright.pixels.clone();
                    diagnosticWidth = upright.width;
                    diagnosticHeight = upright.height;
                    diagnosticTransform = transform;
                    diagnosticMarkerPoints = detection.points.clone();
                    diagnosticMarkerSizeMm = detection.markerSizeMm;
                    // Diagnostic overlay must validate the entire printed 100-question sheet,
                    // independent of the currently selected exam question count.
                    diagnosticCenters = OmrTemplateLayout.bubbleCentersForPage(
                            OmrTemplateLayout.QUESTIONS_PER_PAGE, 0);
                    lastDiagnosticFrameSaved = diagnosticNow;
                }
                stableMarkerFrames++;
                currentLightSpread = measureLightSpread(upright.pixels, upright.width, upright.height, transform);
                if (currentLightSpread <= READY_LIGHT_SPREAD) stableLightFrames++; else stableLightFrames = 0;
                missingMarkerFrames = 0;
                // Capture is initiated only by the explicit on-screen button.
                // Preview analysis is used for guidance only and never triggers a photo.
            } else {
                stableMarkerFrames = 0;
                stableLightFrames = 0;
                currentLightSpread = Double.POSITIVE_INFINITY;
                // TEMPORARILY DISABLED: do not trigger the next-sheet / next-participant
                // flow when the previous sheet leaves the camera. Keep the preview available
                // so the operator can capture diagnostic screenshots without a dialog blocking it.
                if (sheetConsumed && markerCount < 2) {
                    missingMarkerFrames = 0;
                } else if (markerCount >= 2) {
                    missingMarkerFrames = 0;
                }
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastStatusUpdate > 350) {
                lastStatusUpdate = now;
                String message;
                if (sheetConsumed) {
                    message = waitingForNextName
                            ? "برگهٔ قبلی کنار رفت؛ نام شرکت‌کنندهٔ بعدی را ثبت کنید."
                            : (lastSheetResultMessage.isEmpty()
                                ? "برگه شناسایی شد؛ در حال بررسی پاسخ‌ها..."
                                : lastSheetResultMessage);
                } else if (transform != null && stableMarkerFrames >= REQUIRED_STABLE_MARKER_FRAMES && stableLightFrames >= REQUIRED_STABLE_MARKER_FRAMES) {
                    // Require a short run of consecutive valid frames before declaring readiness.
                    if (sheetGuideOverlay != null) sheetGuideOverlay.setReady(true);
                    message = "چهار مربع شناسایی شدند و نور مناسب است؛ کادر سبز شد. عکس بگیرید.";
                } else if (transform != null) {
                    if (sheetGuideOverlay != null) sheetGuideOverlay.setReady(false);
                    message = "چهار مربع پیدا شدند؛ گوشی و برگه را ثابت نگه دارید…";
                } else if (markerCount >= 4) {
                    message = "نشانه‌های گوشه پیدا شدند، اما هندسهٔ برگه قابل اعتماد نیست؛ برگه را کامل و صاف داخل کادر بگیرید.";
                } else if (markerCount >= 2) {
                    message = "چند نشانهٔ مربعی پیدا شد؛ کل برگه را داخل کادر و با نور یکنواخت نگه دارید.";
                } else {
                    if (sheetGuideOverlay != null) sheetGuideOverlay.setReady(false);
                    message = "در حال جست‌وجوی مربع‌های گوشه… چهار گوشهٔ برگه را داخل تصویر نگه دارید.";
                }
                updateStatus(message);
                if (transform != null && markerCount == 4 && lightGuideView != null) {
                    updateLiveLightGuide(upright.pixels, upright.width, upright.height, transform);
                } else if (lightGuideView != null) {
                    lightGuideView.setText("راهنمای نور: چهار مربع گوشه باید شناسایی شوند.");
                    lightGuideView.setTextColor(Color.rgb(71, 85, 105));
                    lightGuideView.setBackgroundColor(Color.rgb(226, 232, 240));
                }
            }
        } catch (RuntimeException ignored) {
            updateStatus("کیفیت تصویر کافی نیست؛ فاصله و نور را تنظیم کنید.");
        } finally {
            image.close();
        }
    }

    /**
     * Live heuristic lighting assessment. Samples multiple paper locations in
     * each quadrant and recommends a small trial movement toward the brighter
     * side; the user should verify that the measured spread decreases.
     */
    private void updateLiveLightGuide(byte[] luma, int width, int height,
                                      SheetPerspectiveTransform transform) {
        try {
            double pageW = transform.getPageWidth(), pageH = transform.getPageHeight();
            double[] brightness = new double[4];
            // Four quarter-page areas; use a small grid and average luma.
            for (int q = 0; q < 4; q++) {
                int col = q % 2, row = q / 2;
                double sum = 0; int n = 0;
                for (int gy = 0; gy < 5; gy++) {
                    for (int gx = 0; gx < 5; gx++) {
                        double px = pageW * (col * 0.5 + 0.12 + gx * 0.07);
                        double py = pageH * (row * 0.5 + 0.12 + gy * 0.07);
                        SheetPerspectiveTransform.Point p = transform.toImage(px, py);
                        int ix = (int) Math.round(p.x), iy = (int) Math.round(p.y);
                        if (ix >= 0 && iy >= 0 && ix < width && iy < height) {
                            sum += (luma[iy * width + ix] & 0xff); n++;
                        }
                    }
                }
                brightness[q] = n > 0 ? sum / n : 0;
            }
            double min = brightness[0], max = brightness[0], mean = 0;
            int darkest = 0;
            for (int i = 0; i < 4; i++) {
                mean += brightness[i];
                if (brightness[i] < min) { min = brightness[i]; darkest = i; }
                if (brightness[i] > max) max = brightness[i];
            }
            mean /= 4.0;
            double spread = max - min;
            String text;
            int color, bg;
            if (spread <= 14.0) {
                text = "نور یکنواخت است (" + Math.round(spread) + " واحد اختلاف). برگه را ثابت نگه دارید؛ آمادهٔ کالیبراسیون.";
                color = Color.rgb(22, 101, 52); bg = Color.rgb(220, 252, 231);
            } else if (spread <= 29.0) {
                text = "اختلاف نور متوسط (" + Math.round(spread) + "). یک جابه‌جایی کوچک آزمایشی به سمت ";
                color = Color.rgb(146, 64, 14); bg = Color.rgb(254, 243, 199);
                text += (darkest == 0 ? "بالا-راست" : darkest == 1 ? "بالا-چپ" : darkest == 2 ? "پایین-چپ" : "پایین-راست")
                        + " انجام دهید؛ سپس اختلاف را دوباره بسنجید.";
            } else {
                text = "سایه/اختلاف نور زیاد است (" + Math.round(spread) + "). برگه را کمی به سمت ";
                color = Color.rgb(153, 27, 27); bg = Color.rgb(254, 226, 226);
                text += (darkest == 0 ? "بالا-راست" : darkest == 1 ? "بالا-چپ" : darkest == 2 ? "پایین-چپ" : "پایین-راست")
                        + " حرکت دهید یا منبع نور را تغییر دهید؛ دوباره اندازه‌گیری کنید.";
            }
            lightGuideView.setText(text);
            lightGuideView.setTextColor(color);
            lightGuideView.setBackgroundColor(bg);
        } catch (RuntimeException ignored) {
            lightGuideView.setText("راهنمای نور در این فریم قابل محاسبه نیست؛ برگه را صاف نگه دارید.");
            lightGuideView.setTextColor(Color.rgb(71, 85, 105));
            lightGuideView.setBackgroundColor(Color.rgb(226, 232, 240));
        }
    }

    private double measureLightSpread(byte[] luma, int width, int height, SheetPerspectiveTransform transform) {\n        double pageW=transform.getPageWidth(), pageH=transform.getPageHeight(); double[] b=new double[4];\n        for(int q=0;q<4;q++){ int col=q%2,row=q/2; double sum=0; int n=0;\n          for(int gy=0;gy<5;gy++) for(int gx=0;gx<5;gx++){ double px=pageW*(col*0.5+0.12+gx*0.07), py=pageH*(row*0.5+0.12+gy*0.07);\n            SheetPerspectiveTransform.Point p=transform.toImage(px,py); int ix=(int)Math.round(p.x),iy=(int)Math.round(p.y);\n            if(ix>=0&&iy>=0&&ix<width&&iy<height){sum+=luma[iy*width+ix]&0xff;n++;}} b[q]=n>0?sum/n:0;}\n        double min=b[0],max=b[0]; for(int i=1;i<4;i++){min=Math.min(min,b[i]);max=Math.max(max,b[i]);} return max-min;\n    }\n\n    private static final class UprightLuma {
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
        // The sheet may occupy only the middle 60–80% of the camera frame.
        // Search a wider outer corner window so perspective/margins do not put
        // the printed registration squares outside the detector's search area.
        // Opposite windows remain separated, avoiding duplicate corner matches.
        final int cornerW = Math.max(12, (int) Math.round(gw * 0.40));
        final int cornerH = Math.max(12, (int) Math.round(gh * 0.40));
        final int rightStart = gw - cornerW;
        final int bottomStart = gh - cornerH;
        final int[][] regions = {
                {0, 0, cornerW, cornerH},
                {rightStart, 0, gw, cornerH},
                {rightStart, bottomStart, gw, gh},
                {0, bottomStart, cornerW, gh}
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
                    // The current template has both 16 mm primary marks and nearby
                    // 3 mm auxiliary marks. On the right side the auxiliary mark is
                    // encountered first; without this preference the transform can
                    // map a small mark as if it were the page corner, compressing all
                    // bubble coordinates and causing filled answers to read BLANK.
                    // Strongly prefer the large primary registration marks (about 16 mm)
                    // over the tiny auxiliary squares printed beside them (about 3 mm).
                    // The auxiliary marks can be sharper at low resolution, so a small
                    // shape/fill advantage must never outweigh a genuine primary marker.
                    double score = 2.0 - Math.abs(1.0 - aspect) * 1.4
                            + Math.min(1.0, fill) * 0.5
                            + (newSize ? 2.0 : 0.0) - sizePenalty * 0.25;
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

        // Reject mixed marker families before building the perspective transform.
        // Averaging 3 mm auxiliary marks with 16 mm primary marks can select the
        // wrong template coordinates and make every bubble overlay drift badly.
        if (found == 4) {
            boolean primaryFamily = sizes[0] > 8.0;
            for (int i = 1; i < sizes.length; i++) {
                if ((sizes[i] > 8.0) != primaryFamily) {
                    return new MarkerDetection(0,
                            new SheetPerspectiveTransform.Point[4], markerSizeMm);
                }
            }
        }

        // SECOND CRITERION: check the spacing between the four chosen squares.
        // The primary 16 mm corner marks have centers 184 mm apart horizontally
        // (13 mm to 197 mm), while the auxiliary 3 mm squares are only 161 mm
        // apart (24.5 mm to 185.5 mm). Prefer/accept a set whose measured
        // inter-corner spacing is consistent with the physical size of the
        // selected marks; this catches a small square being mistaken for a
        // page corner even when its shape score is high.
        if (found == 4 && markerSizeMm > 0) {
            double top = distance(points[0], points[1]);
            double right = distance(points[1], points[2]);
            double bottom = distance(points[2], points[3]);
            double left = distance(points[3], points[0]);
            double meanHorizontal = (top + bottom) * 0.5;
            double meanVertical = (left + right) * 0.5;
            double expectedMarkerPixels = markerSizeMm * gw / 210.0;
            double spacingInMarkerWidths = meanHorizontal / Math.max(1.0, expectedMarkerPixels);
            boolean sidesConsistent = ratio(top, bottom) < 2.2
                    && ratio(left, right) < 2.2;

            // THIRD CRITERION: validate both diagonals as a second independent
            // geometry check. For the primary marks, the canonical center-to-center
            // spans are 184 mm horizontally and 271 mm vertically. Their diagonals
            // should therefore be close to sqrt(184^2 + 271^2), after perspective
            // distortion. Do not demand a literal 45-degree angle: an A4 portrait
            // sheet is not square, so its diagonal is about 35 degrees to the long
            // horizontal axis in the ideal page geometry.
            double diagonal14 = distance(points[0], points[2]);
            double diagonal23 = distance(points[1], points[3]);
            double meanDiagonal = (diagonal14 + diagonal23) * 0.5;
            double expectedDiagonal = Math.hypot(meanHorizontal, meanVertical);
            boolean diagonalsMatchEachOther = ratio(diagonal14, diagonal23) <= 1.45;
            boolean diagonalsMatchSideScale = expectedDiagonal > 0
                    && meanDiagonal / expectedDiagonal >= 0.70
                    && meanDiagonal / expectedDiagonal <= 1.35;

            // Large corner marks yield about 11.5 marker-widths between centers.
            // The auxiliary squares yield over 50, so they fail this second test.
            boolean spacingMatchesPrimaryMarks = spacingInMarkerWidths >= 7.0
                    && spacingInMarkerWidths <= 22.0;
            // A page is portrait; allow substantial perspective skew, but reject
            // implausible point sets before computing bubble coordinates.
            double pageShape = meanHorizontal / Math.max(1.0, meanVertical);
            boolean portraitShape = pageShape >= 0.30 && pageShape <= 1.25;
            if (!sidesConsistent || !spacingMatchesPrimaryMarks || !portraitShape
                    || !diagonalsMatchEachOther || !diagonalsMatchSideScale) {
                return new MarkerDetection(0, new SheetPerspectiveTransform.Point[4], markerSizeMm);
            }
        }
        return new MarkerDetection(found, points, markerSizeMm);
    }

    private static double distance(SheetPerspectiveTransform.Point a,
            SheetPerspectiveTransform.Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static double ratio(double a, double b) {
        double smaller = Math.min(a, b);
        double larger = Math.max(a, b);
        return smaller <= 0 ? Double.POSITIVE_INFINITY : larger / smaller;
    }

    private void updateLightingButtonLabel(Button button) {
        String mode = getSharedPreferences("azmoonyar_local", MODE_PRIVATE)
                .getString("omr_lighting_mode", "auto");
        button.setText("آلتای نور: " + ("day".equals(mode)
                ? "روز / نور زیاد" : ("night".equals(mode) ? "شب / نور کم" : "خودکار")));
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
        // User-selectable lighting calibration: bright/day mode uses a lower
        // split to avoid paper texture being treated as a dark marker; dim/night
        // mode uses a higher split so genuinely dark printed marks remain visible.
        String lightingMode = getSharedPreferences("azmoonyar_local", MODE_PRIVATE)
                .getString("omr_lighting_mode", "auto");
        if ("day".equals(lightingMode)) {
            return Math.max(45, Math.min(115, mean - 50));
        }
        if ("night".equals(lightingMode)) {
            return Math.max(70, Math.min(145, mean - 30));
        }
        return Math.max(55, Math.min(125, mean - 45));
    }

    private int lumaAt(byte[] yPlane, int width, int height, int sampleX, int sampleY, int sample) {
        int x = Math.min(width - 1, sampleX * sample);
        int y = Math.min(height - 1, sampleY * sample);
        return yPlane[y * width + x] & 0xff;
    }

    private boolean isValidAnswerKey(String key, int total) {
        if (key == null || key.length() < total) return false;
        for (int i = 0; i < total; i++) {
            char c = Character.toUpperCase(key.charAt(i));
            if (c < 'A' || c > 'D') return false;
        }
        return true;
    }

    private String buildAnswerKeyFromQuestionBank(String json, int total) {
        try {
            JSONArray bank = new JSONArray(json == null ? "[]" : json);
            if (bank.length() < total) return "";
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < total; i++) {
                JSONObject question = bank.optJSONObject(i);
                int correct = question == null ? -1 : question.optInt("correct_index", -1);
                if (correct < 0 || correct > 3) return "";
                key.append((char) ('A' + correct));
            }
            return key.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Capture a full-quality JPEG with CameraX after the user presses the manual capture button.
     * OMR processing then reopens this exact saved file; preview pixels are never scored.
     */
    private void captureRealPhotoAndCorrect() {
        if (imageCapture == null || finished) {
            updateSheetResultStatus("دوربین برای ثبت عکس آماده نیست؛ دوباره تلاش کنید.");
            sheetConsumed = false;
            return;
        }
        java.io.File photoDir = new java.io.File(getFilesDir(), "answer_sheet_photos");
        if (!photoDir.exists() && !photoDir.mkdirs()) {
            updateSheetResultStatus("پوشهٔ ذخیرهٔ عکس ساخته نشد.");
            sheetConsumed = false;
            return;
        }
        java.io.File photoFile = new java.io.File(photoDir,
                "answer_sheet_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        updateStatus("فرمان دریافت شد؛ عکس با وضوح کامل گرفته می‌شود…");
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                        if (finished) return;
                        try {
                            processSavedPhotoFile(photoFile);
                        } catch (Exception error) {
                            updateSheetResultStatus("عکس ذخیره شد، اما خواندن آن ناموفق بود؛ نور و کادر را بررسی و دوباره عکس بگیرید.");
                            sheetConsumed = false;
                        }
                    }
                    @Override public void onError(@NonNull ImageCaptureException error) {
                        if (photoFile.exists()) photoFile.delete();
                        updateSheetResultStatus("ثبت عکس ناموفق بود؛ برگه را ثابت نگه دارید و دکمهٔ عکس‌گرفتن را دوباره لمس کنید.");
                        sheetConsumed = false;
                    }
                });
    }

    private void processSavedPhotoFile(java.io.File photoFile) throws Exception {
        // Decode at a bounded size to avoid out-of-memory on high-resolution phone cameras.
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new java.io.IOException("Saved image is unreadable");
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2400) sample *= 2;
        android.graphics.BitmapFactory.Options decode = new android.graphics.BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath(), decode);
        if (bitmap == null) throw new java.io.IOException("Saved image decode failed");
        try {
            // Respect the JPEG orientation recorded by CameraX/device.
            android.media.ExifInterface exif = new android.media.ExifInterface(photoFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);
            int degrees = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (degrees != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; }
            }
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            byte[] luma = new byte[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                luma[i] = (byte) ((Color.red(c) * 299 + Color.green(c) * 587
                        + Color.blue(c) * 114) / 1000);
            }
            MarkerDetection detection = detectDarkCornerMarkers(luma, width, height);
            if (detection.count != 4) {
                throw new java.io.IOException("Could not locate all four sheet registration marks");
            }
            // The saved full-resolution photo is validated independently; preview
            // readiness never authorizes scoring if the captured geometry is invalid.
            double inset = detection.markerSizeMm > 8 ? 13.0 : 6.5;
            double right = detection.markerSizeMm > 8 ? 197.0 : 203.5;
            double bottom = detection.markerSizeMm > 8 ? 284.0 : 290.5;
            SheetPerspectiveTransform transform = SheetPerspectiveTransform.fromControlPoints(
                    new SheetPerspectiveTransform.Point[]{
                            new SheetPerspectiveTransform.Point(inset, inset),
                            new SheetPerspectiveTransform.Point(right, inset),
                            new SheetPerspectiveTransform.Point(right, bottom),
                            new SheetPerspectiveTransform.Point(inset, bottom)
                    }, detection.points, OmrTemplateLayout.PAGE_WIDTH_MM,
                    OmrTemplateLayout.PAGE_HEIGHT_MM);
            getSharedPreferences("azmoonyar_local", MODE_PRIVATE).edit()
                    .putString("last_answer_sheet_photo", photoFile.getAbsolutePath()).apply();
            updateStatus("عکس واقعی ذخیره شد؛ تصحیح از همان فایل انجام می‌شود…");
            processRecognizedSheet(luma, width, height, transform);
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void processRecognizedSheet(byte[] luma, int width, int height,
            SheetPerspectiveTransform transform) {
        try {
            SharedPreferences prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
            int total = Math.max(1, Math.min(100, prefs.getInt("question_count", 20)));
            String answerKey = prefs.getString("answer_key", "");
            // The dashboard's "کلید اصلی پاسخ" is derived from question_bank_json,
            // not from the legacy answer_key field. Prefer a complete saved model key,
            // then derive the original key from the same question bank shown in that dialog.
            if (!isValidAnswerKey(answerKey, total)) {
                String modelKey = prefs.getString("answer_key_model_A", "");
                if (isValidAnswerKey(modelKey, total)) {
                    answerKey = modelKey;
                } else {
                    answerKey = buildAnswerKeyFromQuestionBank(prefs.getString("question_bank_json", "[]"), total);
                }
            }
            if (participantName.trim().isEmpty()) {
                updateSheetResultStatus("برگه شناسایی شد، اما نام شرکت‌کننده ثبت نشده است.");
                return;
            }
            if (!isValidAnswerKey(answerKey, total)) {
                updateSheetResultStatus("برگه شناسایی شد، اما کلید پاسخ معتبر و کامل پیدا نشد. بانک سؤال و پاسخ صحیح هر سؤال را بررسی کنید.");
                return;
            }
            double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(total, 0);
            SharedPreferences scanPrefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
            double[] regionalOffsets = null;
            try {
                JSONArray savedRegional = new JSONArray(scanPrefs.getString("omr_regional_offsets", "[]"));
                if (savedRegional.length() == 4) {
                    regionalOffsets = new double[4];
                    for (int r = 0; r < 4; r++) regionalOffsets[r] = savedRegional.getDouble(r);
                }
            } catch (Exception ignored) { regionalOffsets = null; }
            List<OMRSheetReader.QuestionResult> questions =
                    new OMRSheetReader(scanPrefs.getFloat("omr_calibration_offset", 0f), regionalOffsets)
                    .read(luma, width, height, transform, centers, 1.45);
            java.util.ArrayList<OMRAnswerClassifier.Result> answers = new java.util.ArrayList<>();
            for (OMRSheetReader.QuestionResult q : questions) answers.add(q.classification);
            // Correct = +1; wrong = 0; blank = 0. Uncertain answers remain in the review queue.
            String activeKey = answerKey.substring(0, total);

            // Keep a small, anonymized calibration record for reverse-engineering OMR.
            // Store raw four-option darkness scores beside the known answer key and the
            // classifier result, so thresholds can be tuned from evidence rather than guesses.
            try {
                JSONArray samples;
                try { samples = new JSONArray(prefs.getString("omr_calibration_samples", "[]")); }
                catch (Exception ignored) { samples = new JSONArray(); }
                JSONObject sample = new JSONObject();
                sample.put("createdAt", System.currentTimeMillis());
                sample.put("totalQuestions", total);
                sample.put("answerKey", activeKey);
                JSONArray sampleQuestions = new JSONArray();
                for (int i = 0; i < questions.size(); i++) {
                    OMRSheetReader.QuestionResult question = questions.get(i);
                    OMRAnswerClassifier.Result classification = question.classification;
                    JSONObject item = new JSONObject();
                    item.put("question", i + 1);
                    item.put("expectedOptionIndex", activeKey.charAt(i) - 'A');
                    item.put("status", classification.status.name());
                    item.put("predictedOptionIndex", classification.optionIndex);
                    item.put("strongestScore", classification.strongestScore);
                    item.put("margin", classification.margin);
                    JSONArray scores = new JSONArray();
                    for (double darkness : question.darkness) scores.put(darkness);
                    item.put("optionScores", scores);
                    sampleQuestions.put(item);
                }
                sample.put("questions", sampleQuestions);
                samples.put(sample);
                // Bound local storage; keep the 20 most recent calibration samples.
                while (samples.length() > 20) samples.remove(0);
                prefs.edit().putString("omr_calibration_samples", samples.toString()).apply();
            } catch (Exception calibrationError) {
                android.util.Log.e("AzmoonyarOMR", "Could not save calibration sample", calibrationError);
            }

            // Wrong answers have no negative marking; blank answers also earn zero.
            ExamScorer.Result score = ExamScorer.score(
                    activeKey, answers, 1.0, 0.0, 0.0);
            if (!score.isComplete()) {
                // Preserve all per-question readings in the review queue while still
                // reporting the determinate correct/wrong/blank answers below.
                JSONArray queue;
                try { queue = new JSONArray(prefs.getString("grading_review_queue", "[]")); }
                catch (Exception ignored) { queue = new JSONArray(); }
                JSONObject pending = new JSONObject();
                pending.put("name", participantName.trim());
                pending.put("examName", prefs.getString("exam_name", "آزمون بدون نام"));
                pending.put("createdAt", System.currentTimeMillis());
                pending.put("source", "saved_camera_photo");
                pending.put("needsReview", score.needsReview);
                pending.put("totalQuestions", score.totalQuestions);
                JSONArray questionStates = new JSONArray();
                for (int i = 0; i < questions.size(); i++) {
                    OMRAnswerClassifier.Result answer = questions.get(i).classification;
                    JSONObject item = new JSONObject();
                    item.put("question", i + 1);
                    item.put("status", answer.status.name());
                    item.put("optionIndex", answer.optionIndex);
                    item.put("margin", answer.margin);
                    item.put("strongestScore", answer.strongestScore);
                    JSONArray optionScores = new JSONArray();
                    for (double darkness : questions.get(i).darkness) optionScores.put(darkness);
                    item.put("optionScores", optionScores);
                    questionStates.put(item);
                }
                pending.put("questions", questionStates);
                queue.put(pending);
                prefs.edit().putString("grading_review_queue", queue.toString()).apply();
            }
            JSONArray saved;
            try { saved = new JSONArray(prefs.getString("grading_results", "[]")); }
            catch (Exception ignored) { saved = new JSONArray(); }
            JSONObject record = new JSONObject();
            record.put("name", participantName.trim());
            record.put("examName", prefs.getString("exam_name", "آزمون بدون نام"));
            JSONArray correctDetails = new JSONArray();
            JSONArray wrongQuestions = new JSONArray();
            JSONArray wrongDetails = new JSONArray();
            JSONArray blankQuestions = new JSONArray();
            JSONArray reviewQuestions = new JSONArray();
            for (int i = 0; i < questions.size(); i++) {
                OMRAnswerClassifier.Result answer = questions.get(i).classification;
                int questionNumber = i + 1;
                if (answer.status == OMRAnswerClassifier.Status.BLANK) {
                    blankQuestions.put(questionNumber);
                } else if (answer.status == OMRAnswerClassifier.Status.ANSWERED) {
                    String selected = String.valueOf((char) ('A' + answer.optionIndex));
                    String expected = String.valueOf(activeKey.charAt(i));
                    JSONObject detail = new JSONObject();
                    detail.put("question", questionNumber);
                    detail.put("selectedOption", selected);
                    if (selected.equals(expected)) {
                        correctDetails.put(detail);
                    } else {
                        wrongQuestions.put(questionNumber);
                        detail.put("correctOption", expected);
                        wrongDetails.put(detail);
                    }
                } else if (answer.status == OMRAnswerClassifier.Status.MULTIPLE_MARKS) {
                    wrongQuestions.put(questionNumber);
                    JSONObject detail = new JSONObject();
                    detail.put("question", questionNumber);
                    detail.put("correctOption", String.valueOf(activeKey.charAt(i)));
                    detail.put("selectedOption", "چند گزینه");
                    wrongDetails.put(detail);
                } else if (answer.status == OMRAnswerClassifier.Status.REVIEW) {
                    reviewQuestions.put(questionNumber);
                }
            }
            // Provisional percentage is informational only when any question is under review.
            record.put("score", score.isComplete() ? score.percentage()
                    : (score.correct * 100.0 / Math.max(1, score.totalQuestions)));
            record.put("earnedPoints", score.earnedPoints);
            record.put("pendingReview", !score.isComplete());
            record.put("reviewQuestions", reviewQuestions);
            record.put("needsReview", score.needsReview);
            record.put("correct", score.correct);
            record.put("wrong", score.wrong);
            record.put("correctDetails", correctDetails);
            record.put("wrongQuestions", wrongQuestions);
            record.put("wrongDetails", wrongDetails);
            record.put("blankQuestions", blankQuestions);
            record.put("blank", score.blank);
            record.put("multipleMarks", score.multipleMarks);
            record.put("totalQuestions", score.totalQuestions);
            record.put("createdAt", System.currentTimeMillis());
            saved.put(record);
            prefs.edit().putString("grading_results", saved.toString()).apply();
            String wrongList = formatQuestionOptions(wrongDetails);
            String blankList = blankQuestions.length() == 0 ? "ندارد"
                    : blankQuestions.toString().replace("[", "").replace("]", "");
            String correctList = formatQuestionOptions(correctDetails);
            updateSheetResultStatus("تصحیح انجام شد: " + participantName
                    + " • صحیح: " + score.correct + " (" + correctList + ")"
                    + " • غلط: " + score.wrong + " (" + wrongList + ")"
                    + " • سفید: " + score.blank + " (سؤال‌های " + blankList + ")"
                    + (score.isComplete()
                        ? " • نمره: " + String.format(java.util.Locale.US, "%.1f", score.earnedPoints)
                            + " از " + total
                        : " • بازبینی: سؤال‌های " + formatQuestionNumbers(reviewQuestions)
                            + " (نمره موقت؛ پس از بازبینی قطعی می‌شود)")
                    + ". برگه را کنار ببرید تا نفر بعدی را ثبت کنیم.");
        } catch (Exception e) {
            updateSheetResultStatus("برگه شناسایی شد، اما خواندن حباب‌ها خطا داد: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "؛ برگه را کامل داخل کادر بگیرید.");
        }
    }

    /** Displays the captured blank A4 luminance as a false-color light/shadow map. */
    private void showBlankSheetLightMap(byte[] luma, int width, int height,
            double[] scores, double[] xs, double[] ys) {
        if (luma == null || width <= 0 || height <= 0
                || (long) width * height > 12000000L) return;
        try {
            int maxSide = 900;
            float scale = Math.min(1f, maxSide / (float) Math.max(width, height));
            int outW = Math.max(1, Math.round(width * scale));
            int outH = Math.max(1, Math.round(height * scale));
            Bitmap map = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[outW * outH];
            long sum = 0;
            int min = 255, max = 0;
            for (int y = 0; y < outH; y++) {
                int sourceY = Math.min(height - 1, (int) (y / scale));
                for (int x = 0; x < outW; x++) {
                    int sourceX = Math.min(width - 1, (int) (x / scale));
                    int gray = luma[sourceY * width + sourceX] & 0xff;
                    sum += gray;
                    min = Math.min(min, gray);
                    max = Math.max(max, gray);
                    // False-color luminance: dark/shadow=blue, mid=green/yellow, bright=red.
                    int red, green, blue;
                    if (gray < 128) {
                        float t = gray / 128f;
                        red = 0;
                        green = Math.round(180 * t);
                        blue = Math.round(255 * (1 - t) + 55);
                    } else {
                        float t = (gray - 128) / 127f;
                        red = Math.round(255 * t + 40);
                        green = Math.round(220 * (1 - t) + 35);
                        blue = 0;
                    }
                    pixels[y * outW + x] = Color.rgb(Math.min(255, red),
                            Math.min(255, green), Math.min(255, blue));
                }
            }
            map.setPixels(pixels, 0, outW, 0, 0, outW, outH);
            // Suitability overlay: classify each page quadrant by local sample stability
            // and its deviation from the blank-sheet global baseline.
            @SuppressWarnings("unchecked") java.util.ArrayList<Double>[] zones = new java.util.ArrayList[] {
                    new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                    new java.util.ArrayList<>(), new java.util.ArrayList<>()};
            for (int i = 0; i < scores.length; i++) {
                int zone = (ys[i] >= .5 ? 2 : 0) + (xs[i] >= .5 ? 1 : 0);
                zones[zone].add(scores[i]);
            }
            double globalMedian = calibrationMedian(scores);
            Canvas suitabilityCanvas = new Canvas(map);
            Paint suitabilityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            suitabilityPaint.setStyle(Paint.Style.FILL);
            Paint zoneLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            zoneLabelPaint.setColor(Color.WHITE);
            zoneLabelPaint.setTextSize(Math.max(15f, outW / 32f));
            zoneLabelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            String[] zoneNames = {"بالا چپ", "بالا راست", "پایین چپ", "پایین راست"};
            StringBuilder zoneReport = new StringBuilder();
            for (int zone = 0; zone < 4; zone++) {
                double[] zoneValues = new double[zones[zone].size()];
                for (int k = 0; k < zoneValues.length; k++) zoneValues[k] = zones[zone].get(k);
                double median = calibrationMedian(zoneValues);
                double[] deviations = new double[zoneValues.length];
                for (int k = 0; k < zoneValues.length; k++) deviations[k] = Math.abs(zoneValues[k] - median);
                double mad = calibrationMedian(deviations);
                double baselineDelta = Math.abs(median - globalMedian);
                boolean green = mad <= .02 && baselineDelta <= .04 && zoneValues.length >= 4;
                boolean yellow = mad <= .035 && baselineDelta <= .08 && zoneValues.length >= 4;
                int color = green ? Color.rgb(20, 170, 65)
                        : (yellow ? Color.rgb(235, 180, 0) : Color.rgb(220, 45, 45));
                suitabilityPaint.setColor(Color.argb(95, Color.red(color), Color.green(color), Color.blue(color)));
                float left = (zone % 2) * outW / 2f;
                float top = (zone / 2) * outH / 2f;
                float right = left + outW / 2f, bottom = top + outH / 2f;
                suitabilityCanvas.drawRect(left, top, right, bottom, suitabilityPaint);
                String label = zoneNames[zone] + (green ? " • مناسب" : (yellow ? " • با احتیاط" : " • نامناسب"));
                suitabilityCanvas.drawText(label, left + 10, top + Math.max(25f, outW / 28f), zoneLabelPaint);
                if (zoneReport.length() > 0) zoneReport.append("\\n");
                zoneReport.append(zoneNames[zone]).append(": ")
                        .append(green ? "سبز/مناسب" : (yellow ? "زرد/احتیاط" : "قرمز/نامناسب"))
                        .append(" (پراکندگی ").append(String.format(java.util.Locale.US, "%.3f", mad)).append(")");
            }
            double average = sum / (double) pixels.length;
            ImageView image = new ImageView(this);
            image.setImageBitmap(map);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            TextView stats = new TextView(this);
            stats.setText(String.format(java.util.Locale.US,
                    "نقشهٔ سایه/روشنایی و قابلیت کالیبراسیون A4\\nمیانگین نور: %.0f/255\\nکمترین نور (سایه): %d\\nبیشترین نور: %d",
                    average, min, max) + "\\n" + zoneReport.toString()
                    + "\\nسبز = مناسب • زرد = با احتیاط • قرمز = نامناسب");
            stats.setTextSize(14);
            stats.setPadding(16, 12, 16, 12);
            ScrollView scroll = new ScrollView(this);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.addView(stats, new LinearLayout.LayoutParams(-1, -2));
            content.addView(image, new LinearLayout.LayoutParams(-1, -2));
            scroll.addView(content);
            new AlertDialog.Builder(this).setTitle("نمایش سایه و روشنی پاسخنامه")
                    .setView(scroll).setPositiveButton("بستن", (dialog, which) -> image.setImageDrawable(null))
                    .setOnCancelListener(dialog -> image.setImageDrawable(null)).show();
        } catch (RuntimeException ignored) {
            updateStatus("نمایش نقشهٔ نور انجام نشد؛ کالیبراسیون عددی همچنان بررسی شد.");
        }
    }

    private double calibrationMedian(double[] values) {
        if (values == null || values.length == 0) return 0;
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    /** Formats question numbers held in the review queue JSON array. */
    private String formatQuestionNumbers(JSONArray questions) {
        if (questions == null || questions.length() == 0) return "ندارد";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < questions.length(); i++) {
            Object value = questions.opt(i);
            int number;
            if (value instanceof JSONObject) {
                JSONObject item = (JSONObject) value;
                number = item.optInt("question", item.optInt("questionNumber", i + 1));
            } else {
                number = questions.optInt(i, i + 1);
            }
            if (out.length() > 0) out.append("، ");
            out.append(number);
        }
        return out.length() == 0 ? "ندارد" : out.toString();
    }

    private void showBubbleCoordinateOverlay(byte[] luma, int width, int height,
            SheetPerspectiveTransform transform, double[][][] centers,
            SheetPerspectiveTransform.Point[] markerPoints, double markerSizeMm) {
        // Called from the camera analyzer thread; build the diagnostic image there,
        // then display it on the UI thread. The original luminance array is not retained.
        try {
            if (luma == null || width <= 0 || height <= 0
                    || (long) width * height > 12000000L) {
                updateSheetResultStatus("تصویر تشخیصی معتبر نیست؛ دوباره برگه را اسکن کنید.");
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[width * height];
            int available = Math.min(luma.length, pixels.length);
            for (int i = 0; i < available; i++) {
                int gray = luma[i] & 0xff;
                pixels[i] = Color.rgb(gray, gray, gray);
            }
            for (int i = available; i < pixels.length; i++) {
                pixels[i] = Color.BLACK;
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, width / 700f * 2.0f));
            // Use high-contrast option-specific colors so the diagnostic rings
            // are easy to distinguish against grayscale camera imagery.
            final int[] bubbleOverlayColors = new int[] {
                    Color.rgb(255, 35, 35),   // A: vivid red
                    Color.rgb(20, 105, 255),   // B: vivid blue
                    Color.rgb(0, 190, 70),     // C: vivid green
                    Color.rgb(255, 190, 0)     // D: vivid yellow/amber
            };
            // The printed bubbles are ellipses (4.3 x 3.4 mm), not circles.
            // Derive each overlay's local width/height through the homography so
            // the rings shrink/grow with perspective instead of drifting outside
            // the bubbles on distant columns or near the page edges.
            final double halfBubbleWidthMm = 2.15;
            final double halfBubbleHeightMm = 1.70;
            for (int q = 0; q < centers.length; q++) {
                for (int option = 0; option < 4; option++) {
                    double x = centers[q][option][0];
                    double y = centers[q][option][1];
                    SheetPerspectiveTransform.Point p = transform.toImage(x, y);
                    SheetPerspectiveTransform.Point leftEdge = transform.toImage(
                            Math.max(0.0, x - halfBubbleWidthMm), y);
                    SheetPerspectiveTransform.Point rightEdge = transform.toImage(
                            Math.min(OmrTemplateLayout.PAGE_WIDTH_MM, x + halfBubbleWidthMm), y);
                    SheetPerspectiveTransform.Point topEdge = transform.toImage(
                            x, Math.max(0.0, y - halfBubbleHeightMm));
                    SheetPerspectiveTransform.Point bottomEdge = transform.toImage(
                            x, Math.min(OmrTemplateLayout.PAGE_HEIGHT_MM, y + halfBubbleHeightMm));
                    float rx = (float) (Math.hypot(p.x - leftEdge.x, p.y - leftEdge.y)
                            + Math.hypot(rightEdge.x - p.x, rightEdge.y - p.y)) * 0.5f;
                    float ry = (float) (Math.hypot(p.x - topEdge.x, p.y - topEdge.y)
                            + Math.hypot(bottomEdge.x - p.x, bottomEdge.y - p.y)) * 0.5f;
                    // Match the printed 4.3 x 3.4 mm ellipse closely.
                    // 0.82 made the diagnostic rings visibly too small; keep only a
                    // slight inset so the center alignment can be judged against the
                    // actual printed border.
                    rx = Math.max(2.0f, rx * 0.98f);
                    ry = Math.max(2.0f, ry * 0.98f);
                    paint.setColor(bubbleOverlayColors[option]);
                    canvas.drawOval((float) p.x - rx, (float) p.y - ry,
                            (float) p.x + rx, (float) p.y + ry, paint);
                    if (q < 10) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setTextSize(Math.max(14f, width / 60f));
                        canvas.drawText("ABCD".substring(option, option + 1),
                                (float) p.x + rx, (float) p.y - ry, paint);
                        paint.setStyle(Paint.Style.STROKE);
                    }
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    bitmap.recycle();
                    return;
                }
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ScrollView scroll = new ScrollView(this);
                scroll.addView(image, new ScrollView.LayoutParams(-1, -2));
                int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.68f);
                LinearLayout content = new LinearLayout(this);
                content.setOrientation(LinearLayout.VERTICAL);
                TextView legend = new TextView(this);
                legend.setText("مراکز نمونه‌برداری حباب‌ها — A قرمز، B آبی، C سبز، D زرد. حروف ۱۰ سؤال اول نمایش داده شده‌اند.");
                legend.setTextSize(14);
                legend.setPadding(16, 12, 16, 8);
                content.addView(legend);
                TextView geometry = new TextView(this);
                double top = distance(markerPoints[0], markerPoints[1]);
                double right = distance(markerPoints[1], markerPoints[2]);
                double bottom = distance(markerPoints[3], markerPoints[2]);
                double left = distance(markerPoints[0], markerPoints[3]);
                double diag14 = distance(markerPoints[0], markerPoints[2]);
                double diag23 = distance(markerPoints[1], markerPoints[3]);
                double horizontalScale = (top + bottom) * 0.5 / (OmrTemplateLayout.PAGE_WIDTH_MM
                        - 2 * (markerSizeMm > 8 ? OmrTemplateLayout.NEW_MARKER_CENTER_INSET_MM
                        : OmrTemplateLayout.LEGACY_MARKER_CENTER_INSET_MM));
                double verticalScale = (left + right) * 0.5 / (OmrTemplateLayout.PAGE_HEIGHT_MM
                        - 2 * (markerSizeMm > 8 ? OmrTemplateLayout.NEW_MARKER_CENTER_INSET_MM
                        : OmrTemplateLayout.LEGACY_MARKER_CENTER_INSET_MM));
                geometry.setText(String.format(java.util.Locale.US,
                        "فاصلهٔ افقی بالا/پایین: %.1f / %.1f px\n"
                        + "فاصلهٔ عمودی چپ/راست: %.1f / %.1f px\n"
                        + "قطر گوشهٔ ۱ تا ۳: %.1f px\nقطر گوشهٔ ۲ تا ۴: %.1f px\n"
                        + "مقیاس میانگین افقی/عمودی: %.2f / %.2f px/mm\n"
                        + "نشانهٔ استفاده‌شده: %.1f mm",
                        top, bottom, left, right, diag14, diag23,
                        horizontalScale, verticalScale, markerSizeMm));
                geometry.setTextSize(13);
                geometry.setPadding(16, 4, 16, 12);
                content.addView(geometry);
                content.addView(scroll, new LinearLayout.LayoutParams(-1, maxHeight));
                new AlertDialog.Builder(this)
                        .setTitle("آزمایش مختصات حباب‌ها")
                        .setView(content)
                        .setPositiveButton("بستن", (dialog, which) -> {
                            image.setImageDrawable(null);
                            bitmap.recycle();
                        })
                        .setOnCancelListener(dialog -> {
                            image.setImageDrawable(null);
                            bitmap.recycle();
                        })
                        .show();
            });
        } catch (RuntimeException error) {
            runOnUiThread(() -> updateSheetResultStatus(
                    "نمایش تصویر تشخیصی ناموفق بود: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
        }
    }

    private void showNextParticipantDialog() {
        runOnUiThread(() -> {
            if (finished || isFinishing() || isDestroyed()) return;
            EditText nameInput = new EditText(this);
            nameInput.setSingleLine(true);
            nameInput.setHint("نام شرکت‌کنندهٔ بعدی");
            Button voiceButton = new Button(this);
            voiceButton.setText("🎙 ثبت نام با صدا");
            voiceButton.setAllCaps(false);
            voiceButton.setTextColor(Color.WHITE);
            voiceButton.setTextSize(18);
            voiceButton.setTypeface(null, android.graphics.Typeface.BOLD);
            voiceButton.setMinHeight(Math.round(58 * getResources().getDisplayMetrics().density));
            voiceButton.setPadding(20, 12, 20, 12);
            android.graphics.drawable.GradientDrawable voiceBackground =
                    new android.graphics.drawable.GradientDrawable();
            voiceBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            voiceBackground.setCornerRadius(1000f);
            voiceBackground.setColor(Color.rgb(220, 38, 38));
            voiceBackground.setStroke(Math.round(2 * getResources().getDisplayMetrics().density),
                    Color.rgb(185, 28, 28));
            voiceButton.setBackground(voiceBackground);
            voiceButton.setElevation(Math.round(4 * getResources().getDisplayMetrics().density));
            TextView voiceStatus = new TextView(this);
            voiceStatus.setText("نام را تایپ کنید یا با صدا بگویید.");
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(24, 8, 24, 4);
            content.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));
            content.addView(voiceButton, new LinearLayout.LayoutParams(-1, -2));
            content.addView(voiceStatus, new LinearLayout.LayoutParams(-1, -2));
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("آماده برای پاسخنامهٔ بعدی")
                    .setMessage("برگهٔ قبلی کنار رفته است. نام نفر بعدی را ثبت کنید.")
                    .setView(content)
                    .setPositiveButton("شروع اسکن بعدی", null)
                    .setCancelable(false)
                    .create();
            voiceButton.setOnClickListener(v -> {
                if (voiceRecognizer != null) voiceRecognizer.stop();
                voiceRecognizer = new PersianVosk();
                voiceButton.setEnabled(false);
                voiceStatus.setText("گوش می‌دهم؛ نام را بگویید…");
                voiceRecognizer.start(this, new PersianVosk.Callback() {
                    @Override public void onText(String text) {
                        runOnUiThread(() -> nameInput.setText(text));
                        voiceButton.setEnabled(true);
                        if (voiceRecognizer != null) voiceRecognizer.stop();
                    }
                    @Override public void onStatus(String status) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) voiceStatus.setText(status);
                            if (status.contains("ناموفق") || status.startsWith("خطای میکروفون")
                                    || status.startsWith("ضبط بدون دریافت گفتار")) voiceButton.setEnabled(true);
                        });
                    }
                });
            });
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String nextName = nameInput.getText().toString().trim();
                        if (nextName.isEmpty()) {
                            nameInput.setError("نام شرکت‌کننده را وارد کنید.");
                            return;
                        }
                        participantName = nextName;
                        getSharedPreferences("azmoonyar_local", MODE_PRIVATE).edit()
                                .putString("pending_scan_participant", nextName).apply();
                        if (voiceRecognizer != null) { voiceRecognizer.stop(); voiceRecognizer = null; }
                        waitingForNextName = false;
                        sheetConsumed = false;
                        voiceCaptureRequested = false;
                        ensureVoicePermissionAndStart();
                        stableMarkerFrames = 0;
                        missingMarkerFrames = 0;
                        dialog.dismiss();
                        updateStatus("آمادهٔ اسکن پاسخنامهٔ " + participantName + ".");
                    }));
            dialog.show();
        });
    }

    private String formatQuestionOptions(JSONArray details) {
        if (details == null || details.length() == 0) return "ندارد";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < details.length(); i++) {
            JSONObject item = details.optJSONObject(i);
            if (item == null) continue;
            if (out.length() > 0) out.append("، ");
            out.append("سؤال ").append(item.optInt("question", i + 1))
                    .append(" گزینه ").append(item.optString("selectedOption", "—"));
        }
        return out.length() == 0 ? "ندارد" : out.toString();
    }

    private void updateSheetResultStatus(String message) {
        lastSheetResultMessage = message;
        updateStatus(message);
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
        } else if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceCaptureListener();
            } else {
                updateStatus("برای گرفتن عکس با فرمان صوتی، اجازهٔ میکروفون لازم است.");
            }
        }
    }

    private void ensureVoicePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceCaptureListener();
        } else if (!isFinishing()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
        }
    }

    private void startVoiceCaptureListener() {
        if (finished || voiceCommandListening || sheetConsumed || waitingForNextName) return;
        voiceCommandListening = true;
        if (voiceRecognizer != null) { voiceRecognizer.stop(); voiceRecognizer = null; }
        voiceRecognizer = new PersianVosk();
        voiceRecognizer.start(this, new PersianVosk.Callback() {
            @Override public void onText(String text) {
                String normalized = text == null ? "" : text.trim()
                        .replace("‌", " ")
                        .replace('ي', 'ی')
                        .replace('ك', 'ک')
                        .replaceAll("[\\p{Punct}\\p{S}،؛؟!]", " ")
                        .replaceAll("\\s+", " ");
                // Persian ASR may omit the verb or return the command with punctuation.
                if (normalized.contains("عکس بگیر") || normalized.equals("عکس")
                        || normalized.contains("عکس بگیره") || normalized.contains("عکس بگير")
                        || normalized.contains("عکس بگیر") || normalized.contains("عکس بگیرش")) {
                    voiceCaptureRequested = true;
                    voiceCommandListening = false;
                    stopVoiceCaptureListener();
                    updateStatus("فرمان دریافت شد؛ برگه را ثبت می‌کنم…");
                } else {
                    voiceCommandListening = false;
                    stopVoiceCaptureListener();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            this::restartVoiceCapture, 250);
                }
            }
            private void restartVoiceCapture() {
                if (!finished && !sheetConsumed && !waitingForNextName) startVoiceCaptureListener();
            }
            @Override public void onStatus(String status) {
                // Keep the command listener silent; no spoken guidance or status narration.
                if (status.startsWith("خطای میکروفون") || status.startsWith("دریافت مدل صوتی فارسی ناموفق")) {
                    voiceCommandListening = false;
                    stopVoiceCaptureListener();
                    updateStatus("تشخیص فرمان صوتی آماده نیست؛ مجوز میکروفون و اینترنتِ دریافت اولیهٔ مدل را بررسی کنید.");
                } else if (status.startsWith("ضبط بدون دریافت گفتار")) {
                    voiceCommandListening = false;
                    stopVoiceCaptureListener();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            () -> { if (!finished && !sheetConsumed && !waitingForNextName) startVoiceCaptureListener(); }, 250);
                }
            }
        });
    }

    private void stopVoiceCaptureListener() {
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
        }
    }

    @Override protected void onDestroy() {
        finished = true;
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        super.onDestroy();
    }
}