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
    private static final int REQUEST_ANSWER_KEY_IMAGE = 7314;
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
        buildScreen();
        // Do not download the optional Persian speech model automatically on launch.
        // The user can start this download explicitly from the “مدل صوتی فارسی” button.
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

        addButtonGrid(
                new String[]{"تعریف / ویرایش آزمون", "ثبت و مدیریت شرکت‌کنندگان"},
                new View.OnClickListener[]{v -> showExamDialog(), v -> showStudentsDialog()});
        addButtonGrid(
                new String[]{"چاپ پرسشنامه و پاسخنامه", "کلیدهای پاسخ"},
                new View.OnClickListener[]{v -> showPrintStudentDialog(), v -> showKeyDialog()});
        addButtonGrid(
                new String[]{"نمایش کلید و وضعیت تکمیل", "اسکن زنده پاسخنامه"},
                new View.OnClickListener[]{v -> showKeySummary(), v -> openScanner()});
        addButtonGrid(
                new String[]{"تنظیمات ذخیره تصاویر"},
                new View.OnClickListener[]{v -> showImageRetentionDialog()});
        addButtonGrid(
                new String[]{"گزارش‌ها و حریم خصوصی", "بانک سؤال"},
                new View.OnClickListener[]{v -> showReportsCenter(), v -> showQuestionBank()});
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

    private void showPrintStudentDialog() {
        // All new variants go through the preview and explicit save/print confirmation.
        generateRandomVariants();
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
                    if (page == 1) {
                        html.append("<section class='page'><h1 class='head'>").append(safeExam)
                            .append(" — برگه سؤال‌ها</h1>")
                            .append("<img class='qr' src='data:image/png;base64,").append(qr64).append("'/>")
                            .append("<div class='meta'>نام و نام خانوادگی: ........................................................</div>")
                            .append("<div class='meta'>تعداد سؤال: ").append(total).append("</div><div class='clear'></div><hr/>");
                        for (int i = 0; i < total; i++) {
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

    private void showKeyDialog() {
        if (examName().isEmpty()) { info("ابتدا یک آزمون تعریف کن."); return; }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(6), dp(8), dp(2));

        TextView targetLabel = new TextView(this);
        targetLabel.setText("کلیدی که می‌خواهی ببینی یا ویرایش کنی:");
        targetLabel.setTextSize(15);
        form.addView(targetLabel);

        RadioGroup targetGroup = new RadioGroup(this);
        targetGroup.setOrientation(RadioGroup.VERTICAL);
        int availableModels = Math.max(1, Math.min(26, prefs.getInt("issued_model_count", 2)));
        RadioButton mainTarget = new RadioButton(this);
        mainTarget.setText("کلید اصلی بانک سؤال");
        mainTarget.setTag("main");
        targetGroup.addView(mainTarget);
        mainTarget.setChecked(true);
        for (int i = 0; i < availableModels; i++) {
            String model = String.valueOf((char)('A' + i));
            RadioButton rb = new RadioButton(this);
            rb.setText("کلید نسخه " + model);
            rb.setTag(model);
            targetGroup.addView(rb);
        }
        form.addView(targetGroup);

        TextView currentKey = new TextView(this);
        currentKey.setTextSize(13);
        currentKey.setTextColor(Color.rgb(51, 65, 85));
        currentKey.setPadding(0, dp(6), 0, dp(8));
        form.addView(currentKey);

        final String[] selectedTarget = {"main"};
        Runnable updateTargetInfo = () -> {
            String target = selectedTarget[0];
            String value;
            if ("main".equals(target)) {
                value = buildOriginalBankKey();
                currentKey.setText("کلید اصلی از پاسخ‌های صحیح بانک سؤال ساخته می‌شود.\n" + formatAnswerKey(value, questionCount()));
            } else {
                value = getGeneratedModelKey(target);
                currentKey.setText(value.isEmpty()
                    ? "هنوز نسخه " + target + " ساخته یا چاپ نشده است. ابتدا «چاپ پرسشنامه و پاسخنامه» را اجرا کن."
                    : "کلید نسخه " + target + " به‌صورت خودکار از ترتیب واقعی سؤال‌ها و گزینه‌های نسخه ذخیره‌شده ساخته شده است.\n" + formatAnswerKey(value, questionCount()));
            }
        };
        targetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null) {
                selectedTarget[0] = String.valueOf(selected.getTag());
                updateTargetInfo.run();
            }
        });
        updateTargetInfo.run();

        new AlertDialog.Builder(this)
            .setTitle("کلیدهای پاسخ")
            .setMessage("کلید بیرونی بدون بارکد قابل ورود است. تصویر ذخیره می‌شود؛ خواندن خودکار حباب‌ها هنوز فعال نیست و باید پاسخ‌ها را دستی وارد و تأیید کنی.")
            .setView(form)
            .setNegativeButton("بستن", null)
            .setNeutralButton("بارگذاری تصویر کلید", (d, w) -> {
                pendingKeyTarget = selectedTarget[0];
                pickAnswerKeyImage();
            })
            .setPositiveButton("نمایش کلیدها", (d, w) -> showKeySummary())
            .show();
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
        if (requestCode != REQUEST_ANSWER_KEY_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
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
        new AlertDialog.Builder(this).setTitle("کلید اصلی و کلید نسخه‌ها")
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
        new AlertDialog.Builder(this)
            .setTitle("بانک سؤال (" + bank.length() + ")")
            .setMessage(bank.length() == 0
                ? "هنوز سؤالی ثبت نشده است. برای ساخت نسخه‌های تصادفی، سؤال و چهار گزینه و پاسخ صحیح را ثبت کن."
                : "تعداد سؤال‌های ذخیره‌شده: " + bank.length() + "\nهر نسخه از همین بانک ساخته می‌شود؛ ترتیب سؤال‌ها و گزینه‌ها جابه‌جا می‌شوند.")
            .setPositiveButton("افزودن سؤال", (d,w) -> showAddBankQuestionDialog())
            .setNeutralButton("بستن", null)
            .setNegativeButton("ساخت نسخه‌های تصادفی", (d,w) -> generateRandomVariants())
            .show();
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
