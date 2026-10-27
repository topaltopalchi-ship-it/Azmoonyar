package ir.azmoonyar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
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
    private EditText answerKeyInput;
    private CheckBox hasCornerMarks;
    private RadioGroup anchorMode;
    private RadioGroup tableMode;
    private TextView savedStatus;
    private ImageView samplePreview;
    private Uri sampleUri;
    private static final int REQUEST_SAMPLE_IMAGE = 8402;
    private static final int REQUEST_FILLED_IMAGE = 8403;
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

        page.addView(label("کلید پاسخ صحیح (با ویرگول جدا کنید)"));
        answerKeyInput = edit("");
        answerKeyInput.setSingleLine(false);
        answerKeyInput.setMinLines(2);
        answerKeyInput.setHint("مثال: D,B,A,C,D");
        page.addView(answerKeyInput);
        page.addView(text("برای قالب چهارگزینه‌ای پیش‌فرض، گزینه‌ها از چپ به راست D/C/B/A هستند.", 13, false));

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

        page.addView(label("نمونهٔ خالی پاسخنامه"));
        TextView sampleHelp = text("یک عکس واضح از کل برگهٔ خالی انتخاب کنید. فعلاً عکس برای مرجع کالیبراسیون ذخیره می‌شود؛ تشخیص خودکار حباب‌ها مرحلهٔ بعدی است.", 14, false);
        page.addView(sampleHelp);
        Button chooseSample = new Button(this);
        chooseSample.setText("انتخاب عکس پاسخنامهٔ خالی");
        chooseSample.setAllCaps(false);
        page.addView(chooseSample);
        chooseSample.setOnClickListener(v -> chooseSampleImage());
        samplePreview = new ImageView(this);
        samplePreview.setAdjustViewBounds(true);
        samplePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(260));
        previewParams.topMargin = dp(8);
        page.addView(samplePreview, previewParams);
        Button calibrate = new Button(this);
        calibrate.setText("تعیین محدودهٔ جدول روی عکس");
        calibrate.setAllCaps(false);
        page.addView(calibrate);
        calibrate.setOnClickListener(v -> openTableCalibration());
        Button gridCalibrate = new Button(this);
        gridCalibrate.setText("تنظیم ردیف سؤال‌ها و ستون گزینه‌ها");
        gridCalibrate.setAllCaps(false);
        page.addView(gridCalibrate);
        gridCalibrate.setOnClickListener(v -> openGridCalibration());
        Button readFilled = new Button(this);
        readFilled.setText("خواندن آزمایشی پاسخ‌ها از عکس برگهٔ پرشده");
        readFilled.setAllCaps(false);
        page.addView(readFilled);
        readFilled.setOnClickListener(v -> chooseFilledImage());

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
        String normalizedKey = normalizeAnswerKey(answerKeyInput.getText().toString(), questions, options);
        if (normalizedKey == null) {
            answerKeyInput.setError("کلید باید دقیقاً به تعداد سؤال‌ها و با پاسخ‌های معتبرِ جداشده با ویرگول وارد شود");
            return;
        }
        RadioButton selectedAnchor = findViewById(anchorMode.getCheckedRadioButtonId());
        RadioButton selectedTable = findViewById(tableMode.getCheckedRadioButtonId());
        prefs.edit()
                .putString("name", name)
                .putInt("questions", questions)
                .putInt("options", options)
                .putString("answer_key", normalizedKey)
                .putBoolean("has_corner_marks", hasCornerMarks.isChecked())
                .putString("anchor_mode", selectedAnchor == null ? "edges" : String.valueOf(selectedAnchor.getTag()))
                .putString("table_mode", selectedTable == null ? "single" : String.valueOf(selectedTable.getTag()))
                .putString("sample_image_uri", sampleUri == null ? prefs.getString("sample_image_uri", "") : sampleUri.toString())
                .putBoolean("profile_saved", true)
                .apply();
        savedStatus.setText(sampleUri == null ? "تنظیمات قالب ذخیره شد. هنوز عکس نمونه انتخاب نشده و تصحیح خودکار فعال نیست." : "تنظیمات و عکس نمونه ذخیره شد. مرحلهٔ بعد تعیین و تأیید محل حباب‌هاست؛ تصحیح خودکار هنوز فعال نیست.");
        Toast.makeText(this, "قالب خارجی ذخیره شد", Toast.LENGTH_SHORT).show();
    }

    private void loadProfile() {
        String savedUri = prefs.getString("sample_image_uri", "");
        if (!savedUri.isEmpty()) {
            try { sampleUri = Uri.parse(savedUri); samplePreview.setImageURI(sampleUri); } catch (Exception ignored) { }
        }
        if (!prefs.getBoolean("profile_saved", false)) return;
        templateName.setText(prefs.getString("name", ""));
        questionCount.setText(String.valueOf(prefs.getInt("questions", 10)));
        optionCount.setText(String.valueOf(prefs.getInt("options", 4)));
        answerKeyInput.setText(prefs.getString("answer_key", ""));
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

    private void openTableCalibration() {
        if (sampleUri == null) {
            Toast.makeText(this, "ابتدا عکس پاسخنامهٔ خالی را انتخاب کنید", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(sampleUri));
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            TableCalibrationView calibrationView = new TableCalibrationView(bitmap);
            new AlertDialog.Builder(this)
                    .setTitle("محدودهٔ جدول پاسخ‌ها")
                    .setMessage("دو گوشهٔ روبه‌روی جدول را لمس کنید: ابتدا بالا-چپ، سپس پایین-راست. این روش به مربع‌های گوشهٔ صفحه وابسته نیست.")
                    .setView(calibrationView)
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("ذخیرهٔ محدوده", (dialog, which) -> {
                        if (!calibrationView.hasRectangle()) {
                            Toast.makeText(this, "برای ذخیره باید هر دو گوشه را مشخص کنید", Toast.LENGTH_LONG).show();
                            return;
                        }
                        RectF rect = calibrationView.normalizedRect();
                        prefs.edit().putFloat("table_left", rect.left).putFloat("table_top", rect.top)
                                .putFloat("table_right", rect.right).putFloat("table_bottom", rect.bottom)
                                .putBoolean("table_bounds_saved", true).apply();
                        savedStatus.setText(String.format(java.util.Locale.US,
                                "محدودهٔ جدول ذخیره شد: چپ %.2f، بالا %.2f، راست %.2f، پایین %.2f. مرحلهٔ بعد تعیین مراکز حباب‌هاست.",
                                rect.left, rect.top, rect.right, rect.bottom));
                    }).show();
        } catch (Exception e) {
            Toast.makeText(this, "باز کردن عکس ناموفق بود: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openGridCalibration() {
        if (sampleUri == null || !prefs.getBoolean("table_bounds_saved", false)) {
            Toast.makeText(this, "ابتدا عکس را انتخاب و محدودهٔ جدول را ذخیره کنید", Toast.LENGTH_LONG).show();
            return;
        }
        int questions = number(questionCount.getText().toString(), 0);
        int options = number(optionCount.getText().toString(), 0);
        if (questions < 1 || questions > 100 || options < 2 || options > 6) {
            Toast.makeText(this, "تعداد سؤال یا گزینه معتبر نیست", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(sampleUri));
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            GridCalibrationView grid = new GridCalibrationView(bitmap, questions, options,
                    prefs.getFloat("table_left", 0), prefs.getFloat("table_top", 0),
                    prefs.getFloat("table_right", 1), prefs.getFloat("table_bottom", 1));
            new AlertDialog.Builder(this).setTitle("پیش‌نمایش شبکهٔ حباب‌ها")
                    .setMessage("شبکهٔ پیشنهادی روی محدودهٔ ذخیره‌شده نمایش داده می‌شود. اگر ردیف‌ها و ستون‌ها با حباب‌ها منطبق نیستند، محدودهٔ جدول را دوباره تنظیم کنید.")
                    .setView(grid).setNegativeButton("بستن", null)
                    .setPositiveButton("تأیید و ذخیرهٔ شبکه", (dialog, which) -> {
                        prefs.edit().putInt("grid_questions", questions).putInt("grid_options", options)
                                .putString("grid_option_order", options == 4 ? "D,C,B,A" : "custom")
                                .putBoolean("grid_saved", true).apply();
                        savedStatus.setText("شبکهٔ پیشنهادی " + questions + " ردیف و " + options + " ستون ذخیره شد. ترتیب پیش‌فرض چهارگزینه‌ای از چپ به راست D/C/B/A است؛ خواندن پاسخ‌ها هنوز باید آزمایش شود.");
                    }).show();
        } catch (Exception e) {
            Toast.makeText(this, "باز کردن عکس ناموفق بود: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseFilledImage() {
        if (!prefs.getBoolean("table_bounds_saved", false)) {
            Toast.makeText(this, "ابتدا عکس خالی را انتخاب و محدودهٔ جدول را ذخیره کنید", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FILLED_IMAGE);
    }

    private void analyzeFilledImage(Uri uri) {
        Bitmap bitmap = null;
        try {
            java.io.InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) throw new IllegalStateException("فایل عکس باز نشد");
            try { bitmap = BitmapFactory.decodeStream(stream); } finally { stream.close(); }
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            int questions = number(questionCount.getText().toString(), prefs.getInt("questions", 10));
            int options = number(optionCount.getText().toString(), prefs.getInt("options", 4));
            if (questions < 1 || questions > 100 || options < 2 || options > 6) throw new IllegalArgumentException("تعداد سؤال یا گزینه معتبر نیست");
            float left=prefs.getFloat("table_left",0), top=prefs.getFloat("table_top",0);
            float right=prefs.getFloat("table_right",1), bottom=prefs.getFloat("table_bottom",1);
            if (!(left>=0 && top>=0 && right<=1 && bottom<=1 && right>left && bottom>top)) throw new IllegalStateException("محدودهٔ جدول معتبر نیست");
            String key = normalizeAnswerKey(prefs.getString("answer_key", answerKeyInput.getText().toString()), questions, options);
            if (key == null) throw new IllegalStateException("ابتدا کلید صحیح تمام سؤال‌ها را وارد و ذخیره کنید");
            String[] correct = key.split(",");
            StringBuilder report = new StringBuilder();
            int answered=0, blank=0, ambiguous=0, correctCount=0, wrongCount=0;
            for (int q=0;q<questions;q++) {
                double[] darkness = new double[options];
                for (int o=0;o<options;o++) {
                    double nx=left+(o+0.5)*(right-left)/options;
                    double ny=top+(q+0.5)*(bottom-top)/questions;
                    darkness[o]=sampleDarkness(bitmap,nx,ny,
                            Math.max(1,(right-left)*bitmap.getWidth()/options*0.17),
                            Math.max(1,(bottom-top)*bitmap.getHeight()/questions*0.17));
                }
                int best=0, second=-1;
                for(int o=1;o<options;o++) {
                    if(darkness[o]<darkness[best]) { second=best; best=o; }
                    else if(second<0 || darkness[o]<darkness[second]) second=o;
                }
                double next=second<0?255:darkness[second];
                String answer;
                if(darkness[best]>150) { answer="خالی/نامشخص"; blank++; }
                else if(next<165 && next-darkness[best]<12) { answer="چندعلامتی/نیازمند بازبینی"; ambiguous++; }
                else {
                    answer=optionLabel(best,options); answered++;
                    if (answer.equals(correct[q])) { correctCount++; answer += " ✓"; }
                    else { wrongCount++; answer += " ✗ (صحیح: " + correct[q] + ")"; }
                }
                report.append("سؤال ").append(q+1).append(": ").append(answer)
                        .append("  [تیرگی ").append(Math.round(darkness[best])).append("]").append("\n");
            }
            int scored = correctCount + wrongCount;
            String scoreSummary = "صحیح: "+correctCount+" | غلط: "+wrongCount+" | خالی: "+blank+" | بازبینی: "+ambiguous + (scored > 0 ? " | درصد پاسخ‌های قطعی: "+Math.round(100.0*correctCount/scored)+"٪" : "");
            TextView resultView=text("نتیجهٔ آزمایشی تصحیح\n"+scoreSummary+"\n\n"+report,14,false);
            resultView.setTextIsSelectable(true);
            ScrollView resultScroll=new ScrollView(this); resultScroll.addView(resultView);
            new AlertDialog.Builder(this).setTitle("نتیجهٔ خواندن آزمایشی")
                    .setMessage("این روش از شبکهٔ منظم داخل محدودهٔ جدول نمونه‌برداری می‌کند؛ برای عکس چرخیده، جدول نامنظم یا شبکهٔ ناهماهنگ ممکن است اشتباه کند. هیچ نمره‌ای ذخیره نمی‌شود.")
                    .setView(resultScroll).setPositiveButton("باشه",null).show();
        } catch(Exception e) {
            Toast.makeText(this,"خواندن عکس ناموفق بود: "+e.getMessage(),Toast.LENGTH_LONG).show();
        } finally { if(bitmap!=null) bitmap.recycle(); }
    }

    private double sampleDarkness(Bitmap bitmap, double nx, double ny, double rx, double ry) {
        int cx=(int)Math.round(nx*bitmap.getWidth()), cy=(int)Math.round(ny*bitmap.getHeight());
        int radX=Math.max(2,(int)Math.round(rx)), radY=Math.max(2,(int)Math.round(ry));
        long total=0; int count=0;
        for(int y=Math.max(0,cy-radY);y<Math.min(bitmap.getHeight(),cy+radY+1);y+=2) {
            for(int x=Math.max(0,cx-radX);x<Math.min(bitmap.getWidth(),cx+radX+1);x+=2) {
                double dx=(x-cx)/(double)radX, dy=(y-cy)/(double)radY;
                if(dx*dx+dy*dy>1) continue;
                int pixel=bitmap.getPixel(x,y);
                int luminance=(int)(0.299*Color.red(pixel)+0.587*Color.green(pixel)+0.114*Color.blue(pixel));
                total+=luminance; count++;
            }
        }
        return count==0?255:(double)total/count;
    }

    private String optionLabel(int index,int options) {
        if(options==4) return new String[]{"D","C","B","A"}[index];
        return String.valueOf((char)('A' + index));
    }

    private String normalizeAnswerKey(String raw, int questions, int options) {
        if (raw == null) return null;
        String cleaned = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('؛', ',').replace('，', ',').replace(';', ',');
        cleaned = cleaned.replace(" ", "").replace("\\n", "").replace("\\t", "");
        if (cleaned.isEmpty()) return null;
        String[] parts = cleaned.split(",");
        if (parts.length != questions) return null;
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            String value = part.trim();
            if (value.matches("[1-6]")) {
                int n = Integer.parseInt(value);
                if (n > options) return null;
                value = options == 4 ? new String[]{"D","C","B","A"}[n-1] : String.valueOf((char)('A'+n-1));
            }
            boolean valid = false;
            for (int i=0;i<options;i++) if (value.equals(optionLabel(i, options))) valid = true;
            if (!valid) return null;
            if (normalized.length()>0) normalized.append(',');
            normalized.append(value);
        }
        return normalized.toString();
    }

    private void chooseSampleImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_SAMPLE_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_FILLED_IMAGE) {
            Uri filledUri=data.getData();
            try { getContentResolver().takePersistableUriPermission(filledUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            analyzeFilledImage(filledUri);
            return;
        }
        if (requestCode != REQUEST_SAMPLE_IMAGE) return;
        sampleUri = data.getData();
        try { getContentResolver().takePersistableUriPermission(sampleUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        samplePreview.setImageURI(sampleUri);
        savedStatus.setText("عکس نمونه انتخاب شد؛ برای ذخیرهٔ دائمی، «ذخیرهٔ تنظیمات قالب» را بزنید.");
    }

    private final class GridCalibrationView extends View {
        private final Bitmap bitmap;
        private final int questions, options;
        private final float left, top, right, bottom;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        GridCalibrationView(Bitmap bitmap, int questions, int options, float left, float top, float right, float bottom) {
            super(ExternalTemplateActivity.this); this.bitmap=bitmap; this.questions=questions; this.options=options;
            this.left=left; this.top=top; this.right=right; this.bottom=bottom;
            setLayoutParams(new LinearLayout.LayoutParams(-1, dp(380)));
            gridPaint.setColor(Color.rgb(0,160,70)); gridPaint.setStrokeWidth(dp(1)); gridPaint.setStyle(Paint.Style.STROKE);
            labelPaint.setColor(Color.RED); labelPaint.setTextSize(dp(11)); labelPaint.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());
            float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
            imageRect.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
            canvas.drawBitmap(bitmap,null,imageRect,imagePaint);
            float l=imageRect.left+left*imageRect.width(), r=imageRect.left+right*imageRect.width();
            float t=imageRect.top+top*imageRect.height(), b=imageRect.top+bottom*imageRect.height();
            canvas.drawRect(l,t,r,b,gridPaint);
            for(int q=0;q<questions;q++) {
                float y=t+(q+0.5f)*(b-t)/questions;
                for(int o=0;o<options;o++) {
                    float x=l+(o+0.5f)*(r-l)/options;
                    canvas.drawCircle(x,y,Math.max(dp(2),Math.min(dp(8),(r-l)/options*0.22f)),gridPaint);
                }
            }
            canvas.drawText("ردیف‌ها: "+questions+" | ستون‌ها: "+options,(l+r)/2f,Math.max(dp(14),t-dp(5)),labelPaint);
        }
    }

    private final class TableCalibrationView extends View {
        private final Bitmap bitmap;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] points = new float[4];
        private int pointCount = 0;
        private RectF imageRect = new RectF();
        TableCalibrationView(Bitmap bitmap) { super(ExternalTemplateActivity.this); this.bitmap = bitmap; setLayoutParams(new LinearLayout.LayoutParams(-1, dp(380))); linePaint.setColor(Color.RED); linePaint.setStrokeWidth(dp(3)); linePaint.setStyle(Paint.Style.STROKE); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
            float w=bitmap.getWidth()*scale, h=bitmap.getHeight()*scale;
            imageRect.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
            if(pointCount>0) canvas.drawCircle(points[0],points[1],dp(6),linePaint);
            if(pointCount>1) { canvas.drawCircle(points[2],points[3],dp(6),linePaint); canvas.drawRect(Math.min(points[0],points[2]),Math.min(points[1],points[3]),Math.max(points[0],points[2]),Math.max(points[1],points[3]),linePaint); }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if(event.getAction()!=android.view.MotionEvent.ACTION_UP) return true;
            if(!imageRect.contains(event.getX(),event.getY())) return true;
            if(pointCount>=2) pointCount=0;
            points[pointCount*2]=event.getX(); points[pointCount*2+1]=event.getY(); pointCount++; invalidate(); return true;
        }
        boolean hasRectangle() { return pointCount==2 && points[2]!=points[0] && points[3]!=points[1]; }
        RectF normalizedRect() {
            float l=Math.min(points[0],points[2]), r=Math.max(points[0],points[2]);
            float t=Math.min(points[1],points[3]), b=Math.max(points[1],points[3]);
            return new RectF((l-imageRect.left)/imageRect.width(),(t-imageRect.top)/imageRect.height(),(r-imageRect.left)/imageRect.width(),(b-imageRect.top)/imageRect.height());
        }
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
