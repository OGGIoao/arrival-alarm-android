package com.example.arrivalalarm;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppPrefs.ReminderTask task = new AppPrefs(this).getTask();
        if (task == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setBackgroundColor(Color.rgb(242, 242, 247));

        TextView icon = text("到", 38, Typeface.BOLD, Color.rgb(0, 122, 255));
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(Color.rgb(232, 242, 255), 28));
        root.addView(icon, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView eyebrow = text("目的地已接近", 13, Typeface.BOLD, Color.rgb(0, 122, 255));
        LinearLayout.LayoutParams eyebrowParams = wrap();
        eyebrowParams.topMargin = dp(28);
        root.addView(eyebrow, eyebrowParams);

        TextView title = text(task.destination.name + " 快到了", 32, Typeface.BOLD, Color.rgb(28, 28, 30));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(12);
        root.addView(title, titleParams);

        TextView detail = text("请确认随身物品，准备下车。", 16, Typeface.NORMAL, Color.rgb(110, 110, 115));
        LinearLayout.LayoutParams detailParams = wrap();
        detailParams.topMargin = dp(12);
        detailParams.bottomMargin = dp(30);
        root.addView(detail, detailParams);

        Button arrived = new Button(this);
        arrived.setText("我已到达");
        arrived.setTextColor(Color.WHITE);
        arrived.setTextSize(18);
        arrived.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrived.setStateListAnimator(null);
        arrived.setBackground(roundRect(Color.rgb(0, 122, 255), 16));
        arrived.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                ReminderRuntimeCoordinator.stopReminder(AlarmActivity.this);
                finish();
            }
        });
        arrived.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
        root.addView(arrived, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private TextView text(String value, int sp, int style, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private GradientDrawable roundRect(int color, int radiusDp) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp)); return drawable; }
}
