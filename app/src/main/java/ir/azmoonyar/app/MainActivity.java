package ir.azmoonyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Initial offline-first UI shell; scanning and grading are not implemented yet. */
public class MainActivity extends Activity {
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 27, 114));
        getWindow().setNavigationBarColor(Color.rgb(7, 27, 114));
        getWindow().getDecorView().setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(245, 248, 255));

        TextView title = new TextView(this);
        title.setText("آزمون‌یار");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(7, 40, 130));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView subtitle = new TextView(this);
        subtitle.setText("اسکن و تصحیح آفلاین پاسخنامه‌های چهارگزینه‌ای");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(65, 80, 115));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = dp(28);
        root.addView(subtitle, subtitleParams);

        addAction(root, "تعریف آزمون", "ساخت آزمون و تعیین کلید پاسخ در مرحله بعد فعال می‌شود.");
        addAction(root, "اسکن پاسخنامه", "اسکن دوربین و تشخیص حباب‌ها هنوز پیاده‌سازی نشده است.");
        addAction(root, "نتایج آزمون", "نتایج پس از پیاده‌سازی ذخیره‌سازی و تصحیح نمایش داده می‌شوند.");

        TextView status = new TextView(this);
        status.setText("نسخه آغازین — بدون اینترنت طراحی شده است\nاین نسخه هنوز پاسخنامه را اسکن یا تصحیح نمی‌کند.");
        status.setTextSize(13);
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(26);
        root.addView(status, statusParams);
        setContentView(root);
    }

    private void addAction(LinearLayout root, String label, String message) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
            .setTitle(label).setMessage(message).setPositiveButton("باشه", null).show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        params.bottomMargin = dp(12);
        root.addView(button, params);
    }
}
