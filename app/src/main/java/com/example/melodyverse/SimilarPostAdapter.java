package com.example.melodyverse;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Danh sách "Bài hát tương tự" ở cuối trang Chi tiết bài viết.
 * Chạm vào dòng → mở chi tiết bài đó; chạm nút ▶ → phát nhạc ngay.
 */
public class SimilarPostAdapter extends RecyclerView.Adapter<SimilarPostAdapter.ViewHolder> {

    private final Context context;
    private final List<PostModel> posts;

    public SimilarPostAdapter(Context context, List<PostModel> posts) {
        this.context = context;
        this.posts = posts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_similar_post, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostModel post = posts.get(position);
        holder.tvTitle.setText(post.getSongTitle());
        holder.tvMeta.setText(post.getArtistName() + " • " + post.getGenre() + " • " + post.getLikeCount() + " lượt thích");
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
            context.startActivity(intent);
        });
        holder.ivPlay.setOnClickListener(v -> MusicService.play(context, post));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvMeta;
        final ImageView ivPlay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_similar_title);
            tvMeta = itemView.findViewById(R.id.tv_similar_meta);
            ivPlay = itemView.findViewById(R.id.iv_similar_play);
        }
    }
}
