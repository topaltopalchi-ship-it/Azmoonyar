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
import android.graphics.Matrix;
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
    private EditText participantNameInput;
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
        TextView intro = text("یک‌بار عکس کامل A4 خالی را انتخاب کنید و «تنظیم یک‌بارهٔ قالب پاسخنامه» را بزنید. محدودهٔ جدول و شبکهٔ حباب‌ها در همان راهنما تنظیم می‌شوند؛ برای برگه‌های بعدی فقط عکس کامل A4 را انتخاب کنید و دیگر این مراحل را تکرار نکنید.", 15, false);
        intro.setPadding(0, dp(10), 0, dp(16));
        page.addView(intro);

        page.addView(label("نام شرکت‌کننده برای برگهٔ در حال تصحیح"));
        participantNameInput = edit("نام و نام خانوادگی");
        page.addView(participantNameInput);

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
        TextView sampleHelp = text("تنظیم فقط یک‌بار برای هر قالب: عکس کامل A4 خالی را انتخاب کنید، کادر جدول و شبکه را دقیق تنظیم و ذخیره کنید. در تصحیح‌های بعدی فقط عکس کامل A4 پرشده را انتخاب کنید؛ کادر برای هر برگه دوباره کشیده نمی‌شود.", 14, false);
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
        calibrate.setText("تشخیص خودکار جدول از روی عکس");
        calibrate.setAllCaps(false);
        page.addView(calibrate);
        calibrate.setOnClickListener(v -> autoDetectTemplateTable());
        Button readFilled = new Button(this);
        readFilled.setText("تصحیح عکس کامل A4 پرشده");
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
        savedStatus.setText(sampleUri == null ? "تنظیمات قالب ذخیره شد. برای تصحیح عکس باید نمونهٔ خالی، محدودهٔ جدول و شبکهٔ حباب‌ها را تنظیم کنید." : "تنظیمات و عکس نمونه ذخیره شد. اکنون محدودهٔ جدول و شبکهٔ حباب‌ها را تعیین کنید؛ نتایج مبهم به‌عنوان نمرهٔ قطعی ذخیره نمی‌شوند.");
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

    private void autoDetectTemplateTable() {
        if (sampleUri == null) {
            Toast.makeText(this, "ابتدا عکس پاسخنامهٔ خالی را انتخاب کنید", Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap bitmap = null;
        try {
            bitmap = decodeOrientedBitmap(sampleUri);
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            ExternalSheetAutoDetector.Detection detection = ExternalSheetAutoDetector.detect(bitmap);
            if (detection == null) {
                new AlertDialog.Builder(this).setTitle("جدول خودکار پیدا نشد")
                        .setMessage("از این عکس نتوانستم کادر جدول را با اطمینان تشخیص بدهم. برای جلوگیری از ذخیرهٔ محدودهٔ اشتباه، تشخیص خودکار متوقف شد. می‌توانید روش دستی را باز کنید.")
                        .setNegativeButton("بستن", null)
                        .setPositiveButton("تنظیم دستی", (d, w) -> openTableCalibration()).show();
                return;
            }
            RectF rect = detection.table;
            prefs.edit().putFloat("table_left", rect.left).putFloat("table_top", rect.top)
                    .putFloat("table_right", rect.right).putFloat("table_bottom", rect.bottom)
                    .putBoolean("table_bounds_saved", true).putBoolean("grid_saved", false).apply();
            savedStatus.setText("جدول به‌طور خودکار پیدا شد. علامت‌های گوشهٔ تشخیص‌داده‌شده: "
                    + detection.cornerMarks + " از ۴. محدوده را در پیش‌نمایش شبکه بررسی کنید؛ هنوز اصلاح پرسپکتیو خودکار فعال نیست.");
            Toast.makeText(this, "محدودهٔ پیشنهادی جدول پیدا شد؛ پیش‌نمایش را بررسی کنید", Toast.LENGTH_LONG).show();
            openGridCalibration();
        } catch (Exception e) {
            Toast.makeText(this, "تشخیص خودکار ناموفق بود: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private void openTableCalibration() {
        if (sampleUri == null) {
            Toast.makeText(this, "ابتدا عکس پاسخنامهٔ خالی را انتخاب کنید", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Bitmap bitmap = decodeOrientedBitmap(sampleUri);
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            TableCalibrationView calibrationView = new TableCalibrationView(bitmap);

            // Put the controls inside the custom dialog content. Android can hide standard
            // AlertDialog action buttons when the image/content is tall on some screen sizes.
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(12), dp(4), dp(12), dp(8));

            TextView instruction = new TextView(this);
            instruction.setText("از گوشهٔ بالا-چپ جدول تا پایین-راست بکشید؛ سپس ذخیره را بزنید.");
            instruction.setTextSize(14);
            instruction.setPadding(dp(4), dp(4), dp(4), dp(8));
            panel.addView(instruction, new LinearLayout.LayoutParams(-1, -2));

            panel.addView(calibrationView, new LinearLayout.LayoutParams(-1, dp(270)));

            LinearLayout zoomControls = new LinearLayout(this);
            zoomControls.setOrientation(LinearLayout.HORIZONTAL);
            zoomControls.setGravity(Gravity.CENTER);
            zoomControls.setPadding(0, dp(4), 0, dp(2));
            Button zoomOutButton = new Button(this);
            zoomOutButton.setText("− کوچک‌نمایی");
            Button zoomInButton = new Button(this);
            zoomInButton.setText("+ بزرگ‌نمایی");
            for (Button b : new Button[]{zoomOutButton, zoomInButton}) {
                b.setAllCaps(false);
                b.setTextSize(13);
                b.setMinHeight(dp(42));
                zoomControls.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
            }
            panel.addView(zoomControls, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER);
            controls.setPadding(0, dp(6), 0, dp(2));

            Button cancelButton = new Button(this);
            cancelButton.setText("انصراف");
            Button resetButton = new Button(this);
            resetButton.setText("پاک‌کردن کادر");
            Button saveButton = new Button(this);
            saveButton.setText("ذخیرهٔ محدوده");
            for (Button b : new Button[]{cancelButton, resetButton, saveButton}) {
                b.setAllCaps(false);
                b.setTextSize(12);
                b.setMinHeight(dp(44));
                b.setPadding(dp(6), dp(4), dp(6), dp(4));
                controls.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1f));
            }
            saveButton.setTextColor(Color.WHITE);
            android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(255, 90, 185), Color.rgb(220, 0, 110)});
            saveBg.setCornerRadius(dp(28));
            saveBg.setStroke(dp(2), Color.rgb(255, 140, 210));
            saveButton.setBackground(saveBg);
            saveButton.setElevation(dp(4));

            panel.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            AlertDialog calibrationDialog = new AlertDialog.Builder(this)
                    .setTitle("محدودهٔ جدول پاسخ‌ها")
                    .setView(panel)
                    .create();
            cancelButton.setOnClickListener(v -> calibrationDialog.dismiss());
            zoomInButton.setOnClickListener(v -> calibrationView.zoomBy(1.25f));
            zoomOutButton.setOnClickListener(v -> calibrationView.zoomBy(0.8f));
            resetButton.setOnClickListener(v -> calibrationView.resetSelection());
            saveButton.setOnClickListener(v -> {
                if (!calibrationView.hasRectangle()) {
                    Toast.makeText(this, "ابتدا با کشیدن انگشت محدودهٔ جدول را مشخص کنید", Toast.LENGTH_LONG).show();
                    return;
                }
                RectF rect = calibrationView.normalizedRect();
                prefs.edit().putFloat("table_left", rect.left).putFloat("table_top", rect.top)
                        .putFloat("table_right", rect.right).putFloat("table_bottom", rect.bottom)
                        .putBoolean("table_bounds_saved", true).apply();
                savedStatus.setText(String.format(java.util.Locale.US,
                        "محدودهٔ جدول ذخیره شد: چپ %.2f، بالا %.2f، راست %.2f، پایین %.2f. مرحلهٔ بعد تعیین مراکز حباب‌هاست.",
                        rect.left, rect.top, rect.right, rect.bottom));
                Toast.makeText(this, "محدودهٔ جدول ذخیره شد؛ شبکهٔ حباب‌ها را یک‌بار بررسی کنید", Toast.LENGTH_SHORT).show();
                calibrationDialog.dismiss();
                openGridCalibration();
            });
            calibrationDialog.show();
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
            Bitmap bitmap = decodeOrientedBitmap(sampleUri);
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            GridCalibrationView grid = new GridCalibrationView(bitmap, questions, options,
                    prefs.getFloat("table_left", 0), prefs.getFloat("table_top", 0),
                    prefs.getFloat("table_right", 1), prefs.getFloat("table_bottom", 1));
            new AlertDialog.Builder(this).setTitle("پیش‌نمایش شبکهٔ حباب‌ها")
                    .setMessage("شبکهٔ پیشنهادی روی محدودهٔ ذخیره‌شده نمایش داده می‌شود. اگر ردیف‌ها و ستون‌ها با حباب‌ها منطبق نیستند، محدودهٔ جدول را دوباره تنظیم کنید.")
                    .setView(grid).setNegativeButton("بستن", null)
                    .setPositiveButton("تأیید و ذخیرهٔ شبکه", (dialog, which) -> {
                        try {
                            Bitmap reference = decodeOrientedBitmap(sampleUri);
                            org.json.JSONArray centers = detectAndStoreBubbleCenters(reference, questions, options);
                            if (reference != null) reference.recycle();
                            if (centers == null || centers.length() != questions * options) {
                                Toast.makeText(this, "مراکز حباب‌ها با اطمینان کافی پیدا نشد؛ شبکه ذخیره نشد.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            prefs.edit().putInt("grid_questions", questions).putInt("grid_options", options)
                                    .putString("grid_option_order", options == 4 ? "D,C,B,A" : "custom")
                                    .putString("bubble_centers", centers.toString())
                                    .putBoolean("grid_saved", true).apply();
                            savedStatus.setText("مراکز " + centers.length() + " حباب از روی عکس مرجع تخمین و ذخیره شدند. پیش از استفادهٔ رسمی با برگه‌های پرشده بررسی کنید؛ اصلاح پرسپکتیو هنوز فعال نیست.");
                        } catch (Exception ex) {
                            Toast.makeText(this, "ذخیرهٔ مراکز حباب‌ها ناموفق بود: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }).show();
        } catch (Exception e) {
            Toast.makeText(this, "باز کردن عکس ناموفق بود: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Refines each expected cell centre against the printed circular ring on the blank reference.
     * Returns null unless every bubble has a clear local ring candidate.
     */
    private org.json.JSONArray detectAndStoreBubbleCenters(Bitmap bitmap, int questions, int options) throws Exception {
        if (bitmap == null) return null;
        float left=prefs.getFloat("table_left",0), top=prefs.getFloat("table_top",0);
        float right=prefs.getFloat("table_right",1), bottom=prefs.getFloat("table_bottom",1);
        double cellW=(right-left)/(options+1.0), cellH=(bottom-top)/(double)questions;
        org.json.JSONArray all = new org.json.JSONArray();
        for (int q=0; q<questions; q++) {
            for (int o=0; o<options; o++) {
                double[] nominal=ExternalSheetLayout.bubbleCenter(q,o,questions,options,
                        prefs.getString("table_mode","single"),left,top,right,bottom);
                int nx=(int)Math.round(nominal[0]*bitmap.getWidth());
                int ny=(int)Math.round(nominal[1]*bitmap.getHeight());
                int searchX=Math.max(2,(int)(cellW*bitmap.getWidth()*0.28));
                int searchY=Math.max(2,(int)(cellH*bitmap.getHeight()*0.25));
                int radius=Math.max(3,(int)(Math.min(cellW*bitmap.getWidth(),cellH*bitmap.getHeight())*0.10));
                double best=-1; int bestX=nx,bestY=ny;
                for(int cy=Math.max(radius,ny-searchY);cy<Math.min(bitmap.getHeight()-radius,ny+searchY);cy+=2) {
                    for(int cx=Math.max(radius,nx-searchX);cx<Math.min(bitmap.getWidth()-radius,nx+searchX);cx+=2) {
                        double ring=0, inner=0; int rc=0,ic=0;
                        for(int yy=-radius;yy<=radius;yy+=2) for(int xx=-radius;xx<=radius;xx+=2) {
                            double d=Math.sqrt(xx*xx+yy*yy)/(double)radius;
                            int px=bitmap.getPixel(cx+xx,cy+yy);
                            double lum=0.299*Color.red(px)+0.587*Color.green(px)+0.114*Color.blue(px);
                            if(d>=0.72 && d<=1.08) {ring+=255-lum;rc++;}
                            else if(d<=0.42) {inner+=lum;ic++;}
                        }
                        if(rc==0||ic==0) continue;
                        double score=ring/rc - (255-inner/ic)*0.25;
                        if(score>best){best=score;bestX=cx;bestY=cy;}
                    }
                }
                if(best<20) return null;
                org.json.JSONObject point=new org.json.JSONObject();
                point.put("q",q); point.put("o",o);
                point.put("x",bestX/(double)bitmap.getWidth());
                point.put("y",bestY/(double)bitmap.getHeight());
                all.put(point);
            }
        }
        return all;
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
            bitmap = decodeOrientedBitmap(uri);
            if (bitmap == null) throw new IllegalStateException("تصویر قابل خواندن نیست");
            // The stored table geometry is normalized to the full reference-page image.
            // Reject differently cropped/oriented images instead of silently applying the
            // saved grid to unrelated pixel locations and producing misleading scores.
            Bitmap referenceBitmap = null;
            String referenceUriText = prefs.getString("sample_image_uri", "");
            if (!referenceUriText.isEmpty()) {
                referenceBitmap = decodeOrientedBitmap(Uri.parse(referenceUriText));
            }
            if (referenceBitmap == null) throw new IllegalStateException("عکس الگوی کامل A4 پیدا نشد؛ دوباره الگوی خالی را انتخاب و ذخیره کنید");
            double refRatio = referenceBitmap.getWidth() / (double) referenceBitmap.getHeight();
            double filledRatio = bitmap.getWidth() / (double) bitmap.getHeight();
            referenceBitmap.recycle();
            if (Math.abs(filledRatio-refRatio) / refRatio > 0.04) {
                throw new IllegalStateException("کادر عکس با الگوی A4 یکسان نیست. از کل برگه، با همان جهت و بدون بریدن لبه‌ها عکس بگیرید؛ این برگه برای جلوگیری از نمرهٔ اشتباه تصحیح نشد.");
            }
            String participantName = participantNameInput.getText().toString().trim();
            if (participantName.isEmpty()) throw new IllegalStateException("نام شرکت‌کننده را وارد کنید");
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
                    double[] center = ExternalSheetLayout.bubbleCenter(
                            q, o, questions, options, prefs.getString("table_mode", "single"),
                            left, top, right, bottom);
                    double nx = center[0];
                    double ny = center[1];
                    // The selected table includes the question-number column as well as answer bubbles.
                    // Sample only the inner center of each bubble cell so printed outlines/grid lines
                    // do not make every option appear equally dark.
                    double cellWidth = (right-left) / (options + 1.0);
                    darkness[o]=sampleDarkness(bitmap,nx,ny,
                            Math.max(1.5,cellWidth*bitmap.getWidth()*0.075),
                            Math.max(1.5,(bottom-top)*bitmap.getHeight()/questions*0.11));
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
            double percentage = questions == 0 ? 0.0 : 100.0 * correctCount / questions;
            boolean finalResultSaved = false;
            if (ambiguous == 0) {
                android.content.SharedPreferences resultPrefs =
                        getSharedPreferences("azmoonyar_local", MODE_PRIVATE);
                org.json.JSONArray saved;
                try { saved = new org.json.JSONArray(resultPrefs.getString("grading_results", "[]")); }
                catch (Exception ignored) { saved = new org.json.JSONArray(); }
                org.json.JSONObject record = new org.json.JSONObject();
                record.put("name", participantName);
                record.put("examName", templateName.getText().toString().trim());
                record.put("score", percentage);
                record.put("correct", correctCount);
                record.put("wrong", wrongCount);
                record.put("blank", blank);
                record.put("multipleMarks", 0);
                record.put("totalQuestions", questions);
                record.put("createdAt", System.currentTimeMillis());
                record.put("source", "external_template_photo");
                saved.put(record);
                resultPrefs.edit().putString("grading_results", saved.toString()).apply();
                finalResultSaved = true;
            }
            String scoreSummary = "صحیح: "+correctCount+" | غلط: "+wrongCount+" | خالی: "+blank
                    +" | نیازمند بازبینی: "+ambiguous+" | درصد از کل سؤال‌ها: "+Math.round(percentage)+"٪";
            if (finalResultSaved) scoreSummary += "\nنتیجهٔ قطعی در بخش نتایج ذخیره شد.";
            else scoreSummary += "\nبه‌دلیل پاسخ مبهم، نمرهٔ قطعی ذخیره نشد.";
            TextView resultView=text("نتیجهٔ تصحیح پاسخنامهٔ خارجی\n"+scoreSummary+"\n\n"+report,14,false);
            resultView.setTextIsSelectable(true);
            ScrollView resultScroll=new ScrollView(this); resultScroll.addView(resultView);
            new AlertDialog.Builder(this).setTitle("نتیجهٔ تصحیح")
                    .setMessage("شبکهٔ ذخیره‌شده یک‌بار از روی الگوی کامل A4 استفاده شده است. این نسخه عکس‌ها را با الگو از نظر نسبت ابعاد بررسی می‌کند، اما هنوز اصلاح پرسپکتیو خودکار ندارد؛ برگه را کاملاً در کادر، با جهت مشابه و بدون برش بگیرید و نتایج را پیش از استفادهٔ رسمی بررسی کنید.")
                    .setView(resultScroll).setPositiveButton("باشه",null).show();
        } catch(Exception e) {
            Toast.makeText(this,"خواندن عکس ناموفق بود: "+e.getMessage(),Toast.LENGTH_LONG).show();
        } finally { if(bitmap!=null) bitmap.recycle(); }
    }

    // Honor the camera/gallery EXIF orientation before comparing A4 dimensions or sampling bubbles.
    // BitmapFactory alone ignores EXIF rotation on many Android versions, causing false A4 mismatch errors.
    private Bitmap decodeOrientedBitmap(Uri uri) throws java.io.IOException {
        int orientation = androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED;
        try (java.io.InputStream exifStream = getContentResolver().openInputStream(uri)) {
            if (exifStream != null) {
                androidx.exifinterface.media.ExifInterface exif =
                        new androidx.exifinterface.media.ExifInterface(exifStream);
                orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED);
            }
        }
        Bitmap source;
        try (java.io.InputStream imageStream = getContentResolver().openInputStream(uri)) {
            if (imageStream == null) throw new java.io.IOException("فایل عکس باز نشد");
            source = BitmapFactory.decodeStream(imageStream);
        }
        if (source == null) throw new java.io.IOException("تصویر قابل خواندن نیست");
        Matrix matrix = new Matrix();
        switch (orientation) {
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
            default: return source;
        }
        Bitmap oriented = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (oriented != source) source.recycle();
        return oriented;
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
        cleaned = cleaned.replaceAll("\\s+", "");
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
                for(int o=0;o<options;o++) {
                    double[] center = ExternalSheetLayout.bubbleCenter(q, o, questions, options,
                            prefs.getString("table_mode", "single"), left, top, right, bottom);
                    float x = imageRect.left + (float) center[0] * imageRect.width();
                    float y = imageRect.top + (float) center[1] * imageRect.height();
                    canvas.drawCircle(x,y,Math.max(1.5f,Math.min(4.0f,(r-l)/(options+1.0f)*0.12f)),gridPaint);
                }
            }
            canvas.drawText("ردیف‌ها: "+questions+" | ستون‌ها: "+options,(l+r)/2f,Math.max(dp(14),t-dp(5)),labelPaint);
        }
    }

    private final class TableCalibrationView extends View {
        private final Bitmap bitmap;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        // TL, TR, BL, BR handles, each as x/y.
        private final float[] corners = new float[8];
        private int activeCorner = -1;
        private boolean hasRect = false;
        private boolean creatingRect = false;
        private final RectF imageRect = new RectF();
        private float zoom = 1f;

        TableCalibrationView(Bitmap bitmap) {
            super(ExternalTemplateActivity.this);
            this.bitmap = bitmap;
            linePaint.setColor(Color.RED);
            linePaint.setStrokeWidth(dp(3));
            linePaint.setStyle(Paint.Style.STROKE);
            handleFill.setColor(Color.WHITE);
            handleFill.setStyle(Paint.Style.FILL);
            setLayoutParams(new LinearLayout.LayoutParams(-1, dp(270)));
            setContentDescription("محدودهٔ جدول: برای اصلاح، یکی از چهار دستگیرهٔ قرمز را بکشید");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight()) * zoom;
            float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
            imageRect.set((getWidth()-w)/2f, (getHeight()-h)/2f, (getWidth()+w)/2f, (getHeight()+h)/2f);
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
            canvas.restore();
            if (hasRect || creatingRect) {
                float left = Math.min(corners[0], corners[4]);
                float right = Math.max(corners[0], corners[4]);
                float top = Math.min(corners[1], corners[5]);
                float bottom = Math.max(corners[1], corners[5]);
                canvas.drawRect(left, top, right, bottom, linePaint);
                for (int i=0; i<4; i++) {
                    float x=corners[i*2], y=corners[i*2+1], radius=dp(10);
                    canvas.drawCircle(x,y,radius,handleFill);
                    canvas.drawCircle(x,y,radius,linePaint);
                    canvas.drawCircle(x,y,dp(3),linePaint);
                }
            }
            if (!hasRect && !creatingRect) {
                Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
                hint.setColor(Color.WHITE);
                hint.setTextSize(dp(13));
                hint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
                canvas.drawText("برای شروع، از گوشهٔ بالا-چپ تا پایین-راست بکشید", dp(8), imageRect.bottom-dp(10), hint);
            }
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            float x = Math.max(imageRect.left, Math.min(imageRect.right, event.getX()));
            float y = Math.max(imageRect.top, Math.min(imageRect.bottom, event.getY()));
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (!imageRect.contains(event.getX(), event.getY())) return false;
                    activeCorner = hasRect ? nearestCorner(event.getX(), event.getY()) : -1;
                    if (activeCorner >= 0) {
                        creatingRect = false;
                    } else {
                        corners[0]=x; corners[1]=y;
                        corners[2]=x; corners[3]=y;
                        corners[4]=x; corners[5]=y;
                        corners[6]=x; corners[7]=y;
                        hasRect=false;
                        creatingRect=true;
                    }
                    invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (activeCorner >= 0) {
                        moveCorner(activeCorner, x, y);
                    } else if (creatingRect) {
                        setRectangle(corners[0], corners[1], x, y);
                    } else {
                        return false;
                    }
                    invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (activeCorner >= 0 || creatingRect) {
                        if (creatingRect) setRectangle(corners[0], corners[1], x, y);
                        hasRect = Math.abs(corners[6]-corners[0]) > dp(8) && Math.abs(corners[7]-corners[1]) > dp(8);
                        creatingRect=false;
                        activeCorner=-1;
                        invalidate();
                        performClick();
                        return true;
                    }
                    return false;
                case android.view.MotionEvent.ACTION_CANCEL:
                    creatingRect=false;
                    activeCorner=-1;
                    return true;
                default:
                    return true;
            }
        }

        private int nearestCorner(float x, float y) {
            int best=-1;
            float bestDistance=dp(28);
            for(int i=0;i<4;i++) {
                float dx=corners[i*2]-x, dy=corners[i*2+1]-y;
                float d=(float)Math.sqrt(dx*dx+dy*dy);
                if(d<bestDistance) { bestDistance=d; best=i; }
            }
            return best;
        }

        private void setRectangle(float x1, float y1, float x2, float y2) {
            float l=Math.min(x1,x2), r=Math.max(x1,x2), t=Math.min(y1,y2), b=Math.max(y1,y2);
            corners[0]=l; corners[1]=t;
            corners[2]=r; corners[3]=t;
            corners[4]=l; corners[5]=b;
            corners[6]=r; corners[7]=b;
        }

        private void moveCorner(int index, float x, float y) {
            float l=Math.min(corners[0], corners[4]), r=Math.max(corners[2], corners[6]);
            float t=Math.min(corners[1], corners[3]), b=Math.max(corners[5], corners[7]);
            if(index==0 || index==2) l=x; else r=x;
            if(index==0 || index==1) t=y; else b=y;
            l=Math.max(imageRect.left,Math.min(l,imageRect.right));
            r=Math.max(imageRect.left,Math.min(r,imageRect.right));
            t=Math.max(imageRect.top,Math.min(t,imageRect.bottom));
            b=Math.max(imageRect.top,Math.min(b,imageRect.bottom));
            setRectangle(l,t,r,b);
            hasRect=Math.abs(r-l)>dp(8)&&Math.abs(b-t)>dp(8);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
        void zoomBy(float factor) {
            float oldScale = zoom;
            zoom = Math.max(1f, Math.min(5f, zoom * factor));
            if (Math.abs(zoom-oldScale) < 0.001f) return;
            // Keep an existing rectangle attached to the same image locations while zoom changes.
            float oldFit = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight()) * oldScale;
            float newFit = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight()) * zoom;
            float oldW=bitmap.getWidth()*oldFit, oldH=bitmap.getHeight()*oldFit;
            float newW=bitmap.getWidth()*newFit, newH=bitmap.getHeight()*newFit;
            RectF oldRect = new RectF((getWidth()-oldW)/2f,(getHeight()-oldH)/2f,(getWidth()+oldW)/2f,(getHeight()+oldH)/2f);
            RectF newRect = new RectF((getWidth()-newW)/2f,(getHeight()-newH)/2f,(getWidth()+newW)/2f,(getHeight()+newH)/2f);
            for(int i=0;i<4;i++) {
                corners[i*2] = newRect.left + (corners[i*2]-oldRect.left)/oldRect.width()*newRect.width();
                corners[i*2+1] = newRect.top + (corners[i*2+1]-oldRect.top)/oldRect.height()*newRect.height();
            }
            invalidate();
        }
        void resetSelection() { hasRect=false; creatingRect=false; activeCorner=-1; zoom=1f; invalidate(); }
        boolean hasRectangle() { return hasRect && Math.abs(corners[6]-corners[0])>dp(8) && Math.abs(corners[7]-corners[1])>dp(8); }
        RectF normalizedRect() {
            float l=Math.min(corners[0],corners[4]), r=Math.max(corners[2],corners[6]);
            float t=Math.min(corners[1],corners[3]), b=Math.max(corners[5],corners[7]);
            return new RectF((l-imageRect.left)/imageRect.width(),(t-imageRect.top)/imageRect.height(),
                    (r-imageRect.left)/imageRect.width(),(b-imageRect.top)/imageRect.height());
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
