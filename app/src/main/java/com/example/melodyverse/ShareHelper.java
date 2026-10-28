package com.example.melodyverse;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

/**
 * Trình trợ giúp Chia sẻ Giai điệu MelodyVerse (Bài 4 - Clipboard).
 * Chỉ một thao tác: sao chép đường dẫn bài hát vào bộ nhớ tạm để dán sang ứng dụng khác.
 */
public class ShareHelper {

    public interface OnShareListener {
        void onShareSuccess(int newShareCount);
    }

    /**
     * Đường dẫn bài hát được sao chép: link nhạc trực tuyến thật của bài (nếu có),
     * còn bài dùng nhạc mẫu thì dùng link bài hát trên MelodyVerse.
     */
    public static String songLink(PostModel post) {
        if (post.getMusicUrl() != null && MusicService.isStreamableUrl(post.getMusicUrl())) {
            return post.getMusicUrl().trim();
        }
        return "https://melodyverse.vn/track/" + (post.getId() != null ? post.getId() : "song");
    }

    /** Sao chép đường dẫn bài hát vào bộ nhớ tạm. */
    public static boolean copySongLink(Context context, PostModel post) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText("MelodyVerse - " + post.getSongTitle(), songLink(post)));
        return true;
    }

    public static void showShareDialog(Context context, PostModel post, OnShareListener listener) {
        if (context == null || post == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.ThemeOverlay_MelodyVerse_Dialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_share_track, null, false);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        // Nền cửa sổ trong suốt để khung bo góc của layout hiển thị trọn vẹn
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_share_song_title);
        TextView tvArtist = view.findViewById(R.id.tv_share_song_artist);
        TextView tvUrl = view.findViewById(R.id.tv_share_song_url);
        View btnClose = view.findViewById(R.id.btn_share_dialog_close);
        View optCopy = view.findViewById(R.id.opt_share_copy_link);

        if (tvTitle != null) tvTitle.setText(post.getSongTitle());
        if (tvArtist != null) tvArtist.setText(post.getArtistName());
        // Hiện đúng đường dẫn sẽ được sao chép
        if (tvUrl != null) tvUrl.setText(songLink(post));

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (optCopy != null) {
            optCopy.setOnClickListener(v -> {
                if (!copySongLink(context, post)) {
                    Toast.makeText(context, "Không truy cập được bộ nhớ tạm của thiết bị", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Tăng lượt share và lưu vào SQLite
                DatabaseHelper.getInstance(context).incrementShareCount(post.getId());
                post.setShareCount(post.getShareCount() + 1);
                if (listener != null) listener.onShareSuccess(post.getShareCount());

                Toast.makeText(context, "Đã sao chép liên kết bài hát vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }
}
