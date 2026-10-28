package com.example.melodyverse;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Màn mở app: 7 vạch sóng âm nhảy theo nhạc, xếp lại thành chữ M (giống nhãn đĩa trong logo),
 * rồi thu vào logo đĩa than và hiện tên app. Chạm màn hình để bỏ qua.
 */
public class SplashActivity extends AppCompatActivity {

    /** Chiều cao các vạch tạo chữ M, cùng tỷ lệ với logo. */
    private static final float[] M_SHAPE = {14f, 19f, 11f, 7f, 11f, 19f, 14f};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> bars = new ArrayList<>();
    private final List<ObjectAnimator> dancing = new ArrayList<>();
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Áp dụng Dark Mode đã lưu ngay từ màn đầu tiên (không bị đổi màu giữa chừng khi sang Đăng nhập)
        boolean darkMode = getSharedPreferences("melodyverse_settings", MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(darkMode
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        LinearLayout barRow = findViewById(R.id.layout_splash_bars);
        float density = getResources().getDisplayMetrics().density;
        int barColor = 0xFFFFFFFF;
        for (int i = 0; i < M_SHAPE.length; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (7 * density), (int) (72 * density));
            lp.leftMargin = lp.rightMargin = (int) (3.5f * density);
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(R.drawable.bg_splash_bar);
            bar.setBackgroundTintList(ColorStateList.valueOf(barColor));
            bar.setScaleY(0.1f);
            barRow.addView(bar);
            bars.add(bar);
        }

        findViewById(R.id.layout_splash_root).setOnClickListener(v -> goNext());

        startDancing();
        handler.postDelayed(this::formLetterM, 1150);
        handler.postDelayed(this::revealLogo, 1650);
        handler.postDelayed(this::revealName, 2050);
        handler.postDelayed(this::goNext, 2900);
    }

    /** Bước 1: các vạch nhảy lên xuống như equalizer. */
    private void startDancing() {
        Random random = new Random();
        for (View bar : bars) {
            float[] frames = new float[7];
            frames[0] = 0.1f;
            for (int k = 1; k < frames.length; k++) frames[k] = 0.2f + random.nextFloat() * 0.8f;
            ObjectAnimator anim = ObjectAnimator.ofFloat(bar, View.SCALE_Y, frames);
            anim.setDuration(900 + random.nextInt(400));
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.start();
            dancing.add(anim);
        }
    }

    /** Bước 2: các vạch dừng lại thành chữ M và chuyển sang màu tím → hồng. */
    private void formLetterM() {
        for (ObjectAnimator a : dancing) a.cancel();
        int from = 0xFFFFFFFF;
        int violet = 0xFF7C3AED, pink = 0xFFEC4899;
        for (int i = 0; i < bars.size(); i++) {
            View bar = bars.get(i);
            bar.animate().scaleY(M_SHAPE[i] / 19f).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();

            int to = (int) new ArgbEvaluator().evaluate(M_SHAPE[i] / 19f, violet, pink);
            ValueAnimator color = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
            color.setDuration(380);
            color.addUpdateListener(a -> bar.setBackgroundTintList(
                    ColorStateList.valueOf((int) a.getAnimatedValue())));
            color.start();
        }
    }

    /** Bước 3: chữ M thu nhỏ vào nhãn đĩa, logo đĩa than bung ra. */
    private void revealLogo() {
        View row = findViewById(R.id.layout_splash_bars);
        row.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(260)
                .setInterpolator(new AccelerateInterpolator()).start();

        View logo = findViewById(R.id.iv_splash_logo);
        logo.setScaleX(0.45f);
        logo.setScaleY(0.45f);
        logo.setRotation(-90f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(620)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    /** Bước 4: hiện tên app và khẩu hiệu. */
    private void revealName() {
        float shift = 14 * getResources().getDisplayMetrics().density;
        View name = findViewById(R.id.tv_splash_name);
        View tagline = findViewById(R.id.tv_splash_tagline);
        name.setTranslationY(shift);
        tagline.setTranslationY(shift);
        name.animate().alpha(1f).translationY(0f).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Sang màn Đăng nhập (màn này tự chuyển vào Bảng tin nếu đã đăng nhập). */
    private void goNext() {
        if (navigated) return;
        navigated = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (ObjectAnimator a : dancing) a.cancel();
        super.onDestroy();
    }
}
