package com.example.melodyverse;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import java.util.List;

/**
 * Màn hình Thông báo (Bài 7 - Notifications & Bài 2 - Activities).
 *
 * Mỗi tài khoản có danh sách thông báo riêng (Firestore notifications, lọc theo uid người nhận) và trạng thái
 * đã đọc của TỪNG thông báo. Trạng thái đọc lưu ngay vào SQLite (giữ nguyên sau khi tắt / mở lại app)
 * rồi đồng bộ lên Firestore để các thiết bị khác của cùng tài khoản cũng thấy đã đọc.
 */
public class NotificationsActivity extends BaseActivity {

    // Khóa lưu trạng thái khi xoay màn hình (Activity bị hủy và tạo lại)
    private static final String STATE_REFRESHING = "state_notifications_refreshing";
    private static final String STATE_DETAIL_ID = "state_notifications_detail_id";

    private DatabaseHelper dbHelper;
    private LinearLayout listContainer;
    private TextView tvEmpty, tvSummary, btnMarkAllRead;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private boolean isLoading = false;
    /** Dialog chi tiết đang mở và thông báo tương ứng (mở lại sau khi xoay màn hình). */
    private androidx.appcompat.app.AlertDialog detailDialog;
    private String detailNotificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_notifications);
        dbHelper = DatabaseHelper.getInstance(this);

        listContainer = findViewById(R.id.layout_notification_list);
        tvEmpty = findViewById(R.id.tv_notifications_empty);
        tvSummary = findViewById(R.id.tv_notifications_summary);
        btnMarkAllRead = findViewById(R.id.btn_mark_all_read);
        swipeRefresh = findViewById(R.id.swipe_refresh_notifications);

        if (btnMarkAllRead != null) {
            btnMarkAllRead.setOnClickListener(v -> {
                int changed = dbHelper.markAllNotificationsRead(me());
                renderList();
                if (changed > 0) {
                    Toast.makeText(this, "Đã đánh dấu tất cả thông báo là đã đọc!", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorAccent, R.color.colorNeonPink);
            swipeRefresh.setOnRefreshListener(() -> reload(true));
        }

        renderList();
        // Xoay màn hình: đang kéo làm mới dở thì tải tiếp (kèm vòng xoay), mở lại dialog chi tiết nếu đang mở.
        // Vị trí cuộn của danh sách do ScrollView (có id) tự khôi phục vì danh sách đã vẽ xong từ SQLite.
        boolean wasRefreshing = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_REFRESHING, false);
        reload(wasRefreshing);
        if (savedInstanceState != null) {
            String detailId = savedInstanceState.getString(STATE_DETAIL_ID);
            if (detailId != null) {
                for (NotificationModel n : dbHelper.getNotifications(me())) {
                    if (detailId.equals(n.getId())) {
                        showNotificationDetail(n);
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_REFRESHING, swipeRefresh != null && swipeRefresh.isRefreshing());
        if (detailDialog != null && detailDialog.isShowing()) {
            outState.putString(STATE_DETAIL_ID, detailNotificationId);
        }
    }

    @Override
    protected void onDestroy() {
        // Đóng dialog chi tiết trước khi Activity bị hủy (tránh rò rỉ cửa sổ khi xoay màn hình)
        if (detailDialog != null && detailDialog.isShowing()) {
            detailDialog.dismiss();
        }
        super.onDestroy();
    }

    private String me() {
        return SessionManager.getCurrentUsername(this);
    }

    /** Tải thông báo mới nhất của tài khoản từ Firestore (offline: giữ danh sách đã lưu trên máy). */
    private void reload(boolean userInitiated) {
        if (isLoading) return;
        isLoading = true;
        if (userInitiated && swipeRefresh != null) swipeRefresh.setRefreshing(true);
        SyncManager.get(this).refreshNotifications(true, (status, more) -> {
            isLoading = false;
            if (isFinishing() || isDestroyed()) return;
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            renderList();
            if (userInitiated && status == SyncManager.Status.OFFLINE) {
                Toast.makeText(this, "Đang offline — hiển thị thông báo đã lưu trên máy", Toast.LENGTH_SHORT).show();
            } else if (userInitiated && status == SyncManager.Status.ERROR) {
                Toast.makeText(this, "Không tải được thông báo mới. Vui lòng thử lại!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onLocalDataChanged() {
        // Realtime: thông báo mới / đã đọc trên thiết bị khác
        renderList();
    }

    @Override
    protected void onReconnectedRefresh() {
        reload(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
    }

    /** Vẽ lại danh sách, chấm đỏ và số chưa đọc từ SQLite (chỉ thông báo của tài khoản đang đăng nhập). */
    private void renderList() {
        updateNotificationBadge();
        if (listContainer == null) return;
        listContainer.removeAllViews();
        List<NotificationModel> list = dbHelper.getNotifications(me());
        LayoutInflater inflater = LayoutInflater.from(this);
        int unread = 0;
        for (NotificationModel n : list) {
            if (!n.isRead()) unread++;
            View item = inflater.inflate(R.layout.item_notification, listContainer, false);
            bindRow(item, n);
            listContainer.addView(item);
        }
        if (tvEmpty != null) tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        if (tvSummary != null) {
            tvSummary.setText(list.isEmpty() ? "" : unread > 0
                    ? "Bạn có " + unread + " thông báo chưa đọc"
                    : "Bạn đã đọc tất cả thông báo");
        }
        if (btnMarkAllRead != null) {
            boolean allRead = unread == 0;
            btnMarkAllRead.setText(allRead ? "Đã đọc tất cả" : "Đánh dấu đã đọc");
            btnMarkAllRead.setTextColor(ContextCompat.getColor(this, allRead ? R.color.colorTextSecondary : R.color.colorAccent));
            btnMarkAllRead.setEnabled(!allRead);
        }
    }

    private void bindRow(View item, NotificationModel n) {
        TextView tvText = item.findViewById(R.id.tv_notif_text);
        TextView tvTime = item.findViewById(R.id.tv_notif_time);
        ImageView ivIcon = item.findViewById(R.id.iv_notif_icon);
        View frame = item.findViewById(R.id.frame_notif_icon);
        View dot = item.findViewById(R.id.dot_notif_unread);

        tvText.setText(n.getSummary());
        tvTime.setText(n.getTimeAgo() + (n.isSample() ? " • Minh họa" : ""));
        dot.setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);
        tvText.setTypeface(null, n.isRead() ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);

        Style style = styleOf(n.getType());
        ivIcon.setImageResource(style.icon);
        ivIcon.setImageTintList(ColorStateList.valueOf(style.color));
        frame.setBackgroundTintList(ColorStateList.valueOf((style.color & 0x00FFFFFF) | 0x22000000));

        item.findViewById(R.id.layout_notif_row).setOnClickListener(v -> {
            // Mở chi tiết = đã đọc: cập nhật ngay chấm đỏ & số chưa đọc, lưu SQLite, đồng bộ Firestore
            if (dbHelper.markNotificationRead(n.getId())) {
                n.setRead(true);
                renderList();
            }
            showNotificationDetail(n);
        });
    }

    private static class Style {
        final int icon;
        final int color;
        final String category;

        Style(int icon, int color, String category) {
            this.icon = icon;
            this.color = color;
            this.category = category;
        }
    }

    private static Style styleOf(String type) {
        switch (type) {
            case NotificationModel.TYPE_LIKE:
                return new Style(R.drawable.ic_heart, 0xFFEC4899, "TƯƠNG TÁC ÂM NHẠC");
            case NotificationModel.TYPE_COMMENT:
                return new Style(R.drawable.ic_comment, 0xFF10B981, "BÌNH LUẬN MỚI");
            case NotificationModel.TYPE_FOLLOW:
                return new Style(R.drawable.ic_person, 0xFF06B6D4, "NGƯỜI THEO DÕI MỚI");
            case NotificationModel.TYPE_NEW_POST:
                return new Style(R.drawable.ic_music_note, 0xFF7C3AED, "BÀI HÁT MỚI");
            default:
                return new Style(R.drawable.ic_notifications, 0xFF7C3AED, "MELODYVERSE");
        }
    }

    /** Chi tiết + nút hành động thực hiện ĐÚNG việc nút mô tả, dựa trên ID bài / bình luận / tài khoản của thông báo. */
    private void showNotificationDetail(NotificationModel n) {
        Style style = styleOf(n.getType());
        String title;
        String message;
        String actionText;
        Runnable action;
        switch (n.getType()) {
            case NotificationModel.TYPE_LIKE:
                title = n.getActorName() + " đã thích bài viết của bạn";
                message = n.getActorName() + " đã thả tim cho bài hát “" + n.getSongTitle() + "” bạn chia sẻ trên MelodyVerse.";
                actionText = "Phát bài hát này";
                action = () -> openPost(n, false, true);
                break;
            case NotificationModel.TYPE_COMMENT:
                title = n.getActorName() + " đã bình luận về bài viết của bạn";
                message = n.getActorName() + ": “" + n.getPreview() + "”\nBài viết: “" + n.getSongTitle() + "”";
                actionText = "Xem bình luận";
                action = () -> openPost(n, true, false);
                break;
            case NotificationModel.TYPE_FOLLOW:
                title = n.getActorName() + " đã theo dõi bạn";
                message = "@" + n.getActor() + " vừa bắt đầu theo dõi bạn trên MelodyVerse. Ghé thăm hồ sơ để xem các bài hát họ chia sẻ!";
                actionText = "Xem hồ sơ";
                action = () -> {
                    Intent intent = new Intent(this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USERNAME, n.getActor());
                    startActivity(intent);
                };
                break;
            case NotificationModel.TYPE_NEW_POST:
                title = n.getActorName() + " vừa chia sẻ bài hát mới";
                message = "“" + n.getSongTitle() + "” vừa được đăng trên MelodyVerse. Nghe ngay nhé!";
                actionText = "Nghe bài hát";
                action = () -> openPost(n, false, true);
                break;
            default:
                title = "Chào mừng đến với MelodyVerse!";
                message = "Tài khoản của bạn đã sẵn sàng. Khám phá các bài hát đang thịnh hành và chia sẻ âm nhạc bạn yêu thích.";
                actionText = "Khám phá âm nhạc";
                action = () -> {
                    Intent intent = new Intent(this, DiscoverActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                };
                break;
        }
        if (n.isSample()) {
            message += "\n\n(Thông báo minh họa từ dữ liệu mẫu của MelodyVerse.)";
        }

        if (isFinishing() || isDestroyed()) return;
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
        detailDialog = dialog;
        detailNotificationId = n.getId();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_detail, null, false);
        dialog.setView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        ImageView ivIcon = dialogView.findViewById(R.id.iv_notif_detail_icon);
        TextView tvCat = dialogView.findViewById(R.id.tv_notif_detail_category);
        TextView tvTitle = dialogView.findViewById(R.id.tv_notif_detail_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_notif_detail_msg);
        TextView tvTime = dialogView.findViewById(R.id.tv_notif_detail_time);
        View btnClose = dialogView.findViewById(R.id.btn_notif_close);
        TextView btnAction = dialogView.findViewById(R.id.btn_notif_action);

        if (ivIcon != null) {
            ivIcon.setImageResource(style.icon);
            ivIcon.setImageTintList(ColorStateList.valueOf(style.color));
        }
        if (tvCat != null) tvCat.setText(style.category);
        if (tvTitle != null) tvTitle.setText(title);
        if (tvMsg != null) tvMsg.setText(message);
        if (tvTime != null) tvTime.setText(n.getTimeAgo());
        if (btnAction != null) {
            btnAction.setText(actionText);
            btnAction.setOnClickListener(v -> {
                // Đóng dialog trước rồi mới mở màn hình đích
                dialog.dismiss();
                action.run();
            });
        }
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Mở đúng bài viết của thông báo. Bài đã bị xóa / không tải được thì báo rõ thay vì mở màn hình trống.
     * PostDetailActivity tự tải từ Firebase nếu bài chưa có trong SQLite.
     */
    private void openPost(NotificationModel n, boolean toComments, boolean autoPlay) {
        if (n.getPostId().isEmpty()) {
            Toast.makeText(this, "Thông báo này không gắn với bài viết nào.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.getPostById(n.getPostId()) == null && !SyncManager.get(this).isOnline()) {
            Toast.makeText(this, "Bài viết chưa được lưu để xem offline. Vui lòng kết nối mạng!", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, n.getPostId());
        if (toComments) {
            intent.putExtra(PostDetailActivity.EXTRA_SCROLL_TO_COMMENTS, true);
            intent.putExtra(PostDetailActivity.EXTRA_HIGHLIGHT_COMMENT_ID, n.getCommentId());
        }
        if (autoPlay) intent.putExtra(PostDetailActivity.EXTRA_AUTO_PLAY, true);
        startActivity(intent);
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_notifications;
    }
}
