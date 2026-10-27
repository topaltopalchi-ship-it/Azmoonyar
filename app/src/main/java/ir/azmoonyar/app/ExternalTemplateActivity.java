package ir.azmoonyar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
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
import android.widget.Toast;

/**
 * Initial external-sheet template setup. This stores a reusable layout profile;
 * it deliberately does not claim that image detection or scoring is validated.
 */
public final class ExternalTemplateActivity extends Activity {
    private EditText templateName;
    private EditText questionCount;
    private EditText optionCount;
    private CheckBox hasCornerMarks;
    private RadioGroup anchorMode;
    private RadioGroup tableMode;
    private TextView savedStatus;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 27, 114));
        getWindow().setNavigationBarColor(Color.rgb(7, 27, 114));
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prefs = getSharedPreferences("external_omr_templates", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(24));
        page.setBackgroundColor(Color.rgb(245, 248, 255));
        scroll.addView(page);

        TextView title = text("تعریف قالب پاسخنامهٔ خارجی", 23, true);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView intro = text("این تنظیمات برای هر نوع پاسخنامهٔ خارجی جداگانه ذخیره می‌شود و قالب استاندارد ۱۰۰ سؤالی آزمون‌یار را تغییر نمی‌دهد.", 15, false);
        intro.setPadding(0, dp(10), 0, dp(16));
        page.addView(intro);

        page.addView(label("نام قالب"));
        templateName = edit("مثلاً پاسخنامهٔ ریاضی");
        page.addView(templateName);

        page.addView(label("تعداد سؤال‌ها (۱ تا ۱۰۰)"));
        questionCount = edit("10");
        questionCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        page.addView(questionCount);

        page.addView(label("تعداد گزینه‌ها (۲ تا ۶)"));
        optionCount = edit("4");
        optionCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        page.addView(optionCount);

        hasCornerMarks = new CheckBox(this);
        hasCornerMarks.setText("برگه چهار مربع/علامت کالیبراسیون در گوشه‌ها دارد");
        hasCornerMarks.setTextSize(15);
        hasCornerMarks.setChecked(true);
        page.addView(hasCornerMarks);

        page.addView(label("روش مرجع‌یابی تصویر"));
        anchorMode = new RadioGroup(this);
        anchorMode.setOrientation(RadioGroup.VERTICAL);
        RadioButton marks = radio("گوشه‌نشان‌ها (اگر موجود باشند)", "marks");
        RadioButton pageEdges = radio("لبه‌های خود کاغذ (بدون گوشه‌نشان)", "edges");
        RadioButton table = radio("کادر جدول پاسخ‌ها", "table");
        anchorMode.addView(marks);
        anchorMode.addView(pageEdges);
        anchorMode.addView(table);
        anchorMode.check(marks.getId());
        page.addView(anchorMode);
        hasCornerMarks.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && anchorMode.getCheckedRadioButtonId() == marks.getId()) {
                anchorMode.check(pageEdges.getId());
            }
            marks.setEnabled(checked);
        });

        page.addView(label("چیدمان جدول پاسخ‌ها"));
        tableMode = new RadioGroup(this);
        tableMode.setOrientation(RadioGroup.VERTICAL);
        RadioButton single = radio("یک جدول پیوسته", "single");
        RadioButton doubleColumn = radio("دو ستون سؤال", "double");
        RadioButton manual = radio("چیدمان متفاوت؛ نیازمند تنظیم دستی", "manual");
        tableMode.addView(single);
        tableMode.addView(doubleColumn);
        tableMode.addView(manual);
        tableMode.check(single.getId());
        page.addView(tableMode);

        Button save = new Button(this);
        save.setText("ذخیرهٔ تنظیمات قالب");
        save.setAllCaps(false);
        page.addView(save);
        save.setOnClickListener(v -> saveProfile());

        savedStatus = text("", 14, false);
        savedStatus.setPadding(0, dp(12), 0, 0);
        page.addView(savedStatus);

        Button explain = new Button(this);
        explain.setText("روش کالیبراسیون بدون گوشه‌نشان");
        explain.setAllCaps(false);
        page.addView(explain);
        explain.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("کالیبراسیون بدون علامت گوشه")
                .setMessage("اگر برگه مربع‌های گوشه ندارد، روش مرجع‌یابی را روی «لبه‌های خود کاغذ» یا «کادر جدول پاسخ‌ها» بگذارید. برای جلوگیری از جابه‌جایی پاسخ‌ها باید ابتدا چهار گوشهٔ صفحه یا مرز جدول و سپس ردیف‌ها و ستون‌های حباب روی یک نمونهٔ واضح تأیید شوند. این صفحه فعلاً پروفایل تنظیمات را ذخیره می‌کند؛ تشخیص خودکار تصویر و نمره‌دهی برای این قالب هنوز اعتبارسنجی نشده است.")
                .setPositiveButton("متوجه شدم", null).show());

        loadProfile();
        setContentView(scroll);
    }

    private void saveProfile() {
        String name = templateName.getText().toString().trim();
        int questions = number(questionCount.getText().toString(), -1);
        int options = number(optionCount.getText().toString(), -1);
        if (name.isEmpty()) {
            templateName.setError("نام قالب را وارد کنید");
            return;
        }
        if (questions < 1 || questions > 100) {
            questionCount.setError("تعداد باید بین ۱ تا ۱۰۰ باشد");
            return;
        }
        if (options < 2 || options > 6) {
            optionCount.setError("تعداد گزینه باید بین ۲ تا ۶ باشد");
            return;
        }
        RadioButton selectedAnchor = findViewById(anchorMode.getCheckedRadioButtonId());
        RadioButton selectedTable = findViewById(tableMode.getCheckedRadioButtonId());
        prefs.edit()
                .putString("name", name)
                .putInt("questions", questions)
                .putInt("options", options)
                .putBoolean("has_corner_marks", hasCornerMarks.isChecked())
                .putString("anchor_mode", selectedAnchor == null ? "edges" : String.valueOf(selectedAnchor.getTag()))
                .putString("table_mode", selectedTable == null ? "single" : String.valueOf(selectedTable.getTag()))
                .putBoolean("profile_saved", true)
                .apply();
        savedStatus.setText("تنظیمات قالب ذخیره شد. مرحلهٔ بعد، تأیید محل جدول و حباب‌ها روی تصویر نمونه است؛ هنوز تصحیح خودکار این قالب فعال نشده است.");
        Toast.makeText(this, "قالب خارجی ذخیره شد", Toast.LENGTH_SHORT).show();
    }

    private void loadProfile() {
        if (!prefs.getBoolean("profile_saved", false)) return;
        templateName.setText(prefs.getString("name", ""));
        questionCount.setText(String.valueOf(prefs.getInt("questions", 10)));
        optionCount.setText(String.valueOf(prefs.getInt("options", 4)));
        boolean marks = prefs.getBoolean("has_corner_marks", true);
        hasCornerMarks.setChecked(marks);
        String anchor = prefs.getString("anchor_mode", marks ? "marks" : "edges");
        for (int i = 0; i < anchorMode.getChildCount(); i++) {
            RadioButton b = (RadioButton) anchorMode.getChildAt(i);
            if (anchor.equals(String.valueOf(b.getTag()))) anchorMode.check(b.getId());
        }
        String layout = prefs.getString("table_mode", "single");
        for (int i = 0; i < tableMode.getChildCount(); i++) {
            RadioButton b = (RadioButton) tableMode.getChildAt(i);
            if (layout.equals(String.valueOf(b.getTag()))) tableMode.check(b.getId());
        }
        savedStatus.setText("یک قالب ذخیره‌شده پیدا شد؛ با ذخیرهٔ دوباره می‌توانید تنظیماتش را به‌روزرسانی کنید.");
    }

    private RadioButton radio(String value, String tag) {
        RadioButton b = new RadioButton(this);
        b.setText(value);
        b.setTag(tag);
        b.setId(View.generateViewId());
        b.setTextSize(15);
        return b;
    }
    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setHint(hint);
        return e;
    }
    private TextView label(String value) {
        TextView t = text(value, 16, true);
        t.setPadding(0, dp(14), 0, dp(3));
        return t;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(bold ? Color.rgb(7, 40, 130) : Color.rgb(45, 58, 86));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private int number(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception e) { return fallback; }
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
