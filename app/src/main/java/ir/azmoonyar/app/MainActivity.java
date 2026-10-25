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
            String payload = "AZY1|" + examName() + "|" + studentId;
            com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, 360, 360);
            Bitmap qr = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                qr.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            qr.compress(Bitmap.CompressFormat.PNG, 100, output);
            String encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            String safeName = studentName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeExam = examName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String html = "<html><meta charset='utf-8'><body style='font-family:sans-serif;text-align:center;color:#111'>"
                + "<h1>" + safeExam + "</h1><h2>دانش‌آموز: " + safeName + "</h2>"
                + "<p>شناسه: " + studentId + "</p><hr><h2>برگهٔ سؤال</h2>"
                + "<p>این QR را روی برگهٔ سؤال قرار بده</p><img style='width:180px;height:180px' src='data:image/png;base64," + encoded + "'/>"
                + "<hr style='margin:28px 0'><h2>پاسخنامه</h2><p>همین QR باید روی پاسخنامهٔ همین دانش‌آموز باشد</p>"
                + "<img style='width:180px;height:180px' src='data:image/png;base64," + encoded + "'/>"
                + "<p style='font-size:10px'>کد داخلی: " + studentId + "</p></body></html>";
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
            info("ساخت QR برای چاپ ناموفق بود؛ دوباره تلاش کن.");
        }
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

    private void info(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("باشه",null).show();
    }
}
