package ir.azmoonyar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;
import java.util.Locale;

/** Offline exam setup and answer-key editor. Camera OMR is a future milestone. */
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
        addButton("ثبت کلید پاسخ", v -> showKeyDialog());
        addButton("نمایش کلید و وضعیت تکمیل", v -> showKeySummary());
        addButton("اسکن پاسخنامه", v -> info("اسکن دوربین هنوز پیاده‌سازی نشده است. مرحله بعدی پس از آزمون و کلید پاسخ، ساخت قالب استاندارد و الگوریتم OMR است."));
        TextView note = new TextView(this);
        note.setText("اطلاعات آزمون و کلید پاسخ روی همین دستگاه ذخیره می‌شود. این نسخه هنوز پاسخنامه را اسکن یا نمره‌دهی نمی‌کند.");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(20); root.addView(note,np);
        setContentView(scroll);
    }

    private void addButton(String text, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56)); p.topMargin=dp(10); root.addView(b,p);
    }

    private void refreshSummary() {
        if (summary == null) return;
        int filled=0; String k=key();
        for(int i=0;i<Math.min(k.length(),questionCount());i++) if(k.charAt(i)>='A' && k.charAt(i)<='D') filled++;
        String name=examName().isEmpty() ? "هنوز آزمونی تعریف نشده" : "آزمون: "+examName();
        summary.setText(name+"\nتعداد سؤال: "+questionCount()+"\nکلید ثبت‌شده: "+filled+" از "+questionCount());
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
