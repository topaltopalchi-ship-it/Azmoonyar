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
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView summary;

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
        buildScreen();
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
        subtitle.setText("مدیریت بانک سؤال، نسخه‌های آزمون و تصحیح — آفلاین");
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

        addButtonGrid(
                new String[]{"تعریف / ویرایش آزمون", "ثبت و مدیریت شرکت‌کنندگان"},
                new View.OnClickListener[]{v -> showExamDialog(), v -> showStudentsDialog()});
        addButtonGrid(
                new String[]{"چاپ نسخه‌های ساخته‌شده", "اسکن زنده پاسخنامه"},
                new View.OnClickListener[]{v -> showPrintStudentDialog(), v -> openScanner()});
        addButtonGrid(
                new String[]{"تنظیمات ذخیره تصاویر"},
                new View.OnClickListener[]{v -> showImageRetentionDialog()});
        addButtonGrid(
                new String[]{"گزارش‌ها و حریم خصوصی", "بانک سؤال"},
                new View.OnClickListener[]{v -> showReportsCenter(), v -> showQuestionBank()});
        addButtonGrid(
                new String[]{"ساخت نسخه‌های تصادفی آزمون"},
                new View.OnClickListener[]{v -> generateRandomVariants()});
        TextView note = new TextView(this);
        note.setText("اسکن زنده QR بدون ذخیره عکس اضافه شده است. خواندن حباب‌های پاسخ و نمره‌دهی خودکار هنوز در حال توسعه است.");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(20); root.addView(note,np);
        setContentView(scroll);
    }

    private void downloadPersianSpeechModel() {
        File modelDir = new File(getFilesDir(), "vosk-model-small-fa-0.42");
        if (new File(modelDir, "am/final.mdl").exists() && new File(modelDir, "conf/model.conf").exists()) {
            return;
        }
        startPersianModelDownload(modelDir);
    }

    private void startPersianModelDownload(File modelDir) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("مدل صوتی فارسی");
        progress.setMessage("در حال دانلود…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.setCanceledOnTouchOutside(false);
        progress.show();
        new Thread(() -> {
            File zipFile = new File(getCacheDir(), "persian-speech-model.zip");
            File stagingDir = new File(getFilesDir(), "persian-speech-installing");
            String error = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(
                        "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip").openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
                int totalBytes = connection.getContentLength();
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    long downloaded = 0;
                    int lastPercent = -1;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        downloaded += count;
                        if (totalBytes > 0) {
                            int percent = (int) Math.min(100, downloaded * 100L / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                runOnUiThread(() -> {
                                    progress.setProgress(p);
                                    progress.setMessage("مدل صوتی فارسی — " + p + "٪");
                                });
                            }
                        } else {
                            final long mb = downloaded / (1024 * 1024);
                            runOnUiThread(() -> progress.setMessage("مدل صوتی فارسی — " + mb + " مگابایت دانلود شد"));
                        }
                    }
                } finally {
                    connection.disconnect();
                }

                runOnUiThread(() -> {
                    progress.setProgress(100);
                    progress.setMessage("در حال آماده‌سازی مدل صوتی فارسی…");
                });
                deleteTree(stagingDir);
                if (!stagingDir.mkdirs() && !stagingDir.isDirectory()) {
                    throw new java.io.IOException("ساخت پوشهٔ نصب ناموفق بود");
                }
                String prefix = "vosk-model-small-fa-0.42/";
                try (ZipInputStream zip = new ZipInputStream(new java.io.BufferedInputStream(
                        new java.io.FileInputStream(zipFile)))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[16384];
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (!name.startsWith(prefix)) continue;
                        String relative = name.substring(prefix.length());
                        if (relative.isEmpty()) continue;
                        File target = new File(stagingDir, relative);
                        String rootPath = stagingDir.getCanonicalPath() + File.separator;
                        if (!target.getCanonicalPath().startsWith(rootPath)) {
                            throw new java.io.IOException("مسیر نامعتبر در فایل مدل");
                        }
                        if (entry.isDirectory()) {
                            if (!target.isDirectory() && !target.mkdirs()) throw new java.io.IOException("ساخت پوشه ناموفق بود");
                        } else {
                            File parent = target.getParentFile();
                            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                                throw new java.io.IOException("ساخت پوشه ناموفق بود");
                            }
                            try (FileOutputStream out = new FileOutputStream(target)) {
                                int count;
                                while ((count = zip.read(buffer)) != -1) out.write(buffer, 0, count);
                            }
                        }
                        zip.closeEntry();
                    }
                }
                if (!new File(stagingDir, "am/final.mdl").exists()
                        || !new File(stagingDir, "conf/model.conf").exists()) {
                    throw new java.io.IOException("فایل‌های مدل کامل نیستند");
                }
                deleteTree(modelDir);
                if (!stagingDir.renameTo(modelDir)) throw new java.io.IOException("انتقال مدل به پوشهٔ نهایی ناموفق بود");
            } catch (Exception e) {
                error = e.getMessage() == null ? "خطای ناشناخته" : e.getMessage();
                deleteTree(stagingDir);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                zipFile.delete();
            }
            final String result = error;
            runOnUiThread(() -> {
                progress.dismiss();
                if (result != null) {
                    showStyledInfo("دریافت مدل صوتی فارسی کامل نشد. اینترنت را بررسی کن و برنامه را دوباره باز کن.");
                }
            });
        }, "persian-speech-model-download").start();
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
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
            int[][] palette = {
                {Color.rgb(0, 229, 255), Color.rgb(41, 98, 255)},
                {Color.rgb(255, 0, 153), Color.rgb(124, 58, 237)},
                {Color.rgb(255, 145, 0), Color.rgb(255, 23, 68)},
                {Color.rgb(0, 230, 118), Color.rgb(0, 137, 123)},
                {Color.rgb(255, 64, 129), Color.rgb(213, 0, 249)},
                {Color.rgb(255, 202, 40), Color.rgb(245, 81, 95)}
            };
            int colorIndex = Math.abs(labels[i].hashCode()) % palette.length;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, palette[colorIndex]);
            bg.setCornerRadius(dp(40));
            bg.setStroke(dp(1), Color.argb(180, 255, 255, 255));
            b.setBackground(bg);
            b.setElevation(dp(8));
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
        String name=examName().isEmpty() ? "هنوز آزمونی تعریف نشده" : "آزمون: "+examName();
        int scanned=prefs.getInt("scanned_sheet_count",0);
        int studentCount=readStudents().length();
        int variants=readSavedVariants().length();
        summary.setText(name+"\nتعداد سؤال: "+questionCount()+"\nنسخه‌های ذخیره‌شده: "+variants+"\nشرکت‌کنندگان ثبت‌شده: "+studentCount+"\nپاسخنامه‌های QR اسکن‌شده: "+scanned);
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
        try { return new JSONArray(prefs.getString("students_json", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showStudentsDialog() {
        JSONArray students = readStudents();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < students.length(); i++) {
            JSONObject student = students.optJSONObject(i);
            if (student != null) list.append(i + 1).append(". ")
                    .append(student.optString("name", "بدون نام"))
                    .append("  —  شناسه: ").append(student.optString("id", ""))
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
        Button voice = new Button(this);
        voice.setText("🎙 گفتن نام با صدا");
        voice.setAllCaps(false);
        form.addView(voice, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ثبت شرکت‌کننده")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();
        voice.setOnClickListener(v -> startPersianNameCapture(name, voice));
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String studentName = name.getText().toString().trim();
            if (studentName.isEmpty()) {
                info("نام شرکت‌کننده را وارد کن یا با صدا بگو.");
                return;
            }
            JSONArray students = readStudents();
            try {
                JSONObject student = new JSONObject();
                student.put("id", UUID.randomUUID().toString());
                student.put("name", studentName);
                students.put(student);
                prefs.edit().putString("students_json", students.toString()).apply();
                refreshSummary();
                dialog.dismiss();
                info("شرکت‌کننده ذخیره شد و شناسه یکتای او ایجاد شد.");
            } catch (Exception e) {
                info("ذخیره شرکت‌کننده انجام نشد؛ دوباره تلاش کن.");
            }
        }));
        dialog.show();
    }

    private void startPersianNameCapture(EditText target, Button voiceButton) {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 731);
            voiceButton.setText("برای ضبط، مجوز میکروفن را بده");
            return;
        }
        File modelDir = new File(getFilesDir(), "vosk-model-small-fa-0.42");
        if (!new File(modelDir, "am/final.mdl").exists()) {
            info("مدل صوتی فارسی هنوز آماده نیست؛ پس از تکمیل دانلود دوباره تلاش کن.");
            return;
        }
        voiceButton.setEnabled(false);
        voiceButton.setText("در حال آماده‌سازی میکروفن…");
        new Thread(() -> {
            PersianSpeechCapture capture = null;
            try {
                capture = new PersianSpeechCapture(modelDir.getAbsolutePath(), new PersianSpeechCapture.Listener() {
                    @Override public void onText(String text) {
                        runOnUiThread(() -> {
                            target.setText(text);
                            target.setSelection(target.getText().length());
                            voiceButton.setText("🎙 گفتن نام با صدا");
                            voiceButton.setEnabled(true);
                        });
                    }
                    @Override public void onStatus(String status) {
                        runOnUiThread(() -> voiceButton.setText(status));
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            voiceButton.setText("🎙 تلاش دوباره برای گفتن نام");
                            voiceButton.setEnabled(true);
                            info(message);
                        });
                    }
                });
                PersianSpeechCapture ready = capture;
                runOnUiThread(() -> {
                    voiceButton.setText("اکنون نام را بگو…");
                    voiceButton.setEnabled(false);
                });
                capture.start();
            } catch (Exception e) {
                if (capture != null) capture.stop();
                runOnUiThread(() -> {
                    voiceButton.setText("🎙 گفتن نام با صدا");
                    voiceButton.setEnabled(true);
                    info("تشخیص گفتار شروع نشد؛ دوباره تلاش کن.");
                });
            }
        }, "persian-name-recognition").start();
    }


    private void showPrintStudentDialog() {
        if (examName().trim().isEmpty()) { info("ابتدا آزمون را تعریف کن."); return; }
        JSONArray saved = readSavedVariants();
        if (saved.length() == 0) {
            info("ابتدا از بخش «ساخت نسخه‌های تصادفی آزمون» تعداد نسخه‌ها را انتخاب و ذخیره کن.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("چاپ نسخه‌های ذخیره‌شده")
            .setMessage("تعداد نسخه‌های آمادهٔ چاپ: " + saved.length()
                + "\nبرای هر نسخه، برگه سؤال‌ها و پاسخنامه با QR اختصاصی ساخته می‌شود."
                + "\nبا تأیید چاپ، همهٔ صفحات به صف چاپ اندروید ارسال می‌شوند.")
            .setPositiveButton("چاپ پرسشنامه و پاسخنامه", (dialog, which) -> printPairedSheets("", UUID.randomUUID().toString()))
            .setNegativeButton("انصراف", null).show();
    }


        private void printPairedSheets(String participantName, String participantId) {
        try {
            JSONArray bank = readQuestionBank();
            if (bank.length() < questionCount()) {
                info("برای چاپ نسخه‌دار، ابتدا به تعداد سؤال‌های آزمون سؤال در بانک سؤال ثبت کن.");
                return;
            }
            JSONArray variants = readSavedVariants();
            final int total = questionCount();
            final int pageCount = (total + 99) / 100;
            String encodedExamName = Base64.encodeToString(examName().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_WRAP);
            String safeExam = escapeHtml(examName());
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
                String sheetSetId = variant.optString("sheet_set_id", "");
                if (sheetSetId.isEmpty()) { info("شناسه QR نسخه ذخیره نشده است؛ نسخه‌ها را دوباره بساز."); return; }
                JSONArray questions = variant.optJSONArray("questions");
                String answerKey = variant.optString("answer_key", "");
                if (questions == null || questions.length() < total || answerKey.length() < total) {
                    info("ساخت مدل آزمون کامل نبود؛ چاپ متوقف شد.");
                    return;
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
                    html.append("<section class='page'><h1 class='head'>").append(safeExam)
                        .append(" — برگه سؤال‌ها (صفحه ").append(page).append(" از ").append(pageCount).append(")</h1>")
                        .append("<img class='qr' src='data:image/png;base64,").append(qr64).append("'/>")
                        .append("<div class='meta'>نام و نام خانوادگی: ........................................................</div>")
                        .append("<div class='meta'>تعداد کل سؤال‌ها: ").append(total).append("</div><div class='clear'></div><hr/>");
                    for (int i = first; i < last; i++) {
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
                // Keep the model key only in local preferences for later grading; never print it.
                prefs.edit().putString("answer_key_model_" + modelId, answerKey.substring(0, total))
                    .putString("model_questions_" + modelId, questions.toString())
                    .putString("sheet_set_id_model_" + modelId, sheetSetId)
                    .putString("saved_exam_variants", variants.toString()).apply();
            }
            html.append("</body></html>");
            WebView web = new WebView(this);
            web.getSettings().setDefaultTextEncodingName("UTF-8");
            web.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (pm != null) pm.print("AzmoonYar-" + examName(),
                        view.createPrintDocumentAdapter("AzmoonYar-" + examName()),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
                    else info("قابلیت چاپ در این دستگاه در دسترس نیست.");
                }
            });
        } catch (Exception e) {
            info("ساخت برگه برای چاپ ناموفق بود؛ بانک سؤال و مجوز چاپ را بررسی کن.");
        }
    }

    private JSONArray getOrCreateExamVariants(JSONArray bank, int count) throws Exception {
        String cacheKey = "exam_variants_" + Integer.toHexString((examName() + "|" + bank.toString()).hashCode()) + "_" + count;
        String saved = prefs.getString(cacheKey, "");
        if (!saved.isEmpty()) {
            try {
                JSONArray cached = new JSONArray(saved);
                if (cached.length() == count) return cached;
            } catch (Exception ignored) { }
        }
        JSONArray variants = RandomExamBuilder.generateVariants(bank, count, System.nanoTime());
        prefs.edit().putString(cacheKey, variants.toString()).apply();
        return variants;
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
                                .build());
                    } else {
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



        private JSONArray readQuestionBank() {
        try { return new JSONArray(prefs.getString("question_bank_json", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showQuestionBank() {
        JSONArray bank = readQuestionBank();
        new AlertDialog.Builder(this)
            .setTitle("بانک سؤال (" + bank.length() + ")")
            .setMessage(bank.length() == 0
                ? "هنوز سؤالی ثبت نشده است. می‌توانی سؤال‌ها را دستی اضافه کنی یا فایل بانک سؤال JSON/CSV را از گوشی وارد کنی."
                : "تعداد سؤال‌های ذخیره‌شده: " + bank.length() + "\nهر نسخه از همین بانک ساخته می‌شود؛ ترتیب سؤال‌ها و گزینه‌ها جابه‌جا می‌شوند. برای افزودن گروهی، فایل JSON یا CSV وارد کن.")
            .setPositiveButton("افزودن سؤال", (d,w) -> showAddBankQuestionDialog())
            .setNeutralButton("وارد کردن فایل", (d,w) -> chooseQuestionBankFile())
            .setNegativeButton("ساخت نسخه‌ها", (d,w) -> generateRandomVariants())
            .show();
    }

    private static final int REQUEST_QUESTION_BANK_FILE = 5207;

    private void chooseQuestionBankFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/json", "text/csv", "application/csv",
                "application/vnd.ms-excel", "text/plain"
        });
        try {
            startActivityForResult(intent, REQUEST_QUESTION_BANK_FILE);
        } catch (Exception e) {
            info("باز کردن فایل‌گزین گوشی ممکن نشد.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_QUESTION_BANK_FILE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData())) {
            if (input == null) throw new IllegalArgumentException("فایل قابل خواندن نیست.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String content = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            String name = "";
            if (data.getData().getLastPathSegment() != null) {
                name = data.getData().getLastPathSegment().toLowerCase(Locale.ROOT);
            }
            JSONArray imported;
            String trimmed = content.trim();
            if (trimmed.startsWith("[") || trimmed.startsWith("{") || name.endsWith(".json")) {
                imported = parseQuestionBankJson(trimmed);
            } else {
                imported = parseQuestionBankCsv(content);
            }
            if (imported.length() == 0) {
                info("هیچ سؤال معتبری در فایل پیدا نشد؛ بانک فعلی تغییر نکرد.");
                return;
            }
            JSONArray existing = readQuestionBank();
            int added = 0;
            for (int i = 0; i < imported.length(); i++) {
                JSONObject item = imported.optJSONObject(i);
                if (item == null) continue;
                item.put("id", UUID.randomUUID().toString());
                existing.put(item);
                added++;
            }
            prefs.edit().putString("question_bank_json", existing.toString()).apply();
            refreshSummary();
            new AlertDialog.Builder(this)
                    .setTitle("واردکردن بانک سؤال انجام شد")
                    .setMessage(added + " سؤال معتبر اضافه شد.\nتعداد کل سؤال‌های بانک: " + existing.length()
                            + "\nپاسخ صحیح هر سؤال همراه آن ذخیره شده است. نسخه‌های آزمون را حالا می‌توانی بسازی.")
                    .setPositiveButton("ساخت نسخه‌ها", (d,w) -> generateRandomVariants())
                    .setNegativeButton("باشه", null)
                    .show();
        } catch (Exception e) {
            info("فایل وارد نشد: " + (e.getMessage() == null ? "قالب فایل یا داده‌ها معتبر نیست." : e.getMessage())
                    + "\nقالب مجاز: JSON یا CSV با متن سؤال، چهار گزینه و پاسخ صحیح.");
        }
    }

    private JSONArray parseQuestionBankJson(String source) throws Exception {
        Object rootObject;
        if (source.startsWith("[")) {
            rootObject = new JSONArray(source);
        } else {
            JSONObject rootJson = new JSONObject(source);
            rootObject = rootJson.optJSONArray("questions");
            if (rootObject == null) throw new IllegalArgumentException("در JSON باید آرایهٔ questions یا آرایهٔ مستقیم سؤال‌ها وجود داشته باشد.");
        }
        JSONArray rows = (JSONArray) rootObject;
        JSONArray valid = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String text = firstNonEmpty(row.optString("text", ""), row.optString("question", ""));
            JSONArray options = row.optJSONArray("options");
            String[] optionValues = new String[4];
            if (options != null && options.length() >= 4) {
                for (int j = 0; j < 4; j++) optionValues[j] = options.optString(j, "").trim();
            } else {
                optionValues[0] = firstNonEmpty(row.optString("option_a", ""), row.optString("A", ""));
                optionValues[1] = firstNonEmpty(row.optString("option_b", ""), row.optString("B", ""));
                optionValues[2] = firstNonEmpty(row.optString("option_c", ""), row.optString("C", ""));
                optionValues[3] = firstNonEmpty(row.optString("option_d", ""), row.optString("D", ""));
            }
            int correctIndex = readCorrectIndex(row);
            if (isValidQuestion(text, optionValues, correctIndex)) {
                JSONObject normalized = new JSONObject();
                normalized.put("id", UUID.randomUUID().toString());
                normalized.put("text", text);
                normalized.put("topic", row.optString("topic", ""));
                JSONArray normalizedOptions = new JSONArray();
                for (String option : optionValues) normalizedOptions.put(option);
                normalized.put("options", normalizedOptions);
                normalized.put("correct_index", correctIndex);
                valid.put(normalized);
            }
        }
        return valid;
    }

    private int readCorrectIndex(JSONObject row) {
        if (row.has("correct_index")) {
            int value = row.optInt("correct_index", -1);
            if (value >= 0 && value <= 3) return value;
        }
        String correct = firstNonEmpty(row.optString("correct_answer", ""),
                row.optString("answer", ""), row.optString("correct", "")).trim().toUpperCase(Locale.ROOT);
        if (correct.length() == 1 && correct.charAt(0) >= 'A' && correct.charAt(0) <= 'D') {
            return correct.charAt(0) - 'A';
        }
        try {
            int numeric = Integer.parseInt(correct);
            if (numeric >= 1 && numeric <= 4) return numeric - 1;
        } catch (NumberFormatException ignored) { }
        return -1;
    }

    private JSONArray parseQuestionBankCsv(String source) throws Exception {
        JSONArray valid = new JSONArray();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : source.split("\\r?\\n")) if (!line.trim().isEmpty()) lines.add(line);
        if (lines.isEmpty()) return valid;
        int start = 0;
        java.util.List<String> first = parseCsvLine(lines.get(0));
        if (first.size() >= 6 && (first.get(0).toLowerCase(Locale.ROOT).contains("question")
                || first.get(0).contains("سؤال"))) start = 1;
        for (int i = start; i < lines.size(); i++) {
            java.util.List<String> cells = parseCsvLine(lines.get(i));
            if (cells.size() < 6) continue;
            String text = cells.get(0).trim();
            String[] options = new String[]{cells.get(1).trim(), cells.get(2).trim(),
                    cells.get(3).trim(), cells.get(4).trim()};
            String correct = cells.get(5).trim().toUpperCase(Locale.ROOT);
            int correctIndex = -1;
            if (correct.length() == 1 && correct.charAt(0) >= 'A' && correct.charAt(0) <= 'D') {
                correctIndex = correct.charAt(0) - 'A';
            } else {
                try {
                    int numeric = Integer.parseInt(correct);
                    if (numeric >= 1 && numeric <= 4) correctIndex = numeric - 1;
                } catch (NumberFormatException ignored) { }
            }
            if (!isValidQuestion(text, options, correctIndex)) continue;
            JSONObject item = new JSONObject();
            item.put("id", UUID.randomUUID().toString());
            item.put("text", text);
            item.put("topic", cells.size() > 6 ? cells.get(6).trim() : "");
            JSONArray optionArray = new JSONArray();
            for (String option : options) optionArray.put(option);
            item.put("options", optionArray);
            item.put("correct_index", correctIndex);
            valid.put(item);
        }
        return valid;
    }

    private java.util.List<String> parseCsvLine(String line) {
        java.util.List<String> cells = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString()); cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private boolean isValidQuestion(String text, String[] options, int correctIndex) {
        if (text == null || text.trim().isEmpty() || options == null || options.length != 4
                || correctIndex < 0 || correctIndex > 3) return false;
        for (String option : options) if (option == null || option.trim().isEmpty()) return false;
        return true;
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


    private JSONArray readSavedVariants() {
        try { return new JSONArray(prefs.getString("saved_exam_variants", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void generateRandomVariants() {
        JSONArray bank = readQuestionBank();
        if (bank.length() < questionCount()) {
            info("تعداد سؤال‌های بانک باید دست‌کم برابر تعداد سؤال‌های آزمون باشد.");
            return;
        }
        String[] labels = {"۱ نسخه", "۲ نسخه", "۳ نسخه", "۵ نسخه", "۱۰ نسخه"};
        int[] counts = {1, 2, 3, 5, 10};
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        final int[] selectedCount = {2};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTag(counts[i]);
            group.addView(option);
            if (counts[i] == 2) option.setChecked(true);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton selected = g.findViewById(checkedId);
            if (selected != null) selectedCount[0] = (Integer) selected.getTag();
        });
        new AlertDialog.Builder(this)
            .setTitle("تعداد نسخه‌های تصادفی")
            .setMessage("برای هر نسخه، ترتیب سؤال‌ها و گزینه‌ها تصادفی می‌شود. کلید صحیح و شناسه QR همان نسخه نیز ذخیره می‌شوند تا بعداً برای تصحیح استفاده شوند.")
            .setView(group)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ساخت و ذخیره نسخه‌ها", (dialog, which) -> {
                try {
                    JSONArray variants = RandomExamBuilder.generateVariants(bank, selectedCount[0], System.nanoTime());
                    for (int i = 0; i < variants.length(); i++) {
                        JSONObject variant = variants.optJSONObject(i);
                        if (variant != null) {
                            variant.put("sheet_set_id", UUID.randomUUID().toString());
                            variant.put("exam_name", examName());
                            variant.put("question_count", questionCount());
                        }
                    }
                    prefs.edit().putString("saved_exam_variants", variants.toString()).apply();
                    refreshSummary();
                    new AlertDialog.Builder(this)
                        .setTitle("نسخه‌ها ذخیره شدند")
                        .setMessage(variants.length() + " نسخه ساخته و در حافظه برنامه ذخیره شد. برای هر نسخه، کلید تصحیح و QR اختصاصی نگهداری می‌شود.")
                        .setPositiveButton("چاپ نسخه‌ها", (d, w) -> printPairedSheets("", UUID.randomUUID().toString()))
                        .setNegativeButton("بعداً", null)
                        .show();
                } catch (Exception e) {
                    info("ساخت نسخه‌ها ناموفق بود: " + e.getMessage());
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
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(dp(8), dp(8), dp(8), dp(8));
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
        ReportStore reportStore = new ReportStore(this);
        JSONArray inbox = reportStore.getInbox();
        StringBuilder message = new StringBuilder();
        message.append("صندوق گزارش‌های محلی\n");
        message.append("گزارش‌ها فقط روی همین دستگاه ذخیره می‌شوند.\n\n");
        if (inbox.length() == 0) {
            message.append("هنوز گزارشی ثبت نشده است. گزارش واقعی پس از پیاده‌سازی تصحیح و تحلیل خودکار ساخته می‌شود.\n\n");
        } else {
            for (int i = inbox.length() - 1; i >= 0; i--) {
                JSONObject item = inbox.optJSONObject(i);
                if (item == null) continue;
                message.append(item.optString("title", "گزارش ارزیابی")).append("\n")
                    .append(item.optString("body", "")).append("\n")
                    .append(item.optString("created_at", "")).append("\n")
                    .append(item.optBoolean("read", false) ? "خوانده‌شده" : "خوانده‌نشده")
                    .append("\n\n");
                reportStore.markRead(item.optString("id", ""));
            }
        }
        message.append("حریم خصوصی و رضایت\n")
            .append("• ارسال به والدین تا زمان ثبت رضایت و تأیید واقعی شماره تماس غیرفعال است.\n")
            .append("• ثبت نام یا شماره به‌تنهایی به معنی تأیید هویت نیست.\n")
            .append("• ارسال پیامک/ایمیل یا اعلان خارج از برنامه هنوز فعال نشده است.\n")
            .append("• تحلیل و تصحیح خودکار هنوز تکمیل نشده؛ هیچ نتیجهٔ ساختگی ارسال نمی‌شود.");
        new AlertDialog.Builder(this).setTitle("گزارش‌ها و حریم خصوصی")
            .setMessage(message.toString())
            .setPositiveButton("باشه", null)
            .show();
    }

    private void info(String message) {
        showStyledInfo(message);
    }
}
