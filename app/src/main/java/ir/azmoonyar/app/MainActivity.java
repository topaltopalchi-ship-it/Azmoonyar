package ir.azmoonyar.app;

import android.app.Activity;
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
        root.addView(title, new LinearLayout.LayoutParams(-1,dp(58)));

        TextView subtitle = new TextView(this);
        subtitle.setText("مدیریت آزمون و کلید پاسخ — آفلاین");
        subtitle.setTextSize(16); subtitle.setTextColor(Color.rgb(65,80,115)); subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.bottomMargin=dp(22); root.addView(subtitle,sp);

        summary = new TextView(this);
        summary.setTextSize(15); summary.setTextColor(Color.rgb(30,45,80)); summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(12),dp(14),dp(12),dp(14)); summary.setBackgroundColor(Color.WHITE);
        root.addView(summary,new LinearLayout.LayoutParams(-1,-2));
        refreshSummary();

        addButton("تعریف / ویرایش آزمون", v -> showExamDialog());
        addButton("ثبت و مدیریت دانش‌آموزان", v -> showStudentsDialog());
        addButton("چاپ برگه‌های دانش‌آموز با QR مشترک", v -> showPrintStudentDialog());
        addButton("ثبت کلید پاسخ", v -> showKeyDialog());
        addButton("نمایش کلید و وضعیت تکمیل", v -> showKeySummary());
        addButton("اسکن زنده پاسخنامه (بدون عکس)", v -> openScanner());
        addButton("گزارش‌ها و حریم خصوصی", v -> showReportsCenter());
        addButton("بانک سؤال و افزودن سؤال", v -> showQuestionBank());
        addButton("ساخت یک یا چند نسخه تصادفی آزمون", v -> generateRandomVariants());
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

    private void addButton(String text, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56)); p.topMargin=dp(10); root.addView(b,p);
    }

    private void refreshSummary() {
        if (summary == null || prefs == null) return;
        int filled=0; String k=key();
        for(int i=0;i<Math.min(k.length(),questionCount());i++) if(k.charAt(i)>='A' && k.charAt(i)<='D') filled++;
        String name=examName().isEmpty() ? "هنوز آزمونی تعریف نشده" : "آزمون: "+examName();
        int scanned=prefs.getInt("scanned_sheet_count",0);
        int studentCount=readStudents().length();
        summary.setText(name+"\nتعداد سؤال: "+questionCount()+"\nکلید ثبت‌شده: "+filled+" از "+questionCount()+"\nدانش‌آموزان ثبت‌شده: "+studentCount+"\nپاسخنامه‌های دارای QR ثبت‌شده: "+scanned);
    }

    private void showExamDialog() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8),dp(6),dp(8),dp(2));
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("نام آزمون"); name.setText(examName()); form.addView(name);
        EditText count=new EditText(this); count.setSingleLine(true); count.setInputType(2); count.setHint("تعداد سؤال (۱ تا ۳۰۰)"); count.setText(String.valueOf(questionCount())); form.addView(count);
        new AlertDialog.Builder(this).setTitle("تعریف آزمون").setView(form)
          .setNegativeButton("انصراف",null).setPositiveButton("ذخیره",(d,w)->{
            String n=name.getText().toString().trim(); int c;
            try { c=Integer.parseInt(count.getText().toString().trim()); } catch(Exception e) { info("تعداد سؤال را به‌صورت عدد وارد کن."); return; }
            if(n.isEmpty() || c<1 || c>300) { info("نام آزمون لازم است و تعداد سؤال باید بین ۱ تا ۳۰۰ باشد."); return; }
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
        if (list.length() == 0) list.append("هنوز دانش‌آموزی ثبت نشده است.\n");
        new AlertDialog.Builder(this)
                .setTitle("فهرست دانش‌آموزان")
                .setMessage(list.toString())
                .setPositiveButton("افزودن دانش‌آموز", (d, w) -> showAddStudentDialog())
                .setNeutralButton("بستن", null)
                .show();
    }

    private void showAddStudentDialog() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("نام و نام خانوادگی دانش‌آموز");
        new AlertDialog.Builder(this)
                .setTitle("ثبت دانش‌آموز")
                .setView(name)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String studentName = name.getText().toString().trim();
                    if (studentName.isEmpty()) {
                        info("نام دانش‌آموز را وارد کن.");
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
                        info("دانش‌آموز ذخیره شد. شناسه یکتا ایجاد شده است؛ چاپ پرسشنامه/پاسخنامه و QR مشترک در مرحله بعد اضافه می‌شود.");
                    } catch (Exception e) {
                        info("ذخیره دانش‌آموز انجام نشد؛ دوباره تلاش کن.");
                    }
                }).show();
    }

    private void showPrintStudentDialog() {
        if (examName().trim().isEmpty()) { info("ابتدا آزمون را تعریف کن."); return; }
        JSONArray students = readStudents();
        if (students.length() == 0) { info("ابتدا دست‌کم یک دانش‌آموز ثبت کن."); return; }
        String[] labels = new String[students.length()];
        for (int i = 0; i < students.length(); i++) {
            JSONObject s = students.optJSONObject(i);
            labels[i] = (i + 1) + ". " + (s == null ? "بدون نام" : s.optString("name", "بدون نام"));
        }
        new AlertDialog.Builder(this).setTitle("انتخاب دانش‌آموز برای چاپ")
            .setItems(labels, (dialog, which) -> {
                JSONObject s = students.optJSONObject(which);
                if (s != null) printPairedSheets(s.optString("name", ""), s.optString("id", ""));
            }).setNegativeButton("انصراف", null).show();
    }

    private void printPairedSheets(String studentName, String studentId) {
        try {
            // Versioned, delimiter-safe payload. The scanner currently accepts AZY1,
            // so retain the existing payload contract until scanner migration is implemented.
            String payload = "AZY1|" + examName() + "|" + studentId;
            com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, 360, 360);
            Bitmap qr = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                qr.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            qr.compress(Bitmap.CompressFormat.PNG, 100, output);
            String encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            String safeName = escapeHtml(studentName);
            String safeExam = escapeHtml(examName());
            int count = questionCount();

            StringBuilder answerRows = new StringBuilder();
            for (int q = 1; q <= count; q++) {
                answerRows.append("<tr><td class='num'>").append(q).append("</td>");
                for (int option = 0; option < 4; option++) {
                    answerRows.append("<td><span class='bubble'></span></td>");
                }
                answerRows.append("</tr>");
            }

            String html = "<html><head><meta charset='utf-8'><style>"
                + "@page{size:A4;margin:12mm}body{font-family:sans-serif;color:#111;margin:0}"
                + "h1{font-size:20px;margin:0 0 8px}h2{font-size:16px;margin:8px 0}"
                + ".meta{font-size:12px;margin:5px 0}.qr{width:105px;height:105px;float:left}"
                + ".clear{clear:both}.instructions{font-size:11px;border:1px solid #333;padding:7px;margin:8px 0}"
                + "table{width:100%;border-collapse:collapse;font-size:11px}th,td{border:1px solid #777;text-align:center;padding:2px;height:20px}"
                + "th{background:#eee}.num{font-weight:bold;width:12%}.bubble{display:inline-block;width:13px;height:13px;border:1.5px solid #111;border-radius:50%}"
                + ".page{page-break-after:always}.page:last-child{page-break-after:auto}.small{font-size:9px}"
                + "</style></head><body>"
                + "<section class='page'><h1>" + safeExam + "</h1><h2>برگهٔ مشخصات شرکت‌کننده</h2>"
                + "<img class='qr' src='data:image/png;base64," + encoded + "'/>"
                + "<div class='meta'>نام: " + safeName + "</div><div class='meta'>شناسه: " + escapeHtml(studentId) + "</div>"
                + "<div class='meta'>تعداد سؤال: " + count + "</div><div class='clear'></div>"
                + "<div class='instructions'>این صفحه فقط برگهٔ مشخصات و QR است. QR باید با پاسخنامهٔ همین شرکت‌کننده مطابقت داشته باشد.</div>"
                + "<h2>برگهٔ سؤال</h2><p>برگهٔ سؤال اصلی را جداگانه چاپ یا ضمیمه کنید.</p></section>"
                + "<section><h1>" + safeExam + "</h1><h2>پاسخنامهٔ چهارگزینه‌ای</h2>"
                + "<img class='qr' src='data:image/png;base64," + encoded + "'/>"
                + "<div class='meta'>شرکت‌کننده: " + safeName + "</div><div class='meta'>شناسه: " + escapeHtml(studentId) + "</div>"
                + "<div class='meta'>برای هر سؤال فقط یک دایره را کاملاً پر کنید؛ علامت ضربدر یا تیک نزنید.</div><div class='clear'></div>"
                + "<table><thead><tr><th>سؤال</th><th>A</th><th>B</th><th>C</th><th>D</th></tr></thead><tbody>"
                + answerRows + "</tbody></table><p class='small'>نسخهٔ اولیهٔ پاسخنامه؛ تصحیح خودکار از روی حباب‌ها هنوز فعال نیست.</p></section>"
                + "</body></html>";
            WebView web = new WebView(this);
            web.getSettings().setDefaultTextEncodingName("UTF-8");
            web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (pm != null) {
                        android.print.PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("AzmoonYar-" + studentName);
                        pm.print("AzmoonYar-" + studentName, adapter,
                            new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
                    } else info("قابلیت چاپ در این دستگاه در دسترس نیست.");
                }
            });
        } catch (Exception e) {
            info("ساخت برگه برای چاپ ناموفق بود؛ دوباره تلاش کن.");
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void showKeyDialog() {
        if(examName().isEmpty()) { info("ابتدا یک آزمون تعریف کن."); return; }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8),dp(6),dp(8),dp(2));
        EditText q=new EditText(this); q.setSingleLine(true); q.setInputType(2); q.setHint("شماره سؤال"); q.setText("1"); form.addView(q);
        RadioGroup rg=new RadioGroup(this); rg.setOrientation(RadioGroup.HORIZONTAL);
        String[] options={"A","B","C","D"};
        for(String option:options) { RadioButton rb=new RadioButton(this); rb.setText(option); rb.setTag(option); rg.addView(rb); }
        form.addView(rg);
        new AlertDialog.Builder(this).setTitle("ثبت یک پاسخ در کلید").setMessage("شماره سؤال را وارد و گزینه صحیح را انتخاب کن.")
          .setView(form).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",(d,w)->{
            int index;
            try { index=Integer.parseInt(q.getText().toString().trim())-1; } catch(Exception e) { info("شماره سؤال معتبر نیست."); return; }
            if(index<0 || index>=questionCount()) { info("شماره سؤال باید بین ۱ و "+questionCount()+" باشد."); return; }
            int checked=rg.getCheckedRadioButtonId();
            if(checked==-1) { info("یک گزینه از A تا D انتخاب کن."); return; }
            RadioButton selected=rg.findViewById(checked);
            StringBuilder k=new StringBuilder(key());
            while(k.length()<questionCount()) k.append('-');
            k.setCharAt(index, selected.getTag().toString().charAt(0));
            prefs.edit().putString("answer_key",k.toString()).apply();
            refreshSummary(); info("پاسخ سؤال "+(index+1)+" ذخیره شد.");
          }).show();
    }

    private void showKeySummary() {
        if(examName().isEmpty()) { info("هنوز آزمونی تعریف نشده است."); return; }
        StringBuilder lines=new StringBuilder();
        String k=key(); int filled=0;
        for(int i=0;i<questionCount();i++) {
            char a=i<k.length()?k.charAt(i):'-';
            if(a>='A'&&a<='D') filled++;
            lines.append(String.format(Locale.ROOT,"%03d  :  %s%n",i+1,(a=='-'?"ثبت نشده":String.valueOf(a))));
        }
        new AlertDialog.Builder(this).setTitle("کلید پاسخ — "+filled+"/"+questionCount())
          .setMessage(lines.toString()).setPositiveButton("بستن",null).show();
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
                : "تعداد سؤال‌های ذخیره‌شده: " + bank.length() + "\\nهر نسخه از همین بانک ساخته می‌شود؛ ترتیب سؤال‌ها و گزینه‌ها جابه‌جا می‌شوند.")
            .setPositiveButton("افزودن سؤال", (d,w) -> showAddBankQuestionDialog())
            .setNeutralButton("بستن", null)
            .setNegativeButton("ساخت نسخه‌ها", (d,w) -> generateRandomVariants())
            .show();
    }

    private void showAddBankQuestionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(10), dp(4), dp(10), dp(4));
        EditText question = new EditText(this);
        question.setHint("متن سؤال");
        form.addView(question);
        EditText topic = new EditText(this);
        topic.setHint("مبحث (اختیاری؛ مثل نوار قلب یا رادیکال)");
        form.addView(topic);
        EditText[] options = new EditText[4];
        for (int i = 0; i < 4; i++) {
            options[i] = new EditText(this);
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
        String[] labels = {"۱ نسخه (یک مدل)", "۲ نسخه", "۳ نسخه", "۵ نسخه", "۱۰ نسخه"};
        int[] counts = {1, 2, 3, 5, 10};
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
            .setMessage("یک مدل ثابت یا چند نسخه با ترتیب متفاوت سؤال‌ها و گزینه‌ها بساز.")
            .setView(group)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ساخت پیش‌نمایش", (dialog, which) -> {
                try {
                    JSONArray variants = RandomExamBuilder.generateVariants(bank, selectedCount[0], System.nanoTime());
                    StringBuilder report = new StringBuilder();
                    report.append("تعداد نسخه‌های ساخته‌شده: ").append(variants.length()).append("\\n")
                        .append("ترتیب سؤال‌ها و گزینه‌ها جابه‌جا شده و کلید هر نسخه جداگانه محاسبه شده است.\\n")
                        .append("توجه: این پیش‌نمایش است؛ چاپ نسخه‌بندی‌شده و اتصال شناسهٔ نسخه به QR و تصحیح هنوز تکمیل نشده است.\\n\\n");
                    for (int v = 0; v < variants.length(); v++) {
                        JSONObject variant = variants.optJSONObject(v);
                        if (variant == null) continue;
                        report.append("نسخه ").append(variant.optString("variant_id")).append(" — کلید: ")
                            .append(variant.optString("answer_key")).append("\\n");
                        JSONArray questions = variant.optJSONArray("questions");
                        if (questions != null) {
                            for (int i = 0; i < questions.length(); i++) {
                                JSONObject q = questions.optJSONObject(i);
                                if (q != null) report.append(i + 1).append(". ")
                                    .append(q.optString("text")).append(" [").append(q.optString("correct_letter")).append("]")
                                    .append("\\n");
                            }
                        }
                        report.append("\\n");
                    }
                    new AlertDialog.Builder(this).setTitle("پیش‌نمایش نسخه‌های آزمون")
                        .setMessage(report.toString()).setPositiveButton("بستن", null).show();
                } catch (Exception e) {
                    info("ساخت نسخه‌ها ناموفق بود: " + e.getMessage());
                }
            }).show();
    }

    private void showReportsCenter() {
        ReportStore reportStore = new ReportStore(this);
        JSONArray inbox = reportStore.getInbox();
        StringBuilder message = new StringBuilder();
        message.append("صندوق گزارش‌های محلی\\n");
        message.append("گزارش‌ها فقط روی همین دستگاه ذخیره می‌شوند.\\n\\n");
        if (inbox.length() == 0) {
            message.append("هنوز گزارشی ثبت نشده است. گزارش واقعی پس از پیاده‌سازی تصحیح و تحلیل خودکار ساخته می‌شود.\\n\\n");
        } else {
            for (int i = inbox.length() - 1; i >= 0; i--) {
                JSONObject item = inbox.optJSONObject(i);
                if (item == null) continue;
                message.append(item.optString("title", "گزارش ارزیابی")).append("\\n")
                    .append(item.optString("body", "")).append("\\n")
                    .append(item.optString("created_at", "")).append("\\n")
                    .append(item.optBoolean("read", false) ? "خوانده‌شده" : "خوانده‌نشده")
                    .append("\\n\\n");
                reportStore.markRead(item.optString("id", ""));
            }
        }
        message.append("حریم خصوصی و رضایت\\n")
            .append("• ارسال به والدین تا زمان ثبت رضایت و تأیید واقعی شماره تماس غیرفعال است.\\n")
            .append("• ثبت نام یا شماره به‌تنهایی به معنی تأیید هویت نیست.\\n")
            .append("• ارسال پیامک/ایمیل یا اعلان خارج از برنامه هنوز فعال نشده است.\\n")
            .append("• تحلیل و تصحیح خودکار هنوز تکمیل نشده؛ هیچ نتیجهٔ ساختگی ارسال نمی‌شود.");
        new AlertDialog.Builder(this).setTitle("گزارش‌ها و حریم خصوصی")
            .setMessage(message.toString())
            .setPositiveButton("باشه", null)
            .show();
    }

    private void info(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("باشه",null).show();
    }
}
