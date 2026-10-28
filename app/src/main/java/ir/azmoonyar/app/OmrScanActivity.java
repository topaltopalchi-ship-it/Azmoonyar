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
    private volatile String lastSheetResultMessage = "";
    private PersianVosk voiceRecognizer;
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
            framePaint.setColor(Color.rgb(70, 230, 150));
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(4f * getResources().getDisplayMetrics().density);
            framePaint.setStrokeCap(Paint.Cap.ROUND);
            framePaint.setStrokeJoin(Paint.Join.ROUND);
            shadePaint.setColor(Color.argb(210, 70, 230, 150));
            shadePaint.setStyle(Paint.Style.STROKE);
            shadePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
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
        SheetGuideOverlay sheetGuide = new SheetGuideOverlay(this);
        cameraFrame.addView(sheetGuide, new FrameLayout.LayoutParams(-1, -1));
        page.addView(cameraFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = new TextView(this);
        statusView.setText("دوربین برای یافتن چهار مربع گوشه آماده می‌شود…");
        statusView.setTextColor(Color.rgb(30, 41, 59));
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(16, 12, 16, 12);
        page.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        TextView guidance = new TextView(this);
        guidance.setText("پاسخنامه را طوری داخل کادر سبز قرار دهید که هر چهار گوشه دیده شوند. پس از تشخیص پایدار، اسکن خودکار انجام می‌شود؛ برای برگهٔ بعدی، برگهٔ قبلی را کنار ببرید.");
        guidance.setTextColor(Color.rgb(51, 65, 85));
        guidance.setTextSize(13);
        guidance.setGravity(Gravity.CENTER);
        guidance.setBackgroundColor(Color.WHITE);
        guidance.setPadding(18, 8, 18, 10);
        page.addView(guidance, new LinearLayout.LayoutParams(-1, -2));

        Button diagnosticButton = new Button(this);
        diagnosticButton.setText("نمایش مختصات حباب‌ها");
        diagnosticButton.setOnClickListener(v -> {
            byte[] frame = diagnosticLuma;
            SheetPerspectiveTransform transform = diagnosticTransform;
            SheetPerspectiveTransform.Point[] markerPoints = diagnosticMarkerPoints;
            double markerSizeMm = diagnosticMarkerSizeMm;
            double[][][] centers = diagnosticCenters;
            int w = diagnosticWidth, h = diagnosticHeight;
            if (frame == null || transform == null || markerPoints == null
                    || markerPoints.length != 4 || centers == null || w <= 0 || h <= 0) {
                new AlertDialog.Builder(this)
                        .setTitle("تصویر تشخیصی آماده نیست")
                        .setMessage("ابتدا برگه را کامل داخل کادر دوربین بگیرید تا چهار مربع گوشه شناسایی شوند؛ سپس دوباره این دکمه را بزنید.")
                        .setPositiveButton("باشه", null).show();
                return;
            }
            showBubbleCoordinateOverlay(frame, w, h, transform, centers, markerPoints, markerSizeMm);
        });
        page.addView(diagnosticButton, new LinearLayout.LayoutParams(-1, -2));
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
                    diagnosticCenters = OmrTemplateLayout.bubbleCentersForPage(
                            Math.max(1, Math.min(100, getSharedPreferences("azmoonyar_local", MODE_PRIVATE)
                                    .getInt("question_count", 20))), 0);
                    lastDiagnosticFrameSaved = diagnosticNow;
                }
                stableMarkerFrames++;
                missingMarkerFrames = 0;
                if (!sheetConsumed && stableMarkerFrames >= 2) {
                    sheetConsumed = true;
                    processRecognizedSheet(upright.pixels, upright.width, upright.height, transform);
                }
            } else {
                stableMarkerFrames = 0;
                if (sheetConsumed && markerCount < 2) {
                    missingMarkerFrames++;
                    if (missingMarkerFrames >= 3 && !waitingForNextName) {
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
                if (sheetConsumed) {
                    message = waitingForNextName
                            ? "برگهٔ قبلی کنار رفت؛ نام شرکت‌کنندهٔ بعدی را ثبت کنید."
                            : (lastSheetResultMessage.isEmpty()
                                ? "برگه شناسایی شد؛ در حال بررسی پاسخ‌ها..."
                                : lastSheetResultMessage);
                } else if (transform != null) {
                    try {
                        // Live preview while a new sheet is being aligned.
                        double[][][] centers = OmrTemplateLayout.bubbleCentersForPage(
                                OmrTemplateLayout.QUESTIONS_PER_PAGE, 0);
                        List<OMRSheetReader.QuestionResult> results =
                                new OMRSheetReader().read(upright.pixels, upright.width,
                                        upright.height, transform, centers, 1.45);
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
                    // The current template has both 16 mm primary marks and nearby
                    // 3 mm auxiliary marks. On the right side the auxiliary mark is
                    // encountered first; without this preference the transform can
                    // map a small mark as if it were the page corner, compressing all
                    // bubble coordinates and causing filled answers to read BLANK.
                    double score = 2.0 - Math.abs(1.0 - aspect) * 1.4
                            + Math.min(1.0, fill) * 0.5
                            + (newSize ? 0.9 : 0.0) - sizePenalty * 0.25;
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
            List<OMRSheetReader.QuestionResult> questions =
                    new OMRSheetReader().read(luma, width, height, transform, centers, 1.45);
            java.util.ArrayList<OMRAnswerClassifier.Result> answers = new java.util.ArrayList<>();
            for (OMRSheetReader.QuestionResult q : questions) answers.add(q.classification);
            ExamScorer.Result score = ExamScorer.score(
                    answerKey.substring(0, total), answers, 1.0, 0.0, 0.0);
            if (!score.isComplete()) {
                // Preserve the uncertain sheet for later human review, but never store a final score.
                JSONArray queue;
                try { queue = new JSONArray(prefs.getString("grading_review_queue", "[]")); }
                catch (Exception ignored) { queue = new JSONArray(); }
                JSONObject pending = new JSONObject();
                pending.put("name", participantName.trim());
                pending.put("examName", prefs.getString("exam_name", "آزمون بدون نام"));
                pending.put("createdAt", System.currentTimeMillis());
                pending.put("source", "live_camera");
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
                    for (double darkness : questions.get(i).darkness) {
                        optionScores.put(darkness);
                    }
                    item.put("optionScores", optionScores);
                    questionStates.put(item);
                }
                pending.put("questions", questionStates);
                queue.put(pending);
                prefs.edit().putString("grading_review_queue", queue.toString()).apply();
                updateSheetResultStatus("برگهٔ " + participantName + " خوانده شد؛ " + score.needsReview
                        + " سؤال نیازمند بازبینی است. نمرهٔ قطعی ذخیره نشد و برگه به صف بازبینی اضافه شد.");
                return;
            }
            JSONArray saved;
            try { saved = new JSONArray(prefs.getString("grading_results", "[]")); }
            catch (Exception ignored) { saved = new JSONArray(); }
            JSONObject record = new JSONObject();
            record.put("name", participantName.trim());
            record.put("examName", prefs.getString("exam_name", "آزمون بدون نام"));
            record.put("score", score.percentage());
            record.put("correct", score.correct);
            record.put("wrong", score.wrong);
            record.put("blank", score.blank);
            record.put("multipleMarks", score.multipleMarks);
            record.put("totalQuestions", score.totalQuestions);
            record.put("createdAt", System.currentTimeMillis());
            saved.put(record);
            prefs.edit().putString("grading_results", saved.toString()).apply();
            updateSheetResultStatus("تصحیح خودکار انجام شد: " + participantName + " • نمره "
                    + String.format(java.util.Locale.US, "%.1f", score.percentage())
                    + " از 100. برگه را کنار ببرید تا نفر بعدی را ثبت کنیم.");
        } catch (Exception e) {
            updateSheetResultStatus("برگه شناسایی شد، اما خواندن حباب‌ها خطا داد: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "؛ برگه را کامل داخل کادر بگیرید.");
        }
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
            paint.setStrokeWidth(Math.max(3f, width / 360f * 2.2f));
            int[] optionColors = {
                    Color.rgb(239, 68, 68), Color.rgb(37, 99, 235),
                    Color.rgb(22, 163, 74), Color.rgb(234, 179, 8)
            };
            float radius = Math.max(10f, width / 100f);
            for (int q = 0; q < centers.length; q++) {
                for (int option = 0; option < 4; option++) {
                    SheetPerspectiveTransform.Point p =
                            transform.toImage(centers[q][option][0], centers[q][option][1]);
                    paint.setColor(optionColors[option]);
                    canvas.drawCircle((float) p.x, (float) p.y, radius, paint);
                    if (q < 10) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setTextSize(Math.max(18f, width / 45f));
                        canvas.drawText("ABCD".substring(option, option + 1),
                                (float) p.x + radius, (float) p.y - radius, paint);
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
                        stableMarkerFrames = 0;
                        missingMarkerFrames = 0;
                        dialog.dismiss();
                        updateStatus("آمادهٔ اسکن پاسخنامهٔ " + participantName + ".");
                    }));
            dialog.show();
        });
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