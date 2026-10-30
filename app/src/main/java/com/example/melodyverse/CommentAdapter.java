package com.example.melodyverse;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Adapter hiển thị danh sách bình luận chi tiết bài viết.
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private final Context context;
    private List<CommentModel> commentList;
    /** Bình luận được thông báo nhắc tới (tô nền nổi bật). */
    private String highlightedId;

    private final int[] avatarColors = new int[]{
            R.color.colorAccent,
            R.color.colorNeonCyan,
            R.color.colorNeonPink,
            R.color.colorNeonOrange,
            R.color.colorPrimary
    };

    public CommentAdapter(Context context, List<CommentModel> commentList) {
        this.context = context;
        this.commentList = commentList;
    }

    public void setHighlightedId(String id) {
        this.highlightedId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommentModel comment = commentList.get(position);

        holder.tvAuthor.setText(comment.getAuthorName());
        // Bình luận viết lúc offline: nhãn "Đang chờ kết nối mạng"; Firebase từ chối (bài đã bị xóa): nhãn "Không gửi được"
        if (comment.isPending()) {
            holder.tvTime.setText(" • Đang chờ kết nối mạng");
            holder.tvTime.setTextColor(ContextCompat.getColor(context, R.color.colorGold));
        } else if (comment.isFailed()) {
            holder.tvTime.setText(" • Không gửi được (bài viết đã bị xóa)");
            holder.tvTime.setTextColor(ContextCompat.getColor(context, R.color.colorNeonPink));
        } else {
            holder.tvTime.setText(" • " + comment.getTimeAgo());
            holder.tvTime.setTextColor(ContextCompat.getColor(context, R.color.colorTextMuted));
        }
        holder.tvContent.setText(comment.getContent());
        boolean highlighted = highlightedId != null && highlightedId.equals(comment.getId());
        holder.itemView.setBackgroundColor(highlighted ? 0x267C3AED : android.graphics.Color.TRANSPARENT);

        // Màu avatar ngẫu nhiên theo vị trí
        int colorRes = avatarColors[Math.abs(position) % avatarColors.length];
        holder.ivAvatar.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));

        // Chạm ảnh đại diện hoặc tên người bình luận -> mở Trang cá nhân của người đó
        String username = comment.getAuthorUsername();
        View.OnClickListener openProfile = username == null || username.trim().isEmpty() ? null : v -> {
            Intent intent = new Intent(context, ProfileActivity.class);
            intent.putExtra(ProfileActivity.EXTRA_USERNAME, username);
            context.startActivity(intent);
        };
        holder.ivAvatar.setOnClickListener(openProfile);
        holder.tvAuthor.setOnClickListener(openProfile);
        holder.ivAvatar.setClickable(openProfile != null);
        holder.tvAuthor.setClickable(openProfile != null);
    }

    @Override
    public int getItemCount() {
        return commentList != null ? commentList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvAuthor, tvTime, tvContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
            tvAuthor = itemView.findViewById(R.id.tv_comment_author);
            tvTime = itemView.findViewById(R.id.tv_comment_time);
            tvContent = itemView.findViewById(R.id.tv_comment_content);
        }
    }
}
