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
    private CheckBox hasCornerMarks;
    private RadioGroup anchorMode;
    private RadioGroup tableMode;
    private TextView savedStatus;
    private ImageView samplePreview;
    private Uri sampleUri;
    private static final int REQUEST_SAMPLE_IMAGE = 8402;
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

    private void chooseSampleImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_SAMPLE_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAMPLE_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
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
