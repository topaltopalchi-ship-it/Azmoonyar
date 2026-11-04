package ir.azmoonyar.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Bitmap;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.AlertDialog;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline exam setup and answer-key editor. */
public class MainActivity extends Activity {
    private static final int STUDENT_VOICE_REQUEST = 4201;
    private ProgressDialog modelDownloadDialog;
    private EditText activeStudentNameInput;
    private PersianVosk voiceRecognizer;
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView summary;
    private static final int REQUEST_ANSWER_KEY_IMAGE = 7314;
    private static final int REQUEST_QUESTION_IMAGE = 7316;
    private String pendingKeyTarget = "main";

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String examName() { return prefs.getString("exam_name", ""); }
    private int questionCount() { return prefs.getInt("question_count", 20); }
    private String key() { return prefs.getString("answer_key", ""); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(7,27,114));
        getWindow().setNavigationBarColor(Color.rgb(7,27,114));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        showEntryScreen();
        // Begin downloading the Persian offline speech model immediately on first app launch.
        // This runs independently of the microphone permission prompt and never requires a button tap.
        prepareVoskModel();

        // Ask for microphone permission on the first launch, so voice registration can work.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    STUDENT_VOICE_REQUEST
            );
        }
    }

    private void prepareVoskModel() {
        if (isFinishing() || isDestroyed()) return;
        if (modelDownloadDialog == null) {
            modelDownloadDialog = new ProgressDialog(this);
            modelDownloadDialog.setTitle("آماده‌سازی تشخیص گفتار فارسی");
            modelDownloadDialog.setMessage("در حال بررسی مدل صوتی فارسی...");
            modelDownloadDialog.setIndeterminate(true);
            modelDownloadDialog.setCancelable(false);
            modelDownloadDialog.setCanceledOnTouchOutside(false);
            modelDownloadDialog.show();
        } else if (!modelDownloadDialog.isShowing()) {
            modelDownloadDialog.setMessage("در حال بررسی مدل صوتی فارسی...");
            modelDownloadDialog.show();
        }

        PersianVosk.prepareOnFirstLaunch(this, new PersianVosk.Callback() {
            @Override public void onText(String text) {}

            @Override public void onStatus(String status) {
                if (isFinishing() || isDestroyed()) return;

                if (status.equals("مدل آفلاین فارسی آماده است.")) {
                    if (modelDownloadDialog != null && modelDownloadDialog.isShowing()) {
                        modelDownloadDialog.dismiss();
                    }
                    modelDownloadDialog = null;
                    prefs.edit().putBoolean("vosk_model_ready", true).apply();
                    return;
                }

                if (status.contains("ناموفق")) {
                    if (modelDownloadDialog != null && modelDownloadDialog.isShowing()) {
                        modelDownloadDialog.dismiss();
                    }
                    modelDownloadDialog = null;
                    prefs.edit().putBoolean("vosk_model_ready", false).apply();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("دانلود مدل گفتار فارسی انجام نشد")
                            .setMessage(status)
                            .setPositiveButton("تلاش دوباره", (dialog, which) -> prepareVoskModel())
                            .setNegativeButton("بعداً", null)
                            .show();
                    return;
                }

                if (modelDownloadDialog == null) {
                    modelDownloadDialog = new ProgressDialog(MainActivity.this);
                    modelDownloadDialog.setTitle("آماده‌سازی تشخیص گفتار فارسی");
                    modelDownloadDialog.setIndeterminate(true);
                    modelDownloadDialog.setCancelable(false);
                    modelDownloadDialog.setCanceledOnTouchOutside(false);
                    modelDownloadDialog.show();
                }
                modelDownloadDialog.setMessage(status + "\nلطفاً اینترنت را روشن نگه دار و برنامه را نبند.");
            }
        });
    }

    @Override protected void onDestroy() {
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
        }
        if (modelDownloadDialog != null && modelDownloadDialog.isShowing()) {
            modelDownloadDialog.dismiss();
        }
        modelDownloadDialog = null;
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) refreshSummary();
    }


    private void styleInput(EditText input) {
        input.setTextSize(16);
        input.setTextColor(Color.rgb(30, 41, 59));
        input.setHintTextColor(Color.rgb(125, 140, 165));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(203, 213, 235));
        input.setBackground(bg);
        input.setMinHeight(dp(48));
    }

    private void styleOption(RadioButton option, int index) {
        int[] colors = {Color.rgb(37, 99, 235), Color.rgb(13, 148, 136),
                Color.rgb(124, 58, 237), Color.rgb(219, 39, 119)};
        option.setTextColor(colors[index % colors.length]);
        option.setTextSize(16);
        option.setTypeface(null, android.graphics.Typeface.BOLD);
        option.setPadding(dp(8), dp(5), dp(12), dp(5));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(245, 248, 255));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), colors[index % colors.length]);
        option.setBackground(bg);
    }

    private void showStyledInfo(String message) {
        AlertDialog dialog = styledDialogBuilder()
                .setMessage(message)
                .setPositiveButton("باشه", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            dialog.getWindow().setBackgroundDrawable(bg);
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) {
                stylePinkOvalButton(ok);
            }
        });
        dialog.show();
    }


    private void showEntryScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(26), dp(32), dp(26), dp(32));
        page.setBackgroundColor(Color.rgb(245, 248, 255));

        TextView title = new TextView(this);
        title.setText("آزمون‌یار");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(7, 40, 130));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView subtitle = new TextView(this);
        subtitle.setText("برای ادامه یکی از روش‌های ورود را انتخاب کن");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(65, 80, 115));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(28);
        page.addView(subtitle, subtitleParams);

        Button free = new Button(this);
        free.setText("ورود رایگان");
        stylePinkOvalButton(free);
        free.setTextSize(17);
        free.setMinHeight(dp(60));
        page.addView(free, new LinearLayout.LayoutParams(-1, dp(60)));
        free.setOnClickListener(v -> {
            prefs.edit().putString("entry_mode", "free_local").apply();
            buildScreen();
        });

        Button create = new Button(this);
        create.setText("ساخت حساب");
        stylePinkOvalButton(create);
        create.setTextSize(16);
        create.setMinHeight(dp(58));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(58));
        createParams.topMargin = dp(12);
        page.addView(create, createParams);
        create.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("ساخت حساب")
                .setMessage("ورود رایگان بدون ساخت حساب فعال است و اطلاعات این نسخه روی همین دستگاه ذخیره می‌شود. ثبت‌نام اینترنتی و ورود با حساب در چند دستگاه هنوز پیاده‌سازی نشده است.")
                .setPositiveButton("باشه", null)
                .show());

        TextView note = new TextView(this);
        note.setText("ورود رایگان نیازی به نام کاربری یا رمز عبور ندارد.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(90, 105, 130));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(22);
        page.addView(note, noteParams);

        setContentView(page);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16),dp(8),dp(16),dp(12));
        root.setBackgroundColor(Color.rgb(245,248,255));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("آزمون‌یار");
        title.setTextSize(25); title.setTextColor(Color.rgb(7,40,130));
        title.setGravity(Gravity.CENTER); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setShadowLayer(dp(1), 0, dp(1), Color.rgb(210,222,250));
        root.addView(title, new LinearLayout.LayoutParams(-1,dp(42)));

        TextView subtitle = new TextView(this);
        subtitle.setText("مدیریت آزمون و کلید پاسخ — آفلاین");
        subtitle.setTextSize(16); subtitle.setTextColor(Color.rgb(65,80,115)); subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.bottomMargin=dp(8); root.addView(subtitle,sp);

        summary = new TextView(this);
        summary.setTextSize(14); summary.setTextColor(Color.rgb(30,45,80)); summary.setGravity(Gravity.CENTER);
        summary.setTypeface(null, android.graphics.Typeface.BOLD);
        summary.setPadding(dp(12),dp(10),dp(12),dp(10));
        android.graphics.drawable.GradientDrawable summaryBg = new android.graphics.drawable.GradientDrawable();
        summaryBg.setColor(Color.WHITE);
        summaryBg.setCornerRadius(dp(20));
        summaryBg.setStroke(dp(1), Color.rgb(218,228,250));
        summary.setBackground(summaryBg);
        summary.setElevation(dp(3));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1,-2);
        summaryParams.bottomMargin = dp(6);
        root.addView(summary,summaryParams);
        refreshSummary();

        // Participant registration shortcuts are intentionally hidden from the dashboard.
        // Name entry remains available in the scan-and-correct flow.

        // Keep one clear entry point per task; participant names are captured by voice during grading.
        addButtonGrid(
                new String[]{"تعریف / ویرایش آزمون", "فرم سؤال‌ها"},
                new View.OnClickListener[]{v -> showExamDialog(), v -> showQuestionBank()});
        addButtonGrid(
                new String[]{"کلید پاسخ", "نتایج تصحیح"},
                new View.OnClickListener[]{v -> showKeyDialog(), v -> showCorrectionResults()});
        addButtonGrid(
                new String[]{"آنالیز و آمار", "تنظیمات تصاویر"},
                new View.OnClickListener[]{v -> showReportsCenter(), v -> showImageRetentionDialog()});
        Button scanCorrection = new Button(this);
        scanCorrection.setText("اسکن و تصحیح");
        stylePinkOvalButton(scanCorrection);
        scanCorrection.setTextSize(18);
        scanCorrection.setMinHeight(dp(68));
        scanCorrection.setContentDescription("دکمهٔ اصلی اسکن و تصحیح پاسخنامه");
        scanCorrection.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().translationY(dp(4)).setDuration(70).start();
                    view.setElevation(dp(3));
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().translationY(0).setDuration(110).start();
                    view.setElevation(dp(12));
                    break;
            }
            return false;
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, -2);
        scanParams.topMargin = dp(6);
        scanParams.bottomMargin = dp(4);
        root.addView(scanCorrection, scanParams);
        scanCorrection.setOnClickListener(v -> showScanCorrectionEntry());


        TextView note = new TextView(this);
        note.setText("برای تصحیح پاسخنامه، کلید صحیح آزمون را ثبت کنید و از بخش نتایج، وضعیت پاسخ‌ها را بررسی کنید.");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(8); np.bottomMargin=dp(8); root.addView(note,np);
        setContentView(scroll);
    }



    private void showScanCorrectionEntry() {
        // The scan button now opens the participant QR scanner directly.
        // QR supplies the participant identity and answer-sheet model; no voice
        // registration or manual name-entry step is shown before scanning.
        prefs.edit()
                .putString("exam_identity_mode", "qr")
                .putString("pending_scan_participant", "")
                .apply();

        Intent scanIntent = new Intent(MainActivity.this, OmrScanActivity.class);
        startActivity(scanIntent);
    }

    private void addRegistrationButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(8);
        rowParams.bottomMargin = dp(10);
        root.addView(row, rowParams);

        Button voice = createGlossyShortcut(
                "🎙  ثبت‌نام صوتی\nبا گفتن نام و نام خانوادگی",
                new int[]{Color.rgb(0, 190, 255), Color.rgb(0, 92, 255), Color.rgb(0, 37, 160)},
                Color.rgb(0, 220, 255));
        Button typed = createGlossyShortcut(
                "⌨  ثبت‌نام متنی\nبا صفحه‌کلید",
                new int[]{Color.rgb(0, 230, 92), Color.rgb(0, 185, 58), Color.rgb(0, 105, 38)},
                Color.rgb(0, 255, 120));
        voice.setTextSize(14);
        typed.setTextSize(14);
        stylePinkOvalButton(voice);
        stylePinkOvalButton(typed);
        voice.setOnClickListener(v -> showAddStudentDialog(true));
        typed.setOnClickListener(v -> showAddStudentDialog(false));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(102), 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        row.addView(voice, p);
        row.addView(typed, p);
    }

    private int dashboardButtonColorIndex = 0;

    // Shared glossy dashboard style: alternate electric blue and vivid green, with a tactile press.
    private void stylePinkOvalButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        boolean blue = (dashboardButtonColorIndex++ % 2 == 0);
        int c1 = blue ? Color.rgb(0, 174, 255) : Color.rgb(0, 238, 78);
        int c2 = blue ? Color.rgb(0, 91, 255) : Color.rgb(0, 190, 55);
        int c3 = blue ? Color.rgb(0, 35, 160) : Color.rgb(0, 105, 38);
        int border = blue ? Color.rgb(0, 210, 255) : Color.rgb(0, 255, 115);
        android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c1, c2, c2, c3});
        base.setCornerRadius(dp(38));
        base.setStroke(dp(3), border);
        android.graphics.drawable.GradientDrawable shine = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(85, 255, 255, 255), Color.argb(18, 255, 255, 255),
                        Color.argb(65, 0, 20, 90)});
        shine.setCornerRadius(dp(34));
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, shine});
        layers.setLayerInset(1, dp(4), dp(4), dp(4), dp(6));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(145, 255, 255, 255)), layers, null));
        button.setElevation(dp(10));
        button.setStateListAnimator(null);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().translationY(dp(3)).setDuration(70).start();
                    view.setElevation(dp(3));
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().translationY(0).setDuration(100).start();
                    view.setElevation(dp(10));
                    break;
            }
            return false;
        });
    }

    private AlertDialog.Builder styledDialogBuilder() {
        return new AlertDialog.Builder(this) {
            @Override public AlertDialog show() {
                AlertDialog dialog = super.show();
                dialog.setOnShowListener(ignored -> {
                    int[] ids = {android.R.id.button1, android.R.id.button2, android.R.id.button3};
                    for (int id : ids) {
                        android.view.View candidate = dialog.findViewById(id);
                        if (candidate instanceof Button) {
                            Button action = (Button) candidate;
                            stylePinkOvalButton(action);
                            android.view.ViewGroup.LayoutParams old = action.getLayoutParams();
                            if (old != null) {
                                old.height = dp(48);
                                action.setLayoutParams(old);
                            }
                        }
                    }
                });
                return dialog;
            }
        };
    }

    private Button createGlossyShortcut(String label, int[] colors, int borderColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMaxLines(3);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors);
        base.setCornerRadius(dp(38));
        base.setStroke(dp(3), borderColor);
        android.graphics.drawable.GradientDrawable shine = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(85, 255, 255, 255), Color.argb(18, 255, 255, 255),
                        Color.argb(60, 0, 20, 90)});
        shine.setCornerRadius(dp(35));
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, shine});
        layers.setLayerInset(1, dp(4), dp(4), dp(4), dp(6));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(130, 255, 255, 255)), layers, null));
        button.setElevation(dp(9));
        button.setShadowLayer(dp(2), 0, dp(2), Color.argb(130, 0, 25, 70));
        return button;
    }

    private void addButtonGrid(String[] labels, View.OnClickListener[] listeners) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(2);
        rowParams.bottomMargin = dp(2);
        root.addView(row, rowParams);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button b = new Button(this);
            stylePinkOvalButton(b);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(labels[i].length() > 22 ? 12 : 13);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setTextColor(Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(dp(52));
            b.setPadding(dp(7), dp(8), dp(7), dp(8));
            b.setMaxLines(2);
            // Glossy, deeply raised electric button with layered wave ribbons like the selected reference.
            boolean useBlue = (i % 2 == 0);
            int c1 = useBlue ? Color.rgb(0, 174, 255) : Color.rgb(0, 238, 78);
            int c2 = useBlue ? Color.rgb(0, 91, 255) : Color.rgb(0, 190, 55);
            int c3 = useBlue ? Color.rgb(0, 35, 160) : Color.rgb(0, 105, 38);
            android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{c1, c2, c2, c3});
            base.setCornerRadius(dp(34));
            base.setStroke(dp(3), useBlue ? Color.rgb(0, 210, 255) : Color.rgb(0, 255, 115));
            android.graphics.drawable.GradientDrawable wave = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.argb(75,255,255,255), Color.argb(12,255,255,255), Color.argb(85,0,20,90)});
            wave.setCornerRadius(dp(34));
            android.graphics.drawable.LayerDrawable layered = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, wave});
            layered.setLayerInset(1, dp(3), dp(3), dp(3), dp(5));
            RippleDrawable waveBg = new RippleDrawable(
                    ColorStateList.valueOf(Color.argb(145,255,255,255)), layered, null);
            b.setBackground(waveBg);
            b.setTextColor(Color.WHITE);
            b.setShadowLayer(dp(2), 0, dp(2), Color.argb(150,0,25,70));
            b.setElevation(dp(14));
            b.setTranslationZ(dp(2));
            b.setOnTouchListener((view, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    view.animate().translationY(dp(3)).setDuration(70).start();
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    view.animate().translationY(0).setDuration(100).start();
                }
                return false;
            });
            b.setOnClickListener(listeners[i]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1f);
            p.leftMargin = dp(4);
            p.rightMargin = dp(4);
            row.addView(b, p);
        }
    }

    private void refreshSummary() {
        if (summary == null || prefs == null) return;
        int filled=0; String k=key();
        for(int i=0;i<Math.min(k.length(),questionCount());i++) if(k.charAt(i)>='A' && k.charAt(i)<='D') filled++;
        String name=examName().isEmpty() ? "هنوز آزمونی تعریف نشده" : "آزمون: "+examName();
        int scanned=prefs.getInt("scanned_sheet_count",0);
        int studentCount=readStudents().length();
        summary.setText(name+"\nتعداد سؤال: "+questionCount()+"\nکلید ثبت‌شده: "+filled+" از "+questionCount()+"\nشرکت‌کنندگان ثبت‌شده: "+studentCount+"\nصفحه‌های ثبت‌شده: "+scanned);
    }

    private void showExamDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(6), dp(8), dp(6));
        EditText name = new EditText(this);
        styleInput(name); name.setSingleLine(true); name.setHint("نام آزمون"); name.setText(examName());
        content.addView(name);
        EditText count = new EditText(this);
        styleInput(count); count.setSingleLine(true); count.setInputType(2);
        count.setHint("تعداد سؤال (۱ تا ۱۰۰)"); count.setText(String.valueOf(questionCount()));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(6); content.addView(count, countParams);

        TextView identityLabel = new TextView(this);
        identityLabel.setText("روش شناسایی در پاسخنامه");
        identityLabel.setTextSize(16);
        identityLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        identityLabel.setTextColor(Color.rgb(7, 40, 130));
        LinearLayout.LayoutParams identityLabelParams = new LinearLayout.LayoutParams(-1, -2);
        identityLabelParams.topMargin = dp(10);
        content.addView(identityLabel, identityLabelParams);
        RadioGroup identityMode = new RadioGroup(this);
        identityMode.setOrientation(RadioGroup.VERTICAL);
        RadioButton qrMode = new RadioButton(this);
        qrMode.setText("با QR اختصاصی شرکت‌کننده");
        qrMode.setTag("qr");
        RadioButton manualMode = new RadioButton(this);
        manualMode.setText("بدون بارکد — ورود دستی نام");
        manualMode.setTag("manual");
        identityMode.addView(qrMode);
        identityMode.addView(manualMode);
        String savedIdentityMode = prefs.getString("exam_identity_mode", "manual");
        (savedIdentityMode.equals("qr") ? qrMode : manualMode).setChecked(true);
        content.addView(identityMode);

        TextView variantLabel = new TextView(this);
        variantLabel.setText("نسخهٔ سؤال‌ها");
        variantLabel.setTextSize(16);
        variantLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        variantLabel.setTextColor(Color.rgb(7, 40, 130));
        LinearLayout.LayoutParams variantLabelParams = new LinearLayout.LayoutParams(-1, -2);
        variantLabelParams.topMargin = dp(10);
        content.addView(variantLabel, variantLabelParams);
        RadioGroup variantMode = new RadioGroup(this);
        variantMode.setOrientation(RadioGroup.VERTICAL);
        RadioButton onlyA = new RadioButton(this);
        onlyA.setText("فقط نسخهٔ A");
        onlyA.setTag("A");
        RadioButton variantsAB = new RadioButton(this);
        variantsAB.setText("دو نسخهٔ A و B (ترتیب سؤال‌ها متفاوت)");
        variantsAB.setTag("AB");
        variantMode.addView(onlyA);
        variantMode.addView(variantsAB);
        String savedVariantMode = prefs.getString("exam_variant_mode", "A");
        (savedVariantMode.equals("AB") ? variantsAB : onlyA).setChecked(true);
        content.addView(variantMode);

        Button save = new Button(this); save.setText("ذخیره"); stylePinkOvalButton(save);
        Button students = new Button(this); students.setText("ثبت‌نام شرکت‌کنندگان"); stylePinkOvalButton(students);
        Button cancel = new Button(this); cancel.setText("انصراف"); stylePinkOvalButton(cancel);
        for (Button b : new Button[]{save, students, cancel}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.topMargin = dp(8); content.addView(b, lp);
        }
        AlertDialog dialog = styledDialogBuilder().setTitle("تعریف / ویرایش آزمون")
                .setView(content).create();
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            int c;
            try { c = Integer.parseInt(count.getText().toString().trim()); }
            catch (Exception e) { info("تعداد سؤال را به‌صورت عدد وارد کن."); return; }
            if (n.isEmpty() || c < 1 || c > 100) {
                info("نام آزمون لازم است و تعداد سؤال باید بین ۱ تا ۱۰۰ باشد."); return;
            }
            String old = key(); StringBuilder nk = new StringBuilder();
            for (int i = 0; i < c; i++) nk.append(i < old.length() ? old.charAt(i) : '-');
            int checkedIdentity = identityMode.getCheckedRadioButtonId();
            if (checkedIdentity == -1) { info("روش شناسایی شرکت‌کننده را انتخاب کن."); return; }
            RadioButton identitySelected = identityMode.findViewById(checkedIdentity);
            String identityModeValue = String.valueOf(identitySelected.getTag());
            int checkedVariant = variantMode.getCheckedRadioButtonId();
            if (checkedVariant == -1) { info("نسخهٔ سؤال‌ها را انتخاب کن."); return; }
            RadioButton variantSelected = variantMode.findViewById(checkedVariant);
            String variantModeValue = String.valueOf(variantSelected.getTag());
            prefs.edit().putString("exam_name", n).putInt("question_count", c)
                    .putString("exam_identity_mode", identityModeValue)
                    .putString("exam_variant_mode", variantModeValue)
                    .putString("answer_key", nk.toString()).apply();
            refreshSummary(); dialog.dismiss(); info("آزمون روی دستگاه ذخیره شد.");
        });
        students.setOnClickListener(v -> { dialog.dismiss(); showStudentsDialog(); });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private JSONArray readStudents() {
        try {
            JSONArray students = new JSONArray(prefs.getString("students_json", "[]"));
            boolean removedIdentifiers = false;
            for (int i = 0; i < students.length(); i++) {
                JSONObject student = students.optJSONObject(i);
                if (student != null && student.has("id")) {
                    student.remove("id");
                    removedIdentifiers = true;
                }
            }
            if (removedIdentifiers) {
                prefs.edit().putString("students_json", students.toString()).apply();
            }
            return students;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void showStudentsDialog() {
        JSONArray students = readStudents();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < students.length(); i++) {
            JSONObject student = students.optJSONObject(i);
            if (student != null) list.append(i + 1).append(". ")
                    .append(student.optString("name", "بدون نام")).append("\n");
        }
        if (list.length() == 0) list.append("هنوز شرکت‌کننده‌ای ثبت نشده است.\n");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(4));
        TextView roster = new TextView(this);
        roster.setText(list.toString());
        roster.setTextSize(16);
        roster.setTextColor(Color.rgb(30, 41, 59));
        roster.setPadding(dp(8), dp(8), dp(8), dp(12));
        content.addView(roster, new LinearLayout.LayoutParams(-1, -2));

        Button voice = new Button(this);
        voice.setText("🎙 ثبت‌نام صوتی");
        voice.setAllCaps(false);
        stylePinkOvalButton(voice);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(-1, dp(58));
        voiceParams.topMargin = dp(6);
        voiceParams.bottomMargin = dp(6);
        content.addView(voice, voiceParams);
        Button typed = new Button(this);
        typed.setText("⌨ ثبت‌نام متنی");
        typed.setAllCaps(false);
        stylePinkOvalButton(typed);
        LinearLayout.LayoutParams typedParams = new LinearLayout.LayoutParams(-1, dp(58));
        typedParams.topMargin = dp(6);
        typedParams.bottomMargin = dp(6);
        content.addView(typed, typedParams);

        Button printQr = new Button(this);
        printQr.setText("▦ تولید و چاپ QR شرکت‌کنندگان");
        printQr.setAllCaps(false);
        stylePinkOvalButton(printQr);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(-1, dp(58));
        qrParams.topMargin = dp(6);
        qrParams.bottomMargin = dp(6);
        content.addView(printQr, qrParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ثبت‌نام شرکت‌کنندگان")
                .setView(content)
                .setNegativeButton("بستن", null)
                .create();
        voice.setOnClickListener(v -> { dialog.dismiss(); showAddStudentDialog(true); });
        typed.setOnClickListener(v -> { dialog.dismiss(); showAddStudentDialog(false); });
        printQr.setOnClickListener(v -> {
            dialog.dismiss();
            printParticipantQrRoster();
        });
        dialog.show();
    }

    private void printParticipantQrRoster() {
        JSONArray students = readStudents();
        if (students.length() == 0) {
            info("ابتدا شرکت‌کنندگان را ثبت‌نام کن.");
            return;
        }
        if (!"qr".equals(prefs.getString("exam_identity_mode", "manual"))) {
            info("برای چاپ QR، در تعریف آزمون روش «با QR اختصاصی شرکت‌کننده» را انتخاب و ذخیره کن.");
            return;
        }
        try {
            StringBuilder html = new StringBuilder("<html><head><meta charset='utf-8'><style>")
                    .append("@page{size:A4;margin:12mm}body{font-family:sans-serif;direction:rtl}")
                    .append(".grid{display:grid;grid-template-columns:1fr 1fr;gap:8mm}")
                    .append(".card{border:1px solid #333;padding:6mm;text-align:center;page-break-inside:avoid}")
                    .append("img{width:38mm;height:38mm}.name{font-size:15pt;font-weight:bold;margin:3mm}")
                    .append(".code{font-size:8pt;word-break:break-all}</style></head><body><h2>")
                    .append(escapeHtml(examName())).append(" — QR شرکت‌کنندگان</h2><div class='grid'>");
            for (int i = 0; i < students.length(); i++) {
                JSONObject student = students.optJSONObject(i);
                if (student == null) continue;
                String participantId = student.optString("participant_id", "").trim();
                if (participantId.isEmpty()) {
                    participantId = UUID.randomUUID().toString();
                    student.put("participant_id", participantId);
                }
                String participantName = student.optString("name", "").trim();
                JSONObject payload = new JSONObject()
                        .put("participant_id", participantId)
                        .put("participant_name", participantName)
                        .put("participant_code", student.optString("code", "").trim())
                        .put("grade_level", student.optString("grade_level", "").trim())
                        .put("group", student.optString("group", "").trim())
                        .put("parent_contact", student.optString("parent_contact", "").trim())
                        .put("exam_name", examName())
                        .put("model_id", "A")
                        .put("sheet_set_id", "roster-" + participantId);
                html.append("<div class='card'><div class='name'>")
                        .append(escapeHtml(participantName))
                        .append("</div><img alt='QR' src='")
                        .append(createQrDataUri(payload.toString()))
                        .append("'/><div class='code'>")
                        .append(escapeHtml(participantId))
                        .append("</div></div>");
            }
            html.append("</div></body></html>");
            prefs.edit().putString("students_json", students.toString()).apply();
            printHtmlDocument("Azmoonyar-Participant-QR", html.toString());
        } catch (Exception e) {
            info("تولید QR شرکت‌کنندگان ناموفق بود؛ دوباره تلاش کن.");
        }
    }

    private void showAddStudentDialog(boolean voiceMode) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(6), dp(8), dp(2));
        EditText name = new EditText(this);
        styleInput(name);
        name.setSingleLine(true);
        name.setHint("نام و نام خانوادگی شرکت‌کننده");
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        EditText studentCode = new EditText(this); styleInput(studentCode);
        studentCode.setSingleLine(true); studentCode.setHint("کد دانشجویی / کد شرکت‌کننده (اختیاری)");
        form.addView(studentCode, new LinearLayout.LayoutParams(-1, -2));
        EditText studentGroup = new EditText(this); styleInput(studentGroup);
        studentGroup.setSingleLine(true); studentGroup.setHint("کلاس / گروه / رشته (اختیاری)");
        form.addView(studentGroup, new LinearLayout.LayoutParams(-1, -2));
        EditText parentContact = new EditText(this); styleInput(parentContact);
        parentContact.setSingleLine(true); parentContact.setHint("شماره تماس خانواده یا مسئول (اختیاری)");
        parentContact.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        form.addView(parentContact, new LinearLayout.LayoutParams(-1, -2));
        TextView voiceStatus = new TextView(this);
        voiceStatus.setText(voiceMode ? "برای ثبت صوتی، نام و نام خانوادگی را واضح بگو." : "نام و نام خانوادگی را وارد کن.");
        voiceStatus.setTextSize(14);
        voiceStatus.setTextColor(Color.rgb(65, 80, 115));
        voiceStatus.setPadding(dp(4), dp(8), dp(4), dp(8));
        form.addView(voiceStatus, new LinearLayout.LayoutParams(-1, -2));        Button voice = new Button(this);
        voice.setText("🎙 گفتن نام با صدا");
        voice.setAllCaps(false);
        voice.setOnClickListener(v -> {
            activeStudentNameInput = name;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, STUDENT_VOICE_REQUEST);
                info("برای ثبت صوتی، اجازهٔ میکروفون را بده و دوباره دکمه را بزن.");
                return;
            }
            if (voiceRecognizer != null) voiceRecognizer.stop();
            voiceRecognizer = new PersianVosk();
            voiceRecognizer.start(this, new PersianVosk.Callback() {
                @Override public void onText(String text) {
                    name.setText(text);
                    voiceStatus.setText("نام با موفقیت تشخیص داده شد.");
                    if (voiceRecognizer != null) voiceRecognizer.stop();
                }
                @Override public void onStatus(String status) {
                    // Show recording progress inside the form instead of repeatedly opening dialogs.
                    if (status.startsWith("گوش می‌دهم")) {
                        voiceStatus.setText("🎙 میکروفون فعال است؛ نام را واضح بگو...");
                    } else if (status.startsWith("صدای شما دریافت شد: ")) {
                        // Partial hypotheses are often incomplete or wrong. Show them only as a preview;
                        // never overwrite the editable participant name until Vosk emits a final result.
                        String partial = status.substring("صدای شما دریافت شد: ".length()).trim();
                        voiceStatus.setText(partial.isEmpty()
                                ? "🎙 صدا دریافت می‌شود؛ ادامه بده..."
                                : "🎙 شنیده‌شده (موقت): " + partial + "\nپس از پایان گفتار، نام نهایی در کادر قرار می‌گیرد.");
                    } else if (status.startsWith("ضبط بدون دریافت گفتار")) {
                        voiceStatus.setText("صدا دریافت نشد؛ اجازهٔ میکروفون و کلید حریم خصوصی را بررسی کن.");
                        info(status);
                    } else if (status.contains("ناموفق") || status.startsWith("خطای میکروفون")
                            || status.startsWith("در حال دریافت مدل صوتی")
                            || status.startsWith("در حال اتصال برای دریافت مدل")
                            || status.startsWith("ارتباط برقرار شد")
                            || status.startsWith("دریافت مدل صوتی فارسی شروع شد")
                            || status.startsWith("مدل آفلاین فارسی آماده است")
                            || status.startsWith("مدل آفلاین فارسی آماده")
                            || status.startsWith("تلاش ")) {
                        voiceStatus.setText(status);
                    }
                }
            });
        });
        if (voiceMode) form.addView(voice, new LinearLayout.LayoutParams(-1, -2));
        else voiceStatus.setVisibility(View.GONE);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ثبت شرکت‌کننده")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String studentName = name.getText().toString().trim();
            if (studentName.isEmpty()) {
                info("نام شرکت‌کننده را وارد کن.");
                return;
            }
            JSONArray students = readStudents();
            try {
                JSONObject student = new JSONObject();
                student.put("name", studentName);
                student.put("participant_id", UUID.randomUUID().toString());
                student.put("code", studentCode.getText().toString().trim());
                student.put("group", studentGroup.getText().toString().trim());
                student.put("parent_contact", parentContact.getText().toString().trim());
                students.put(student);
                prefs.edit().putString("students_json", students.toString()).apply();
                refreshSummary();
                dialog.dismiss();
                info("شرکت‌کننده ذخیره شد.");
            } catch (Exception e) {
                info("ذخیره شرکت‌کننده انجام نشد؛ دوباره تلاش کن.");
            }
        }));
        dialog.show();
    }

    private void printPairedSheets(String participantName, String participantId) {
        printPairedSheets(participantName, participantId, 2);
    }

    private void printPairedSheets(String participantName, String participantId, int variantCount) {
        printPairedSheets(participantName, participantId, variantCount, null);
    }

    private void printPairedSheets(String participantName, String participantId, int variantCount, JSONArray preparedVariants) {
        try {
            JSONArray bank = readQuestionBank();
            if (bank.length() < questionCount()) {
                info("برای چاپ نسخه‌دار، ابتدا به تعداد سؤال‌های آزمون سؤال در بانک سؤال ثبت کن.");
                return;
            }
            JSONArray variants = preparedVariants == null
                    ? getOrCreateExamVariants(bank, variantCount) : preparedVariants;
            if (variants.length() != variantCount) {
                info("تعداد نسخه‌های آماده با تعداد درخواستی یکسان نیست؛ دوباره نسخه‌ها را بساز.");
                return;
            }
            final int total = questionCount();
            final int printLayoutQuestionCount = 100;
            final int pageCount = 1;
            String encodedExamName = Base64.encodeToString(examName().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_WRAP);
            String safeExam = escapeHtml(examName());
            String batchId = UUID.randomUUID().toString();
            JSONArray batchVariants = new JSONArray();
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='utf-8'><style>")
                .append("@page{size:A4 portrait;margin:0}*{box-sizing:border-box}")
                .append("body{font-family:sans-serif;color:#111;margin:0;direction:rtl}")
                .append(".page{width:210mm;height:297mm;padding:10mm 12mm;position:relative;page-break-after:always;overflow:hidden}")
                .append(".page:last-child{page-break-after:auto}.head{font-size:17px;font-weight:bold;margin:0 0 3mm}")
                .append(".meta{font-size:10px;margin:2mm 0}.clear{clear:both}")
                .append(".instructions{font-size:9px;border:1px solid #333;padding:2mm;margin:2mm 0}")
                .append(".question{font-size:10pt;line-height:1.25;margin:0 0 4mm;padding:0 1mm;page-break-inside:avoid;break-inside:avoid}")
                .append(".options{display:grid;grid-template-columns:1fr 1fr;gap:1.5mm;margin:1.5mm 5mm 0;font-size:9pt;line-height:1.2;direction:ltr;text-align:left}")
                .append(".cols{display:grid;grid-template-columns:1fr 1fr;gap:4mm;margin-top:3mm}")
                .append(".col{border:0.3mm solid #111;padding:2mm 1.5mm;border-radius:1mm}")
                .append("table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:10px}")
                .append("th,td{border:0.25mm solid #555;text-align:center;height:7mm;padding:0}")
                .append(".num{font-weight:bold;width:19%}.bubble{display:inline-block;width:3.6mm;height:2.7mm;border:0.35mm solid #111;border-radius:50%}")
                .append(".mark{position:absolute;width:16mm;height:16mm;background:#000;border:0;display:block;z-index:2;pointer-events:none}.tl{left:5mm;top:5mm}.tr{right:5mm;top:5mm}.bl{left:5mm;bottom:5mm}.br{right:5mm;bottom:5mm}.small-mark{position:absolute;width:3mm;height:3mm;background:#000;z-index:2;pointer-events:none}.stl{left:23mm;top:5mm}.str{right:23mm;top:5mm}.sbl{left:23mm;bottom:5mm}.sbr{right:23mm;bottom:5mm}")
                .append(".answer-page{padding:0}.answer-page .head{position:absolute;top:10mm;left:12mm;right:12mm;height:8mm;text-align:center;font-size:17px;margin:0}")
                .append(".answer-page .sub{position:absolute;top:19mm;left:12mm;right:12mm;height:5mm;text-align:center;font-size:9px;margin:0}")
                .append(".answer-page .meta{position:absolute;top:25mm;left:12mm;right:12mm;height:6mm;font-size:10px;margin:0;display:flex;justify-content:space-between;align-items:center;gap:8mm;white-space:nowrap}")
                .append(".answer-page .instructions{position:absolute;top:31mm;left:12mm;right:12mm;height:12mm;font-size:9px;border:0.3mm solid #777;padding:2mm;margin:0;overflow:hidden}")
                .append(".answer-page .cols{position:absolute;top:44mm;left:12mm;right:12mm;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:3mm;margin:0;direction:ltr}")
                .append(".answer-page .col{border:0.25mm solid #555;border-radius:0;padding:1mm 1mm;min-width:0;direction:ltr}")
                .append(".answer-page table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:8px;direction:ltr}")
                .append(".answer-page th{font-size:8.5px;height:7mm;border:0;border-bottom:0.3mm solid #333;padding:0;direction:ltr}")
                .append(".answer-page tbody tr{height:8.3mm}.answer-page td{height:8.3mm;min-height:8.3mm;text-align:center;padding:0;border:0.25mm solid #999;font-size:8.5px;direction:ltr}.answer-page .bubble{display:inline-block;width:4.2mm;height:3.2mm;border:0.35mm solid #D9D9D9;border-radius:50%;box-sizing:border-box;vertical-align:middle}")
                .append(".answer-page td.num{width:7mm;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".answer-page td.opt{width:auto;padding:0 0.5mm}")

                .append("</style></head><body>");

            for (int v = 0; v < variants.length(); v++) {
                JSONObject variant = variants.optJSONObject(v);
                if (variant == null) continue;
                String modelId = String.valueOf((char)('A' + v));
                String sheetSetId = UUID.randomUUID().toString();
                JSONArray questions = variant.optJSONArray("questions");
                String answerKey = variant.optString("answer_key", "");
                if (questions == null || questions.length() < total || answerKey.length() < total) {
                    info("ساخت مدل آزمون کامل نبود؛ چاپ متوقف شد.");
                    return;
                }
                String validationError = validateVariantForPrinting(questions, answerKey, total);
                if (validationError != null) {
                    info("چاپ برای جلوگیری از تولید پاسخنامهٔ ناسازگار متوقف شد: " + validationError);
                    return;
                }
                // Print question text on its own pages (25 questions per page).
                // belong only on answer sheets, so scanning a question page cannot be counted
                // as a scanned answer sheet.
                final int questionsPerPage = 10;
                final int questionPageCount = Math.max(1, (total + questionsPerPage - 1) / questionsPerPage);
                for (int questionPage = 1; questionPage <= questionPageCount; questionPage++) {
                    int questionFirst = (questionPage - 1) * questionsPerPage;
                    int questionLast = Math.min(questionPage * questionsPerPage, total);
                    html.append("<section class='page'><h1 class='head'>").append(safeExam)
                        .append(" — برگه سؤال‌ها</h1>")
                        .append("<div class='meta'>صفحه ").append(questionPage).append(" از ")
                        .append(questionPageCount).append(" | سؤال‌های ").append(questionFirst + 1)
                        .append(" تا ").append(questionLast).append("</div><hr/>");
                    for (int i = questionFirst; i < questionLast; i++) {
                        JSONObject q = i < questions.length() ? questions.optJSONObject(i) : null;
                        html.append("<article class='question'><b>").append(i + 1).append(". ")
                            .append(q == null ? "........................................................" : escapeHtml(q.optString("text", "")))
                            .append("</b><div class='options'>");
                        JSONArray options = q == null ? null : q.optJSONArray("options");
                        if (options != null) {
                            for (int j = 0; j < Math.min(4, options.length()); j++)
                                html.append("<div>").append((char)('A' + j)).append(") ")
                                    .append(escapeHtml(options.optString(j, ""))).append("</div>");
                        } else {
                            for (int j = 0; j < 4; j++) html.append("<div>").append((char)('A' + j)).append(") ........</div>");
                        }
                        html.append("</div></article>");
                    }
                    html.append("</section>");
                }

                JSONArray registeredStudents = readStudents();
                boolean printRegisteredRoster = registeredStudents.length() > 0;
                int rosterCount = printRegisteredRoster ? registeredStudents.length() : 1;
                for (int rosterIndex = 0; rosterIndex < rosterCount; rosterIndex++) {
                    if (printRegisteredRoster && rosterIndex % variants.length() != v) continue;
                    JSONObject rosterStudent = printRegisteredRoster
                            ? registeredStudents.optJSONObject(rosterIndex) : null;
                    String sheetParticipantName = rosterStudent == null
                            ? participantName : rosterStudent.optString("name", participantName);
                    String sheetParticipantId = rosterStudent == null
                            ? participantId : rosterStudent.optString("participant_id", participantId);
                    if (sheetParticipantId.trim().isEmpty()) sheetParticipantId = UUID.randomUUID().toString();
                    String qrPayload = new JSONObject()
                            .put("participant_id", sheetParticipantId)
                            .put("participant_name", sheetParticipantName)
                            .put("participant_code", rosterStudent == null ? "" : rosterStudent.optString("code", "").trim())
                            .put("grade_level", rosterStudent == null ? "" : rosterStudent.optString("grade_level", "").trim())
                            .put("group", rosterStudent == null ? "" : rosterStudent.optString("group", "").trim())
                            .put("parent_contact", rosterStudent == null ? "" : rosterStudent.optString("parent_contact", "").trim())
                            .put("exam_name", examName())
                            .put("model_id", modelId)
                            .put("sheet_set_id", sheetSetId).toString();
                    String qrDataUri = createQrDataUri(qrPayload);
                    for (int page = 1; page <= pageCount; page++) {
                    int first = (page - 1) * 100;
                    int last = printLayoutQuestionCount;
                    html.append("<section class='page answer-page'><span class='mark tl'></span><span class='mark tr'></span>")
                        .append("<span class='mark bl'></span><span class='mark br'></span>")
                        .append("<span class='small-mark stl'></span><span class='small-mark str'></span>")
                        .append("<span class='small-mark sbl'></span><span class='small-mark sbr'></span>")
                        // Keep the upper answer-sheet area blank for barcode placement.
                        .append("<div style='position:absolute;top:24mm;left:12mm;font-size:11px;font-weight:bold'>نام دانش‌آموز: ").append(escapeHtml(sheetParticipantName)).append(" | نسخه ").append(modelId).append("</div>")
                        .append("<img alt='QR' style='position:absolute;top:10mm;right:12mm;width:22mm;height:22mm' src='").append(qrDataUri).append("'/>")
                        // Omit the template caption to preserve barcode space above the answer grid.
                        .append("<div class='meta'><span>آزمون: ").append(safeExam).append("</span><span>نام دانش‌آموز: ").append(escapeHtml(sheetParticipantName)).append("</span><span>نسخه: ").append(modelId).append("</span><span>سؤال‌های ").append(first + 1).append(" تا ").append(last).append("</span></div>")
                        .append("<div class='instructions'>برای هر سؤال فقط یک حباب را کاملاً پر کنید. شماره سؤال‌ها بین برگه‌ها پیوسته است؛ از پاک‌کن تمیز استفاده کنید.</div><div class='cols'>");
                    for (int col = 0; col < 4; col++) {
                        int colFirst = first + col * 25;
                        int colLast = Math.min(first + (col + 1) * 25, last);
                        if (colFirst >= last) continue;
                        html.append("<div class='col'><table><colgroup><col style='width:7mm'><col style='width:8.6875mm'><col style='width:8.6875mm'><col style='width:8.6875mm'><col style='width:8.6875mm'></colgroup><thead><tr><th>سؤال</th><th>A</th><th>B</th><th>C</th><th>D</th></tr></thead><tbody>");
                        for (int q = colFirst; q < colLast; q++) {
                            html.append("<tr><td class='num'>").append(q + 1).append("</td>");
                            for (int option = 0; option < 4; option++) html.append("<td class='opt'><span class='bubble'></span></td>");
                            html.append("</tr>");
                        }
                        html.append("</tbody></table></div>");
                    }
                    html.append("</div></section>");
                    }
                }
                // Save an immutable grading snapshot for this exact printed batch and model.
                // Save the exact answer key and question order for this printed batch/model.
                JSONObject issuedExam = new JSONObject();
                issuedExam.put("schema_version", 1);
                issuedExam.put("exam_name", examName());
                issuedExam.put("sheet_set_id", sheetSetId);
                issuedExam.put("model_id", modelId);
                issuedExam.put("template_id", "T1");
                issuedExam.put("question_count", total);
                issuedExam.put("answer_key", answerKey.substring(0, total));
                issuedExam.put("questions", questions);
                issuedExam.put("created_at", System.currentTimeMillis());
                // Persist the original bank key alongside this immutable printed variant.
                String originalKey = buildOriginalBankKey();
                issuedExam.put("original_answer_key", originalKey);
                batchVariants.put(issuedExam);
                prefs.edit()
                    .putString("issued_exam_" + sheetSetId + "_" + modelId, issuedExam.toString())
                    .putString("saved_variant_" + sheetSetId, issuedExam.toString())
                    .putString("answer_key_original_snapshot", originalKey)
                    .putString("answer_key_model_" + modelId, answerKey.substring(0, total))
                    .putString("model_questions_" + modelId, questions.toString())
                    .putInt("issued_model_count", variants.length())
                    .apply();
            }
            html.append("</body></html>");
            JSONObject savedBatch = new JSONObject();
            savedBatch.put("batch_id", batchId);
            savedBatch.put("exam_name", examName());
            savedBatch.put("variant_count", variants.length());
            savedBatch.put("created_at", System.currentTimeMillis());
            savedBatch.put("print_html", html.toString());
            savedBatch.put("variants", batchVariants);
            JSONArray savedBatches = readSavedExamBatches();
            savedBatches.put(savedBatch);
            prefs.edit().putString("saved_exam_batches_json", savedBatches.toString()).apply();
            printHtmlDocument("AzmoonYar-" + examName(), html.toString());
        } catch (Exception e) {
            info("ساخت برگه برای چاپ ناموفق بود؛ بانک سؤال و مجوز چاپ را بررسی کن.");
        }
    }

    /**
     * Fail closed before printing: the printed bubbles, question list and answer key must
     * describe the same complete model. This is especially important for saved/original
     * variants, where a plausible-looking but mismatched key would silently corrupt grading.
     */
    private String validateVariantForPrinting(JSONArray questions, String answerKey, int total) {
        if (questions == null || total < 1 || questions.length() < total || answerKey == null
                || answerKey.length() < total) {
            return "تعداد سؤال‌ها و طول کلید پاسخ برابر نیست.";
        }
        for (int i = 0; i < total; i++) {
            JSONObject question = questions.optJSONObject(i);
            if (question == null || question.optString("text", "").trim().isEmpty()) {
                return "متن سؤال " + (i + 1) + " خالی یا نامعتبر است.";
            }
            JSONArray options = question.optJSONArray("options");
            if (options == null || options.length() != 4) {
                return "سؤال " + (i + 1) + " باید دقیقاً چهار گزینه داشته باشد.";
            }
            for (int option = 0; option < 4; option++) {
                if (options.optString(option, "").trim().isEmpty()) {
                    return "گزینهٔ " + (char)('A' + option) + " سؤال " + (i + 1) + " خالی است.";
                }
            }
            char keyLetter = Character.toUpperCase(answerKey.charAt(i));
            if (keyLetter < 'A' || keyLetter > 'D') {
                return "کلید سؤال " + (i + 1) + " پاسخ معتبر A تا D ندارد.";
            }
            if (question.has("correct_index")) {
                int correctIndex = question.optInt("correct_index", -1);
                if (correctIndex < 0 || correctIndex > 3
                        || keyLetter != (char)('A' + correctIndex)) {
                    return "کلید سؤال " + (i + 1) + " با گزینهٔ صحیح ذخیره‌شده تطبیق ندارد.";
                }
            }
        }
        return null;
    }

    private JSONArray getOrCreateExamVariants(JSONArray bank, int count) throws Exception {
        // Every explicit print/build action creates a fresh randomized set.
        // Immutable keys are saved per sheetSetId inside printPairedSheets.
        long seed = System.nanoTime() ^ UUID.randomUUID().getMostSignificantBits();
        return RandomExamBuilder.generateVariants(bank, count, seed);
    }

    private String createQrDataUri(String payload) throws Exception {
        java.util.EnumMap<com.google.zxing.EncodeHintType, Object> qrHints =
                new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
        qrHints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
        com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                .encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 240, 240, qrHints);
        Bitmap bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 240; y++) {
            for (int x = 0; x < 240; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Print only the question paper, paginated at 25 complete questions per A4 page. */
    private void printQuestionnaireOnly() {
        if (examName().trim().isEmpty()) { info("ابتدا آزمون را تعریف کن."); return; }
        JSONArray bank = readQuestionBank();
        final int total = questionCount();
        if (total < 1 || bank.length() < total) {
            info("ابتدا بانک سؤال را تا تعداد سؤال‌های آزمون کامل کن.");
            return;
        }
        StringBuilder html = new StringBuilder();
        String safeExam = escapeHtml(examName());
        html.append("<html><head><meta charset='utf-8'><style>")
            .append("@page{size:A4 portrait;margin:0}*{box-sizing:border-box}")
            .append("body{margin:0;font-family:sans-serif;color:#111;direction:rtl}")
            .append(".page{width:210mm;height:297mm;padding:14mm 14mm 12mm;page-break-after:always;overflow:hidden}")
            .append(".page:last-child{page-break-after:auto}.head{text-align:center;font-size:18px;margin:0 0 5mm}")
            .append(".meta{font-size:10pt;margin-bottom:5mm;border-bottom:1px solid #888;padding-bottom:3mm}")
            .append(".q{font-size:12pt;margin:0 0 5mm;break-inside:avoid;page-break-inside:avoid}")
            .append(".opts{display:grid;grid-template-columns:1fr 1fr;gap:2mm 6mm;direction:rtl;font-size:11pt;margin:2mm 5mm 0}")
            .append("</style></head><body>");
        int perPage = 25;
        int pages = (total + perPage - 1) / perPage;
        for (int page=0; page<pages; page++) {
            int first = page * perPage;
            int last = Math.min(total, first + perPage);
            html.append("<section class='page'><h1 class='head'>").append(safeExam).append(" — پرسشنامه</h1>")
                .append("<div class='meta'>صفحه ").append(page+1).append(" از ").append(pages)
                .append(" | سؤال‌های ").append(first+1).append(" تا ").append(last).append("</div>");
            for (int i=first; i<last; i++) {
                JSONObject q=bank.optJSONObject(i);
                if (q==null || q.optString("text","").trim().isEmpty()) {
                    info("متن سؤال " + (i+1) + " خالی است؛ چاپ انجام نشد."); return;
                }
                JSONArray options=q.optJSONArray("options");
                if(options==null || options.length()!=4) {
                    info("سؤال " + (i+1) + " باید چهار گزینهٔ کامل داشته باشد؛ چاپ انجام نشد."); return;
                }
                html.append("<article class='q'><b>").append(i+1).append(". ")
                    .append(escapeHtml(q.optString("text",""))).append("</b>");
                String imageData = q.optString("image_data", "");
                if (imageData.startsWith("data:image/")) {
                    html.append("<div style='text-align:center;margin:2mm 0'><img style='max-width:100%;max-height:85mm;object-fit:contain' src='")
                        .append(imageData).append("'></div>");
                }
                html.append("<div class='opts'>");
                for(int j=0;j<4;j++) {
                    String option=options.optString(j,"").trim();
                    if(option.isEmpty()) { info("گزینهٔ " + (char)('A'+j) + " سؤال " + (i+1) + " خالی است."); return; }
                    html.append("<div>").append((char)('A'+j)).append(") ").append(escapeHtml(option)).append("</div>");
                }
                html.append("</div></article>");
            }
            html.append("</section>");
        }
        html.append("</body></html>");
        printHtmlDocument("AzmoonYar-Questionnaire-" + examName(), html.toString());
    }

    /** Prints the standard OMR sheet as one A4 page per 100 questions. */
    private void printStandardAnswerSheets() {
        JSONArray roster = readStudents();
        if (roster.length() == 0) { info("ابتدا شرکت‌کنندگان را ثبت کن تا نام و QR روی پاسخنامه چاپ شود."); return; }
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(8), dp(4), dp(8), dp(4));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        CheckBox[] checks = new CheckBox[roster.length()];
        for (int i = 0; i < roster.length(); i++) {
            JSONObject student = roster.optJSONObject(i);
            checks[i] = new CheckBox(this);
            checks[i].setText(student == null ? "بدون نام" : student.optString("name", "بدون نام"));
            checks[i].setTextSize(16);
            list.addView(checks[i]);
        }
        scroll.addView(list);
        choices.addView(scroll, new LinearLayout.LayoutParams(-1, dp(360)));
        Button all = new Button(this); all.setText("انتخاب همه / لغو انتخاب همه");
        styleTurquoiseDashboardButton(all);
        all.setOnClickListener(v -> {
            boolean select = false;
            for (CheckBox check : checks) if (!check.isChecked()) { select = true; break; }
            for (CheckBox check : checks) check.setChecked(select);
        });
        choices.addView(all);
        new AlertDialog.Builder(this).setTitle("انتخاب شرکت‌کنندگان برای چاپ پاسخنامه")
            .setMessage("شرکت‌کنندگان را تیک بزن. نام و QR اختصاصی هر نفر روی پاسخنامه چاپ می‌شود.")
            .setView(choices).setNegativeButton("لغو", null)
            .setPositiveButton("چاپ پاسخنامه‌ها", (dialog, which) -> {
                JSONArray selected = new JSONArray();
                for (int i = 0; i < checks.length; i++) if (checks[i].isChecked()) {
                    JSONObject student = roster.optJSONObject(i);
                    if (student != null) selected.put(student);
                }
                if (selected.length() == 0) { info("حداقل یک شرکت‌کننده را انتخاب کن."); return; }
                printStandardAnswerSheetsSelected(selected);
            }).show();
    }

    private void printStandardAnswerSheetsSelected(JSONArray selectedStudents) {
        if (examName().trim().isEmpty()) {
            info("ابتدا آزمون را تعریف کن.");
            return;
        }
        // The printable OMR master is always a fixed 100-question A4 sheet,
        // independent of the current exam's configured question count.
        final int total = 100;
        try {
            String safeExam = escapeHtml(examName());
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='utf-8'><style>")
                .append("@page{size:A4 portrait;margin:0}*{box-sizing:border-box}")
                .append("body{margin:0;font-family:sans-serif;color:#111;direction:rtl}")
                .append(".sheet{width:210mm;height:297mm;padding:10mm 12mm 9mm;position:relative;page-break-after:always;overflow:hidden}")
                .append(".sheet:last-child{page-break-after:auto}")
                .append(".head{position:absolute;top:10mm;left:12mm;right:12mm;height:8mm;text-align:center;font-size:17px;font-weight:bold;margin:0}")
                .append(".sub{position:absolute;top:19mm;left:12mm;right:12mm;height:5mm;text-align:center;font-size:9px;margin:0}")
                .append(".student{position:absolute;top:8mm;right:28mm;left:45mm;font-size:14px;font-weight:bold;text-align:right;height:12mm}.qr{position:absolute;top:7mm;left:28mm;width:20mm;height:20mm}.meta{position:absolute;top:25mm;left:12mm;right:12mm;height:5mm;font-size:10px;margin:0;display:flex;justify-content:space-between}")
                .append(".instruction{position:absolute;top:31mm;left:12mm;right:12mm;height:12mm;font-size:9px;border:0.3mm solid #777;padding:2mm;margin:0;overflow:hidden}")
                .append(".cols{position:absolute;top:44mm;left:12mm;right:12mm;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:3mm;direction:ltr;outline:0.45mm solid #111;outline-offset:1mm}")
                .append(".col{border:0.25mm solid #555;border-radius:0;padding:1mm;min-width:0;direction:ltr}")
                .append("table{width:100%;border-collapse:collapse;table-layout:fixed}")
                .append("th{font-size:8px;height:7mm;border:0;border-bottom:0.3mm solid #333;padding:0;direction:ltr}")
                .append("td{height:8.3mm;min-height:8.3mm;text-align:center;padding:0;border:0.2mm solid #c4c4c4;font-size:8.5px;direction:ltr}")
                .append("td.num{width:7mm;font-weight:bold;text-align:center}")
                .append("td.opt{width:auto}")
                .append(".bubble{display:inline-block;width:5.2mm;height:4.1mm;border:0.4mm solid #111;border-radius:50%;vertical-align:middle}")
                .append("/* Solid vector registration markers; keep the existing page and bubble layout unchanged. */")
                .append(".marks{position:absolute;width:16mm;height:16mm;background:#000;border:0;display:block;z-index:2;pointer-events:none}")
                .append(".tl{left:5mm;top:5mm}.tr{right:5mm;top:5mm}.bl{left:5mm;bottom:5mm}.br{right:5mm;bottom:5mm}")
                .append(".small-mark{position:absolute;width:3mm;height:3mm;background:#000;z-index:2;pointer-events:none}.stl{left:23mm;top:5mm}.str{right:23mm;top:5mm}.sbl{left:23mm;bottom:5mm}.sbr{right:23mm;bottom:5mm}")
                .append("</style></head><body>");
            int pages = (total + 99) / 100;
            for (int studentIndex = 0; studentIndex < selectedStudents.length(); studentIndex++) {
                JSONObject student = selectedStudents.optJSONObject(studentIndex);
                if (student == null) continue;
                String studentName = student.optString("name", "بدون نام");
                String participantId = student.optString("participant_id", "");
                if (participantId.isEmpty()) { participantId = UUID.randomUUID().toString(); student.put("participant_id", participantId); }
                JSONObject qrPayload = new JSONObject();
                qrPayload.put("participant_id", participantId);
                qrPayload.put("participant_name", studentName);
                qrPayload.put("exam_name", examName());
                qrPayload.put("model_id", "A");
                qrPayload.put("sheet_set_id", "roster-" + participantId);
                String qrData = createQrDataUri(qrPayload.toString());
                for (int page = 0; page < pages; page++) {
                int first = page * 100 + 1;
                int last = Math.min(total, first + 99);
                html.append("<section class='sheet'>")
                    .append("<i class='marks tl'></i><i class='marks tr'></i><i class='marks bl'></i><i class='marks br'></i>")
                    .append("<i class='small-mark stl'></i><i class='small-mark str'></i><i class='small-mark sbl'></i><i class='small-mark sbr'></i>")
                    // Keep the top of the OMR master blank for barcode placement.
                    // Omit the template caption to preserve the blank barcode area.
                    .append("<div class='student'>نام و نام خانوادگی: ").append(escapeHtml(studentName)).append("</div><img class='qr' src='").append(qrData).append("'/><div class='meta'><span>آزمون: ").append(safeExam).append("</span><span>سؤال‌های ").append(first).append(" تا ").append(last).append("</span></div>")
                    .append("<div class='instruction'>برای هر سؤال فقط یک حباب را کاملاً پر کنید. شماره سؤال‌ها بین برگه‌ها پیوسته است؛ از پاک‌کن تمیز استفاده کنید.</div>")
                    .append("<div class='cols'>");
                for (int col = 0; col < 4; col++) {
                    int colFirst = first + col * 25;
                    int colLast = Math.min(last, colFirst + 24);
                    html.append("<div class='col'><table><colgroup><col style='width:7mm'><col style='width:8.6875mm'><col style='width:8.6875mm'><col style='width:8.6875mm'><col style='width:8.6875mm'></colgroup><thead><tr><th>Question</th><th>A</th><th>B</th><th>C</th><th>D</th></tr></thead><tbody>");
                    for (int q = colFirst; q <= colLast; q++) {
                        html.append("<tr><td class='num'>").append(q).append("</td>");
                        for (int option = 0; option < 4; option++) html.append("<td class='opt'><span class='bubble'></span></td>");
                        html.append("</tr>");
                    }
                    html.append("</tbody></table></div>");
                }
                html.append("</div></section>");
                }
            }
            html.append("</body></html>");
            WebView web = new WebView(this);
            web.getSettings().setDefaultTextEncodingName("UTF-8");
            web.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (pm != null) {
                        pm.print("AzmoonYar-OMR-" + total,
                            view.createPrintDocumentAdapter("AzmoonYar-OMR-" + total),
                            new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                // The OMR template uses absolute A4 millimeter coordinates.
                                // Do not let Android's print framework add default margins
                                // and scale the sheet into a smaller printable rectangle.
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                                .build());                    } else {
                        info("قابلیت چاپ در این دستگاه در دسترس نیست.");
                    }
                }
            });
        } catch (Exception e) {
            info("ساخت پاسخنامه برای چاپ ناموفق بود؛ دوباره تلاش کن.");
        }
    }

    private void printTeacherAnswerKey() {
        if (examName().trim().isEmpty()) {
            info("ابتدا آزمون را تعریف کن.");
            return;
        }
        JSONArray bank = readQuestionBank();
        if (bank.length() < questionCount()) {
            info("برای ساخت کلید مدل‌ها، ابتدا بانک سؤال را کامل کن.");
            return;
        }
        try {
            JSONArray variants = getOrCreateExamVariants(bank, 5);
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='utf-8'><style>")
                .append("@page{size:A4 portrait;margin:12mm}body{font-family:sans-serif;color:#111;direction:rtl}")
                .append("h1{text-align:center;font-size:18px}h2{font-size:14px;margin:7mm 0 2mm}")
                .append("p{font-size:10px}table{border-collapse:collapse;width:100%;table-layout:fixed;margin-bottom:5mm}")
                .append("th,td{border:0.3mm solid #777;text-align:center;height:7mm;font-size:10px}")
                .append(".num{width:15%;white-space:nowrap}.bubble{display:inline-block;width:4.3mm;height:3.4mm;border:0.4mm solid #111;border-radius:50%;vertical-align:middle}")
                .append(".correct{background:#111}.note{border:1px solid #777;padding:2mm;font-size:10px}")
                .append("</style></head><body><h1>کلیدهای پاسخ محرمانه — مسئول آزمون</h1><p>")
                .append(escapeHtml(examName())).append(" | فقط برای مسئول آزمون؛ در اختیار شرکت‌کنندگان قرار نگیرد.</p>");
            for (int v = 0; v < variants.length(); v++) {
                JSONObject variant = variants.optJSONObject(v);
                if (variant == null) continue;
                String modelId = String.valueOf((char)('A' + v));
                JSONArray questions = variant.optJSONArray("questions");
                if (questions == null || questions.length() < questionCount()) {
                    info("کلید یکی از مدل‌ها کامل نیست؛ ابتدا مدل‌ها را دوباره بساز.");
                    return;
                }
                String answerKey = variant.optString("answer_key", "");
                html.append("<h2>کلید داخلی مدل ").append(modelId).append("</h2><table><thead><tr><th class='num'>شماره</th><th>A</th><th>B</th><th>C</th><th>D</th></tr></thead><tbody>");
                for (int q = 0; q < questionCount(); q++) {
                    char answer = q < answerKey.length() ? answerKey.charAt(q) : '-';
                    html.append("<tr><td>").append(q + 1).append("</td>");
                    for (int option = 0; option < 4; option++) {
                        html.append("<td><span class='bubble");
                        if (answer == (char)('A' + option)) html.append(" correct");
                        html.append("'></span></td>");
                    }
                    html.append("</tr>");
                }
                html.append("</tbody></table>");
            }
            html.append("<div class='note'>حباب سیاه گزینهٔ صحیح است. این برگه محرمانه است و نباید همراه برگه‌های شرکت‌کنندگان توزیع شود.</div></body></html>");
            WebView web = new WebView(this);
            web.getSettings().setDefaultTextEncodingName("UTF-8");
            web.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (pm != null) pm.print("AzmoonYar-Teacher-Variant-Keys",
                        view.createPrintDocumentAdapter("AzmoonYar-Teacher-Variant-Keys"),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
                    else info("قابلیت چاپ در این دستگاه در دسترس نیست.");
                }
            });
        } catch (Exception e) {
            info("ساخت کلیدهای مدل‌ها ناموفق بود؛ بانک سؤال را بررسی کن.");
        }
    }

    private void showKeyDialog() {
        if (examName().isEmpty()) { info("ابتدا یک آزمون تعریف کن."); return; }
        String storedKey = prefs.getString("answer_key", "");
        String value = storedKey.matches("[A-D]{"+questionCount()+"}") ? storedKey : buildOriginalBankKey();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = new TextView(this);
        heading.setText("کلید اصلی پاسخ — " + examName());
        heading.setTextSize(18);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.rgb(7, 40, 130));
        form.addView(heading);

        TextView note = new TextView(this);
        note.setText("کلید اصلی از پاسخ‌های ذخیره‌شده یا بانک سؤال نمایش داده می‌شود. اگر کلید را از تصویر وارد کرده‌ای، پاسخ‌ها در این قسمت قابل مشاهده‌اند.");
        note.setTextSize(14);
        note.setPadding(0, dp(8), 0, dp(8));
        form.addView(note);

        int filled = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) >= 'A' && value.charAt(i) <= 'D') filled++;
        }
        TextView completion = new TextView(this);
        completion.setText("پاسخ‌های ثبت‌شده: " + filled + " از " + questionCount());
        completion.setTextSize(14);
        completion.setTypeface(null, android.graphics.Typeface.BOLD);
        completion.setTextColor(Color.rgb(15, 118, 110));
        completion.setPadding(0, dp(8), 0, dp(8));
        form.addView(completion);

        LinearLayout answers = new LinearLayout(this);
        answers.setOrientation(LinearLayout.VERTICAL);
        String formatted = formatAnswerKey(value, questionCount());
        String[] lines = formatted.split("\\n");
        for (String line : lines) {
            String item = line.trim();
            if (item.isEmpty() || item.startsWith("تکمیل:")) continue;
            TextView answer = new TextView(this);
            answer.setText(item);
            answer.setTextSize(16);
            answer.setTypeface(null, android.graphics.Typeface.BOLD);
            answer.setTextColor(Color.rgb(157, 23, 77));
            answer.setGravity(Gravity.CENTER);
            answer.setClickable(false);
            answer.setFocusable(false);
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setColor(Color.rgb(253, 242, 248));
            pill.setCornerRadius(dp(28));
            pill.setStroke(dp(1), Color.rgb(236, 72, 153));
            answer.setBackground(pill);
            LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(-1, dp(44));
            answerLp.setMargins(0, dp(3), 0, dp(3));
            answers.addView(answer, answerLp);
        }
        ScrollView answerScroll = new ScrollView(this);
        answerScroll.setFillViewport(false);
        answerScroll.addView(answers);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(230));
        form.addView(answerScroll, scrollLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("کلید پاسخ اصلی")
            .setView(form)
            .create();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, dp(10), 0, dp(6));
        Button editKey = new Button(this);
        editKey.setText("ویرایش پاسخ‌های کلید");
        styleTurquoiseDashboardButton(editKey);
        Button printSave = new Button(this);
        printSave.setText("چاپ / ذخیره کلید");
        styleTurquoiseDashboardButton(printSave);
        Button loadImage = new Button(this);
        loadImage.setText("بارگذاری تصویر کلید");
        styleTurquoiseDashboardButton(loadImage);
        Button close = new Button(this);
        close.setText("بازگشت / خروج");
        styleTurquoiseDashboardButton(close);
        for (Button button : new Button[]{editKey, printSave, loadImage, close}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.setMargins(0, dp(3), 0, dp(3));
            actions.addView(button, lp);
        }
        form.addView(actions);
        editKey.setOnClickListener(v -> { dialog.dismiss(); showAnswerKeyEditor(value); });
        printSave.setOnClickListener(v -> { dialog.dismiss(); showKeyOutputChoices("main"); });
        loadImage.setOnClickListener(v -> { dialog.dismiss(); pendingKeyTarget = "main"; pickAnswerKeyImage(); });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAnswerKeyEditor(String currentKey) {
        int count = questionCount();
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < count; i++) {
            char c = i < currentKey.length() ? Character.toUpperCase(currentKey.charAt(i)) : '-';
            normalized.append(c >= 'A' && c <= 'D' ? c : '-');
        }
        final char[] edited = normalized.toString().toCharArray();
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(12), dp(8), dp(12), dp(8));
        for (int i = 0; i < count; i++) {
            final int index = i;
            Button answer = new Button(this);
            answer.setAllCaps(false);
            answer.setText("سؤال " + (i + 1) + " : " + (edited[i] == '-' ? "ثبت نشده" : edited[i]));
            answer.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("پاسخ صحیح سؤال " + (index + 1))
                .setItems(new String[]{"A", "B", "C", "D", "ثبت نشده"}, (d, choice) -> {
                    edited[index] = choice == 4 ? '-' : (char)('A' + choice);
                    answer.setText("سؤال " + (index + 1) + " : " + (edited[index] == '-' ? "ثبت نشده" : edited[index]));
                }).show());
            rows.addView(answer, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(rows);
        new AlertDialog.Builder(this).setTitle("ویرایش کلید پاسخ (" + count + " سؤال)")
            .setView(scroll)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره تغییرات", (d, which) -> {
                StringBuilder key = new StringBuilder();
                for (char c : edited) key.append(c);
                prefs.edit().putString("answer_key", key.toString()).apply();
                JSONArray bank = readQuestionBank();
                for (int i = 0; i < Math.min(count, bank.length()); i++) {
                    JSONObject q = bank.optJSONObject(i);
                    if (q != null) {
                        char c = edited[i];
                        try { q.put("correct_index", c >= 'A' && c <= 'D' ? c - 'A' : -1); }
                        catch (Exception ignored) { }
                    }
                }
                prefs.edit().putString("question_bank_json", bank.toString()).apply();
                info("کلید پاسخ و پاسخ‌های بانک سؤال ذخیره شد.");
                showKeyDialog();
            }).show();
    }

    private void showKeyOutputChoices(String target) {
        new AlertDialog.Builder(this).setTitle("خروجی کلید پاسخ")
            .setItems(new String[]{"چاپ کلید", "ذخیره به‌صورت HTML"}, (dialog, which) -> {
                if (which == 0) printKeyHtml(target);
                else {
                    pendingKeyTarget = target;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/html");
                    intent.putExtra(Intent.EXTRA_TITLE, "azmoonyar-answer-key-" + target + ".html");
                    try { startActivityForResult(intent, 7315); } catch (Exception e) { info("ذخیرهٔ فایل ممکن نشد."); }
                }
            }).show();
    }

    private String keyHtml(String target) {
        String value = "main".equals(target) ? (prefs.getString("answer_key", "").matches("[A-D]{"+questionCount()+"}") ? prefs.getString("answer_key", "") : buildOriginalBankKey()) : getGeneratedModelKey(target);
        String title = "main".equals(target) ? "کلید اصلی بانک سؤال" : "کلید نسخه " + target;
        StringBuilder html = new StringBuilder("<html><head><meta charset=\"utf-8\"><style>body{font-family:sans-serif;direction:rtl}table{border-collapse:collapse;width:100%}td,th{border:1px solid #333;padding:8px;text-align:center}</style></head><body>");
        html.append("<h1>").append(escapeHtml(examName())).append("</h1><h2>").append(escapeHtml(title)).append("</h2><table><tr><th>شماره سؤال</th><th>گزینه صحیح</th></tr>");
        for (int i=0;i<value.length();i++) { int a=value.charAt(i); html.append("<tr><td>").append(i+1).append("</td><td>").append(a >= 65 && a <= 68 ? (char)a : "ثبت نشده").append("</td></tr>"); }
        return html.append("</table></body></html>").toString();
    }

    private void printKeyHtml(String target) {
        WebView web = new WebView(this);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (manager != null) manager.print("کلید پاسخ آزمون‌یار", view.createPrintDocumentAdapter("کلید پاسخ"), new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
            }
        });
        web.loadDataWithBaseURL(null, keyHtml(target), "text/html", "UTF-8", null);
    }
    private void pickAnswerKeyImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_ANSWER_KEY_IMAGE); }
        catch (Exception e) { info("بازکردن انتخاب‌گر تصویر ممکن نشد."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == 7315) {
            try (java.io.OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new java.io.IOException("output stream unavailable");
                output.write(keyHtml(pendingKeyTarget).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                info("فایل کلید پاسخ ذخیره شد.");
            } catch (Exception e) { info("ذخیرهٔ فایل کلید ناموفق بود."); }
            return;
        }
        if (requestCode == REQUEST_QUESTION_IMAGE) { saveScannedQuestionImage(data.getData()); return; }
        if (requestCode != REQUEST_ANSWER_KEY_IMAGE) return;
        android.net.Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
        prefs.edit().putString("answer_key_image_" + pendingKeyTarget, uri.toString()).apply();
        scanPrintedAzmoonyarKey(uri, pendingKeyTarget);
    }


    private void scanPrintedAzmoonyarKey(android.net.Uri uri, String target) {
        android.graphics.Bitmap bitmap = null;
        try {
            java.io.InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new java.io.IOException("empty image");
            bitmap = android.graphics.BitmapFactory.decodeStream(input);
            input.close();
            if (bitmap == null) throw new java.io.IOException("invalid image");
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            if (w < 500 || h < 700 || Math.abs(w / (float) h - 210f / 297f) > 0.12f) {
                info("لطفاً تصویر صاف و کامل برگه A4 آزمون‌یار را انتخاب کن؛ تصویر نباید برش خورده باشد.");
                return;
            }
            int count = Math.min(100, questionCount());
            StringBuilder answers = new StringBuilder();
            StringBuilder uncertain = new StringBuilder();
            for (int q = 0; q < count; q++) {
                int col = q / 25, row = q % 25;
                // Calibrated to the actual four-column Azmoonyar A4 answer-key print:
                // the first bubble centers are ~4.5 mm farther right and ~4 mm lower
                // than the former estimate, which was sampling the gaps between bubbles.
                float leftMm = 23.5f + col * 46.2f;
                float yMm = 60.0f + row * 8.0f;
                double[] scores = new double[4];
                for (int a = 0; a < 4; a++) {
                    float xMm = leftMm + 5.0f + 8.5f * a;
                    scores[a] = scanBubbleDarkness(bitmap, xMm / 210f * w, yMm / 297f * h,
                            2f / 210f * w, 1.7f / 297f * h);
                }
                int best = 0;
                for (int a = 1; a < 4; a++) if (scores[a] > scores[best]) best = a;
                double runner = 0;
                for (int a = 0; a < 4; a++) if (a != best) runner = Math.max(runner, scores[a]);
                answers.append((char) ('A' + best));
                if (scores[best] < 0.16 || scores[best] - runner < 0.025) uncertain.append(q + 1).append("، ");
            }
            showScannedKeyReview(answers.toString(), target, uncertain.toString());
        } catch (Exception e) {
            info("تحلیل تصویر انجام نشد؛ لطفاً عکس واضح و کامل برگه را انتخاب کن.");
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private double scanBubbleDarkness(android.graphics.Bitmap bitmap, float cx, float cy, float rx, float ry) {
        int l = Math.max(0, (int)(cx-rx)), r = Math.min(bitmap.getWidth()-1, (int)(cx+rx));
        int t = Math.max(0, (int)(cy-ry)), b = Math.min(bitmap.getHeight()-1, (int)(cy+ry));
        long sum = 0; int n = 0;
        int sy = Math.max(1, (b-t)/12), sx = Math.max(1, (r-l)/12);
        for (int y=t; y<=b; y+=sy) for (int x=l; x<=r; x+=sx) {
            int c=bitmap.getPixel(x,y);
            int gray=(int)(0.299*Color.red(c)+0.587*Color.green(c)+0.114*Color.blue(c));
            sum += 255-gray; n++;
        }
        return n==0 ? 0 : sum/(255.0*n);
    }

    private void showScannedKeyReview(String extracted, String target, String uncertain) {
        EditText editor = new EditText(this);
        editor.setText(extracted);
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        String message = "کلید تقریبی است؛ پیش از ذخیره پاسخ‌ها را بررسی و اصلاح کن."
                + (uncertain.isEmpty() ? "" : "\nسؤال‌های نامطمئن: " + uncertain);
        new AlertDialog.Builder(this).setTitle("بازبینی کلید اسکن‌شده").setMessage(message).setView(editor)
                .setPositiveButton("تأیید و ذخیره", (d, which) -> {
                    String normalized = editor.getText().toString().toUpperCase(Locale.US).replaceAll("[^A-D]", "");
                    if (normalized.length() != questionCount()) {
                        info("تعداد پاسخ‌ها باید دقیقاً " + questionCount() + " مورد باشد.");
                        return;
                    }
                    if ("main".equals(target)) prefs.edit().putString("answer_key", normalized).apply();
                    else prefs.edit().putString("answer_key_" + target, normalized).apply();
                    refreshSummary();
                    info("کلید پس از بازبینی ذخیره شد.");
                }).setNegativeButton("لغو", null).show();
    }

    private String buildOriginalBankKey() {
        JSONArray bank = readQuestionBank();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < questionCount(); i++) {
            JSONObject q = i < bank.length() ? bank.optJSONObject(i) : null;
            int correct = q == null ? -1 : q.optInt("correct_index", -1);
            result.append(correct >= 0 && correct <= 3 ? (char)('A' + correct) : '-');
        }
        return result.toString();
    }

    private String getGeneratedModelKey(String model) {
        String savedKey = prefs.getString("answer_key_model_" + model, "");
        if (savedKey.length() >= questionCount()) return savedKey.substring(0, questionCount());
        String stored = prefs.getString("model_questions_" + model, "");
        if (stored.isEmpty()) return "";
        try {
            JSONArray questions = new JSONArray(stored);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < questionCount(); i++) {
                JSONObject q = i < questions.length() ? questions.optJSONObject(i) : null;
                String letter = q == null ? "" : q.optString("correct_letter", "");
                if (!letter.matches("[A-D]")) {
                    int idx = q == null ? -1 : q.optInt("correct_index", -1);
                    letter = idx >= 0 && idx <= 3 ? String.valueOf((char)('A' + idx)) : "-";
                }
                result.append(letter);
            }
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void showKeySummary() {
        if(examName().isEmpty()) { info("هنوز آزمونی تعریف نشده است."); return; }
        StringBuilder report = new StringBuilder();
        JSONArray bank = readQuestionBank();
        String bankKey = buildOriginalBankKey();
        report.append("کلید اصلی بانک سؤال (ترتیب اولیه):\\n");
        report.append(formatAnswerKey(bankKey, questionCount())).append("\\n\\n");
        int savedModelCount = Math.max(1, Math.min(26, prefs.getInt("issued_model_count", 1)));
        for (int modelIndex = 0; modelIndex < savedModelCount; modelIndex++) {
            String model = String.valueOf((char)('A' + modelIndex));
            report.append("نسخه ").append(model).append(":\\n");
            String modelKey = getGeneratedModelKey(model);
            if (modelKey.isEmpty()) {
                report.append("کلید نسخه در حافظهٔ برنامه پیدا نشد؛ ابتدا نسخه‌ها را چاپ کن.\\n\\n");
            } else {
                report.append(formatAnswerKey(modelKey, questionCount())).append("\\n\\n");
            }
        }
        report.append("توجه: کلید نسخه‌ها از آخرین اطلاعات ذخیره‌شدهٔ برنامه خوانده می‌شود؛ اگر چاپ قبلی با نسخه‌ای انجام شده که ترتیب نسخه‌ها را ذخیره نمی‌کرد، کلید دقیق همان چاپ از این اطلاعات قابل تضمین نیست.");
        new AlertDialog.Builder(this).setTitle("کلید پاسخ فرم واحد")
          .setMessage(report.toString()).setPositiveButton("بستن",null).show();
    }

    private String formatAnswerKey(String answerKey, int count) {
        StringBuilder out = new StringBuilder();
        int filled = 0;
        for (int i = 0; i < count; i++) {
            char a = answerKey != null && i < answerKey.length() ? answerKey.charAt(i) : '-';
            if (a >= 'A' && a <= 'D') filled++;
            out.append(String.format(Locale.ROOT, "%02d: %s%n", i + 1,
                    (a >= 'A' && a <= 'D') ? String.valueOf(a) : "ثبت نشده"));
        }
        out.insert(0, "تکمیل: " + filled + "/" + count + "\n");
        return out.toString();
    }

    private JSONArray readQuestionBank() {
        try { return new JSONArray(prefs.getString("question_bank_json", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showQuestionBank() {
        JSONArray bank = readQuestionBank();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView message = new TextView(this);
        message.setText(bank.length() == 0
                ? "هنوز سؤالی ثبت نشده است. برای شروع، سؤال‌ها را به‌صورت دستی اضافه کن."
                : "تعداد سؤال‌های ذخیره‌شده: " + bank.length() + "\nهر نسخه از همین بانک ساخته می‌شود؛ ترتیب سؤال‌ها و گزینه‌ها جابه‌جا می‌شوند.");
        message.setTextSize(15);
        message.setPadding(0, 0, 0, dp(12));
        panel.addView(message);
        Button add = new Button(this); add.setText("افزودن سؤال"); styleTurquoiseDashboardButton(add);
        add.setOnClickListener(v -> showAddBankQuestionDialog()); panel.addView(add);
        Button scanQuestion = new Button(this); scanQuestion.setText("افزودن سؤال از طریق اسکنر"); styleTurquoiseDashboardButton(scanQuestion);
        scanQuestion.setOnClickListener(v -> pickQuestionImage()); panel.addView(scanQuestion);
        Button clearBank = new Button(this); clearBank.setText("پاک کردن بانک سؤال / فرم واحد"); styleTurquoiseDashboardButton(clearBank);
        clearBank.setOnClickListener(v -> confirmClearQuestionBank()); panel.addView(clearBank);
        Button saved = new Button(this); saved.setText("سؤالات ذخیره‌شده"); styleTurquoiseDashboardButton(saved);
        saved.setOnClickListener(v -> showSavedExamBatches()); panel.addView(saved);
        Button bankItems = new Button(this); bankItems.setText("مشاهده سؤال‌های بانک و پاسخ‌ها"); styleTurquoiseDashboardButton(bankItems);
        bankItems.setOnClickListener(v -> showQuestionBankItems()); panel.addView(bankItems);
        Button printQuestions = new Button(this); printQuestions.setText("چاپ پرسشنامه فقط"); styleTurquoiseDashboardButton(printQuestions);
        printQuestions.setOnClickListener(v -> printQuestionnaireOnly()); panel.addView(printQuestions);
        Button printAnswers = new Button(this); printAnswers.setText("چاپ پاسخنامه فقط"); styleTurquoiseDashboardButton(printAnswers);
        printAnswers.setOnClickListener(v -> printStandardAnswerSheets()); panel.addView(printAnswers);
        new AlertDialog.Builder(this).setTitle("بانک سؤال (" + bank.length() + ")")
            .setView(panel).setPositiveButton("بستن", null).show();
    }

    private void showQuestionBankItems() {
        JSONArray bank = readQuestionBank();
        if (bank.length() == 0) {
            info("بانک سؤال خالی است. سؤال‌های اسکن‌شده پس از ثبت در اینجا نمایش داده می‌شوند.");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(list);
        for (int i = 0; i < bank.length(); i++) {
            JSONObject item = bank.optJSONObject(i);
            if (item == null) continue;
            int number = item.optInt("question_number", i + 1);
            int correct = item.optInt("correct_index", -1);
            String answer = correct >= 0 && correct < 4 ? String.valueOf((char)('A' + correct)) : "ثبت‌نشده";
            TextView title = new TextView(this);
            title.setText("سؤال شمارهٔ " + number + " — پاسخ صحیح: " + answer
                    + "\\n" + item.optString("text", "سؤال تصویری " + number));
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(Color.rgb(30, 41, 59));
            title.setPadding(0, dp(12), 0, dp(8));
            list.addView(title);
            JSONArray options = item.optJSONArray("options");
            if (options != null) {
                for (int j = 0; j < options.length(); j++) {
                    TextView option = new TextView(this);
                    option.setText(String.valueOf((char)('A' + j)) + ". " + options.optString(j));
                    option.setTextSize(14);
                    option.setTextColor(j == correct ? Color.rgb(5, 150, 105) : Color.DKGRAY);
                    option.setPadding(dp(8), dp(4), dp(8), dp(4));
                    list.addView(option);
                }
            }
            String imageData = item.optString("image_data", "");
            if (imageData.startsWith("data:image") && imageData.contains(",")) {
                try {
                    byte[] imageBytes = Base64.decode(imageData.substring(imageData.indexOf(',') + 1), Base64.DEFAULT);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        android.widget.ImageView image = new android.widget.ImageView(this);
                        image.setImageBitmap(bitmap);
                        image.setAdjustViewBounds(true);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        list.addView(image, new LinearLayout.LayoutParams(-1, -2));
                    }
                } catch (Exception ignored) { }
            }
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(226, 232, 240));
            list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        new AlertDialog.Builder(this).setTitle("سؤال‌های بانک (" + bank.length() + ")")
                .setView(scroll).setPositiveButton("بستن", null).show();
    }

    private void confirmClearQuestionBank() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("پاک کردن بانک سؤال")
            .setMessage("همهٔ سؤال‌های بانک و کلید فرم واحد پاک شوند؟ این کار قابل بازگشت نیست.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("پاک کردن", (d, w) -> {
                String emptyKey = new String(new char[Math.max(1, questionCount())]).replace("\\0", "-");
                prefs.edit().putString("question_bank_json", "[]")
                    .putString("answer_key", emptyKey)
                    .putString("answer_key_master", "")
                    .putString("model_questions_master", "[]")
                    .apply();
                refreshSummary();
                info("بانک سؤال و کلید فرم واحد پاک شدند.");
            }).create();
        dialog.setOnShowListener(d -> {
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button clear = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (cancel != null) styleTurquoiseDashboardButton(cancel);
            if (clear != null) styleTurquoiseDashboardButton(clear);
        });
        dialog.show();
    }

    private JSONArray readSavedExamBatches() {
        try { return new JSONArray(prefs.getString("saved_exam_batches_json", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showSavedExamBatches() {
        JSONArray batches = readSavedExamBatches();
        if (batches.length() == 0) {
            info("هنوز آزمون ذخیره‌شده‌ای وجود ندارد. آزمون‌های قبلی پس از ذخیره در این فهرست نمایش داده می‌شوند.");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(list);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, new Locale("fa"));
        for (int i = 0; i < batches.length(); i++) {
            JSONObject batch = batches.optJSONObject(i);
            if (batch == null) continue;
            final JSONObject selectedBatch = batch;
            final String batchId = batch.optString("batch_id", "");
            String label = (i + 1) + ". " + batch.optString("exam_name", "آزمون")
                    + " — " + batch.optInt("variant_count", 0) + " نسخه — "
                    + df.format(new java.util.Date(batch.optLong("created_at", 0)));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(label);
            name.setTextSize(14);
            name.setTextColor(Color.rgb(30, 41, 59));
            name.setPadding(dp(10), dp(12), dp(10), dp(12));
            name.setOnClickListener(v -> showSavedBatchContents(selectedBatch));
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            Button print = new Button(this);
            print.setText("انتقال به چاپ");
            styleTurquoiseDashboardButton(print);
            print.setTextSize(12);
            print.setMinWidth(0);
            print.setPadding(dp(8), dp(6), dp(8), dp(6));
            print.setOnClickListener(v -> {
                String savedHtml = selectedBatch.optString("print_html", "");
                if (savedHtml.isEmpty()) {
                    showSavedBatchContents(selectedBatch);
                } else {
                    printHtmlDocument("AzmoonYar-" + selectedBatch.optString("exam_name", "آزمون"), savedHtml);
                }
            });
            LinearLayout.LayoutParams printLp = new LinearLayout.LayoutParams(dp(118), dp(48));
            printLp.setMargins(0, 0, dp(6), 0);
            row.addView(print, printLp);

            Button delete = new Button(this);
            delete.setText("حذف");
            delete.setAllCaps(false);
            delete.setTextColor(Color.WHITE);
            delete.setTypeface(null, android.graphics.Typeface.BOLD);
            android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(255, 92, 92), Color.rgb(220, 38, 38), Color.rgb(153, 27, 27)});
            deleteBg.setCornerRadius(dp(36));
            deleteBg.setStroke(dp(2), Color.rgb(255, 170, 170));
            delete.setBackground(deleteBg);
            delete.setPadding(dp(10), dp(6), dp(10), dp(6));
            delete.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("حذف مجموعه")
                    .setMessage("«" + selectedBatch.optString("exam_name", "آزمون") + "» حذف شود؟ این کار مجموعه‌های دیگر را تغییر نمی‌دهد.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف", (dialog, which) -> {
                        deleteSavedExamBatch(batchId);
                        showSavedExamBatches();
                    }).show());
            row.addView(delete, new LinearLayout.LayoutParams(dp(76), dp(48)));
            list.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(226, 232, 240));
            list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        new AlertDialog.Builder(this).setTitle("مجموعه‌های ذخیره‌شده")
            .setView(scroll).setPositiveButton("بستن", null).show();
    }

    private void showSavedBatchActions(JSONObject batch) {
        if (batch == null) { info("اطلاعات این مجموعه خوانده نشد."); return; }
        String id = batch.optString("batch_id", "");
        String title = batch.optString("exam_name", "آزمون");
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage("تعداد نسخه‌ها: " + batch.optInt("variant_count", 0)
                    + "\nبرای دیدن سؤال‌ها، ترتیب گزینه‌ها و کلید هر نسخه، «مشاهده محتویات» را بزن.")
            .setItems(new String[]{"مشاهده محتویات", "چاپ دوباره همین مجموعه", "حذف این مجموعه"}, (d, which) -> {
                if (which == 0) {
                    showSavedBatchContents(batch);
                } else if (which == 1) {
                    String savedHtml = batch.optString("print_html", "");
                    if (savedHtml.isEmpty()) { info("فایل چاپ این مجموعه در دسترس نیست."); return; }
                    printHtmlDocument("AzmoonYar-" + title, savedHtml);
                } else {
                    new AlertDialog.Builder(this).setTitle("حذف مجموعه")
                        .setMessage("«" + title + "» حذف شود؟ این کار مجموعه‌های دیگر را تغییر نمی‌دهد.")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف", (confirm, w) -> deleteSavedExamBatch(id))
                        .show();
                }
            }).setNegativeButton("بازگشت", (d, w) -> showSavedExamBatches()).show();
    }

    private void showSavedBatchContents(JSONObject batch) {
        StringBuilder details = new StringBuilder();
        JSONArray variants = batch.optJSONArray("variants");
        if (variants == null || variants.length() == 0) {
            info("محتویات نسخه‌ها در این مجموعه ذخیره نشده است؛ چاپ دوباره را بررسی کن.");
            return;
        }
        for (int v = 0; v < variants.length(); v++) {
            JSONObject variant = variants.optJSONObject(v);
            if (variant == null) continue;
            String model = variant.optString("model_id", "?");
            details.append("نسخه ").append(model)
                .append(" — تعداد سؤال: ").append(variant.optInt("question_count", 0))
                                .append("\nکلید پاسخ: ").append(variant.optString("answer_key", "ثبت نشده"))
                .append("\n\n");
            JSONArray questions = variant.optJSONArray("questions");
            if (questions != null) {
                for (int q = 0; q < questions.length(); q++) {
                    JSONObject item = questions.optJSONObject(q);
                    if (item == null) continue;
                    details.append("سؤال ").append(q + 1).append(": ")
                        .append(item.optString("text", "").trim()).append("\n");
                    JSONArray options = item.optJSONArray("options");
                    if (options != null) {
                        for (int o = 0; o < options.length(); o++) {
                            details.append("   ").append((char)('A' + o)).append(") ")
                                .append(options.optString(o, "").trim()).append("\n");
                        }
                    }
                    details.append("\n");
                }
            }
            details.append("--------------------------------\n\n");
        }
        ScrollView scroll = new ScrollView(this);
        TextView content = new TextView(this);
        content.setText(details.toString());
        content.setTextSize(17);
        content.setTextDirection(View.TEXT_DIRECTION_RTL);
        content.setGravity(Gravity.RIGHT);
        content.setLineSpacing(dp(3), 1.05f);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("محتویات «" + batch.optString("exam_name", "آزمون") + "»")
            .setView(scroll).setPositiveButton("بستن", null)
            .setNeutralButton("بازگشت", (d, w) -> showSavedBatchActions(batch)).show();
    }

    private void deleteSavedExamBatch(String batchId) {
        JSONArray current = readSavedExamBatches();
        JSONArray kept = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject batch = current.optJSONObject(i);
            if (batch == null || !batchId.equals(batch.optString("batch_id", ""))) {
                if (batch != null) kept.put(batch);
            } else {
                JSONArray variants = batch.optJSONArray("variants");
                if (variants != null) for (int v = 0; v < variants.length(); v++) {
                    JSONObject variant = variants.optJSONObject(v);
                    if (variant == null) continue;
                    String sheetId = variant.optString("sheet_set_id", "");
                    String model = variant.optString("model_id", "");
                    prefs.edit().remove("issued_exam_" + sheetId + "_" + model)
                        .remove("saved_variant_" + sheetId).apply();
                }
            }
        }
        prefs.edit().putString("saved_exam_batches_json", kept.toString()).apply();
        info("مجموعه انتخاب‌شده حذف شد؛ سایر مجموعه‌ها باقی ماندند.");
    }

    private WebView activePrintWebView;

    private void printHtmlDocument(String title, String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            info("محتوای چاپ خالی است؛ چیزی برای چاپ وجود ندارد.");
            return;
        }
        final WebView web = new WebView(this);
        activePrintWebView = web;
        web.getSettings().setDefaultTextEncodingName("UTF-8");
        web.getSettings().setJavaScriptEnabled(false);
        web.setBackgroundColor(Color.WHITE);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView hint = new TextView(this);
        hint.setText("پیش‌نمایش برگه‌های چاپی؛ پس از بارگذاری، دکمهٔ چاپ را بزن.");
        hint.setPadding(dp(4), dp(4), dp(4), dp(8));
        preview.addView(hint);
        preview.addView(web, new LinearLayout.LayoutParams(-1, dp(420)));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("آماده‌سازی چاپ")
                .setView(preview)                .setNegativeButton("لغو", (d, w) -> {
                    if (activePrintWebView == web) activePrintWebView = null;
                    web.destroy();
                })
                .setPositiveButton("چاپ…", null)
                .create();
        web.setWebViewClient(new WebViewClient() {
            private boolean pageReady;
            @Override public void onPageFinished(WebView view, String url) {
                if (pageReady) return;
                pageReady = true;
                hint.setText("پیش‌نمایش بارگذاری شد. برای باز کردن پنجرهٔ چاپ، «چاپ…» را بزن.");
                Button printButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (printButton != null) printButton.setEnabled(true);
            }
        });
        dialog.setOnShowListener(ignored -> {
            Button printButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            printButton.setEnabled(false);
            printButton.setOnClickListener(v -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) {
                    hint.setText("قابلیت چاپ در این دستگاه در دسترس نیست.");
                    return;
                }
                try {
                    pm.print(title, web.createPrintDocumentAdapter(title),
                            new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
                    hint.setText("پنجرهٔ چاپ باز شد؛ چاپگر یا «ذخیره به‌صورت PDF» را انتخاب کن.");
                } catch (Exception e) {
                    hint.setText("بازکردن پنجرهٔ چاپ ناموفق بود: " + e.getMessage());
                }
            });
        });
        dialog.setOnDismissListener(ignored -> {
            if (activePrintWebView == web) activePrintWebView = null;
            web.destroy();
        });
        dialog.show();
        web.loadDataWithBaseURL("https://azmoonyar.local/", htmlContent, "text/html", "UTF-8", null);
    }

    private void pickQuestionImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_QUESTION_IMAGE); }
        catch (Exception e) { info("بازکردن انتخاب‌گر تصویر سؤال ممکن نشد."); }
    }

    private void saveScannedQuestionImage(android.net.Uri uri) {
        try {
            android.graphics.Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("empty image");
                bitmap = android.graphics.BitmapFactory.decodeStream(input);
            }
            if (bitmap == null) throw new java.io.IOException("invalid image");
            // Resize large scans to keep the local question bank reasonably small.
            int maxSide = 1600;
            if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSide) {
                float scale = maxSide / (float) Math.max(bitmap.getWidth(), bitmap.getHeight());
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bytes);
            bitmap.recycle();
            String imageData = "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);

            JSONArray bank = readQuestionBank();
            int questionNumber = bank.length() + 1;
            String answerKey = key();
            char answer = questionNumber <= answerKey.length() ? answerKey.charAt(questionNumber - 1) : '-';
            int correctIndex = answer >= 'A' && answer <= 'D' ? answer - 'A' : -1;
            JSONArray options = new JSONArray();
            for (int i = 0; i < 4; i++) options.put("گزینه " + (char)('A' + i));
            JSONObject item = new JSONObject();
            item.put("question_number", questionNumber);
            item.put("id", UUID.randomUUID().toString());
            item.put("text", "سؤال تصویری " + questionNumber);
            item.put("topic", "");
            item.put("options", options);
            item.put("correct_index", correctIndex);
            item.put("image_data", imageData);
            bank.put(item);
            prefs.edit().putString("question_bank_json", bank.toString()).apply();
            info(correctIndex >= 0
                    ? "تصویر سؤال " + questionNumber + " به بانک اضافه شد و پاسخ " + answer + " از کلید آزمون برداشت شد."
                    : "تصویر سؤال " + questionNumber + " اضافه شد؛ پاسخ این شماره در کلید آزمون ثبت نشده است.");
        } catch (Exception e) {
            info("افزودن تصویر سؤال ناموفق بود: " + e.getMessage());
        }
    }

    private void showAddBankQuestionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(10), dp(4), dp(10), dp(4));
        JSONArray existingBank = readQuestionBank();
        TextView numberLabel = new TextView(this);
        numberLabel.setText("شمارهٔ سؤال بعدی: " + (existingBank.length() + 1));
        numberLabel.setTextSize(16);
        numberLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        numberLabel.setPadding(0, 0, 0, dp(8));
        form.addView(numberLabel);
        EditText question = new EditText(this); styleInput(question);
        question.setHint("متن سؤال شمارهٔ " + (existingBank.length() + 1));
        form.addView(question);
        EditText topic = new EditText(this); styleInput(topic);
        topic.setHint("مبحث (اختیاری؛ مثل نوار قلب یا رادیکال)");
        form.addView(topic);
        EditText[] options = new EditText[4];
        for (int i = 0; i < 4; i++) {
            options[i] = new EditText(this); styleInput(options[i]);
            options[i].setSingleLine(false);
            options[i].setHint("گزینه " + (char)('A' + i));
            form.addView(options[i]);
        }
        TextView label = new TextView(this);
        label.setText("پاسخ صحیح را انتخاب کن:");
        form.addView(label);
        RadioGroup correctGroup = new RadioGroup(this);
        correctGroup.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            RadioButton rb = new RadioButton(this);
            styleOption(rb, i);
            rb.setText(String.valueOf((char)('A' + i)));
            rb.setTag(i);
            correctGroup.addView(rb);
        }
        form.addView(correctGroup);
        new AlertDialog.Builder(this).setTitle("افزودن به بانک سؤال").setView(form)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", (d,w) -> {
                String qText = question.getText().toString().trim();
                if (qText.isEmpty()) { info("متن سؤال را وارد کن."); return; }
                JSONArray optionArray = new JSONArray();
                for (EditText option : options) {
                    String value = option.getText().toString().trim();
                    if (value.isEmpty()) { info("هر چهار گزینه باید تکمیل شوند."); return; }
                    optionArray.put(value);
                }
                int checked = correctGroup.getCheckedRadioButtonId();
                if (checked == -1) { info("گزینه صحیح را انتخاب کن."); return; }
                RadioButton selected = correctGroup.findViewById(checked);
                int correctIndex = (Integer) selected.getTag();
                try {
                    JSONArray bank = readQuestionBank();
                    int questionNumber = bank.length() + 1;
                    JSONObject item = new JSONObject();
                    item.put("question_number", questionNumber);
                    item.put("id", UUID.randomUUID().toString());
                    item.put("text", qText);
                    item.put("topic", topic.getText().toString().trim());
                    item.put("options", optionArray);
                    item.put("correct_index", correctIndex);
                    bank.put(item);
                    prefs.edit().putString("question_bank_json", bank.toString()).apply();
                    info("سؤال شمارهٔ " + questionNumber + " در بانک محلی ذخیره شد.");
                } catch (Exception e) {
                    info("ذخیره سؤال ناموفق بود.");
                }
            }).show();
    }

    private void generateRandomVariants() {
        JSONArray bank = readQuestionBank();
        if (bank.length() < 1) {
            info("ابتدا حداقل یک سؤال در بانک سؤال ثبت کن.");
            return;
        }
        String[] labels = {"۱ نسخه (یک مدل)", "۲ نسخه", "۳ نسخه", "۴ نسخه", "۵ نسخه", "۱۰ نسخه"};
        int[] counts = {1, 2, 3, 4, 5, 10};
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        final int[] selectedCount = {3};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTag(counts[i]);
            group.addView(option);
            if (counts[i] == 3) {
                option.setChecked(true);
                selectedCount[0] = 3;
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton selected = g.findViewById(checkedId);
            if (selected != null) selectedCount[0] = (Integer) selected.getTag();
        });
        new AlertDialog.Builder(this)
            .setTitle("تعداد مدل‌های آزمون")
            .setMessage("برای هر مدل، کلید تصحیح به‌طور خودکار ساخته می‌شود. کلید در پیش‌نمایش مخصوص مسئول آزمون است و روی برگه شرکت‌کننده چاپ نمی‌شود.")
            .setView(group)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ساخت مدل‌ها و کلیدها", (dialog, which) -> {
                try {
                    JSONArray variants = RandomExamBuilder.generateVariants(bank, selectedCount[0], System.nanoTime());
                    ScrollView scroll = new ScrollView(this);
                    LinearLayout content = new LinearLayout(this);
                    content.setOrientation(LinearLayout.VERTICAL);
                    content.setPadding(dp(12), dp(8), dp(12), dp(16));
                    scroll.addView(content);
                    TextView intro = new TextView(this);
                    intro.setText("مدل‌ها و کلیدهای اختصاصی ساخته شدند. بیضی رنگی نشان‌دهندهٔ گزینهٔ صحیح هر سؤال است. این صفحه پیش‌نمایش مسئول آزمون است؛ نام مدل نباید روی پرسشنامه یا پاسخنامهٔ شرکت‌کننده چاپ شود.");
                    intro.setTextSize(14);
                    intro.setTextColor(Color.rgb(30, 41, 59));
                    intro.setPadding(dp(4), dp(6), dp(4), dp(12));
                    content.addView(intro);
                    for (int v = 0; v < variants.length(); v++) {
                        JSONObject variant = variants.optJSONObject(v);
                        if (variant == null) continue;
                        TextView heading = new TextView(this);
                        heading.setText("کلید داخلی مدل " + variant.optString("variant_id") +
                                "   •   " + variant.optString("answer_key").length() + " سؤال");
                        heading.setTextSize(17);
                        heading.setTypeface(null, android.graphics.Typeface.BOLD);
                        heading.setTextColor(Color.rgb(29, 78, 216));
                        heading.setPadding(dp(4), dp(12), dp(4), dp(8));
                        content.addView(heading);
                        JSONArray questions = variant.optJSONArray("questions");
                        if (questions == null) continue;
                        for (int i = 0; i < questions.length(); i++) {
                            JSONObject q = questions.optJSONObject(i);
                            if (q == null) continue;
                            LinearLayout row = new LinearLayout(this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding(dp(4), dp(5), dp(4), dp(5));
                            TextView number = new TextView(this);
                            number.setText(String.valueOf(i + 1) + ".");
                            number.setTextSize(14);
                            number.setTextColor(Color.rgb(51, 65, 85));
                            number.setGravity(Gravity.CENTER);
                            row.addView(number, new LinearLayout.LayoutParams(dp(34), -2));
                            char correct = q.optString("correct_letter", "").isEmpty()
                                    ? '-' : q.optString("correct_letter").charAt(0);
                            for (int option = 0; option < 4; option++) {
                                char letter = (char) ('A' + option);
                                TextView bubble = new TextView(this);
                                bubble.setText(String.valueOf(letter));
                                bubble.setTextSize(12);
                                bubble.setTypeface(null, android.graphics.Typeface.BOLD);
                                bubble.setGravity(Gravity.CENTER);
                                int color = letter == correct ? Color.rgb(22, 163, 74) : Color.rgb(100, 116, 139);
                                android.graphics.drawable.GradientDrawable bubbleBg = new android.graphics.drawable.GradientDrawable();
                                // Keep answer-key preview bubbles monochrome; do not color-code options.
                                bubbleBg.setColor(Color.WHITE);
                                bubbleBg.setCornerRadius(dp(30));
                                bubbleBg.setStroke(dp(2), Color.BLACK);
                                bubble.setBackground(bubbleBg);
                                bubble.setTextColor(Color.BLACK);
                                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(38), dp(30));
                                bp.leftMargin = dp(3);
                                bp.rightMargin = dp(3);
                                row.addView(bubble, bp);
                            }
                            TextView questionText = new TextView(this);
                            questionText.setText(q.optString("text", ""));
                            questionText.setTextSize(12);
                            questionText.setTextColor(Color.rgb(51, 65, 85));
                            questionText.setMaxLines(2);
                            questionText.setPadding(dp(6), 0, dp(4), 0);
                            row.addView(questionText, new LinearLayout.LayoutParams(0, -2, 1f));
                            content.addView(row);
                            View separator = new View(this);
                            separator.setBackgroundColor(Color.rgb(226, 232, 240));
                            content.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
                        }
                    }
                    new AlertDialog.Builder(this).setTitle("پیش‌نمایش نسخه‌ها و کلیدهای پاسخ")
                        .setMessage("نسخه‌ها فعلاً فقط پیش‌نمایش هستند. با تأیید، همین ترتیب سؤال‌ها و گزینه‌ها، کلید اصلی و کلید اختصاصی هر نسخه ذخیره می‌شود و سپس همین نسخه‌ها برای چاپ آماده می‌شوند.")
                        .setView(scroll)
                        .setNegativeButton("لغو؛ ذخیره نکن", null)
                        .setPositiveButton("تأیید، ذخیره و چاپ", (d, w) ->
                            printPairedSheets("", UUID.randomUUID().toString(), variants.length(), variants))
                        .show();
                } catch (Exception e) {
                    info("ساخت مدل‌ها ناموفق بود: " + e.getMessage());
                }
            }).show();
    }

    private void showImageRetentionDialog() {
        final String[] values = {"never", "all", "suspicious"};
        final String[] labels = {
            "۱) هرگز عکس ذخیره نشود",
            "۲) ذخیره همهٔ تصاویر پاسخنامه‌ها",
            "۳) ذخیره فقط تصاویر مشکوک"
        };
        String saved = prefs.getString("image_retention_policy", "never");
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(saved)) checked = i;
        }
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);        group.setPadding(dp(8), dp(8), dp(8), dp(8));
        // Match the dashboard's glossy blue/green button palette.
        int[][] optionColors = {
            {0, 174, 255, 0, 210, 255},
            {0, 238, 78, 0, 255, 115},
            {0, 174, 255, 0, 210, 255}
        };
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTextSize(15);
            option.setTypeface(null, android.graphics.Typeface.BOLD);
            option.setTag(values[i]);
            option.setPadding(dp(14), dp(12), dp(14), dp(12));
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(optionColors[i][3], optionColors[i][4], optionColors[i][5])));
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                    Color.rgb(optionColors[i][0], optionColors[i][1], optionColors[i][2]),
                    Color.rgb(Math.max(0, optionColors[i][0] - 35),
                              Math.max(0, optionColors[i][1] - 65),
                              Math.max(0, optionColors[i][2] - 100))
                });
            pill.setCornerRadius(dp(24));
            pill.setStroke(dp(2), Color.rgb(optionColors[i][3], optionColors[i][4], optionColors[i][5]));
            option.setBackground(pill);
            option.setElevation(dp(4));
            option.setOnTouchListener((view, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    view.setTranslationY(dp(2));
                    view.setElevation(dp(1));
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    view.setTranslationY(0);
                    view.setElevation(dp(4));
                }
                return false;
            });
            RadioGroup.LayoutParams optionLp = new RadioGroup.LayoutParams(-1, -2);
            optionLp.setMargins(0, dp(5), 0, dp(5));
            group.addView(option, optionLp);
            if (i == checked) option.setChecked(true);
        }
        TextView note = new TextView(this);
        note.setText("اسکن زنده، پاسخ‌ها را از روی جایگاه حباب‌ها بررسی می‌کند. این تنظیم فقط تعیین می‌کند تصاویر پاسخنامه نگه‌داری شوند یا نه؛ اگر پاسخ‌ها با اطمینان کافی تشخیص داده نشوند، نمرهٔ قطعی ثبت نمی‌شود.");
        note.setTextSize(14);
        note.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(group);
        layout.addView(note);
        styledDialogBuilder()
            .setTitle("حفظ حریم خصوصی تصاویر")
            .setView(layout)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره انتخاب", (dialog, which) -> {
                int id = group.getCheckedRadioButtonId();
                if (id == -1) {
                    info("یکی از سه گزینه را انتخاب کن.");
                    return;
                }
                RadioButton selected = group.findViewById(id);
                String policy = String.valueOf(selected.getTag());
                prefs.edit().putString("image_retention_policy", policy).apply();
                String confirmation;
                if ("never".equals(policy)) {
                    confirmation = "سیاست انتخابی ذخیره‌نکردن تصاویر ثبت شد. در نسخهٔ فعلی نیز تصاویر ذخیره نمی‌شوند.";
                } else if ("all".equals(policy)) {
                    confirmation = "ترجیح ذخیرهٔ همهٔ تصاویر ثبت شد؛ اما این قابلیت تا پیاده‌سازی ذخیره‌سازی تصویر هنوز فعال نیست.";
                } else {
                    confirmation = "ترجیح ذخیرهٔ تصاویر مشکوک ثبت شد؛ تشخیص موارد مشکوک و ذخیرهٔ آن‌ها تا پیاده‌سازی تحلیل تصویر هنوز فعال نیست.";
                }
                info(confirmation);
            }).show();
    }

    private android.graphics.drawable.GradientDrawable turquoiseButtonBackground(boolean filled) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                filled
                        ? new int[]{Color.rgb(255, 105, 180), Color.rgb(236, 72, 153), Color.rgb(190, 24, 93)}
                        : new int[]{Color.rgb(255, 240, 248), Color.rgb(255, 218, 238), Color.rgb(252, 165, 211)});
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(2), Color.rgb(219, 39, 119));
        return bg;
    }

    private void styleTurquoiseDashboardButton(Button button) {
        // Match the dashboard: alternating glossy electric blue and vivid green oval buttons.
        stylePinkOvalButton(button);
    }

    private void showTurquoiseMenu(String title, String[] items, java.util.function.IntConsumer onChoose,
                                   String backLabel, Runnable onBack) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(12), dp(8), dp(12), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(menu)
            .setNegativeButton(backLabel, (d, w) -> { if (onBack != null) onBack.run(); }).create();
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            Button button = new Button(this);
            button.setText(items[i]);
            styleTurquoiseDashboardButton(button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
            lp.setMargins(0, dp(5), 0, dp(5));
            menu.addView(button, lp);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                onChoose.accept(index);
            });
        }
        dialog.show();
    }

    private void showReportsCenter() {
        showTurquoiseMenu("آنالیز و آمار",
            new String[]{"نتایج آزمون", "صندوق گزارش‌ها و حریم خصوصی"},
            which -> { if (which == 0) showExamResultsMenu(); else showReportsPrivacyDetails(); },
            "بستن", null);
    }

    private JSONArray readGradingResults() {
        try { return new JSONArray(prefs.getString("grading_results", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showCorrectionResults() {
        JSONArray results = readGradingResults();
        if (results.length() == 0) {
            new AlertDialog.Builder(this).setTitle("نتایج تصحیح")
                .setMessage("هنوز نتیجهٔ تصحیحی ثبت نشده است.")
                .setPositiveButton("باشه", null).show();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        for (int i = results.length() - 1; i >= 0; i--) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) continue;
            String name = result.optString("name", "").trim();
            if (name.isEmpty()) name = "بدون نام";
            String exam = result.optString("examName", "آزمون بدون نام");
            double score = result.optDouble("score", 0.0);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(12), dp(16), dp(12));
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dp(18));
            cardBg.setStroke(dp(1), Color.rgb(218, 228, 250));
            card.setBackground(cardBg);
            card.setElevation(dp(2));

            TextView student = new TextView(this);
            student.setText(name);
            student.setTextSize(18);
            student.setTypeface(null, android.graphics.Typeface.BOLD);
            student.setTextColor(Color.rgb(7, 40, 130));
            student.setGravity(Gravity.RIGHT);
            card.addView(student, new LinearLayout.LayoutParams(-1, -2));

            TextView examLine = new TextView(this);
            examLine.setText(exam);
            examLine.setTextSize(14);
            examLine.setTextColor(Color.rgb(85, 99, 125));
            examLine.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams examLp = new LinearLayout.LayoutParams(-1, -2);
            examLp.topMargin = dp(4);
            card.addView(examLine, examLp);

            TextView grade = new TextView(this);
            grade.setText(String.format(Locale.US, "%.1f", score) + " از 100");
            grade.setTextSize(23);
            grade.setTypeface(null, android.graphics.Typeface.BOLD);
            grade.setTextColor(score >= 50 ? Color.rgb(13, 148, 136) : Color.rgb(220, 38, 38));
            grade.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams gradeLp = new LinearLayout.LayoutParams(-1, -2);
            gradeLp.topMargin = dp(6);
            card.addView(grade, gradeLp);

            TextView stats = new TextView(this);
            stats.setText("درست: " + result.optInt("correct", 0)
                    + "   •   غلط: " + result.optInt("wrong", 0)
                    + "   •   سفید: " + result.optInt("blank", 0)
                    + "   •   بازبینی: " + result.optInt("needsReview", 0));
            stats.setTextSize(13);
            stats.setTextColor(Color.rgb(71, 85, 105));
            stats.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, -2);
            statsLp.topMargin = dp(5);
            card.addView(stats, statsLp);

            Button share = new Button(this);
            share.setText("ارسال گزارش به خانواده");
            styleTurquoiseDashboardButton(share);
            LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(-1, dp(46));
            shareLp.topMargin = dp(8);
            card.addView(share, shareLp);
            final JSONObject chosenResult = result;
            share.setOnClickListener(v -> chooseReportShareMethod(chosenResult));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.setMargins(0, dp(5), 0, dp(9));
            list.addView(card, cardLp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(list);
        new AlertDialog.Builder(this)
            .setTitle("نتایج تصحیح • گزارش دانش‌آموزان")
            .setView(scroll)
            .setPositiveButton("بستن", null)
            .setNeutralButton("جزئیات و آمار", (d, w) -> showReportsCenter())
            .show();
    }

    private String buildGuardianReportText(JSONObject result) {
        String name = result.optString("name", "بدون نام");
        String exam = result.optString("examName", "آزمون بدون نام");
        String score = String.format(Locale.US, "%.1f", result.optDouble("score", 0.0));
        StringBuilder report = new StringBuilder();
        report.append("📘 گزارش نتیجه آزمون — آزمون‌یار\n")
            .append("دانش‌آموز: ").append(name).append("\n")
            .append("آزمون: ").append(exam).append("\n")
            .append("نمره: ").append(score).append(" از 100\n")
            .append("✅ صحیح: ").append(result.optInt("correct", 0)).append("\n")
            .append("❌ غلط: ").append(result.optInt("wrong", 0)).append("\n")
            .append("⬜ بدون پاسخ: ").append(result.optInt("blank", 0)).append("\n")
            .append("🔎 نیازمند بازبینی: ").append(result.optInt("needsReview", 0)).append("\n\n")
            .append("این گزارش از نتیجه ثبت‌شده روی دستگاه تهیه شده است. برای موارد نیازمند بازبینی، نمره را قطعی تلقی نکنید.");
        return report.toString();
    }

    private void chooseReportShareMethod(JSONObject result) {
        final String[] methods = {"واتساپ", "تلگرام", "پیامک", "سایر برنامه‌ها (اشتراک‌گذاری)"};
        new AlertDialog.Builder(this)
            .setTitle("روش ارسال گزارش")
            .setItems(methods, (dialog, which) -> shareGuardianReport(result, which))
            .setNegativeButton("انصراف", null)
            .show();
    }

    private void shareGuardianReport(JSONObject result, int method) {
        String reportText = buildGuardianReportText(result);
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "گزارش نتیجه آزمون — " + result.optString("name", "دانش‌آموز"));
            send.putExtra(Intent.EXTRA_TEXT, reportText);
            if (method == 2) {
                Intent sms = new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"));
                sms.putExtra("sms_body", reportText);
                startActivity(sms);
                return;
            }
            if (method == 0) send.setPackage("com.whatsapp");
            else if (method == 1) send.setPackage("org.telegram.messenger");
            else {
                startActivity(Intent.createChooser(send, "ارسال گزارش با"));
                return;
            }
            if (send.resolveActivity(getPackageManager()) != null) {
                startActivity(send);
            } else {
                Intent fallback = new Intent(Intent.ACTION_SEND);
                fallback.setType("text/plain");
                fallback.putExtra(Intent.EXTRA_TEXT, reportText);
                startActivity(Intent.createChooser(fallback, "برنامه موردنظر نصب نیست؛ ارسال با"));
            }
        } catch (Exception e) {
            info("باز کردن برنامه ارسال گزارش ممکن نشد. لطفاً نصب بودن برنامه را بررسی کن.");
        }
    }

    private void showExamResultsMenu() {
        showTurquoiseMenu("نتایج آزمون",
            new String[]{"نتایج آزمون اخیر", "نتایج قبلی", "آنالیز شرکت‌کننده", "آنالیز هر آزمون", "صف برگه‌های نیازمند بازبینی"},
            which -> {
                if (which == 0) showRecentExamResults();
                else if (which == 1) showPreviousExamResults();
                else if (which == 2) showParticipantAnalysis();
                else if (which == 3) showExamAnalysis();
                else showReviewQueue();
            }, "بازگشت", this::showReportsCenter);
    }

    private JSONArray readReviewQueue() {
        try { return new JSONArray(prefs.getString("grading_review_queue", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showReviewQueue() {
        JSONArray queue = readReviewQueue();
        if (queue.length() == 0) {
            // Recover entries only when saved results contain question-level REVIEW details.
            JSONArray results = readGradingResults();
            JSONArray recovered = new JSONArray();
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.optJSONObject(i);
                if (result == null) continue;
                JSONArray questions = result.optJSONArray("questions");
                int reviewCount = result.optInt("needsReview", 0);
                if (questions != null) {
                    for (int q = 0; q < questions.length(); q++) {
                        JSONObject question = questions.optJSONObject(q);
                        if (question != null && "REVIEW".equalsIgnoreCase(question.optString("status", ""))) reviewCount++;
                    }
                }
                if (reviewCount <= 0) continue;
                JSONObject pending = new JSONObject();
                try {
                    pending.put("name", result.optString("name", "بدون نام"));
                    pending.put("examName", result.optString("examName", "آزمون بدون نام"));
                    pending.put("createdAt", result.optLong("createdAt", System.currentTimeMillis()));
                    pending.put("source", "recovered_from_grading_results");
                    pending.put("needsReview", reviewCount);
                    pending.put("totalQuestions", result.optInt("totalQuestions", questions == null ? 0 : questions.length()));
                    if (questions != null) pending.put("questions", questions);
                    recovered.put(pending);
                } catch (Exception ignored) {}
            }
            if (recovered.length() > 0) {
                prefs.edit().putString("grading_review_queue", recovered.toString()).commit();
                queue = recovered;
            }
        }
        if (queue.length() == 0) {
            new AlertDialog.Builder(this).setTitle("صف بازبینی")
                .setMessage("صف بازبینی خالی است. اگر نتیجه‌های قبلی جزئیات سؤال‌های مبهم را ذخیره نکرده باشند، بازیابی خودکار ممکن نیست. از این نسخه به بعد برگه‌های مبهم باید مستقیماً در صف ذخیره شوند.")
                .setPositiveButton("باشه", null)
                .setNegativeButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
            return;
        }
        String[] labels = new String[queue.length()];
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null) item = new JSONObject();
            labels[i] = (i + 1) + ". " + item.optString("name", "بدون نام")
                + " — " + item.optString("examName", "آزمون بدون نام")
                + " — سؤال مبهم: " + item.optInt("needsReview", 0);
        }
        new AlertDialog.Builder(this).setTitle("برگه‌های نیازمند بازبینی")
            .setItems(labels, (d, which) -> showReviewQueueItem(which))
            .setNegativeButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
    }

    private void showReviewQueueItem(int index) {
        JSONArray queue = readReviewQueue();
        if (index < 0 || index >= queue.length()) { showReviewQueue(); return; }
        JSONObject item = queue.optJSONObject(index);
        if (item == null) { showReviewQueue(); return; }
        StringBuilder detail = new StringBuilder();
        detail.append("شرکت‌کننده: ").append(item.optString("name", "بدون نام"))
            .append("\nآزمون: ").append(item.optString("examName", "آزمون بدون نام"))
            .append("\nسؤال‌های نیازمند بازبینی: ").append(item.optInt("needsReview", 0))
            .append("\n\nوضعیت خواندن سؤال‌ها:\n");
        JSONArray questions = item.optJSONArray("questions");
        if (questions != null) {
            for (int i = 0; i < questions.length(); i++) {
                JSONObject q = questions.optJSONObject(i);
                if (q == null) continue;
                int option = q.optInt("optionIndex", -1);
                detail.append("سؤال ").append(q.optInt("question", i + 1)).append(": ")
                    .append(q.optString("status", "نامشخص"));
                if (option >= 0) detail.append(" (گزینهٔ ").append("ABCD".charAt(Math.min(3, option))).append(")");
                JSONArray optionScores = q.optJSONArray("optionScores");
                if (optionScores != null && optionScores.length() >= 4) {
                    detail.append("\n  تیرگی حباب‌ها — ");
                    for (int j = 0; j < 4; j++) {
                        if (j > 0) detail.append(" | ");
                        detail.append("ABCD".charAt(j)).append(": ")
                            .append(String.format(java.util.Locale.US, "%.2f", optionScores.optDouble(j, 0)));
                    }
                    detail.append("\n");
                }
                detail.append("\n");
            }
        }
        detail.append("\nاین مورد نمرهٔ قطعی ندارد. برای نمره‌دهی، پاسخ‌های مبهم باید با خود برگه تطبیق داده شوند.");
        new AlertDialog.Builder(this).setTitle("جزئیات برگهٔ بازبینی").setMessage(detail.toString())
            .setPositiveButton("حذف از صف", (d, w) -> {
                JSONArray current = readReviewQueue();
                JSONArray kept = new JSONArray();
                for (int i = 0; i < current.length(); i++) {
                    if (i == index) continue;
                    JSONObject entry = current.optJSONObject(i);
                    if (entry != null) kept.put(entry);
                }
                prefs.edit().putString("grading_review_queue", kept.toString()).apply();
                showReviewQueue();
            })
            .setNegativeButton("بازگشت", (d, w) -> showReviewQueue()).show();
    }

    private void showRecentExamResults() {
        JSONArray results = readGradingResults();
        if (results.length() == 0) {
            info("هنوز نتیجه‌ای ثبت نشده است.");
            return;
        }
        showResultList("نتایج آزمون اخیر — همهٔ شرکت‌کنندگان", results, 0, results.length() - 1);
    }

    private void showPreviousExamResults() {
        JSONArray results = readGradingResults();
        if (results.length() == 0) {
            info("هنوز نتیجه‌ای برای نمایش وجود ندارد.");
            return;
        }

        // Group saved results by exam name and calendar date. Selecting a group
        // opens the participants and their scores for that exam session.
        java.util.LinkedHashMap<String, JSONArray> sessions = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<>();
        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        for (int i = results.length() - 1; i >= 0; i--) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) continue;
            long createdAt = result.optLong("createdAt", 0L);
            String date = createdAt > 0 ? dateFormat.format(new java.util.Date(createdAt)) : "تاریخ نامشخص";
            String name = result.optString("examName", "آزمون بدون نام");
            String sessionKey = name + "|" + date;
            if (!sessions.containsKey(sessionKey)) {
                sessions.put(sessionKey, new JSONArray());
                labels.put(sessionKey, name + " — " + date);
            }
            sessions.get(sessionKey).put(result);
        }

        if (sessions.isEmpty()) {
            info("نتیجهٔ آزمونی برای نمایش وجود ندارد.");
            return;
        }

        String[] keys = sessions.keySet().toArray(new String[0]);
        String[] items = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            JSONArray sessionResults = sessions.get(keys[i]);
            items[i] = (i + 1) + ". " + labels.get(keys[i]) +
                    "  (" + sessionResults.length() + " شرکت‌کننده)";
        }

        new AlertDialog.Builder(this)
                .setTitle("نتایج قبلی — انتخاب آزمون")
                .setItems(items, (dialog, which) -> {
                    JSONArray selected = sessions.get(keys[which]);
                    if (selected != null && selected.length() > 0) {
                        showResultList(labels.get(keys[which]), selected, 0, selected.length() - 1);
                    }
                })
                .setNegativeButton("بازگشت", (d, w) -> showExamResultsMenu())
                .show();
    }

    private String persianOcrStatus(String status) {
        if ("ANSWERED".equals(status)) return "پاسخ ثبت‌شده";
        if ("BLANK".equals(status)) return "سفید";
        if ("MULTIPLE_MARKS".equals(status)) return "چند علامت";
        if ("REVIEW".equals(status)) return "نیازمند بازبینی";
        return status.isEmpty() ? "نامشخص" : status;
    }

    private void showResultList(String title, JSONArray results, int first, int last) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(dp(3), dp(3), dp(3), dp(3));

        for (int i = last; i >= first; i--) {
            final JSONObject result = results.optJSONObject(i);
            if (result == null) continue;
            String name = result.optString("name", "بدون نام");
            String exam = result.optString("examName", "—");
            double score = result.optDouble("score", 0.0);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            int color = ((last - i) % 2 == 0) ? Color.rgb(232, 248, 239) : Color.rgb(232, 242, 255);
            int stroke = ((last - i) % 2 == 0) ? Color.rgb(34, 160, 95) : Color.rgb(45, 120, 210);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(2), stroke);
            card.setBackground(bg);

            TextView summary = new TextView(this);
            summary.setText("شرکت‌کننده: " + name + "\nآزمون: " + exam
                    + "\nنمره: " + String.format(Locale.US, "%.1f", score) + " از 100"
                    + "\nدرست: " + result.optInt("correct", 0)
                    + " | غلط: " + result.optInt("wrong", 0)
                    + " | سفید: " + result.optInt("blank", 0)
                    + " | نیازمند بازبینی: " + result.optInt("needsReview", 0));
            summary.setTextSize(16);
            summary.setTextColor(Color.rgb(45, 55, 65));
            summary.setTextDirection(View.TEXT_DIRECTION_RTL);
            summary.setGravity(Gravity.RIGHT);
            card.addView(summary);

            TextView detailText = new TextView(this);
            StringBuilder detail = new StringBuilder("جزئیات تیرگی گزینه‌ها (A، B، C، D):");
            JSONArray questionDetails = result.optJSONArray("questionDetails");
            if (questionDetails == null || questionDetails.length() == 0) {
                detail.append("\nجزئیات عددی برای این نتیجهٔ قدیمی ذخیره نشده است.");
            } else {
                for (int q = 0; q < questionDetails.length(); q++) {
                    JSONObject d = questionDetails.optJSONObject(q);
                    if (d == null) continue;
                    detail.append("\nسؤال ").append(d.optInt("question", q + 1))
                            .append(" — پاسخ ثبت‌شده: ").append(persianOcrStatus(d.optString("status", "")))
                            .append(" | کلید: ").append(d.optString("correctOption", "—"))
                            .append(" | انتخاب: ").append(d.optString("selectedOption", "—"));
                    JSONArray scores = d.optJSONArray("optionScores");
                    if (scores != null && scores.length() == 4) {
                        detail.append("\nA: ").append(String.format(Locale.US, "%.3f", scores.optDouble(0)))
                                .append("  B: ").append(String.format(Locale.US, "%.3f", scores.optDouble(1)))
                                .append("  C: ").append(String.format(Locale.US, "%.3f", scores.optDouble(2)))
                                .append("  D: ").append(String.format(Locale.US, "%.3f", scores.optDouble(3)));
                    }
                }
            }
            detailText.setText(detail.toString());
            detailText.setTextSize(14);
            detailText.setTextColor(Color.rgb(75, 85, 95));
            detailText.setTextDirection(View.TEXT_DIRECTION_RTL);
            detailText.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(8);
            card.addView(detailText, detailLp);

            Button delete = new Button(this);
            delete.setText("پاک کردن این نتیجه");
            delete.setTextColor(Color.WHITE);
            delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(205, 55, 55)));
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(-1, dp(46));
            deleteLp.topMargin = dp(8);
            card.addView(delete, deleteLp);
            delete.setOnClickListener(v -> confirmDeleteGradingResult(result, title, results, first, last));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.setMargins(0, dp(5), 0, dp(8));
            cards.addView(card, cardLp);
        }
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button clearAll = new Button(this);
        clearAll.setText("پاک کردن همهٔ نتایج");
        clearAll.setTextColor(Color.WHITE);
        clearAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(170, 35, 35)));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, dp(50));
        clearLp.setMargins(dp(3), dp(8), dp(3), dp(2));
        root.addView(clearAll, clearLp);
        clearAll.setOnClickListener(v -> confirmClearAllResultsFromList(title));

        new AlertDialog.Builder(this).setTitle(title).setView(root)
                .setPositiveButton("بستن", null)
                .setNeutralButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
    }

    private void confirmDeleteGradingResult(JSONObject target, String title, JSONArray displayed, int first, int last) {
        new AlertDialog.Builder(this)
                .setTitle("پاک کردن نتیجه؟")
                .setMessage("نتیجهٔ «" + target.optString("name", "بدون نام") + "» حذف شود؟")
                .setPositiveButton("پاک شود", (d, w) -> {
                    JSONArray stored = readGradingResults();
                    JSONArray kept = new JSONArray();
                    boolean removed = false;
                    for (int i = 0; i < stored.length(); i++) {
                        JSONObject item = stored.optJSONObject(i);
                        if (!removed && item != null && item.toString().equals(target.toString())) {
                            removed = true;
                        } else if (item != null) kept.put(item);
                    }
                    prefs.edit().putString("grading_results", kept.toString()).commit();
                    info(removed ? "نتیجه پاک شد." : "نتیجه پیدا نشد؛ فهرست تازه شد.");
                    showResultList(title, displayed, first, last);
                })
                .setNegativeButton("انصراف", null).show();
    }

    private void confirmClearAllResultsFromList(String title) {
        new AlertDialog.Builder(this)
                .setTitle("پاک کردن همه نتایج؟")
                .setMessage("تمام نتایج ذخیره‌شده حذف می‌شوند. این کار برگشت‌پذیر نیست.")
                .setPositiveButton("بله، همه پاک شوند", (d, w) -> {
                    prefs.edit().putString("grading_results", "[]").commit();
                    info("تمام نتایج پاک شدند.");
                })
                .setNegativeButton("انصراف", null).show();
    }

    private void showParticipantAnalysis() {
        JSONArray results = readGradingResults();
        if (results.length() == 0) {
            info("برای آنالیز، هنوز نتیجه‌ای ثبت نشده است.");
            return;
        }

        java.util.LinkedHashMap<String, Boolean> participants = new java.util.LinkedHashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r != null) participants.put(r.optString("name", "بدون نام"), true);
        }
        String[] names = participants.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("آنالیز شرکت‌کننده — نام را انتخاب کن")
                .setItems(names, (dialog, which) -> showParticipantExamChoices(names[which], results))
                .setPositiveButton("پاک کردن همه", (dialog, which) -> confirmClearAllParticipantResults())
                .setNegativeButton("بازگشت", (d, w) -> showExamResultsMenu())
                .show();
    }

    private void confirmClearAllParticipantResults() {
        new AlertDialog.Builder(this)
                .setTitle("پاک کردن همه نتایج؟")
                .setMessage("تمام نتایج ثبت‌شدهٔ دانش‌آموزان از حافظهٔ محلی برنامه حذف می‌شود. این کار برگشت‌پذیر نیست. مطمئنی؟")
                .setPositiveButton("بله، پاک شود", (dialog, which) -> {
                    prefs.edit().putString("grading_results", "[]").commit();
                    info("همه نتایج دانش‌آموزان پاک شد.");
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showParticipantExamChoices(String participantName, JSONArray allResults) {
        java.util.ArrayList<JSONObject> participantResults = new java.util.ArrayList<>();
        for (int i = 0; i < allResults.length(); i++) {
            JSONObject r = allResults.optJSONObject(i);
            if (r != null && participantName.equals(r.optString("name", "بدون نام"))) {
                participantResults.add(r);
            }
        }
        java.util.Collections.sort(participantResults, (a, b) ->
                Long.compare(a.optLong("createdAt", 0L), b.optLong("createdAt", 0L)));

        if (participantResults.isEmpty()) {
            info("برای این شرکت‌کننده نتیجه‌ای ثبت نشده است.");
            return;
        }
        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        String[] choices = new String[participantResults.size()];
        for (int i = 0; i < participantResults.size(); i++) {
            JSONObject r = participantResults.get(i);
            long timestamp = r.optLong("createdAt", 0L);
            String date = timestamp > 0 ? dateFormat.format(new java.util.Date(timestamp)) : "تاریخ نامشخص";
            choices[i] = (i + 1) + ". " + r.optString("examName", "آزمون بدون نام") +
                    " — " + date + " — نمره: " +
                    String.format(Locale.US, "%.1f", r.optDouble("score", 0.0));
        }
        new AlertDialog.Builder(this)
                .setTitle("آزمون شرکت‌کننده: " + participantName + "\nآزمون را بر اساس تاریخ یا شماره انتخاب کن")
                .setItems(choices, (dialog, which) -> {
                    JSONObject selected = participantResults.get(which);
                    JSONObject previous = which > 0 ? participantResults.get(which - 1) : null;
                    showParticipantPieChart(participantName, selected, previous, which + 1);
                })
                .setNegativeButton("بازگشت", (d, w) -> showParticipantAnalysis())
                .show();
    }

    private void showParticipantPieChart(String participantName, JSONObject result,
                                         JSONObject previous, int examNumber) {
        int correct = result.optInt("correct", 0);
        int wrong = result.optInt("wrong", 0);
        int blank = result.optInt("blank", 0);
        int total = Math.max(0, correct) + Math.max(0, wrong) + Math.max(0, blank);
        double score = result.optDouble("score", 0.0);
        StringBuilder details = new StringBuilder();
        details.append("شرکت‌کننده: ").append(participantName)
                .append("\nآزمون شماره: ").append(examNumber)
                .append("\nنام آزمون: ").append(result.optString("examName", "—"))
                .append("\nنمره: ").append(String.format(Locale.US, "%.1f", score)).append(" از 100")
                .append("\nسؤال صحیح: ").append(correct)
                .append("\nسؤال غلط: ").append(wrong)
                .append("\nسؤال بی‌پاسخ: ").append(blank);

        if (previous != null) {
            double previousScore = previous.optDouble("score", 0.0);
            double change = score - previousScore;
            details.append("\n\nمقایسه با آزمون قبلی: ")
                    .append(change > 0 ? "پیشرفت " : change < 0 ? "افت " : "بدون تغییر ")
                    .append(String.format(Locale.US, "%.1f", Math.abs(change)))
                    .append(" امتیاز از 100")
                    .append("\nنمره آزمون قبلی: ")
                    .append(String.format(Locale.US, "%.1f", previousScore));
        } else {
            details.append("\n\nاین اولین نتیجهٔ ثبت‌شده برای این شرکت‌کننده است؛ هنوز آزمون قبلی برای مقایسه وجود ندارد.");
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView detailText = new TextView(this);
        detailText.setText(details.toString());
        detailText.setTextSize(15);
        detailText.setTextColor(Color.rgb(30, 41, 59));
        detailText.setTextDirection(View.TEXT_DIRECTION_RTL);
        detailText.setGravity(Gravity.RIGHT);
        content.addView(detailText, new LinearLayout.LayoutParams(-1, -2));

        if (total > 0) {
            View chart = new ParticipantPieChartView(this, correct, wrong, blank);
            LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(230));
            chartParams.topMargin = dp(8);
            content.addView(chart, chartParams);
        } else {
            TextView empty = new TextView(this);
            empty.setText("\nبرای این نتیجه، تعداد پاسخ‌ها ثبت نشده است و نمودار قابل نمایش نیست.");
            content.addView(empty);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("نمودار عملکرد — " + participantName)
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .setNeutralButton("انتخاب آزمون دیگر", (d, w) -> showParticipantExamChoices(participantName, readGradingResults()))
                .show();
    }

    private class ParticipantPieChartView extends View {
        private final int correct, wrong, blank;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF pieBounds = new android.graphics.RectF();

        ParticipantPieChartView(Context context, int correct, int wrong, int blank) {
            super(context);
            this.correct = Math.max(0, correct);
            this.wrong = Math.max(0, wrong);
            this.blank = Math.max(0, blank);
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float radius = Math.min(dp(78), Math.min(getWidth() * 0.28f, getHeight() * 0.38f));
            float cx = getWidth() * 0.72f;
            float cy = getHeight() * 0.48f;
            pieBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            int total = correct + wrong + blank;
            if (total <= 0) return;

            int[] values = {correct, wrong, blank};
            int[] colors = {Color.rgb(22, 163, 74), Color.rgb(220, 38, 38), Color.rgb(148, 163, 184)};
            float start = -90f;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == 0) continue;
                float sweep = 360f * values[i] / total;
                paint.setColor(colors[i]);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawArc(pieBounds, start, sweep, true, paint);
                start += sweep;
            }

            String[] labels = {"صحیح: " + correct, "غلط: " + wrong, "بی‌پاسخ: " + blank};
            paint.setTextSize(dp(14));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(android.graphics.Paint.Align.RIGHT);
            for (int i = 0; i < labels.length; i++) {
                float y = dp(62) + i * dp(34);
                paint.setColor(colors[i]);
                canvas.drawRoundRect(getWidth() - dp(18), y - dp(10), getWidth() - dp(5), y + dp(3),
                        dp(3), dp(3), paint);
                paint.setColor(Color.rgb(30, 41, 59));
                canvas.drawText(labels[i], getWidth() - dp(28), y, paint);
            }
        }
    }

    private void showExamAnalysis() {
        JSONArray results = readGradingResults();
        if (results.length() == 0) { info("برای آنالیز، هنوز نتیجه‌ای ثبت نشده است."); return; }
        java.util.LinkedHashMap<String, double[]> totals = new java.util.LinkedHashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            String name = r.optString("examName", "آزمون بدون نام");
            double[] t = totals.get(name);
            if (t == null) { t = new double[2]; totals.put(name, t); }
            t[0] += r.optDouble("score", 0.0); t[1]++;
        }

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(dp(12), dp(8), dp(12), dp(8));
        for (java.util.Map.Entry<String, double[]> entry : totals.entrySet()) {
            final double average = entry.getValue()[0] / entry.getValue()[1];
            final int count = (int) entry.getValue()[1];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), Color.rgb(220, 230, 245));
            card.setBackground(bg);
            card.setElevation(dp(2));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            TextView examTitle = new TextView(this);
            examTitle.setText(entry.getKey());
            examTitle.setTextSize(18);
            examTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            examTitle.setTextColor(Color.rgb(15, 35, 90));
            examTitle.setGravity(Gravity.RIGHT);
            details.addView(examTitle, new LinearLayout.LayoutParams(-1, -2));

            TextView countText = new TextView(this);
            countText.setText("👥 تعداد نتایج ثبت‌شده: " + count);
            countText.setTextSize(14);
            countText.setTextColor(Color.rgb(71, 85, 105));
            countText.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-1, -2);
            countLp.topMargin = dp(8);
            details.addView(countText, countLp);

            TextView averageText = new TextView(this);
            averageText.setText("میانگین کلاس: " + String.format(Locale.US, "%.1f", average) + " از 100");
            averageText.setTextSize(15);
            averageText.setTypeface(null, android.graphics.Typeface.BOLD);
            averageText.setTextColor(Color.rgb(13, 148, 136));
            averageText.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams avgLp = new LinearLayout.LayoutParams(-1, -2);
            avgLp.topMargin = dp(4);
            details.addView(averageText, avgLp);

            CircleScoreView circle = new CircleScoreView(this, average, count);
            LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(116), dp(116));
            circleLp.setMargins(dp(12), 0, 0, 0);
            card.addView(circle, circleLp);
            card.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.setMargins(0, dp(5), 0, dp(10));
            cards.addView(card, cardLp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(cards);
        new AlertDialog.Builder(this).setTitle("آمار دایره‌ای آزمون‌ها")
            .setView(scroll).setPositiveButton("بستن", null)
            .setNeutralButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
    }

    private class CircleScoreView extends View {
        private final double score;
        private final int count;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        CircleScoreView(Context context, double score, int count) {
            super(context);
            this.score = Math.max(0, Math.min(100, score));
            this.count = count;
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.39f;
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(dp(9));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setColor(Color.rgb(226, 232, 240));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(score >= 70 ? Color.rgb(13, 148, 136) : score >= 50 ? Color.rgb(245, 158, 11) : Color.rgb(239, 68, 68));
            android.graphics.RectF arc = new android.graphics.RectF(cx-radius, cy-radius, cx+radius, cy+radius);
            canvas.drawArc(arc, -90, (float)(score * 3.6), false, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(Color.rgb(15, 23, 42));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextSize(dp(20));
            canvas.drawText(String.format(Locale.US, "%.1f", score), cx, cy + dp(2), paint);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            paint.setTextSize(dp(10));
            paint.setColor(Color.rgb(100, 116, 139));
            canvas.drawText("از 100", cx, cy + dp(18), paint);
        }
    }

    private void showAnalysisText(String title, String value) {
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(16);
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setGravity(Gravity.RIGHT);
        text.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton("بستن", null)
            .setNeutralButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
    }

    private void showReportsPrivacyDetails() {
        new AlertDialog.Builder(this)
            .setTitle("صندوق گزارش‌ها و حریم خصوصی")
            .setItems(new String[]{"صندوق گزارش‌های محلی", "تنظیمات و راهنمای حریم خصوصی", "راهنمای ارسال نتیجه به خانواده"}, (dialog, which) -> {
                if (which == 0) showLocalReportInbox();
                else if (which == 1) showPrivacyGuidance();
                else showGuardianShareGuidance();
            })
            .setNegativeButton("بستن", null)
            .show();
    }

    private void showLocalReportInbox() {
        ReportStore reportStore = new ReportStore(this);
        JSONArray inbox = reportStore.getInbox();
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (inbox.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("هنوز گزارشی در صندوق محلی ثبت نشده است.\nگزارش‌های تصحیح دانش‌آموزان از بخش «نتایج تصحیح» قابل اشتراک‌گذاری هستند.");
            empty.setTextSize(16);
            empty.setTextColor(Color.rgb(71, 85, 105));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            items.addView(empty);
        } else {
            for (int i = inbox.length() - 1; i >= 0; i--) {
                JSONObject item = inbox.optJSONObject(i);
                if (item == null) continue;
                TextView card = new TextView(this);
                card.setText(item.optString("title", "گزارش") + "\n"
                    + item.optString("body", "") + "\n"
                    + item.optString("created_at", ""));
                card.setTextSize(15);
                card.setTextColor(Color.rgb(30, 41, 59));
                card.setGravity(Gravity.RIGHT);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(Color.WHITE);
                bg.setCornerRadius(dp(16));
                bg.setStroke(dp(1), Color.rgb(218, 228, 250));
                card.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(4), 0, dp(8));
                items.addView(card, lp);
                reportStore.markRead(item.optString("id", ""));
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(items);
        new AlertDialog.Builder(this).setTitle("صندوق گزارش‌های محلی")
            .setView(scroll).setPositiveButton("بستن", null)
            .setNegativeButton("حریم خصوصی", (d, w) -> showPrivacyGuidance()).show();
    }

    private void showPrivacyGuidance() {
        String message = "🔐 حریم خصوصی و کنترل ارسال\n\n"
            + "• نتایج تصحیح و صندوق گزارش‌ها روی همین دستگاه نگه‌داری می‌شوند.\n"
            + "• ارسال نتیجه فقط پس از انتخاب شما و باز کردن برنامه مقصد انجام می‌شود؛ برنامه به‌صورت خودکار برای والدین پیام نمی‌فرستد.\n"
            + "• قبل از ارسال، نام دانش‌آموز و نمره را بررسی کنید و فقط با رضایت خانواده اشتراک‌گذاری کنید.\n"
            + "• انتخاب واتساپ/تلگرام به معنی تأیید هویت یا شماره تماس والدین نیست.\n"
            + "• برنامه فعلاً شماره والدین را با OTP تأیید نمی‌کند و گزارش ابری یا ارسال زمان‌بندی‌شده ندارد.\n"
            + "• داده‌های خصوصی یا جزئیات پاسخ‌ها را بدون ضرورت ارسال نکنید.\n\n"
            + "برای حذف یا مدیریت گزارش‌های ذخیره‌شده، از تنظیمات دستگاه و داده‌های محلی برنامه استفاده کنید.";
        new AlertDialog.Builder(this).setTitle("حریم خصوصی و رضایت")
            .setMessage(message).setPositiveButton("متوجه شدم", null)
            .setNeutralButton("راهنمای ارسال", (d, w) -> showGuardianShareGuidance()).show();
    }

    private void showGuardianShareGuidance() {
        String message = "۱) وارد «نتایج تصحیح» شوید.\n"
            + "۲) کارت دانش‌آموز موردنظر را پیدا کنید.\n"
            + "۳) «ارسال گزارش به خانواده» را بزنید.\n"
            + "۴) واتساپ، تلگرام، پیامک یا سایر برنامه‌ها را انتخاب کنید.\n"
            + "۵) مخاطب خانواده را خودتان انتخاب کرده و متن را قبل از ارسال مرور کنید.\n\n"
            + "گزارش شامل نام دانش‌آموز، نام آزمون، نمره از ۱۰۰ و تعداد پاسخ‌های صحیح، غلط، سفید و نیازمند بازبینی است.";
        new AlertDialog.Builder(this).setTitle("ارسال گزارش به خانواده")
            .setMessage(message).setPositiveButton("باشه", null).show();
    }

    private void info(String message) {
        showStyledInfo(message);
    }
}