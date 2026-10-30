package com.example.melodyverse;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {

    public interface OnPostMusicClickListener {
        void onMusicPlayClicked(PostModel post);
    }

    public interface OnHashtagClickListener {
        void onHashtagClicked(String hashtag);
    }

    private Context context;
    private List<PostModel> postList;
    private OnPostMusicClickListener musicClickListener;
    private OnHashtagClickListener hashtagClickListener;
    private DatabaseHelper dbHelper;

    public void setOnHashtagClickListener(OnHashtagClickListener listener) {
        this.hashtagClickListener = listener;
    }

    public PostAdapter(Context context, List<PostModel> postList, OnPostMusicClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.musicClickListener = listener;
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostModel post = postList.get(position);

        // Đổ dữ liệu văn bản
        holder.tvAuthor.setText(post.getAuthorName());
        holder.tvUsername.setText(" • @" + post.getAuthorUsername());
        // Bài đăng lúc offline: hiện nhãn chờ cho tới khi đã lên Firebase
        holder.tvTime.setText(post.isPendingSync() ? "Chờ có mạng để đăng" : post.getTimeAgo());
        holder.tvCaption.setText(post.getCaption());
        holder.tvSongTitle.setText(post.getSongTitle());
        holder.tvSongArtist.setText(post.getArtistName());
        holder.tvLikeCount.setText(likeCountText(post));
        holder.tvCommentCount.setText(String.valueOf(post.getCommentCount()));
        holder.tvShareCount.setText(String.valueOf(post.getShareCount()));

        // Chế độ Riêng tư / Công khai
        if (post.isPrivate()) {
            if (holder.ivPrivacyIcon != null) {
                holder.ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
                holder.ivPrivacyIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorGold)));
            }
            if (holder.tvPrivacyLabel != null) {
                holder.tvPrivacyLabel.setText("Riêng tư");
                holder.tvPrivacyLabel.setTextColor(ContextCompat.getColor(context, R.color.colorGold));
            }
        } else {
            if (holder.ivPrivacyIcon != null) {
                holder.ivPrivacyIcon.setImageResource(R.drawable.ic_public);
                holder.ivPrivacyIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorTextMuted)));
            }
            if (holder.tvPrivacyLabel != null) {
                holder.tvPrivacyLabel.setText("Công khai");
                holder.tvPrivacyLabel.setTextColor(ContextCompat.getColor(context, R.color.colorTextMuted));
            }
        }

        if (holder.tvGenreBadge != null) {
            holder.tvGenreBadge.setText(post.getGenre());
        }


        // Nhãn đĩa than: cặp màu cố định theo từng bài (đồng nhất với màn Đang phát / Khám phá)
        if (holder.viewArtLabel != null) {
            android.graphics.drawable.GradientDrawable label = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, DiscPalette.colorsFor(post.getId()));
            label.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            label.setStroke((int) context.getResources().getDisplayMetrics().density, 0x33FFFFFF);
            holder.viewArtLabel.setBackground(label);
        }

        // Xử lý Hashtags động từ post và sự kiện bấm lọc hashtag
        String rawTags = post.getHashtags() != null ? post.getHashtags().trim() : "";
        String[] tags = rawTags.isEmpty() ? new String[0] : rawTags.split("\\s+");
        if (tags.length > 0 && !tags[0].isEmpty()) {
            if (holder.tvHashtag1 != null) {
                holder.tvHashtag1.setVisibility(View.VISIBLE);
                holder.tvHashtag1.setText(tags[0]);
                holder.tvHashtag1.setOnClickListener(v -> dispatchHashtagClick(tags[0]));
            }
        } else if (holder.tvHashtag1 != null) {
            holder.tvHashtag1.setVisibility(View.GONE);
        }

        if (tags.length > 1 && !tags[1].isEmpty()) {
            if (holder.tvHashtag2 != null) {
                holder.tvHashtag2.setVisibility(View.VISIBLE);
                holder.tvHashtag2.setText(tags[1]);
                holder.tvHashtag2.setOnClickListener(v -> dispatchHashtagClick(tags[1]));
            }
        } else if (holder.tvHashtag2 != null) {
            holder.tvHashtag2.setVisibility(View.GONE);
        }

        // Tương tác chạm vào bài viết -> Mở Màn hình Chi tiết bài viết (PostDetailActivity)
        View.OnClickListener openDetailListener = v -> {
            Intent detailIntent = new Intent(context, PostDetailActivity.class);
            detailIntent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
            context.startActivity(detailIntent);
        };

        holder.itemView.setOnClickListener(openDetailListener);
        holder.tvCaption.setOnClickListener(openDetailListener);

        // Chạm vào Tên tác giả hoặc Avatar -> Mở Trang cá nhân ProfileActivity
        View.OnClickListener openAuthorProfileListener = v -> {
            Intent profileIntent = new Intent(context, ProfileActivity.class);
            profileIntent.putExtra(ProfileActivity.EXTRA_USERNAME, post.getAuthorUsername());
            context.startActivity(profileIntent);
        };
        holder.tvAuthor.setOnClickListener(openAuthorProfileListener);
        holder.ivAvatar.setOnClickListener(openAuthorProfileListener);

        // Trạng thái Like & Lưu SQLite
        updateLikeUi(holder, post);
        holder.btnLikeContainer.setOnClickListener(v -> {
            boolean newLiked = !post.isLiked();
            post.setLiked(newLiked);
            post.setLikeCount(post.getLikeCount() + (newLiked ? 1 : -1));
            updateLikeUi(holder, post);

            // Lưu ngay vào SQLite, SyncManager tự đẩy lên Firebase
            dbHelper.updatePostLike(post.getId(), newLiked, post.getLikeCount());
            // nhãn chờ = đã lưu trên máy, chờ Firebase xác nhận (đồng bộ xong thì màn hình đọc lại và bỏ nhãn chờ)
            PostModel saved = dbHelper.getPostById(post.getId());
            post.setLikePending(saved != null && saved.isLikePending());
            holder.tvLikeCount.setText(likeCountText(post));

            // Hiệu ứng nhấp nháy scale nhẹ
            holder.ivLike.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction(() -> {
                holder.ivLike.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }).start();
        });

        // Nút Lưu: chỉ lưu BẢN NHẠC vào Danh sách nhạc đã lưu (SQLite, SyncManager tự đẩy lên Firebase)
        updateBookmarkUi(holder, post);
        holder.ivBookmark.setOnClickListener(v -> {
            boolean newBookmarked = !post.isBookmarked();
            post.setBookmarked(newBookmarked);
            updateBookmarkUi(holder, post);

            // Lưu ngay vào SQLite, SyncManager tự đẩy lên Firebase
            dbHelper.updatePostBookmark(post, newBookmarked);

            String msg = newBookmarked
                    ? "Đã lưu bài hát \"" + post.getSongTitle() + "\" vào Danh sách nhạc đã lưu"
                    : "Đã bỏ bài hát khỏi Danh sách nhạc đã lưu";
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        });

        // Nút Comment Click -> Mở Chi tiết bài viết tự động cuộn đến danh sách bình luận & ô nhập
        holder.btnCommentContainer.setOnClickListener(v -> {
            Intent detailIntent = new Intent(context, PostDetailActivity.class);
            detailIntent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
            detailIntent.putExtra(PostDetailActivity.EXTRA_SCROLL_TO_COMMENTS, true);
            context.startActivity(detailIntent);
        });

        // Nút Share Click -> Hộp thoại chia sẻ: sao chép liên kết bài hát
        holder.btnShareContainer.setOnClickListener(v -> {
            ShareHelper.showShareDialog(context, post, newCount -> {
                holder.tvShareCount.setText(String.valueOf(newCount));
            });
        });

        // Nút Play trên Card Nhạc
        boolean isThisPlaying = MusicService.isPlaying && post.getId().equals(MusicService.currentPostId);
        post.setPlaying(isThisPlaying);
        updateMusicPlayUi(holder, post);

        // Hiệu ứng đĩa Vinyl quay quanh chính tâm
        if (holder.frameMusicArt != null) {
            if (isThisPlaying) {
                if (holder.frameMusicArt.getAnimation() == null) {
                    RotateAnimation anim = new RotateAnimation(0f, 360f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f);
                    anim.setDuration(6000);
                    anim.setRepeatCount(Animation.INFINITE);
                    anim.setInterpolator(new LinearInterpolator());
                    holder.frameMusicArt.startAnimation(anim);
                }
            } else {
                holder.frameMusicArt.clearAnimation();
            }
        }

        // Cập nhật Sóng âm Equalizer động
        if (holder.layoutEqWave != null) {
            if (isThisPlaying) {
                holder.layoutMusicCard.setBackgroundResource(R.drawable.bg_music_card_playing);
                if (holder.tvSongTitle != null) holder.tvSongTitle.setTextColor(android.graphics.Color.WHITE);
                if (holder.tvSongArtist != null) holder.tvSongArtist.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
                holder.layoutEqWave.setVisibility(View.VISIBLE);
                startWaveAnimation(holder.eqBar1, 350, 0.4f, 1.3f);
                startWaveAnimation(holder.eqBar2, 280, 0.3f, 1.4f);
                startWaveAnimation(holder.eqBar3, 420, 0.5f, 1.2f);
                startWaveAnimation(holder.eqBar4, 310, 0.2f, 1.5f);
            } else {
                holder.layoutMusicCard.setBackgroundResource(R.drawable.bg_music_card);
                if (holder.tvSongTitle != null) holder.tvSongTitle.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
                if (holder.tvSongArtist != null) holder.tvSongArtist.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
                holder.layoutEqWave.setVisibility(View.GONE);
                if (holder.eqBar1 != null) holder.eqBar1.clearAnimation();
                if (holder.eqBar2 != null) holder.eqBar2.clearAnimation();
                if (holder.eqBar3 != null) holder.eqBar3.clearAnimation();
                if (holder.eqBar4 != null) holder.eqBar4.clearAnimation();
            }
        }

        View.OnClickListener playListener = v -> {
            if (musicClickListener != null) {
                musicClickListener.onMusicPlayClicked(post);
            }
        };
        holder.ivMusicPlay.setOnClickListener(playListener);
        holder.layoutMusicCard.setOnClickListener(playListener);

        // Nút More Popup Menu
        holder.ivMore.setOnClickListener(v -> {
            boolean isMine = SessionManager.isCurrentUser(context, post.getAuthorUsername());
            PopupMenu popup = new PopupMenu(context, holder.ivMore);
            if (isMine) {
                popup.getMenu().add(0, MENU_EDIT, 0, "Chỉnh sửa bài viết");
                popup.getMenu().add(0, MENU_DELETE, 4, "Xóa bài viết");
            }
            popup.getMenu().add(0, MENU_COPY_LINK, 1, "Sao chép liên kết bài hát");
            if (!isMine) {
                popup.getMenu().add(0, MENU_HIDE, 2, "Ẩn bài viết này");
                popup.getMenu().add(0, MENU_REPORT, 3, "Báo cáo vi phạm (Report)");
            }
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case MENU_EDIT:
                        Intent editIntent = new Intent(context, CreatePostActivity.class);
                        editIntent.putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, post.getId());
                        context.startActivity(editIntent);
                        break;
                    case MENU_COPY_LINK:
                        copySongLink(post);
                        break;
                    case MENU_HIDE:
                        hidePost(holder, post);
                        break;
                    case MENU_DELETE:
                        confirmDeletePost(context, post, () -> {
                            int pos = postList.indexOf(post);
                            if (pos >= 0) {
                                postList.remove(pos);
                                notifyItemRemoved(pos);
                            }
                        });
                        break;
                    case MENU_REPORT:
                        showReportDialog(context, post);
                        break;
                }
                return true;
            });
            popup.show();
        });
    }

    /** Số tim kèm nhãn chờ khi lượt thích / bỏ thích của mình chưa được Firebase xác nhận. */
    static String likeCountText(PostModel post) {
        return post.getLikeCount() + (post.isLikePending() ? " (chờ)" : "");
    }

    private static final int MENU_EDIT = 1;
    private static final int MENU_COPY_LINK = 2;
    private static final int MENU_HIDE = 3;
    private static final int MENU_REPORT = 4;
    private static final int MENU_DELETE = 5;

    /**
     * Hỏi lại rồi xóa bài của chính mình (dùng chung cho Bảng tin, Hồ sơ và Chi tiết bài viết).
     * Xóa ngay trên máy; SyncManager xóa trên Firebase khi có mạng.
     */
    public static void confirmDeletePost(Context context, PostModel post, Runnable onDeleted) {
        new androidx.appcompat.app.AlertDialog.Builder(context, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle("Xóa bài viết?")
                .setMessage("Bài \"" + post.getSongTitle() + "\" cùng toàn bộ bình luận, lượt thích sẽ bị xóa vĩnh viễn.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    // Dừng hẳn (kể cả khi đang tạm dừng) để Mini Player không còn giữ bài đã xóa
                    MusicService.stopIfPlaying(context, post.getId());
                    DatabaseHelper.getInstance(context).deletePost(post.getId());
                    boolean online = SyncManager.get(context).isOnline();
                    Toast.makeText(context, online
                            ? "Đã xóa bài viết"
                            : "Đã xóa trên máy — sẽ xóa trên hệ thống khi có mạng", Toast.LENGTH_SHORT).show();
                    if (onDeleted != null) onDeleted.run();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Thành viên báo cáo bài viết vi phạm: chọn lý do rồi gửi lên Firebase để Quản trị viên xử lý. */
    public static void showReportDialog(Context context, PostModel post) {
        String[] reasons = {"Spam / quảng cáo", "Nội dung phản cảm", "Vi phạm bản quyền âm nhạc", "Thông tin sai lệch", "Lý do khác"};
        new androidx.appcompat.app.AlertDialog.Builder(context, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle("Báo cáo bài viết vi phạm")
                .setItems(reasons, (dialog, which) -> SyncManager.get(context).reportPost(post.getId(), reasons[which], (status, more) -> {
                    if (status == SyncManager.Status.OK) {
                        Toast.makeText(context, "Cảm ơn bạn đã báo cáo. Quản trị viên sẽ xem xét bài viết này.", Toast.LENGTH_SHORT).show();
                    } else if (status == SyncManager.Status.OFFLINE) {
                        Toast.makeText(context, "Cần kết nối Internet để gửi báo cáo vi phạm.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Không gửi được báo cáo, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void copySongLink(PostModel post) {
        if (ShareHelper.copySongLink(context, post)) {
            Toast.makeText(context, "Đã sao chép liên kết bài hát", Toast.LENGTH_SHORT).show();
        }
    }

    /** Ẩn bài khỏi bảng tin của mình (lưu SQLite) kèm nút Hoàn tác. */
    private void hidePost(ViewHolder holder, PostModel post) {
        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        View anchor = (View) holder.itemView.getParent();
        dbHelper.hidePost(post.getId());
        postList.remove(position);
        notifyItemRemoved(position);

        com.google.android.material.snackbar.Snackbar
                .make(anchor != null ? anchor : holder.itemView, "Đã ẩn bài viết khỏi bảng tin của bạn",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("HOÀN TÁC", v -> {
                    dbHelper.unhidePost(post.getId());
                    int insertAt = Math.min(position, postList.size());
                    postList.add(insertAt, post);
                    notifyItemInserted(insertAt);
                })
                .show();
    }

    private void updateLikeUi(ViewHolder holder, PostModel post) {
        int color = post.isLiked() 
                ? ContextCompat.getColor(context, R.color.colorNeonPink)
                : ContextCompat.getColor(context, R.color.colorTextSecondary);
        holder.ivLike.setImageTintList(ColorStateList.valueOf(color));
        holder.tvLikeCount.setTextColor(color);
    }

    private void updateBookmarkUi(ViewHolder holder, PostModel post) {
        int color = post.isBookmarked()
                ? ContextCompat.getColor(context, R.color.colorBookmarkActive)
                : ContextCompat.getColor(context, R.color.colorTextSecondary);
        holder.ivBookmark.setImageTintList(ColorStateList.valueOf(color));
    }

    private void updateMusicPlayUi(ViewHolder holder, PostModel post) {
        if (post.isPlaying()) {
            holder.ivMusicPlay.setImageResource(R.drawable.ic_pause);
            holder.ivMusicPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent)));
        } else {
            holder.ivMusicPlay.setImageResource(R.drawable.ic_play);
            holder.ivMusicPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent)));
        }
    }

    private void startWaveAnimation(View bar, long duration, float fromScale, float toScale) {
        if (bar == null) return;
        android.view.animation.ScaleAnimation scale = new android.view.animation.ScaleAnimation(
                1f, 1f, fromScale, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 1f
        );
        scale.setDuration(duration);
        scale.setRepeatCount(Animation.INFINITE);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        bar.startAnimation(scale);
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivMore, ivMusicPlay, ivLike, ivComment, ivShare, ivBookmark, ivPrivacyIcon;
        TextView tvAuthor, tvUsername, tvTime, tvCaption, tvSongTitle, tvSongArtist, tvGenreBadge;
        TextView tvLikeCount, tvCommentCount, tvShareCount, tvPrivacyLabel, tvHashtag1, tvHashtag2;
        View viewArtLabel;
        View layoutMusicCard, btnLikeContainer, btnCommentContainer, btnShareContainer, frameMusicArt;
        View layoutEqWave, eqBar1, eqBar2, eqBar3, eqBar4;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_post_avatar);
            ivMore = itemView.findViewById(R.id.iv_post_more);
            tvAuthor = itemView.findViewById(R.id.tv_post_author);
            tvUsername = itemView.findViewById(R.id.tv_post_username);
            tvTime = itemView.findViewById(R.id.tv_post_time);
            tvCaption = itemView.findViewById(R.id.tv_post_caption);
            
            ivPrivacyIcon = itemView.findViewById(R.id.iv_post_privacy_icon);
            tvPrivacyLabel = itemView.findViewById(R.id.tv_post_privacy_label);
            tvHashtag1 = itemView.findViewById(R.id.tv_hashtag1);
            tvHashtag2 = itemView.findViewById(R.id.tv_hashtag2);

            layoutMusicCard = itemView.findViewById(R.id.layout_music_card);
            frameMusicArt = itemView.findViewById(R.id.frame_music_art);
            viewArtLabel = itemView.findViewById(R.id.view_music_art_label);
            tvSongTitle = itemView.findViewById(R.id.tv_music_title);
            tvSongArtist = itemView.findViewById(R.id.tv_music_artist);
            ivMusicPlay = itemView.findViewById(R.id.iv_music_play);
            tvGenreBadge = itemView.findViewById(R.id.tv_genre_badge);

            layoutEqWave = itemView.findViewById(R.id.layout_eq_wave);
            eqBar1 = itemView.findViewById(R.id.eq_bar_1);
            eqBar2 = itemView.findViewById(R.id.eq_bar_2);
            eqBar3 = itemView.findViewById(R.id.eq_bar_3);
            eqBar4 = itemView.findViewById(R.id.eq_bar_4);

            btnLikeContainer = itemView.findViewById(R.id.btn_like_container);
            ivLike = itemView.findViewById(R.id.iv_post_like);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);

            btnCommentContainer = itemView.findViewById(R.id.btn_comment_container);
            ivComment = itemView.findViewById(R.id.iv_post_comment);
            tvCommentCount = itemView.findViewById(R.id.tv_comment_count);

            btnShareContainer = itemView.findViewById(R.id.btn_share_container);
            ivShare = itemView.findViewById(R.id.iv_post_share);
            tvShareCount = itemView.findViewById(R.id.tv_share_count);

            ivBookmark = itemView.findViewById(R.id.iv_post_bookmark);
        }
    }

    private void dispatchHashtagClick(String hashtag) {
        if (hashtagClickListener != null) {
            hashtagClickListener.onHashtagClicked(hashtag);
        } else {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("SEARCH_QUERY", hashtag);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }
}
