package com.example.melodyverse;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trang Quản trị viên — tài khoản quản trị riêng, chỉ tập trung vào nghiệp vụ kiểm duyệt với 3 khu vực:
 * Quản lý bài hát, Quản lý người dùng và Quản lý bài viết vi phạm (bài bị thành viên báo cáo).
 */
public class AdminActivity extends BaseActivity {

    private TextView tvStatPosts, tvStatUsers, tvStatReports, tvStatSystem;
    private TextView tabPosts, tabUsers, tabReports, tvEmpty;
    private RecyclerView rvPosts, rvUsers, rvReports;
    private View layoutAdminUsersContainer;
    private EditText etAdminSearchUsers;

    private DatabaseHelper dbHelper;
    private SyncManager sync;
    private AdminPostAdapter postAdapter, reportAdapter;
    private AdminUserAdapter userAdapter;
    private final List<PostModel> postList = new ArrayList<>();
    private final List<PostModel> reportList = new ArrayList<>();
    /** postId → thông tin báo cáo vi phạm của bài. */
    private final Map<String, SyncManager.ReportedPost> reportInfo = new HashMap<>();
    private final List<UserModel> userList = new ArrayList<>();
    private final List<UserModel> allUserList = new ArrayList<>();
    /** Kênh lắng nghe realtime collection reports (mở khi màn hình hiển thị, đóng khi rời màn hình). */
    private com.google.firebase.firestore.ListenerRegistration reportsReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bơm giao diện Admin vào FrameLayout của BaseActivity
        setContentLayout(R.layout.activity_admin);

