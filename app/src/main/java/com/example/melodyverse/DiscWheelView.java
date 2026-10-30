package com.example.melodyverse;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Vòng xoay đĩa than ở trang Khám phá.
 * Các đĩa xếp theo một vòng cung: đĩa nằm giữa to, rõ và hiện nút Phát; càng xa tâm càng nhỏ, mờ và lệch sang phải.
 * Lướt đến đâu đĩa xoay theo đến đó, thả tay thì đĩa gần nhất tự hút vào tâm (kèm rung nhẹ).
 */
public class DiscWheelView extends RecyclerView {

    public interface Listener {
        /** Bấm nút Phát/Dừng của đĩa đang ở giữa. */
        void onPlayClick(PostModel post);
        /** Bấm vào thẻ của đĩa đang ở giữa. */
        void onOpenPost(PostModel post);
    }

    private static final int LOOP_ROUNDS = 2000;

    private final List<PostModel> posts = new ArrayList<>();
    private final WheelAdapter adapter = new WheelAdapter();
    private final LinearLayoutManager layoutManager;
    private Listener listener;

    private int centerPosition = NO_POSITION;
    private String playingPostId;
    private float scrollRotation = 0f;
    /** 0 = các đĩa còn nằm ngoài mép phải, 1 = đã bung thành vòng cung. */
    private float introProgress = 1f;
    private boolean introPlayed = false;
    /** Bài cần đưa về giữa ở lần nạp danh sách kế tiếp (khôi phục sau khi xoay màn hình). */
    private String pendingCenterPostId;

    public DiscWheelView(@NonNull Context context) {
        this(context, null);
    }

