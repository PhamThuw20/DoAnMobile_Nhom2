package com.example.melodyverse;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Danh sách bài hát dạng gọn (Bảng xếp hạng ở Khám phá, Danh sách nhạc đã lưu ở Hồ sơ).
 * Bài đang phát: đĩa than xoay và thanh sóng nhạc chuyển động; tạm dừng thì cả hai cùng dừng.
 */
public class TrackListAdapter extends RecyclerView.Adapter<TrackListAdapter.ViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(PostModel track, int position);
    }

    /** Dòng phụ dưới tên bài (ví dụ "Nghệ sĩ · 1.024 lượt yêu thích"). null = chỉ hiện tên nghệ sĩ. */
    public interface SubtitleProvider {
        String subtitleFor(PostModel track, int position);
    }

    private final Context context;
    private final List<PostModel> trackList;
    private final OnTrackClickListener clickListener;
    private OnTrackClickListener longClickListener;
    private SubtitleProvider subtitleProvider;
    /** true = tô màu huy chương cho Top 1-3 (Bảng xếp hạng). */
    private boolean rankStyle = false;
    /** Hạng của hàng đầu tiên trừ 1 (ví dụ 1 = danh sách bắt đầu từ hạng #2 vì hạng #1 đã ở banner). */
    private int rankOffset = 0;

    public TrackListAdapter(Context context, List<PostModel> trackList, OnTrackClickListener clickListener) {
        this.context = context;
        this.trackList = trackList;
        this.clickListener = clickListener;
    }

    public void setOnTrackLongClickListener(OnTrackClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSubtitleProvider(SubtitleProvider provider) {
        this.subtitleProvider = provider;
    }

    public void setRankOffset(int rankOffset) {
        this.rankOffset = rankOffset;
    }

    public void setRankStyle(boolean rankStyle) {
        this.rankStyle = rankStyle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostModel track = trackList.get(position);

        holder.tvIndex.setText(rankStyle ? String.valueOf(position + 1 + rankOffset) : String.format("%02d", position + 1));
        holder.tvIndex.setTextColor(ContextCompat.getColor(context, rankColor(position + rankOffset)));
        holder.tvTitle.setText(track.getSongTitle());
        String subtitle = subtitleProvider != null ? subtitleProvider.subtitleFor(track, position) : null;
        holder.tvArtist.setText(subtitle != null ? subtitle : track.getArtistName());

        // Nhãn đĩa: cùng cặp màu với bài này ở Bảng tin / Khám phá / màn Đang phát
        if (holder.viewLabel != null) {
            android.graphics.drawable.GradientDrawable label = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, DiscPalette.colorsFor(track.getId()));
            label.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            holder.viewLabel.setBackground(label);
        }

        boolean isThisPlaying = MusicService.isPlaying && track.getId() != null
                && track.getId().equals(MusicService.currentPostId);

        if (isThisPlaying) {
            holder.itemView.setBackgroundResource(R.drawable.bg_music_card_playing);
            holder.tvTitle.setTextColor(android.graphics.Color.WHITE);
            holder.tvArtist.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
            holder.ivPlay.setImageResource(R.drawable.ic_pause);
            holder.ivPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorNeonPink)));
            holder.layoutEq.setVisibility(View.VISIBLE);
            startWaveAnimation(holder.eq1, 320, 0.4f, 1.3f);
            startWaveAnimation(holder.eq2, 260, 0.3f, 1.4f);
            startWaveAnimation(holder.eq3, 380, 0.5f, 1.2f);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_music_card);
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
            holder.tvArtist.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
            holder.ivPlay.setImageResource(R.drawable.ic_play);
            holder.ivPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)));
            holder.layoutEq.setVisibility(View.GONE);
            holder.eq1.clearAnimation();
            holder.eq2.clearAnimation();
            holder.eq3.clearAnimation();
        }
        holder.setVinylSpinning(isThisPlaying);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (clickListener != null && pos != RecyclerView.NO_POSITION) clickListener.onTrackClick(trackList.get(pos), pos);
        });
        holder.ivPlay.setOnClickListener(v -> holder.itemView.performClick());
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (longClickListener == null || pos == RecyclerView.NO_POSITION) return false;
            longClickListener.onTrackClick(trackList.get(pos), pos);
            return true;
        });
    }

    private int rankColor(int position) {
        if (!rankStyle) return R.color.colorTextMuted;
        if (position == 0) return R.color.colorGold;
        if (position == 1) return R.color.colorSilver;
        if (position == 2) return R.color.colorBronze;
        return R.color.colorTextMuted;
    }

    private void startWaveAnimation(View bar, long duration, float fromScale, float toScale) {
        if (bar == null || bar.getAnimation() != null) return;
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1f, fromScale, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 1f
        );
        scale.setDuration(duration);
        scale.setRepeatCount(Animation.INFINITE);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setInterpolator(new AccelerateDecelerateInterpolator());
        bar.startAnimation(scale);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.setVinylSpinning(false);
    }

    @Override
    public int getItemCount() {
        return trackList != null ? trackList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIndex, tvTitle, tvArtist;
        final ImageView ivPlay;
        final View frameVinyl, viewLabel, layoutEq, eq1, eq2, eq3;
        private ObjectAnimator vinylAnimator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tv_track_index);
            tvTitle = itemView.findViewById(R.id.tv_track_title);
            tvArtist = itemView.findViewById(R.id.tv_track_artist);
            ivPlay = itemView.findViewById(R.id.iv_track_play);
            frameVinyl = itemView.findViewById(R.id.frame_track_vinyl);
            viewLabel = itemView.findViewById(R.id.view_track_vinyl_label);
            layoutEq = itemView.findViewById(R.id.layout_track_eq);
            eq1 = itemView.findViewById(R.id.track_eq_1);
            eq2 = itemView.findViewById(R.id.track_eq_2);
            eq3 = itemView.findViewById(R.id.track_eq_3);
        }

        /** Đĩa than xoay quanh chính tâm khi đang phát; tạm dừng thì dừng tại góc hiện tại. */
        void setVinylSpinning(boolean spinning) {
            if (frameVinyl == null) return;
            if (spinning) {
                if (vinylAnimator == null) {
                    float density = frameVinyl.getResources().getDisplayMetrics().density;
                    frameVinyl.setPivotX(24f * density);
                    frameVinyl.setPivotY(24f * density);
                    vinylAnimator = ObjectAnimator.ofFloat(frameVinyl, View.ROTATION,
                            frameVinyl.getRotation(), frameVinyl.getRotation() + 360f);
                    vinylAnimator.setDuration(6000);
                    vinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
                    vinylAnimator.setInterpolator(new LinearInterpolator());
                    vinylAnimator.start();
                } else if (vinylAnimator.isPaused()) {
                    vinylAnimator.resume();
                }
            } else if (vinylAnimator != null && vinylAnimator.isRunning()) {
                vinylAnimator.pause();
            }
        }
    }
}