        // Chỉ tài khoản có vai trò Quản trị viên trong CSDL mới được vào trang này
        dbHelper = DatabaseHelper.getInstance(this);
        String sessionUser = SessionManager.getCurrentUsername(this);
        if (sessionUser == null || !dbHelper.getUser(sessionUser).isAdmin()) {
            if (sessionUser != null) {
                Toast.makeText(this, "Bạn không có quyền truy cập Trung tâm Quản trị!", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        // Ẩn hoàn toàn thanh điều hướng của thành viên (Admin chỉ có tính năng quản trị chuyên biệt)
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.GONE);
        }

        // Ẩn thanh mini player trong Admin từ đầu, chỉ hiện khi Admin bấm nghe thử để kiểm duyệt
        if (miniPlayerContainer != null && !MusicService.isPlaying) {
            miniPlayerContainer.setVisibility(View.GONE);
        }

        sync = SyncManager.get(this);

        tvStatPosts = findViewById(R.id.tv_admin_stat_posts);
        tvStatUsers = findViewById(R.id.tv_admin_stat_users);
        tvStatReports = findViewById(R.id.tv_admin_stat_reports);
        tvStatSystem = findViewById(R.id.tv_admin_stat_system);

        tabPosts = findViewById(R.id.tab_admin_posts);
        tabUsers = findViewById(R.id.tab_admin_users);
        tabReports = findViewById(R.id.tab_admin_reports);
        tvEmpty = findViewById(R.id.tv_admin_empty);

        rvPosts = findViewById(R.id.rv_admin_posts);
        rvUsers = findViewById(R.id.rv_admin_users);
        rvReports = findViewById(R.id.rv_admin_reports);
        layoutAdminUsersContainer = findViewById(R.id.layout_admin_users_container);
        etAdminSearchUsers = findViewById(R.id.et_admin_search_users);

        AdminPostAdapter.OnAdminPostActionListener postActions = new AdminPostAdapter.OnAdminPostActionListener() {
            @Override
            public void onViewPost(PostModel post) {
                android.content.Intent intent = new android.content.Intent(AdminActivity.this, PostDetailActivity.class);
                intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
                startActivity(intent);
            }

            @Override
            public void onPlayPost(PostModel post) {
                isMiniPlayerDismissed = false;
                MusicService.play(AdminActivity.this, post);
                if (miniPlayerContainer != null) {
                    miniPlayerContainer.setVisibility(View.VISIBLE);
                }
                if (!MusicService.isOfflineStream(AdminActivity.this, post)) {
                    Toast.makeText(AdminActivity.this, "Đang phát kiểm duyệt: " + post.getSongTitle(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onToggleHidden(PostModel post) {
                confirmToggleHidden(post);
            }

            @Override
            public void onDismissReports(PostModel post) {
                confirmDismissReports(post);
            }

            @Override
            public void onDeletePost(PostModel post) {
                showDeletePostConfirmDialog(post);
            }
        };

        // Quản lý bài hát
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new AdminPostAdapter(this, postList, reportInfo, false, postActions);
        rvPosts.setAdapter(postAdapter);

        // Quản lý bài viết vi phạm
        rvReports.setLayoutManager(new LinearLayoutManager(this));
        reportAdapter = new AdminPostAdapter(this, reportList, reportInfo, true, postActions);
        rvReports.setAdapter(reportAdapter);

        // Quản lý người dùng & thanh tìm kiếm
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new AdminUserAdapter(this, userList, this::toggleUserLock);
        rvUsers.setAdapter(userAdapter);

        if (etAdminSearchUsers != null) {
            etAdminSearchUsers.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterUsers(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        View btnTopLogout = findViewById(R.id.btn_admin_top_logout);
        if (btnTopLogout != null) {
            btnTopLogout.setOnClickListener(v -> showAdminLogoutDialog());
        }

        setupTabs();
        // Dữ liệu được tải ở onResume (mở màn hình và mỗi lần quay lại)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.GONE);
        }
        if (miniPlayerContainer != null && !MusicService.isPlaying) {
            miniPlayerContainer.setVisibility(View.GONE);
        }
        if (sync != null) {
            loadDashboardData();
            // Báo cáo vi phạm cập nhật theo thời gian thực (xử lý / xóa trên Console hoặc bởi Quản trị viên khác)
            if (reportsReg == null) reportsReg = sync.watchReports(this::applyReports);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (reportsReg != null) {
            reportsReg.remove();
            reportsReg = null;
        }
    }

    /** Danh sách bài vi phạm mới nhất từ Firebase: cập nhật danh sách và thẻ thống kê ngay lập tức. */
    private void applyReports(List<SyncManager.ReportedPost> reports) {
        if (isFinishing() || isDestroyed() || reportsReg == null) return;
        reportInfo.clear();
        reportList.clear();
        for (SyncManager.ReportedPost r : reports) {
            reportInfo.put(r.post.getId(), r);
            reportList.add(r.post);
        }
        if (tvStatReports != null) tvStatReports.setText(String.valueOf(reportList.size()));
        reportAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    @Override
    public void updateMiniPlayerUi() {
        super.updateMiniPlayerUi();
        // Trong trang Admin, chỉ hiện mini player nếu đang thực sự phát nhạc kiểm duyệt
        if (miniPlayerContainer != null && !MusicService.isPlaying) {
            miniPlayerContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onReconnectedRefresh() {
        if (sync != null) loadDashboardData();
    }

    /** Hiển thị dữ liệu đã lưu trong SQLite, rồi tải số liệu toàn hệ thống + báo cáo vi phạm từ Firebase. */
    private void loadDashboardData() {
        if (postList.isEmpty() && allUserList.isEmpty()) {
            showDashboard(dbHelper.getAllPosts(), dbHelper.getAllUsers(), null);
        }
        setSystemStatus(null);
        sync.loadAdminData((status, posts, users, reports) -> {
            if (isFinishing() || isDestroyed()) return;
            setSystemStatus(status);
            if (status == SyncManager.Status.OK) {
                showDashboard(posts, users, reports);
                if (reports == null) {
                    Toast.makeText(this, "Không tải được bài vi phạm — tài khoản chưa có quyền admins/{uid} trên Firestore", Toast.LENGTH_LONG).show();
                }
            } else if (status == SyncManager.Status.ERROR) {
                Toast.makeText(this, "Lỗi tải dữ liệu quản trị — kiểm tra Firestore Rules / kết nối Firebase (xem Logcat)", Toast.LENGTH_LONG).show();
            } else if (status == SyncManager.Status.OFFLINE) {
                Toast.makeText(this, "Đang offline — hiển thị dữ liệu đã lưu trên máy", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Trạng thái hoạt động của hệ thống: kết nối được Firebase hay không. */
    private void setSystemStatus(SyncManager.Status status) {
        if (tvStatSystem == null) return;
        String text;
        int color;
        if (status == null) {
            text = "●  Đang kiểm tra";
            color = R.color.colorTextSecondary;
        } else if (status == SyncManager.Status.OK) {
            text = "●  Hoạt động";
            color = R.color.colorSuccess;
        } else if (status == SyncManager.Status.OFFLINE) {
            text = "●  Mất kết nối";
            color = R.color.colorGold;
        } else {
            text = "●  Lỗi máy chủ";
            color = R.color.colorDanger;
        }
        tvStatSystem.setText(text);
        tvStatSystem.setTextColor(ContextCompat.getColor(this, color));
    }

    /**
     * @param reports null = chưa tải được từ Firebase (giữ nguyên số liệu vi phạm đang hiển thị)
     */
    private void showDashboard(List<PostModel> posts, List<UserModel> users, List<SyncManager.ReportedPost> reports) {
        // Thống kê người dùng: chỉ đếm thành viên (không tính tài khoản quản trị)
        List<UserModel> members = new ArrayList<>();
        int lockedCount = 0;
        for (UserModel u : users) {
            if (u.isAdmin()) continue;
            members.add(u);
            if (u.isLocked()) lockedCount++;
        }

        if (tvStatPosts != null) tvStatPosts.setText(String.valueOf(posts.size()));
        if (tvStatUsers != null) {
            tvStatUsers.setText(lockedCount > 0 ? members.size() + " (" + lockedCount + " khóa)" : String.valueOf(members.size()));
        }

        postList.clear();
        postList.addAll(posts);

        if (reports != null) {
            reportInfo.clear();
            reportList.clear();
            for (SyncManager.ReportedPost r : reports) {
                reportInfo.put(r.post.getId(), r);
                reportList.add(r.post);
            }
            if (tvStatReports != null) tvStatReports.setText(String.valueOf(reportList.size()));
        }
        postAdapter.notifyDataSetChanged();
        reportAdapter.notifyDataSetChanged();

        allUserList.clear();
        allUserList.addAll(members);
        String currentQuery = etAdminSearchUsers != null ? etAdminSearchUsers.getText().toString() : "";
        filterUsers(currentQuery);

        updateEmptyState();
    }

    /** Thao tác quản trị cần mạng: báo kết quả chung. */
    private SyncManager.Callback adminResult(String successMsg) {
        return (status, more) -> {
            if (isFinishing() || isDestroyed()) return;
            if (status == SyncManager.Status.OK) {
                Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show();
                loadDashboardData();
            } else if (status == SyncManager.Status.OFFLINE) {
                Toast.makeText(this, "Cần kết nối mạng để thực hiện thao tác quản trị!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Thao tác thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void filterUsers(String query) {
        userList.clear();
        String q = query != null ? query.trim().toLowerCase() : "";
        if (q.isEmpty()) {
            userList.addAll(allUserList);
        } else {
            for (UserModel u : allUserList) {
                if (u.getUsername().toLowerCase().contains(q) ||
                    u.getFullName().toLowerCase().contains(q)) {
                    userList.add(u);
                }
            }
        }
        if (userAdapter != null) userAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void setupTabs() {
        tabPosts.setOnClickListener(v -> selectTab(tabPosts));
        tabUsers.setOnClickListener(v -> selectTab(tabUsers));
        tabReports.setOnClickListener(v -> selectTab(tabReports));
    }

    private void selectTab(TextView selected) {
        for (TextView tab : new TextView[]{tabPosts, tabUsers, tabReports}) {
            if (tab == selected) {
                tab.setBackgroundResource(R.drawable.bg_admin_tab_selected);
                tab.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
                tab.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tab.setBackground(null);
                tab.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
                tab.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        rvPosts.setVisibility(selected == tabPosts ? View.VISIBLE : View.GONE);
        layoutAdminUsersContainer.setVisibility(selected == tabUsers ? View.VISIBLE : View.GONE);
        rvReports.setVisibility(selected == tabReports ? View.VISIBLE : View.GONE);
        updateEmptyState();
    }

    private void showAdminLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null, false);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_logout_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_logout_msg);
        View btnCancel = dialogView.findViewById(R.id.btn_dialog_logout_cancel);
        View btnConfirm = dialogView.findViewById(R.id.btn_dialog_logout_confirm);

        if (tvTitle != null) tvTitle.setText("Đăng xuất quản trị");
        if (tvMsg != null) tvMsg.setText("Bạn có chắc chắn muốn đăng xuất khỏi phiên làm việc Quản trị viên?");

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                AuthManager.signOut(this);
                Toast.makeText(this, "Đã đăng xuất tài khoản Quản trị!", Toast.LENGTH_SHORT).show();
                android.content.Intent intent = new android.content.Intent(this, LoginActivity.class);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        dialog.show();
    }

    private void updateEmptyState() {
        if (tvEmpty == null) return;
        if (rvPosts.getVisibility() == View.VISIBLE) {
            tvEmpty.setVisibility(postList.isEmpty() ? View.VISIBLE : View.GONE);
            tvEmpty.setText("Chưa có bài hát nào trên hệ thống");
        } else if (layoutAdminUsersContainer.getVisibility() == View.VISIBLE) {
            tvEmpty.setVisibility(userList.isEmpty() ? View.VISIBLE : View.GONE);
            tvEmpty.setText("Không tìm thấy người dùng phù hợp");
        } else {
            tvEmpty.setVisibility(reportList.isEmpty() ? View.VISIBLE : View.GONE);
            tvEmpty.setText("Không có bài viết vi phạm nào cần xử lý");
        }
    }

    /**
     * Dialog xác nhận thao tác kiểm duyệt, cùng giao diện với dialog đăng xuất.
     * @param danger true = thao tác nguy hiểm (nền icon + nút xác nhận màu đỏ), false = màu tím chủ đạo
     */
    private void showAdminConfirmDialog(int iconRes, boolean danger, String title, String message,
                                        String confirmText, Runnable onConfirm) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_confirm, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View iconBg = dialogView.findViewById(R.id.layout_dialog_admin_icon);
        android.widget.ImageView ivIcon = dialogView.findViewById(R.id.iv_dialog_admin_icon);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_admin_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_admin_msg);
        TextView btnCancel = dialogView.findViewById(R.id.btn_dialog_admin_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_dialog_admin_confirm);

        iconBg.setBackgroundResource(danger ? R.drawable.bg_icon_danger : R.drawable.bg_icon_tonal);
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(ContextCompat.getColor(this, danger ? R.color.colorDanger : R.color.colorAccent));
        tvTitle.setText(title);
        tvMsg.setText(message);
        btnConfirm.setText(confirmText);
        btnConfirm.setBackgroundResource(danger ? R.drawable.bg_button_danger : R.drawable.bg_gradient_button_purple);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });
        dialog.show();
    }

    private void showDeletePostConfirmDialog(PostModel post) {
        showAdminConfirmDialog(R.drawable.ic_delete, true,
                "Xóa bài viết?",
                "Bạn có chắc chắn muốn xóa bài hát \"" + post.getSongTitle() + " - " + post.getArtistName()
                        + "\" khỏi hệ thống không? Bình luận, lượt thích và báo cáo của bài cũng bị xóa.",
                "Xóa ngay",
                () -> sync.adminDeletePost(post.getId(), adminResult("Đã xóa bài hát khỏi hệ thống thành công!")));
    }

    /** Ẩn bài vi phạm khỏi cộng đồng (hoặc hiện lại bài đã ẩn). */
    private void confirmToggleHidden(PostModel post) {
        boolean hide = !post.isHiddenByAdmin();
        showAdminConfirmDialog(hide ? R.drawable.ic_lock : R.drawable.ic_public, hide,
                hide ? "Ẩn bài viết vi phạm?" : "Hiện lại bài viết?",
                hide
                        ? "Bài \"" + post.getSongTitle() + "\" sẽ không còn hiển thị với thành viên. Các báo cáo của bài được đánh dấu đã xử lý."
                        : "Bài \"" + post.getSongTitle() + "\" sẽ hiển thị lại trên bảng tin cộng đồng.",
                hide ? "Ẩn bài" : "Hiện lại",
                () -> sync.adminSetPostHidden(post.getId(), hide,
                        adminResult(hide ? "Đã ẩn bài viết vi phạm" : "Đã hiện lại bài viết")));
    }

    /** Bài không vi phạm: bỏ qua (xóa) các báo cáo của bài. */
    private void confirmDismissReports(PostModel post) {
        showAdminConfirmDialog(R.drawable.ic_check, false,
                "Bỏ qua báo cáo?",
                "Xác nhận bài \"" + post.getSongTitle() + "\" không vi phạm và đóng các báo cáo của bài?",
                "Bỏ qua",
                () -> sync.adminDismissReports(post.getId(), adminResult("Đã bỏ qua báo cáo của bài viết")));
    }

    private void toggleUserLock(UserModel user) {
        if (user.isAdmin()) {
            Toast.makeText(this, "Không thể khóa tài khoản Quản trị viên!", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean willLock = !user.isLocked();
        String actionTitle = willLock ? "Khóa tài khoản?" : "Mở khóa tài khoản?";
        String actionMsg = willLock
                ? "Bạn có chắc chắn muốn khóa tài khoản @" + user.getUsername() + " (" + user.getFullName() + ")? Người dùng này sẽ bị chặn đăng nhập và vô hiệu hóa hoạt động."
                : "Xác nhận mở khóa tài khoản @" + user.getUsername() + " (" + user.getFullName() + ")? Người dùng sẽ có thể đăng nhập trở lại.";
        String confirmText = willLock ? "Khóa ngay" : "Mở khóa";

        showAdminConfirmDialog(R.drawable.ic_lock, willLock, actionTitle, actionMsg, confirmText,
                () -> sync.adminSetLocked(user.getUsername(), willLock,
                        adminResult("Đã " + (willLock ? "khóa" : "mở khóa") + " tài khoản @" + user.getUsername())));
    }

    @Override
    protected int getCheckedItem() {
        return -1;
    }

    // --- Adapter cho Quản lý bài hát & Bài viết vi phạm ---
    public static class AdminPostAdapter extends RecyclerView.Adapter<AdminPostAdapter.ViewHolder> {
        public interface OnAdminPostActionListener {
            void onViewPost(PostModel post);
            void onPlayPost(PostModel post);
            void onToggleHidden(PostModel post);
            void onDismissReports(PostModel post);
            void onDeletePost(PostModel post);
        }

        private final Context context;
        private final List<PostModel> list;
        private final Map<String, SyncManager.ReportedPost> reportInfo;
        /** true = tab Bài viết vi phạm (hiện lý do báo cáo và nút Bỏ qua). */
        private final boolean reportMode;
        private final OnAdminPostActionListener listener;

        public AdminPostAdapter(Context context, List<PostModel> list, Map<String, SyncManager.ReportedPost> reportInfo,
                                boolean reportMode, OnAdminPostActionListener listener) {
            this.context = context;
            this.list = list;
            this.reportInfo = reportInfo;
            this.reportMode = reportMode;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_post, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PostModel post = list.get(position);
            holder.tvAuthor.setText("@" + post.getAuthorUsername() + " • " + DatabaseHelper.formatTimeAgo(post.getCreatedAt()));
            holder.tvTitle.setText(post.getSongTitle());
            holder.tvArtist.setText(post.getArtistName());
            holder.tvCaption.setText(post.getCaption());
            holder.tvCaption.setVisibility(post.getCaption() == null || post.getCaption().trim().isEmpty() ? View.GONE : View.VISIBLE);

            // Trạng thái kiểm duyệt
            SyncManager.ReportedPost report = reportInfo.get(post.getId());
            String moderation = null;
            if (post.isHiddenByAdmin()) {
                moderation = "Đang bị ẩn khỏi cộng đồng";
            } else if (report != null) {
                moderation = report.reportCount + " lượt báo cáo vi phạm"
                        + (report.reasons.isEmpty() ? "" : " • " + report.reasons);
            }
            holder.tvModeration.setVisibility(moderation != null ? View.VISIBLE : View.GONE);
            if (moderation != null) holder.tvModeration.setText(moderation);

            holder.btnHide.setText(post.isHiddenByAdmin() ? "Hiện lại" : "Ẩn bài");
            holder.btnDismiss.setVisibility(reportMode ? View.VISIBLE : View.GONE);

            holder.btnView.setOnClickListener(v -> listener.onViewPost(post));
            holder.btnPlay.setOnClickListener(v -> listener.onPlayPost(post));
            holder.btnHide.setOnClickListener(v -> listener.onToggleHidden(post));
            holder.btnDismiss.setOnClickListener(v -> listener.onDismissReports(post));
            holder.btnDelete.setOnClickListener(v -> listener.onDeletePost(post));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvAuthor, tvTitle, tvArtist, tvCaption, tvModeration;
            final TextView btnView, btnPlay, btnHide, btnDismiss, btnDelete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAuthor = itemView.findViewById(R.id.tv_admin_post_author);
                tvTitle = itemView.findViewById(R.id.tv_admin_post_title);
                tvArtist = itemView.findViewById(R.id.tv_admin_post_artist);
                tvCaption = itemView.findViewById(R.id.tv_admin_post_caption);
                tvModeration = itemView.findViewById(R.id.tv_admin_post_moderation);
                btnView = itemView.findViewById(R.id.btn_admin_view_post);
                btnPlay = itemView.findViewById(R.id.btn_admin_play_post);
                btnHide = itemView.findViewById(R.id.btn_admin_hide_post);
                btnDismiss = itemView.findViewById(R.id.btn_admin_dismiss_report);
                btnDelete = itemView.findViewById(R.id.btn_admin_delete_post);
            }
        }
    }

    // --- Adapter cho Quản lý người dùng (thành viên) ---
    public static class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {
        public interface OnToggleLockListener {
            void onToggleLock(UserModel user);
        }

        private final Context context;
        private final List<UserModel> list;
        private final OnToggleLockListener listener;

        public AdminUserAdapter(Context context, List<UserModel> list, OnToggleLockListener listener) {
            this.context = context;
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserModel user = list.get(position);
            holder.tvFullName.setText(user.getFullName());
            holder.tvUsername.setText("@" + user.getUsername());

            if (user.isLocked()) {
                holder.tvStatus.setText("Đã khóa");
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.colorDanger));
                holder.btnToggleLock.setText("Mở khóa");
                holder.btnToggleLock.setBackgroundResource(R.drawable.bg_admin_btn_green);
            } else {
                holder.tvStatus.setText("Đang hoạt động");
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
                holder.btnToggleLock.setText("Khóa");
                holder.btnToggleLock.setBackgroundResource(R.drawable.bg_admin_btn_red);
            }
            holder.btnToggleLock.setOnClickListener(v -> listener.onToggleLock(user));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvFullName, tvUsername, tvStatus, btnToggleLock;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFullName = itemView.findViewById(R.id.tv_admin_user_fullname);
                tvUsername = itemView.findViewById(R.id.tv_admin_user_username);
                tvStatus = itemView.findViewById(R.id.tv_admin_user_status);
                btnToggleLock = itemView.findViewById(R.id.btn_admin_toggle_lock);
            }
        }
    }
}
