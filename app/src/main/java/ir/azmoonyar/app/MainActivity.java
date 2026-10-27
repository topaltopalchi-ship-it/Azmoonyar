package ir.azmoonyar.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
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
    private String pendingKeyTarget = "main";

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String examName() { return prefs.getString("exam_name", ""); }
    private int questionCount() { return prefs.getInt("question_count", 20); }
    private String key() { return prefs.getString("answer_key", ""); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
        seedOriginalPdfVersionsIfMissing();
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
        AlertDialog dialog = new AlertDialog.Builder(this)
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
                android.graphics.drawable.GradientDrawable buttonBg = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Color.rgb(37, 99, 235), Color.rgb(79, 70, 229)});
                buttonBg.setCornerRadius(dp(12));
                ok.setBackground(buttonBg);
                ok.setTextColor(Color.WHITE);
                ok.setTypeface(null, android.graphics.Typeface.BOLD);
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
        free.setAllCaps(false);
        free.setTextSize(17);
        free.setTypeface(null, android.graphics.Typeface.BOLD);
        free.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable freeBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(37, 99, 235), Color.rgb(79, 70, 229)});
        freeBg.setCornerRadius(dp(18));
        freeBg.setStroke(dp(2), Color.rgb(147, 197, 253));
        free.setBackground(freeBg);
        free.setElevation(dp(6));
        page.addView(free, new LinearLayout.LayoutParams(-1, dp(60)));
        free.setOnClickListener(v -> {
            prefs.edit().putString("entry_mode", "free_local").apply();
            buildScreen();
        });

        Button create = new Button(this);
        create.setText("ساخت حساب");
        create.setAllCaps(false);
        create.setTextSize(16);
        create.setTypeface(null, android.graphics.Typeface.BOLD);
        create.setTextColor(Color.rgb(30, 64, 130));
        android.graphics.drawable.GradientDrawable createBg = new android.graphics.drawable.GradientDrawable();
        createBg.setColor(Color.WHITE);
        createBg.setCornerRadius(dp(18));
        createBg.setStroke(dp(2), Color.rgb(147, 177, 230));
        create.setBackground(createBg);
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
        note.setTextSize(13);
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
        root.setPadding(dp(22),dp(30),dp(22),dp(24));
        root.setBackgroundColor(Color.rgb(245,248,255));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("آزمون‌یار");
        title.setTextSize(32); title.setTextColor(Color.rgb(7,40,130));
        title.setGravity(Gravity.CENTER); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setShadowLayer(dp(1), 0, dp(1), Color.rgb(210,222,250));
        root.addView(title, new LinearLayout.LayoutParams(-1,dp(62)));

        TextView subtitle = new TextView(this);
        subtitle.setText("مدیریت آزمون و کلید پاسخ — آفلاین");
        subtitle.setTextSize(16); subtitle.setTextColor(Color.rgb(65,80,115)); subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.bottomMargin=dp(22); root.addView(subtitle,sp);

        summary = new TextView(this);
        summary.setTextSize(15); summary.setTextColor(Color.rgb(30,45,80)); summary.setGravity(Gravity.CENTER);
        summary.setTypeface(null, android.graphics.Typeface.BOLD);
        summary.setPadding(dp(16),dp(18),dp(16),dp(18));
        android.graphics.drawable.GradientDrawable summaryBg = new android.graphics.drawable.GradientDrawable();
        summaryBg.setColor(Color.WHITE);
        summaryBg.setCornerRadius(dp(20));
        summaryBg.setStroke(dp(1), Color.rgb(218,228,250));
        summary.setBackground(summaryBg);
        summary.setElevation(dp(3));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1,-2);
        summaryParams.bottomMargin = dp(12);
        root.addView(summary,summaryParams);
        refreshSummary();

        // Keep one clear entry point per task; participant names are captured by voice during grading.
        addButtonGrid(
                new String[]{"تعریف / ویرایش آزمون", "فرم سؤال‌ها"},
                new View.OnClickListener[]{v -> showExamDialog(), v -> showQuestionBank()});
        addButtonGrid(
                new String[]{"کلید پاسخ", "اسکن و تصحیح پاسخنامه"},
                new View.OnClickListener[]{v -> showKeyDialog(), v -> openScanner()});
        addButtonGrid(
                new String[]{"آنالیز و آمار", "تنظیمات تصاویر"},
                new View.OnClickListener[]{v -> showReportsCenter(), v -> showImageRetentionDialog()});
        TextView note = new TextView(this);
        note.setText("اسکن زنده QR بدون ذخیره عکس اضافه شده است. خواندن حباب‌های پاسخ و نمره‌دهی خودکار هنوز در حال توسعه است.");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(20); root.addView(note,np);
        setContentView(scroll);
    }

    private void openScanner() {
        if (examName().trim().isEmpty()) {
            info("ابتدا آزمون را تعریف کن؛ کد QR هر پاسخنامه باید نام همان آزمون را داشته باشد.");
            return;
        }
        startActivity(new Intent(this, ScanActivity.class));
    }

    private void addButtonGrid(String[] labels, View.OnClickListener[] listeners) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(5);
        rowParams.bottomMargin = dp(5);
        root.addView(row, rowParams);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(labels[i].length() > 22 ? 12 : 13);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setTextColor(Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(dp(66));
            b.setPadding(dp(7), dp(8), dp(7), dp(8));
            b.setMaxLines(2);
            // Subtle glass-style buttons: consistent palette avoids visual noise from competing gradients.
            int[][] palette = {
                {Color.argb(226, 255, 255, 255), Color.argb(238, 235, 243, 255)},
                {Color.argb(226, 255, 255, 255), Color.argb(238, 229, 245, 255)},
                {Color.argb(226, 255, 255, 255), Color.argb(238, 225, 247, 245)}
            };
            int colorIndex = Math.abs(labels[i].hashCode()) % palette.length;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, palette[colorIndex]);
            bg.setCornerRadius(dp(22));
            bg.setStroke(dp(1), Color.argb(210, 155, 177, 225));
            b.setBackground(bg);
            b.setTextColor(Color.rgb(30, 64, 130));
            b.setElevation(dp(5));
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
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(68), 1f);
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
        summary.setText(name+"\nتعداد سؤال: "+questionCount()+"\nکلید ثبت‌شده: "+filled+" از "+questionCount()+"\nشرکت‌کنندگان ثبت‌شده: "+studentCount+"\nپاسخنامه‌های دارای QR ثبت‌شده: "+scanned);
    }

    private void showExamDialog() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8),dp(6),dp(8),dp(2));
        EditText name=new EditText(this); styleInput(name); name.setSingleLine(true); name.setHint("نام آزمون"); name.setText(examName()); form.addView(name);
        EditText count=new EditText(this); styleInput(count); count.setSingleLine(true); count.setInputType(2); count.setHint("تعداد سؤال (۱ تا ۱۰۰۰)"); count.setText(String.valueOf(questionCount())); form.addView(count);
        new AlertDialog.Builder(this).setTitle("تعریف آزمون").setView(form)
          .setNegativeButton("انصراف",null).setPositiveButton("ذخیره",(d,w)->{
            String n=name.getText().toString().trim(); int c;
            try { c=Integer.parseInt(count.getText().toString().trim()); } catch(Exception e) { info("تعداد سؤال را به‌صورت عدد وارد کن."); return; }
            if(n.isEmpty() || c<1 || c>1000) { info("نام آزمون لازم است و تعداد سؤال باید بین ۱ تا ۱۰۰۰ باشد."); return; }
            String old=key(); StringBuilder nk=new StringBuilder();
            for(int i=0;i<c;i++) nk.append(i<old.length()?old.charAt(i):'-');
            prefs.edit().putString("exam_name",n).putInt("question_count",c).putString("answer_key",nk.toString()).apply();
            refreshSummary(); info("آزمون روی دستگاه ذخیره شد.");
          }).show();
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
                    .append(student.optString("name", "بدون نام"))
                    .append("\n");
        }
        if (list.length() == 0) list.append("هنوز شرکت‌کننده‌ای ثبت نشده است.\n");
        new AlertDialog.Builder(this)
                .setTitle("فهرست شرکت‌کنندگان")
                .setMessage(list.toString())
                .setPositiveButton("افزودن شرکت‌کننده", (d, w) -> showAddStudentDialog())
                .setNeutralButton("بستن", null)
                .show();
    }

    private void showAddStudentDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(6), dp(8), dp(2));
        EditText name = new EditText(this);
        styleInput(name);
        name.setSingleLine(true);
        name.setHint("نام و نام خانوادگی شرکت‌کننده");
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView voiceStatus = new TextView(this);
        voiceStatus.setText("برای ثبت صوتی، دکمهٔ میکروفون را بزن.");
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
        form.addView(voice, new LinearLayout.LayoutParams(-1, -2));
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

    private void showPrintStudentDialog() {
        // Original PDF versions are printed from their immutable stored order when available.
        if (prefs.contains("pdf_original_version_A") && prefs.contains("pdf_original_version_B")) {
            new AlertDialog.Builder(this)
                .setTitle("چاپ پرسشنامه و پاسخنامه")
                .setItems(new String[]{"چاپ دو نسخهٔ اصلی PDF ثبت‌شده", "ساخت نسخه‌های تصادفی جدید"}, (dialog, which) -> {
                    if (which == 0) previewAndPrintOriginalPdfVersions();
                    else generateRandomVariants();
                })
                .setNegativeButton("انصراف", null)
                .show();
        } else {
            generateRandomVariants();
        }
    }

    private void previewAndPrintOriginalPdfVersions() {
        try {
            JSONArray variants = new JSONArray();
            variants.put(new JSONObject(prefs.getString("pdf_original_version_A", "{}")));
            variants.put(new JSONObject(prefs.getString("pdf_original_version_B", "{}")));
            StringBuilder preview = new StringBuilder("نسخه‌ها دقیقاً با ترتیب سؤال‌ها و گزینه‌های فایل اصلی چاپ می‌شوند.\\n");
            for (int i = 0; i < variants.length(); i++) {
                JSONObject variant = variants.optJSONObject(i);
                preview.append("\\nنسخه ").append(i == 0 ? "۱" : "۲").append(" — کلید: ")
                    .append(variant == null ? "" : variant.optString("answer_key", "")).append("\\n");
                JSONArray questions = variant == null ? null : variant.optJSONArray("questions");
                if (questions != null) for (int q = 0; q < questions.length(); q++) {
                    JSONObject item = questions.optJSONObject(q);
                    if (item != null) preview.append(q + 1).append(". ").append(item.optString("text", "")).append("\\n");
                }
            }
            new AlertDialog.Builder(this).setTitle("پیش‌نمایش نسخه‌های اصلی")
                .setMessage(preview.toString())
                .setNegativeButton("لغو", null)
                .setPositiveButton("تأیید، ذخیره و چاپ", (d, w) ->
                    printPairedSheets("", UUID.randomUUID().toString(), variants.length(), variants))
                .show();
        } catch (Exception e) {
            info("نسخه‌های اصلی ذخیره‌شده خوانده نشدند؛ دوباره از بانک سؤال ثبتشان کن.");
        }
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
            final int pageCount = (total + 99) / 100;
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
                .append(".meta{font-size:10px;margin:2mm 0}.qr{width:25mm;height:25mm;float:left}.clear{clear:both}")
                .append(".instructions{font-size:9px;border:1px solid #333;padding:2mm;margin:2mm 0}")
                .append(".question{font-size:11px;margin:0 0 3mm;page-break-inside:avoid}")
                .append(".options{display:grid;grid-template-columns:1fr 1fr;gap:1mm;margin:1mm 5mm 0;font-size:10px}")
                .append(".cols{display:grid;grid-template-columns:1fr 1fr;gap:4mm;margin-top:3mm}")
                .append(".col{border:0.3mm solid #111;padding:2mm 1.5mm;border-radius:1mm}")
                .append("table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:10px}")
                .append("th,td{border:0.25mm solid #555;text-align:center;height:7mm;padding:0}")
                .append(".num{font-weight:bold;width:19%}.bubble{display:inline-block;width:4mm;height:4mm;border:0.35mm solid #111;border-radius:50%}")
                .append(".mark{position:absolute;width:5mm;height:5mm;background:#000}.tl{top:4mm;left:4mm}.tr{top:4mm;right:4mm}.bl{bottom:4mm;left:4mm}.br{bottom:4mm;right:4mm}")
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
                // Print question text on its own pages (25 questions per page). QR codes
                // belong only on answer sheets, so scanning a question page cannot be counted
                // as a scanned answer sheet.
                final int questionsPerPage = 25;
                final int questionPageCount = (total + questionsPerPage - 1) / questionsPerPage;
                for (int questionPage = 1; questionPage <= questionPageCount; questionPage++) {
                    int questionFirst = (questionPage - 1) * questionsPerPage;
                    int questionLast = Math.min(questionPage * questionsPerPage, total);
                    html.append("<section class='page'><h1 class='head'>").append(safeExam)
                        .append(" — برگه سؤال‌ها</h1>")
                        .append("<div class='meta'>صفحه ").append(questionPage).append(" از ")
                        .append(questionPageCount).append(" | سؤال‌های ").append(questionFirst + 1)
                        .append(" تا ").append(questionLast).append("</div><hr/>");
                    for (int i = questionFirst; i < questionLast; i++) {
                        JSONObject q = questions.optJSONObject(i);
                        if (q == null) continue;
                        html.append("<article class='question'><b>").append(i + 1).append(". ")
                            .append(escapeHtml(q.optString("text", ""))).append("</b><div class='options'>");
                        JSONArray options = q.optJSONArray("options");
                        if (options != null) for (int j = 0; j < Math.min(4, options.length()); j++)
                            html.append("<div>").append((char)('A' + j)).append(") ")
                                .append(escapeHtml(options.optString(j, ""))).append("</div>");
                        html.append("</div></article>");
                    }
                    html.append("</section>");
                }

                for (int page = 1; page <= pageCount; page++) {
                    String payload = "AZY3|" + encodedExamName + "|" + sheetSetId + "|"
                            + modelId + "|T1|" + page + "|" + pageCount;
                    com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320);
                    Bitmap qr = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
                    for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++)
                        qr.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    qr.compress(Bitmap.CompressFormat.PNG, 100, output);
                    String qr64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                    int first = (page - 1) * 100;
                    int last = Math.min(page * 100, total);
                    html.append("<section class='page'><span class='mark tl'></span><span class='mark tr'></span>")
                        .append("<span class='mark bl'></span><span class='mark br'></span>")
                        .append("<h1 class='head'>").append(safeExam).append(" — پاسخنامه</h1>")
                        .append("<img class='qr' src='data:image/png;base64,").append(qr64).append("'/>")
                        .append("<div class='meta'>نام و نام خانوادگی: ........................................................</div>")
                        .append("<div class='meta'>صفحه ").append(page).append(" از ").append(pageCount)
                        .append(" | سؤال‌های ").append(first + 1).append(" تا ").append(last).append("</div>")
                        .append("<div class='clear'></div><div class='instructions'>برای هر سؤال فقط یک دایره را کاملاً پر کنید. شماره سؤال‌ها در صفحه‌ها پیوسته است.</div><div class='cols'>");
                    for (int col = 0; col < 2; col++) {
                        int colFirst = first + col * 50;
                        int colLast = Math.min(first + (col + 1) * 50, last);
                        if (colFirst >= last) continue;
                        html.append("<div class='col'><table><thead><tr><th>سؤال</th><th>A</th><th>B</th><th>C</th><th>D</th></tr></thead><tbody>");
                        for (int q = colFirst; q < colLast; q++) {
                            html.append("<tr><td class='num'>").append(q + 1).append("</td>");
                            for (int option = 0; option < 4; option++) html.append("<td><span class='bubble'></span></td>");
                            html.append("</tr>");
                        }
                        html.append("</tbody></table></div>");
                    }
                    html.append("</div></section>");
                }
                // Save an immutable grading snapshot for this exact printed batch and model.
                // The QR contains sheetSetId + modelId, so grading can retrieve this exact key
                // even after another batch is generated with a different question/option order.
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

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Prints the standard OMR sheet as one A4 page per 100 questions. */
    private void printStandardAnswerSheets() {
        if (examName().trim().isEmpty()) {
            info("ابتدا آزمون را تعریف کن.");
            return;
        }
        final int total = questionCount();
        if (total < 1 || total > 1000) {
            info("تعداد سؤال باید بین ۱ تا ۱۰۰۰ باشد.");
            return;
        }
        try {
            String safeExam = escapeHtml(examName());
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='utf-8'><style>")
                .append("@page{size:A4 portrait;margin:0}*{box-sizing:border-box}")
                .append("body{margin:0;font-family:sans-serif;color:#111;direction:rtl}")
                .append(".sheet{width:210mm;height:297mm;padding:10mm 12mm 9mm;position:relative;page-break-after:always;overflow:hidden}")
                .append(".sheet:last-child{page-break-after:auto}")
                .append(".head{text-align:center;font-size:17px;font-weight:bold;margin:0 0 2mm}")
                .append(".sub{text-align:center;font-size:9px;margin:0 0 4mm}")
                .append(".meta{font-size:10px;margin:2mm 0 3mm;display:flex;justify-content:space-between}")
                .append(".instruction{font-size:9px;border:0.3mm solid #777;padding:2mm;margin-bottom:3mm}")
                .append(".cols{display:grid;grid-template-columns:1fr 1fr;gap:4mm}")
                .append(".col{border:0.35mm solid #111;border-radius:2mm;padding:2mm 1.5mm}")
                .append("table{width:100%;border-collapse:collapse;table-layout:fixed}")
                .append("th{font-size:9px;height:6mm;border-bottom:0.3mm solid #333}")
                .append("td{height:4.45mm;text-align:center;padding:0;font-size:8px}")
                .append("td.num{width:13mm;font-weight:bold;text-align:center}")
                .append("td.opt{width:10mm}")
                .append(".bubble{display:inline-block;width:3.6mm;height:2.7mm;border:0.35mm solid #111;border-radius:50%}")
                .append(".marks{position:absolute;width:3mm;height:3mm;border:0.4mm solid #111}")
                .append(".tl{left:5mm;top:5mm}.tr{right:5mm;top:5mm}.bl{left:5mm;bottom:5mm}.br{right:5mm;bottom:5mm}")
                .append(".foot{position:absolute;bottom:7mm;left:12mm;right:12mm;text-align:center;font-size:7px}")
                .append("</style></head><body>");
            int pages = (total + 99) / 100;
            for (int page = 0; page < pages; page++) {
                int first = page * 100 + 1;
                int last = Math.min(total, first + 99);
                html.append("<section class='sheet'>")
                    .append("<i class='marks tl'></i><i class='marks tr'></i><i class='marks bl'></i><i class='marks br'></i>")
                    .append("<h1 class='head'>آزمون‌یار | پاسخنامه چهارگزینه‌ای</h1>")
                    .append("<p class='sub'>قالب مادر AZY-OMR-100-v1 • برگه ").append(page + 1).append(" از ").append(pages).append("</p>")
                    .append("<div class='meta'><span>آزمون: ").append(safeExam).append("</span><span>سؤال‌های ").append(first).append(" تا ").append(last).append("</span></div>")
                    .append("<div class='instruction'>برای هر سؤال فقط یک حباب را کاملاً پر کنید. شماره سؤال‌ها بین برگه‌ها پیوسته است؛ از پاک‌کن تمیز استفاده کنید.</div>")
                    .append("<div class='cols'>");
                for (int col = 0; col < 2; col++) {
                    int colFirst = first + col * 50;
                    int colLast = Math.min(last, colFirst + 49);
                    html.append("<div class='col'><table><thead><tr><th>سؤال</th><th>الف</th><th>ب</th><th>ج</th><th>د</th></tr></thead><tbody>");
                    for (int q = colFirst; q <= colLast; q++) {
                        html.append("<tr><td class='num'>").append(q).append("</td>");
                        for (int option = 0; option < 4; option++) html.append("<td class='opt'><span class='bubble'></span></td>");
                        html.append("</tr>");
                    }
                    html.append("</tbody></table></div>");
                }
                html.append("</div><div class='foot'>AZY-OMR-100-v1 • A4 • چهار گزینه • صفحه ")
                    .append(page + 1).append(" از ").append(pages).append("</div></section>");
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
                .append(".num{width:15%}.bubble{display:inline-block;width:4.5mm;height:4.5mm;border:0.4mm solid #111;border-radius:50%}")
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
        JSONArray bank = readQuestionBank();
        String value = buildOriginalBankKey();
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
        note.setText("این تنها کلید پاسخ برنامه است. کلید به‌ترتیب سؤال‌های بانک ساخته می‌شود و پاسخ صحیحی را نشان می‌دهد که هنگام ثبت هر سؤال انتخاب کرده‌ای.");
        note.setTextSize(14);
        note.setPadding(0, dp(8), 0, dp(8));
        form.addView(note);

        TextView currentKey = new TextView(this);
        currentKey.setTextSize(15);
        currentKey.setTextColor(Color.rgb(30, 41, 59));
        currentKey.setText(formatAnswerKey(value, questionCount()));
        form.addView(currentKey);

        int filled = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) >= 'A' && value.charAt(i) <= 'D') filled++;
        }
        TextView completion = new TextView(this);
        completion.setText("پاسخ‌های ثبت‌شده: " + filled + " از " + questionCount());
        completion.setTextSize(14);
        completion.setTypeface(null, android.graphics.Typeface.BOLD);
        completion.setPadding(0, dp(8), 0, 0);
        form.addView(completion);

        new AlertDialog.Builder(this)
            .setTitle("کلید پاسخ اصلی")
            .setView(form)
            .setNegativeButton("بستن", null)
            .setNeutralButton("بارگذاری تصویر کلید", (d, w) -> {
                pendingKeyTarget = "main";
                pickAnswerKeyImage();
            })
            .setPositiveButton("چاپ / ذخیره کلید", (d, w) -> showKeyOutputChoices("main"))
            .show();
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
        String value = "main".equals(target) ? buildOriginalBankKey() : getGeneratedModelKey(target);
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
        if (requestCode != REQUEST_ANSWER_KEY_IMAGE) return;
        android.net.Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
        prefs.edit().putString("answer_key_image_" + pendingKeyTarget, uri.toString()).apply();
        info("تصویر کلید ذخیره شد. تشخیص خودکار حباب‌ها هنوز فعال نیست.");
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
        out.insert(0, "تکمیل: " + filled + "/" + count + "\\n");
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
        Button add = new Button(this); add.setText("افزودن سؤال"); add.setAllCaps(false);
        add.setOnClickListener(v -> showAddBankQuestionDialog()); panel.addView(add);
        Button clearBank = new Button(this); clearBank.setText("پاک کردن بانک سؤال / فرم واحد"); clearBank.setAllCaps(false);
        clearBank.setOnClickListener(v -> confirmClearQuestionBank()); panel.addView(clearBank);
        Button saved = new Button(this); saved.setText("سؤالات ذخیره‌شده"); saved.setAllCaps(false);
        saved.setOnClickListener(v -> showSavedExamBatches()); panel.addView(saved);
        Button generate = new Button(this); generate.setText("چاپ پرسشنامه و پاسخنامه"); generate.setAllCaps(false);
        generate.setOnClickListener(v -> printPairedSheets("", UUID.randomUUID().toString(), 1)); panel.addView(generate);
        new AlertDialog.Builder(this).setTitle("بانک سؤال (" + bank.length() + ")")
            .setView(panel).setPositiveButton("بستن", null).show();
    }

    private void confirmClearQuestionBank() {
        new AlertDialog.Builder(this).setTitle("پاک کردن بانک سؤال")
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
            }).show();
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
            Button delete = new Button(this);
            delete.setText("حذف");
            delete.setAllCaps(false);
            delete.setTextColor(Color.WHITE);
            delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(185, 28, 28)));
            delete.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("حذف مجموعه")
                    .setMessage("«" + selectedBatch.optString("exam_name", "آزمون") + "» حذف شود؟ این کار مجموعه‌های دیگر را تغییر نمی‌دهد.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف", (dialog, which) -> {
                        deleteSavedExamBatch(batchId);
                        showSavedExamBatches();
                    }).show());
            row.addView(delete, new LinearLayout.LayoutParams(dp(84), dp(48)));
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
                    + "\nبرای دیدن سؤال‌ها، ترتیب گزینه‌ها، QR و کلید هر نسخه، «مشاهده محتویات» را بزن.")
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
                .append("\nشناسه QR: ").append(variant.optString("qr_payload", "ثبت نشده"))
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

    /** Seeds the two photographed forms on first launch so they are already in the app. */
    private void seedOriginalPdfVersionsIfMissing() {
        if (prefs.getBoolean("original_forms_corrected_v2", false)) return;
        try {
            JSONArray a = new JSONArray();
            addPdfQuestion(a, "5×5 = ?", new String[]{"28","85","32","25"}, 3, "فرم A");
            addPdfQuestion(a, "12−4 = ?", new String[]{"7","8","6","0"}, 1, "فرم A");
            addPdfQuestion(a, "8×6 = ?", new String[]{"76","48","49","45"}, 1, "فرم A");
            addPdfQuestion(a, "8×4 = ?", new String[]{"65","31","65","32"}, 3, "فرم A");
            addPdfQuestion(a, "7×7 = ?", new String[]{"65","43","49","80"}, 2, "فرم A");
            addPdfQuestion(a, "54+12 = ?", new String[]{"67","69","66","68"}, 2, "فرم A؛ گزینه C اصلاح شد به ۶۶");
            addPdfQuestion(a, "21÷3 = ?", new String[]{"9","7","8","6"}, 1, "فرم A");
            addPdfQuestion(a, "8÷2 = ?", new String[]{"8","5","4","3"}, 2, "فرم A");
            addPdfQuestion(a, "4+4 = ?", new String[]{"7","9","2","8"}, 3, "فرم A");
            addPdfQuestion(a, "18÷3 = ?", new String[]{"9","6","7","5"}, 1, "فرم A");

            JSONArray b = new JSONArray();
            addPdfQuestion(b, "7×7 = ?", new String[]{"49","65","80","43"}, 0, "فرم B");
            addPdfQuestion(b, "8×4 = ?", new String[]{"31","65","65","32"}, 3, "فرم B");
            addPdfQuestion(b, "54+12 = ?", new String[]{"67","69","66","56"}, 2, "فرم B؛ گزینه C اصلاح شد به ۶۶");
            addPdfQuestion(b, "12−4 = ?", new String[]{"6","7","8","0"}, 2, "فرم B");
            addPdfQuestion(b, "8×6 = ?", new String[]{"49","48","45","76"}, 1, "فرم B");
            addPdfQuestion(b, "21÷3 = ?", new String[]{"8","6","9","7"}, 3, "فرم B");
            addPdfQuestion(b, "4+4 = ?", new String[]{"7","8","9","2"}, 1, "فرم B");
            addPdfQuestion(b, "18÷3 = ?", new String[]{"7","5","6","9"}, 2, "فرم B");
            addPdfQuestion(b, "5×5 = ?", new String[]{"28","32","85","25"}, 3, "فرم B");
            addPdfQuestion(b, "8÷2 = ?", new String[]{"4","8","5","3"}, 1, "فرم B");

            JSONObject fixedA = makeFixedPdfVariant("A", a);
            JSONObject fixedB = makeFixedPdfVariant("B", b);
            String masterKey = fixedA.optString("answer_key");
            prefs.edit().putString("exam_name", "ریاضی")
                .putInt("question_count", 10)
                .putString("question_bank_json", a.toString())
                .putString("answer_key", masterKey)
                .putString("pdf_original_version_A", fixedA.toString())
                .putString("pdf_original_version_B", fixedB.toString())
                .putString("model_questions_A", a.toString())
                .putString("model_questions_B", b.toString())
                .putString("answer_key_model_A", fixedA.optString("answer_key"))
                .putString("answer_key_model_B", fixedB.optString("answer_key"))
                .putString("answer_key_master", masterKey)
                .putString("model_questions_master", a.toString())
                .putInt("issued_model_count", 2).putBoolean("original_forms_corrected_v2", true).apply();
            registerImportedQrVariants(new JSONObject[]{fixedA, fixedB});
        } catch (Exception ignored) {
            // The manual import action remains available if first-launch seeding fails.
        }
    }

    /** Imports the two photographed original forms, preserving printed order and options exactly. */
    private void importPdfMathSample() {
        new AlertDialog.Builder(this)
            .setTitle("ثبت دو فرم اصلی A و B")
            .setMessage("سؤال‌ها مطابق دو عکس ثبت می‌شوند؛ برای سؤال ۵۴+۱۲، گزینه C به ۶۶ اصلاح و در کلید پاسخ ثبت می‌شود.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ثبت فرم‌های A و B", (dialog, which) -> {
                try {
                    // Form A: second photo, preserving printed option order A/B/C/D.
                    JSONArray versionA = new JSONArray();
                    addPdfQuestion(versionA, "5×5 = ?", new String[]{"28","85","32","25"}, 3, "فرم A");
                    addPdfQuestion(versionA, "12−4 = ?", new String[]{"7","8","6","0"}, 1, "فرم A");
                    addPdfQuestion(versionA, "8×6 = ?", new String[]{"76","48","49","45"}, 1, "فرم A");
                    addPdfQuestion(versionA, "8×4 = ?", new String[]{"65","31","65","32"}, 3, "فرم A");
                    addPdfQuestion(versionA, "7×7 = ?", new String[]{"65","43","49","80"}, 2, "فرم A");
                    addPdfQuestion(versionA, "54+12 = ?", new String[]{"67","56","66","69"}, 2, "فرم A؛ گزینه C اصلاح شد به ۶۶");
                    addPdfQuestion(versionA, "21÷3 = ?", new String[]{"9","7","8","6"}, 1, "فرم A");
                    addPdfQuestion(versionA, "8÷2 = ?", new String[]{"8","5","4","3"}, 2, "فرم A");
                    addPdfQuestion(versionA, "4+4 = ?", new String[]{"7","9","2","8"}, 3, "فرم A");
                    addPdfQuestion(versionA, "18÷3 = ?", new String[]{"9","6","7","5"}, 1, "فرم A");

                    // Form B: first photo, preserving printed option order A/B/C/D.
                    JSONArray versionB = new JSONArray();
                    addPdfQuestion(versionB, "7×7 = ?", new String[]{"49","65","80","43"}, 0, "فرم B");
                    addPdfQuestion(versionB, "8×4 = ?", new String[]{"31","65","65","32"}, 3, "فرم B");
                    addPdfQuestion(versionB, "54+12 = ?", new String[]{"67","56","66","69"}, 2, "فرم B؛ گزینه C اصلاح شد به ۶۶");
                    addPdfQuestion(versionB, "12−4 = ?", new String[]{"6","7","8","0"}, 2, "فرم B");
                    addPdfQuestion(versionB, "8×6 = ?", new String[]{"49","48","45","76"}, 1, "فرم B");
                    addPdfQuestion(versionB, "21÷3 = ?", new String[]{"8","6","9","7"}, 3, "فرم B");
                    addPdfQuestion(versionB, "4+4 = ?", new String[]{"7","8","9","2"}, 1, "فرم B");
                    addPdfQuestion(versionB, "18÷3 = ?", new String[]{"7","5","6","9"}, 2, "فرم B");
                    addPdfQuestion(versionB, "5×5 = ?", new String[]{"28","32","85","25"}, 3, "فرم B");
                    addPdfQuestion(versionB, "8÷2 = ?", new String[]{"4","8","5","3"}, 1, "فرم B");

                    JSONObject fixedA = makeFixedPdfVariant("A", versionA);
                    JSONObject fixedB = makeFixedPdfVariant("B", versionB);
                    // Keep only the two supplied forms as printable/scannable variants.
                    // The master key is based on the original question bank, not a third form.
                    String masterKey = fixedA.optString("answer_key");
                    prefs.edit().putString("exam_name", "ریاضی (۱)")
                        .putInt("question_count", 10)
                        .putString("question_bank_json", versionA.toString())
                        .putString("answer_key", masterKey)
                        .putString("pdf_original_version_A", fixedA.toString())
                        .putString("pdf_original_version_B", fixedB.toString())
                        .putString("model_questions_A", versionA.toString())
                        .putString("model_questions_B", versionB.toString())
                        .putString("answer_key_model_A", fixedA.optString("answer_key"))
                        .putString("answer_key_model_B", fixedB.optString("answer_key"))
                        .putString("answer_key_master", masterKey)
                        .putString("model_questions_master", versionA.toString())
                        .putInt("issued_model_count", 2).apply();
                    registerImportedQrVariants(new JSONObject[]{fixedA, fixedB});
                    refreshSummary();
                    info("دو نسخهٔ A و B ثبت شدند؛ پاسخ سؤال ۵۴+۱۲ گزینه C=۶۶ است و کلیدها به‌روزرسانی شدند. نسخهٔ C ساخته نمی‌شود. کلید اصلی: "
                        + masterKey + ".");
                } catch (Exception e) {
                    info("ثبت نسخه‌های اصلی PDF ناموفق بود؛ دوباره تلاش کن.");
                }
            }).show();
    }

    private void registerImportedQrVariants(JSONObject[] variants) throws Exception {
        String encodedName = Base64.encodeToString(examName().getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String batchId = UUID.randomUUID().toString();
        JSONArray savedVariants = new JSONArray();
        StringBuilder html = new StringBuilder("<html><head><meta charset='utf-8'><style>@page{size:A4;margin:10mm}body{font-family:sans-serif;direction:rtl}.page{page-break-after:always}.qr{width:28mm;height:28mm;float:left}.q{padding:3mm;border-bottom:1px solid #aaa}.opts{display:grid;grid-template-columns:1fr 1fr}.bubble{display:inline-block;width:5mm;height:5mm;border:1px solid #111;border-radius:50%}table{width:100%;border-collapse:collapse}td,th{border:1px solid #555;text-align:center;padding:2mm}</style></head><body>");
        for (JSONObject variant : variants) {
            String model = variant.optString("variant_id", "");
            JSONArray questions = variant.optJSONArray("questions");
            String answerKey = variant.optString("answer_key", "");
            if (questions == null || questions.length() != 10 || answerKey.length() != 10) throw new IllegalStateException("Invalid variant");
            String sheetId = UUID.randomUUID().toString();
            String payload = "AZY3|" + encodedName + "|" + sheetId + "|" + model + "|T1|1|1";
            com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320);
            Bitmap qr = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) qr.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); qr.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            String qr64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
            JSONObject issued = new JSONObject();
            issued.put("schema_version", 1); issued.put("exam_name", examName()); issued.put("sheet_set_id", sheetId);
            issued.put("model_id", model); issued.put("template_id", "T1"); issued.put("question_count", 10);
            issued.put("answer_key", answerKey); issued.put("questions", questions); issued.put("qr_payload", payload);
            issued.put("created_at", System.currentTimeMillis()); issued.put("original_answer_key", answerKey);
            prefs.edit().putString("issued_exam_" + sheetId + "_" + model, issued.toString())
                    .putString("saved_variant_" + sheetId, issued.toString()).putString("answer_key_model_" + model, answerKey)
                    .putString("model_questions_" + model, questions.toString()).putString("pdf_original_version_" + model, variant.toString())
                    .putString("qr_payload_model_" + model, payload).putInt("issued_model_count", variants.length).apply();
            savedVariants.put(issued);
            html.append("<section class='page'><h1>").append(escapeHtml(examName())).append(" — نسخه ").append(model)
                    .append("</h1><img class='qr' src='data:image/png;base64,").append(qr64)
                    .append("'/><p>نام و نام خانوادگی: ................................</p><h2>پرسشنامه</h2>");
            for (int i = 0; i < questions.length(); i++) {
                JSONObject q = questions.optJSONObject(i); JSONArray options = q == null ? null : q.optJSONArray("options");
                html.append("<div class='q'><b>").append(i + 1).append(". ").append(escapeHtml(q == null ? "" : q.optString("text", ""))).append("</b><div class='opts'>");
                for (int j = 0; options != null && j < Math.min(4, options.length()); j++)
                    html.append("<div>").append((char)('A' + j)).append(") ").append(escapeHtml(options.optString(j, ""))).append("</div>");
                html.append("</div></div>");
            }
            html.append("<h2>پاسخنامه</h2><table><tr><th>سؤال</th><th>A</th><th>B</th><th>C</th><th>D</th></tr>");
            for (int i = 1; i <= 10; i++) html.append("<tr><td>").append(i).append("</td><td><span class='bubble'></span></td><td><span class='bubble'></span></td><td><span class='bubble'></span></td><td><span class='bubble'></span></td></tr>");
            html.append("</table></section>");
        }
        html.append("</body></html>");
        JSONObject batch = new JSONObject(); batch.put("batch_id", batchId); batch.put("exam_name", examName());
        batch.put("variant_count", variants.length); batch.put("created_at", System.currentTimeMillis());
        batch.put("print_html", html.toString()); batch.put("variants", savedVariants);
        JSONArray batches = readSavedExamBatches(); batches.put(batch);
        prefs.edit().putString("saved_exam_batches_json", batches.toString()).apply();
        printHtmlDocument("AzmoonYar-" + examName() + "-AB-QR", html.toString());
    }

    private JSONObject makeFixedPdfVariant(String model, JSONArray sourceQuestions) throws Exception {
        JSONArray questions = new JSONArray();
        StringBuilder answerKey = new StringBuilder();
        for (int i = 0; i < sourceQuestions.length(); i++) {
            JSONObject source = sourceQuestions.optJSONObject(i);
            JSONArray options = source.optJSONArray("options");
            int correct = source.optInt("correct_index", -1);
            JSONObject q = new JSONObject();
            q.put("source_id", source.optString("id", ""));
            q.put("position", i + 1);
            q.put("text", source.optString("text", ""));
            q.put("topic", source.optString("topic", ""));
            q.put("options", options == null ? new JSONArray() : options);
            q.put("correct_index", correct);
            String letter = correct >= 0 && correct <= 3 ? String.valueOf((char)('A' + correct)) : "-";
            q.put("correct_letter", letter);
            questions.put(q);
            answerKey.append(letter);
        }
        JSONObject variant = new JSONObject();
        variant.put("variant_id", model);
        variant.put("questions", questions);
        variant.put("answer_key", answerKey.toString());
        variant.put("source", "AzmoonYar-ریاضی(1).PDF");
        variant.put("fixed_original_order", true);
        return variant;
    }

    private void addPdfQuestion(JSONArray bank, String text, String[] options, int correctIndex, String topic) throws Exception {
        JSONObject q = new JSONObject();
        q.put("id", UUID.randomUUID().toString());
        q.put("text", text);
        q.put("topic", topic);
        JSONArray opts = new JSONArray();
        for (String option : options) opts.put(option);
        q.put("options", opts);
        q.put("correct_index", correctIndex);
        bank.put(q);
    }

    private void showAddBankQuestionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(10), dp(4), dp(10), dp(4));
        EditText question = new EditText(this); styleInput(question);
        question.setHint("متن سؤال");
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
                    JSONObject item = new JSONObject();
                    item.put("id", UUID.randomUUID().toString());
                    item.put("text", qText);
                    item.put("topic", topic.getText().toString().trim());
                    item.put("options", optionArray);
                    item.put("correct_index", correctIndex);
                    bank.put(item);
                    prefs.edit().putString("question_bank_json", bank.toString()).apply();
                    info("سؤال در بانک محلی ذخیره شد.");
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
                                bubbleBg.setColor(letter == correct ? Color.rgb(220, 252, 231) : Color.WHITE);
                                bubbleBg.setCornerRadius(dp(30));
                                bubbleBg.setStroke(dp(2), color);
                                bubble.setBackground(bubbleBg);
                                bubble.setTextColor(color);
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
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTag(values[i]);
            group.addView(option);
            if (i == checked) option.setChecked(true);
        }
        TextView note = new TextView(this);
        note.setText("توجه: در نسخهٔ فعلی اسکن QR انجام می‌شود و هیچ تصویری ذخیره نمی‌شود. این انتخاب فقط سیاست دلخواه شما را ثبت می‌کند؛ ذخیرهٔ واقعی تصاویر و تشخیص پاسخنامهٔ مشکوک تا زمان پیاده‌سازی پردازش تصویر فعال نیست.");
        note.setTextSize(14);
        note.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(group);
        layout.addView(note);
        new AlertDialog.Builder(this)
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

    private void showReportsCenter() {
        new AlertDialog.Builder(this)
            .setTitle("آنالیز و آمار")
            .setItems(new String[]{"نتایج آزمون", "صندوق گزارش‌ها و حریم خصوصی"}, (dialog, which) -> {
                if (which == 0) showExamResultsMenu();
                else showReportsPrivacyDetails();
            })
            .setNegativeButton("بستن", null)
            .show();
    }

    private JSONArray readGradingResults() {
        try { return new JSONArray(prefs.getString("grading_results", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showExamResultsMenu() {
        new AlertDialog.Builder(this)
            .setTitle("نتایج آزمون")
            .setItems(new String[]{"نتایج آزمون اخیر", "نتایج قبلی", "آنالیز شرکت‌کننده", "آنالیز هر آزمون"},
                (dialog, which) -> {
                    if (which == 0) showRecentExamResults();
                    else if (which == 1) showPreviousExamResults();
                    else if (which == 2) showParticipantAnalysis();
                    else showExamAnalysis();
                })
            .setNegativeButton("بازگشت", (d, w) -> showReportsCenter())
            .show();
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

    private void showResultList(String title, JSONArray results, int first, int last) {
        StringBuilder message = new StringBuilder();
        for (int i = last; i >= first; i--) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) continue;
            message.append("شرکت‌کننده: ").append(result.optString("name", "بدون نام"))
                .append("\nآزمون: ").append(result.optString("examName", "—"))
                .append("\nنمره: ").append(String.format(Locale.US, "%.1f", result.optDouble("score", 0.0))).append(" از 100")
                .append("\nدرست: ").append(result.optInt("correct", 0))
                .append(" | غلط: ").append(result.optInt("wrong", 0))
                .append(" | سفید: ").append(result.optInt("blank", 0))
                .append("\n\n--------------------\n\n");
        }
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(message.toString());
        text.setTextSize(16);
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setGravity(Gravity.RIGHT);
        text.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton("بستن", null)
            .setNeutralButton("بازگشت", (d, w) -> showExamResultsMenu()).show();
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
                .setNegativeButton("بازگشت", (d, w) -> showExamResultsMenu())
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
        StringBuilder out = new StringBuilder();
        for (java.util.Map.Entry<String, double[]> e : totals.entrySet())
            out.append(e.getKey()).append("\nتعداد نتایج: ").append((int)e.getValue()[1])
                .append("\nمیانگین نمره: ").append(String.format(Locale.US, "%.1f", e.getValue()[0] / e.getValue()[1]))
                .append(" از 100\n\n");
        showAnalysisText("آنالیز هر آزمون", out.toString());
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
        ReportStore reportStore = new ReportStore(this);
        JSONArray inbox = reportStore.getInbox();
        StringBuilder message = new StringBuilder("صندوق گزارش‌های محلی\\n");
        message.append("گزارش‌ها فقط روی همین دستگاه ذخیره می‌شوند.\\n\\n");
        if (inbox.length() == 0) message.append("هنوز گزارشی ثبت نشده است.\\n\\n");
        else for (int i = inbox.length() - 1; i >= 0; i--) {
            JSONObject item = inbox.optJSONObject(i);
            if (item == null) continue;
            message.append(item.optString("title", "گزارش ارزیابی")).append("\\n")
                .append(item.optString("body", "")).append("\\n")
                .append(item.optString("created_at", "")).append("\\n\\n");
            reportStore.markRead(item.optString("id", ""));
        }
        message.append("حریم خصوصی و رضایت\\n")
            .append("• ارسال به والدین تا زمان ثبت رضایت و تأیید واقعی شماره تماس غیرفعال است.\\n")
            .append("• ثبت نام یا شماره به‌تنهایی به معنی تأیید هویت نیست.\\n")
            .append("• ارسال پیامک/ایمیل یا اعلان خارج از برنامه هنوز فعال نشده است.\\n")
            .append("• تحلیل و تصحیح خودکار هنوز تکمیل نشده؛ هیچ نتیجهٔ ساختگی ارسال نمی‌شود.");
        new AlertDialog.Builder(this).setTitle("صندوق گزارش‌ها و حریم خصوصی")
            .setMessage(message.toString()).setPositiveButton("باشه", null).show();
    }

    private void info(String message) {
        showStyledInfo(message);
    }
}