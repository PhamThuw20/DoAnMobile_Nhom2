package com.example.melodyverse;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter danh sách người theo dõi (Followers) và đang theo dõi (Following).
 */
public class FollowUserAdapter extends RecyclerView.Adapter<FollowUserAdapter.ViewHolder> {

    public interface OnFollowChangeListener {
        void onFollowCountChanged(int delta);
    }

    private final Context context;
    private final List<UserModel> userList;
    private final boolean isFollowingList;
    private final OnFollowChangeListener listener;
    private final DatabaseHelper dbHelper;

    private final int[] avatarColors = new int[]{
            R.color.colorAccent,
            R.color.colorNeonCyan,
            R.color.colorNeonPink,
            R.color.colorNeonOrange,
            R.color.colorPrimary
    };

    public FollowUserAdapter(Context context, List<UserModel> userList, boolean isFollowingList, OnFollowChangeListener listener) {
        this.context = context;
        this.userList = userList;
        this.isFollowingList = isFollowingList;
        this.listener = listener;
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public void updateList(List<UserModel> newList) {
        if (this.userList != null) {
            this.userList.clear();
            if (newList != null) {
                this.userList.addAll(newList);
            }
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_follow_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserModel user = userList.get(position);

        holder.tvFullName.setText(user.getFullName());
        holder.tvUsername.setText("@" + user.getUsername());
        holder.tvBio.setText(user.getBio());

        int colorRes = avatarColors[Math.abs(position) % avatarColors.length];
        holder.ivAvatar.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));

        // Mở trang cá nhân chi tiết khi chạm vào người dùng
        View.OnClickListener openProfileListener = v -> {
            Intent intent = new Intent(context, ProfileActivity.class);
            intent.putExtra(ProfileActivity.EXTRA_USERNAME, user.getUsername());
            context.startActivity(intent);
        };
        holder.itemView.setOnClickListener(openProfileListener);
        holder.ivAvatar.setOnClickListener(openProfileListener);
        holder.tvFullName.setOnClickListener(openProfileListener);
        holder.tvUsername.setOnClickListener(openProfileListener);

        String currentUser = SessionManager.getCurrentUsername(context);
        boolean isSelf = SessionManager.isCurrentUser(context, user.getUsername());
        holder.btnFollow.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        boolean isFollowed = dbHelper.isUserFollowed(currentUser, user.getUsername());
        updateButtonUi(holder.btnFollow, isFollowed);

        holder.btnFollow.setOnClickListener(v -> {
            boolean nowFollowed;
            if (dbHelper.isUserFollowed(currentUser, user.getUsername())) {
                dbHelper.unfollowUser(currentUser, user.getUsername());
                nowFollowed = false;
                if (listener != null) listener.onFollowCountChanged(-1);
                Toast.makeText(context, "Đã bỏ theo dõi @" + user.getUsername(), Toast.LENGTH_SHORT).show();
            } else {
                dbHelper.followUser(currentUser, user.getUsername());
                nowFollowed = true;
                if (listener != null) listener.onFollowCountChanged(1);
                Toast.makeText(context, "Đã theo dõi @" + user.getUsername(), Toast.LENGTH_SHORT).show();
            }
            updateButtonUi(holder.btnFollow, nowFollowed);

            // Hiệu ứng nảy nút
            holder.btnFollow.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(() ->
                    holder.btnFollow.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            ).start();
        });
    }

    private void updateButtonUi(AppCompatButton btn, boolean isFollowed) {
        if (isFollowed) {
            btn.setText("Đang theo dõi");
            btn.setBackgroundResource(R.drawable.bg_input_field);
            btn.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
        } else {
            btn.setText("+ Theo dõi");
            btn.setBackgroundResource(R.drawable.bg_gradient_button);
            btn.setTextColor(ContextCompat.getColor(context, R.color.white));
        }
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvFullName, tvUsername, tvBio;
        AppCompatButton btnFollow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_follow_avatar);
            tvFullName = itemView.findViewById(R.id.tv_follow_fullname);
            tvUsername = itemView.findViewById(R.id.tv_follow_username);
            tvBio = itemView.findViewById(R.id.tv_follow_bio);
            btnFollow = itemView.findViewById(R.id.btn_follow_action);
        }
    }
}