    public DiscWheelView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        layoutManager = new LinearLayoutManager(context);
        setLayoutManager(layoutManager);
        setAdapter(adapter);
        setClipToPadding(false);
        setClipChildren(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setItemAnimator(null);
        new LinearSnapHelper().attachToRecyclerView(this);

        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                scrollRotation += dy * 0.6f;
                applyTransforms();
            }
        });
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyTransforms());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPosts(List<PostModel> newPosts) {
        boolean sameList = newPosts.size() == posts.size();
        for (int i = 0; sameList && i < posts.size(); i++) {
            String a = posts.get(i).getId(), b = newPosts.get(i).getId();
            sameList = a == null ? b == null : a.equals(b);
        }
        posts.clear();
        posts.addAll(newPosts);
        adapter.notifyDataSetChanged();
        if (!sameList && !posts.isEmpty()) {
            // Vòng lặp: bắt đầu ở giữa dải vị trí ảo để lướt lên hay xuống đều gặp đĩa
            int start = isLooping() ? (LOOP_ROUNDS / 2) * posts.size() : Math.min(2, posts.size() - 1);
            int pendingIndex = indexOfPost(pendingCenterPostId);
            if (pendingIndex >= 0) {
                start = isLooping() ? (LOOP_ROUNDS / 2) * posts.size() + pendingIndex : pendingIndex;
            }
            pendingCenterPostId = null;
            int target = start;
            centerPosition = NO_POSITION;
            post(() -> layoutManager.scrollToPositionWithOffset(target, 0));
        }
    }

    private int indexOfPost(@Nullable String postId) {
        if (postId == null) return -1;
        for (int i = 0; i < posts.size(); i++) {
            if (postId.equals(posts.get(i).getId())) return i;
        }
        return -1;
    }

    /** Id bài của đĩa đang nằm giữa (lưu lại khi xoay màn hình), null nếu chưa có. */
    @Nullable
    public String getCenterPostId() {
        if (centerPosition == NO_POSITION || posts.isEmpty()) return null;
        return postAt(centerPosition).getId();
    }

    /** Đĩa sẽ được đưa về giữa ở lần {@link #setPosts(List)} kế tiếp (dùng khi Activity được tạo lại). */
    public void setPendingCenterPostId(@Nullable String postId) {
        pendingCenterPostId = postId;
    }

    /** Từ 3 đĩa trở lên thì vòng xoay lặp vô tận (hết đĩa cuối quay lại đĩa đầu). */
    private boolean isLooping() {
        return posts.size() >= 3;
    }

    private PostModel postAt(int adapterPosition) {
        return posts.get(adapterPosition % posts.size());
    }

    public int getPostCount() {
        return posts.size();
    }

    /** Cập nhật đĩa nào đang phát (đĩa đó tự quay và nút đổi thành Dừng). */
    public void setPlayingPostId(@Nullable String postId) {
        if (postId == null ? playingPostId == null : postId.equals(playingPostId)) return;
        playingPostId = postId;
        adapter.notifyDataSetChanged();
    }

    /** Hiệu ứng bung vòng cung khi người dùng lướt tới (chỉ chạy một lần). */
    public void playIntro() {
        if (introPlayed) return;
        introPlayed = true;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            introProgress = (float) a.getAnimatedValue();
            applyTransforms();
        });
        introProgress = 0f;
        applyTransforms();
        anim.start();
    }

    public void hideForIntro() {
        if (introPlayed) return;
        introProgress = 0f;
        applyTransforms();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Chừa khoảng trống trên/dưới để đĩa đầu và cuối danh sách cũng lăn được vào giữa
        int itemHeight = dp(78);
        int pad = Math.max(0, h / 2 - itemHeight / 2);
        setPadding(0, pad, 0, pad);
    }

    @Override
    public void smoothScrollToPosition(int position) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return 60f / displayMetrics.densityDpi;
            }
        };
        scroller.setTargetPosition(position);
        layoutManager.startSmoothScroll(scroller);
    }

    /** Tính lại vị trí cong, độ lớn, độ mờ của từng đĩa theo khoảng cách tới tâm. */
    private void applyTransforms() {
        int height = getHeight();
        int width = getWidth();
        if (height == 0 || width == 0) return;
        float centerY = height / 2f;
        float halfSpan = Math.max(1f, height / 2f);

        int newCenter = NO_POSITION;
        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            WheelHolder holder = (WheelHolder) getChildViewHolder(child);
            float childCenter = (child.getTop() + child.getBottom()) / 2f;
            float d = (childCenter - centerY) / halfSpan;          // -1 (trên) .. 0 (giữa) .. 1 (dưới)
            float ad = Math.abs(d);

            if (ad < bestDistance) {
                bestDistance = ad;
                newCenter = holder.getAdapterPosition();
            }

            // Đĩa vào sau thì bung muộn hơn một chút
            float p = clamp(introProgress * 1.6f - ad * 0.5f, 0f, 1f);

            float arcX = width * 0.30f * d * d;                    // vòng cung lệch sang phải
            float scale = 1f - 0.30f * Math.min(ad, 1.2f);
            float alpha = clamp(1f - 0.85f * (float) Math.pow(ad, 1.3), 0.08f, 1f);
            float focus = clamp(1f - ad * 3.2f, 0f, 1f);          // 1 khi đĩa nằm đúng tâm

            View row = holder.row;
            row.setPivotX(dp(32));
            row.setPivotY(row.getHeight() / 2f);
            row.setTranslationX(arcX * p + (1f - p) * width * 0.7f);
            row.setScaleX(scale);
            row.setScaleY(scale);
            row.setAlpha(alpha * p);

            Drawable bg = row.getBackground();
            if (bg != null) bg.mutate().setAlpha((int) (255 * focus));
            holder.btnPlay.setAlpha(focus);
            holder.btnPlay.setClickable(focus > 0.5f);
            holder.btnPlay.setVisibility(focus > 0.02f ? VISIBLE : INVISIBLE);

            holder.disc.setRotation(scrollRotation + (holder.getAdapterPosition() % 8) * 47f);
        }

        if (newCenter != NO_POSITION && newCenter != centerPosition) {
            boolean wasSet = centerPosition != NO_POSITION;
            centerPosition = newCenter;
            if (wasSet && introPlayed) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------------------------------

    private class WheelAdapter extends Adapter<WheelHolder> {
        @NonNull
        @Override
        public WheelHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disc_wheel, parent, false);
            return new WheelHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull WheelHolder holder, int position) {
            PostModel post = postAt(position);
            holder.tvTitle.setText(post.getSongTitle());
            holder.tvArtist.setText(post.getArtistName());

            holder.tvInitial.setText(DiscPalette.initialOf(post.getSongTitle()));

            int[] c = DiscPalette.colorsFor(post.getId() != null ? post.getId() : post.getSongTitle());
            GradientDrawable label = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
            label.setShape(GradientDrawable.OVAL);
            holder.label.setBackground(label);

            boolean playing = MusicService.isPlaying && post.getId() != null && post.getId().equals(playingPostId);
            holder.btnPlay.setText(playing ? "Dừng" : "Phát");
            holder.btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    playing ? R.drawable.ic_pause : R.drawable.ic_play, 0, 0, 0);
            holder.setSpinning(playing);

            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == NO_POSITION) return;
                if (pos != centerPosition) {
                    smoothScrollToPosition(pos);
                } else if (listener != null) {
                    listener.onOpenPost(postAt(pos));
                }
            });
            holder.btnPlay.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != NO_POSITION && listener != null) listener.onPlayClick(postAt(pos));
            });
        }

        @Override
        public void onViewRecycled(@NonNull WheelHolder holder) {
            holder.setSpinning(false);
        }

        @Override
        public int getItemCount() {
            return isLooping() ? posts.size() * LOOP_ROUNDS : posts.size();
        }
    }

    private static class WheelHolder extends ViewHolder {
        final View row, disc, spin, label;
        final TextView tvTitle, tvArtist, tvInitial, btnPlay;
        ObjectAnimator spinAnimator;

        WheelHolder(@NonNull View v) {
            super(v);
            row = v.findViewById(R.id.layout_wheel_row);
            disc = v.findViewById(R.id.frame_wheel_disc);
            spin = v.findViewById(R.id.frame_wheel_disc_spin);
            label = v.findViewById(R.id.view_wheel_disc_label);
            tvInitial = v.findViewById(R.id.tv_wheel_disc_initial);
            tvTitle = v.findViewById(R.id.tv_wheel_title);
            tvArtist = v.findViewById(R.id.tv_wheel_artist);
            btnPlay = v.findViewById(R.id.btn_wheel_play);
        }

        void setSpinning(boolean spinning) {
            if (spinning) {
                if (spinAnimator == null) {
                    spinAnimator = ObjectAnimator.ofFloat(spin, View.ROTATION, 0f, 360f);
                    spinAnimator.setDuration(3200);
                    spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
                    spinAnimator.setInterpolator(new LinearInterpolator());
                }
                if (!spinAnimator.isStarted()) spinAnimator.start();
            } else if (spinAnimator != null) {
                spinAnimator.cancel();
                spin.setRotation(0f);
            }
        }
    }
}
