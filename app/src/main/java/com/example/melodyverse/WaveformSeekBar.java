package com.example.melodyverse;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;

import java.util.Random;

/**
 * Thanh tua dạng sóng âm cho màn Đang phát. Vẫn là SeekBar (kéo để tua, setProgress như cũ),
 * chỉ thay phần vẽ: mỗi bài có một hình sóng riêng (sinh từ id bài), phần đã nghe tô màu tím,
 * và các vạch nhấp nhô nhẹ khi nhạc đang phát.
 */
public class WaveformSeekBar extends AppCompatSeekBar {

    private final Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float barWidth, barGap, density;

    private String seed = "";
    private float[] amplitudes;
    private float phase = 0f;
    private ValueAnimator pulse;

    public WaveformSeekBar(Context context) {
        this(context, null);
    }

    public WaveformSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.seekBarStyle);
    }

    public WaveformSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        barWidth = 3f * density;
        barGap = 2.5f * density;

        playedPaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        restPaint.setColor(ContextCompat.getColor(context, R.color.colorTextMuted));
        restPaint.setAlpha(90);
        headPaint.setColor(ContextCompat.getColor(context, R.color.colorTextPrimary));

        // Ẩn nút kéo và thanh mặc định, chỉ giữ phần xử lý chạm của SeekBar
        setThumb(new ColorDrawable(0));
        setProgressDrawable(new ColorDrawable(0));
        setBackground(null);
        setSplitTrack(false);
        setThumbOffset(0);
    }

    /** Đổi hình sóng theo bài hát. */
    public void setSeed(String key) {
        String k = key != null ? key : "";
        if (k.equals(seed) && amplitudes != null) return;
        seed = k;
        amplitudes = null;
        invalidate();
    }

    /** Bật/tắt nhấp nhô khi đang phát. */
    public void setPlaying(boolean playing) {
        if (playing) {
            if (pulse == null) {
                pulse = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
                pulse.setDuration(1400);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new LinearInterpolator());
                pulse.addUpdateListener(a -> {
                    phase = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!pulse.isStarted()) pulse.start();
        } else if (pulse != null) {
            pulse.cancel();
            phase = 0f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulse != null) pulse.cancel();
        super.onDetachedFromWindow();
    }

    private void buildAmplitudes(int count) {
        amplitudes = new float[count];
        Random r = new Random(seed.hashCode());
        float p1 = r.nextFloat() * 6f, p2 = r.nextFloat() * 6f, p3 = r.nextFloat() * 6f;
        for (int i = 0; i < count; i++) {
            float x = i / (float) Math.max(1, count - 1);
            // Vài làn sóng chồng lên nhau + chút ngẫu nhiên, hai đầu nhỏ dần như intro/outro
            float v = 0.45f
                    + 0.22f * (float) Math.sin(i * 0.31f + p1)
                    + 0.16f * (float) Math.sin(i * 0.83f + p2)
                    + 0.10f * (float) Math.sin(i * 1.7f + p3)
                    + 0.22f * r.nextFloat();
            float envelope = (float) Math.min(1.0, Math.min(x, 1 - x) * 6 + 0.25);
            amplitudes[i] = Math.max(0.12f, Math.min(1f, v * envelope));
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float width = right - left;
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        float midY = getPaddingTop() + height / 2f;

        int count = (int) ((width + barGap) / (barWidth + barGap));
        if (count <= 0) return;
        if (amplitudes == null || amplitudes.length != count) buildAmplitudes(count);

        float frac = getMax() > 0 ? getProgress() / (float) getMax() : 0f;
        float playedX = left + frac * width;
        boolean pulsing = pulse != null && pulse.isRunning();
        float radius = barWidth / 2f;

        for (int i = 0; i < count; i++) {
            float x = left + i * (barWidth + barGap);
            float amp = amplitudes[i];
            if (pulsing) amp *= 0.86f + 0.14f * (float) Math.sin(phase * 2 + i * 0.55f);
            float h = Math.max(barWidth, amp * height);
            bar.set(x, midY - h / 2f, x + barWidth, midY + h / 2f);

            if (x + barWidth <= playedX) {
                canvas.drawRoundRect(bar, radius, radius, playedPaint);
            } else if (x >= playedX) {
                canvas.drawRoundRect(bar, radius, radius, restPaint);
            } else {
                // Vạch đang nằm ngay đầu phát: nửa tô, nửa chưa
                canvas.drawRoundRect(bar, radius, radius, restPaint);
                canvas.save();
                canvas.clipRect(x, bar.top, playedX, bar.bottom);
                canvas.drawRoundRect(bar, radius, radius, playedPaint);
                canvas.restore();
            }
        }

        // Kim chỉ vị trí đang phát
        float headW = 2f * density;
        bar.set(playedX - headW / 2f, getPaddingTop(), playedX + headW / 2f, getPaddingTop() + height);
        canvas.drawRoundRect(bar, headW, headW, headPaint);
    }
}
