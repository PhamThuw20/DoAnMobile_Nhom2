package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Trang cá nhân người dùng: đọc SQLite trước, đồng bộ Firebase sau, phát nhạc qua MusicService.
 * Tự động đồng bộ Danh sách nhạc đã lưu, bài viết của tôi, và danh sách followers/following.
 */
public class ProfileActivity extends BaseActivity {

    public static final String EXTRA_USERNAME = "extra_username";
    private String currentUsername = "";
    private String targetUsername = "";
    private boolean isOwnProfile = true;
    private boolean isTargetFollowed = false;

    private static final int TAB_MY_POSTS = 0;
    private static final int TAB_LIKED = 1;
    private static final int TAB_SAVED = 2;
    private int currentTab = TAB_MY_POSTS;

    private TextView tvName, tvBio, tvUsername;
    private TextView tvStatPosts, tvStatFollowers, tvStatFollowing;
    private View btnStatPosts, btnStatFollowers, btnStatFollowing;
    private TextView tabMyPosts, tabLiked, tabSaved;
    private ImageView ivProfileBack;
    private androidx.appcompat.widget.AppCompatButton btnEditProfile, btnLogout;

    private RecyclerView rvProfilePosts;
    private TextView tvProfileEmpty;
    private PostAdapter postAdapter;
    private final List<PostModel> postList = new ArrayList<>();

    // Danh sách nhạc đã lưu (chỉ hồ sơ của mình): các bản nhạc dạng gọn, phát trong đúng phạm vi danh sách
    static final String SAVED_QUEUE_NAME = "Danh sách nhạc đã lưu";
    private View layoutSavedTracks;
    private TextView tvSavedCount;
    private TrackListAdapter savedAdapter;
    private final List<PostModel> savedTracks = new ArrayList<>();
    /** Hồ sơ đang xem là tài khoản quản trị hệ thống (không phải thành viên mạng xã hội). */
    private boolean isTargetAdmin = false;

    private DatabaseHelper dbHelper;
    private int currentFollowingCount = 0;
    private AlertDialog activeFollowDialog;
    private FollowUserAdapter activeFollowAdapter;
    private TextView activeDialogTitle;
    private boolean isActiveDialogFollowingList = false;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_profile);

        String loggedInUser = SessionManager.getCurrentUsername(this);
        currentUsername = loggedInUser != null ? loggedInUser : "";
        targetUsername = resolveTargetUsername(getIntent());
        isOwnProfile = targetUsername.equalsIgnoreCase(currentUsername);

        dbHelper = DatabaseHelper.getInstance(this);
        isTargetAdmin = !isOwnProfile && dbHelper.getUser(targetUsername).isAdmin();

        initViews();
        setupRecyclerView();
        loadUserProfile();
        setupListeners();
        switchTab(TAB_MY_POSTS);

        // Hiển thị ngay bản đã lưu trong SQLite, rồi tải bản mới nhất từ Firebase
        dbHelper.markFeedViewed(DatabaseHelper.feedUser(targetUsername));
        swipeRefresh = findViewById(R.id.swipe_refresh_profile);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorAccent, R.color.colorNeonPink);
            swipeRefresh.setOnRefreshListener(this::reloadFromServer);
        }
        reloadFromServer();
    }

    /** Tải hồ sơ, bài đã đăng, người theo dõi (và bài đã thích / đã lưu nếu là hồ sơ của mình) từ Firebase. */
    private void reloadFromServer() {
        SyncManager.get(this).refreshProfile(targetUsername, (status, more) -> {
            if (isFinishing() || isDestroyed()) return;
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            onLocalDataChanged();
        });
    }

    @Override
    protected void onLocalDataChanged() {
        loadUserProfile();
        loadTabPosts();
        if (!isOwnProfile && !isTargetAdmin) {
            // Lượt theo dõi bị xóa / thêm trên Firebase (realtime) -> nút đổi trạng thái ngay
            isTargetFollowed = dbHelper.isUserFollowed(currentUsername, targetUsername);
            updateTargetFollowButtonUi();
        }
        refreshActiveFollowDialog();
    }

    @Override
    protected void onReconnectedRefresh() {
        reloadFromServer();
    }

    /** Username cần hiển thị: lấy từ Intent, mặc định là người đang đăng nhập. */
    private String resolveTargetUsername(Intent intent) {
        String extra = intent != null ? intent.getStringExtra(EXTRA_USERNAME) : null;
        if (extra != null && !extra.trim().isEmpty()) {
            return extra.replace("@", "").trim();
        }
        return currentUsername;
    }

    /**
     * Khi bấm tab "Hồ sơ" (FLAG_ACTIVITY_REORDER_TO_FRONT) mà màn hình đang hiển thị hồ sơ người khác,
     * mở lại đúng hồ sơ của người đang đăng nhập thay vì tái sử dụng trang cũ.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String newTarget = resolveTargetUsername(intent);
        if (!newTarget.equalsIgnoreCase(targetUsername)) {
            Intent fresh = new Intent(this, ProfileActivity.class);
            fresh.putExtra(EXTRA_USERNAME, newTarget);
            startActivity(fresh);
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
        } else {
            setIntent(intent);
        }
    }

    @Override
    protected boolean isNavRootScreen() {
        return isOwnProfile;
    }

    private void initViews() {
        tvName = findViewById(R.id.tv_name);
        tvBio = findViewById(R.id.tv_bio);
        tvUsername = findViewById(R.id.tv_username);
        tvStatPosts = findViewById(R.id.tv_stat_posts);
        tvStatFollowers = findViewById(R.id.tv_stat_followers);
        tvStatFollowing = findViewById(R.id.tv_stat_following);

        btnStatPosts = findViewById(R.id.btn_stat_posts);
        btnStatFollowers = findViewById(R.id.btn_stat_followers);
        btnStatFollowing = findViewById(R.id.btn_stat_following);

        tabMyPosts = findViewById(R.id.tab_profile_my_posts);
        tabLiked = findViewById(R.id.tab_profile_liked);
        tabSaved = findViewById(R.id.tab_profile_saved);

        rvProfilePosts = findViewById(R.id.rv_profile_posts);
        tvProfileEmpty = findViewById(R.id.tv_profile_empty);
        layoutSavedTracks = findViewById(R.id.layout_profile_saved_tracks);
        tvSavedCount = findViewById(R.id.tv_saved_tracks_count);

        ivProfileBack = findViewById(R.id.iv_profile_back);
        btnEditProfile = findViewById(R.id.btn_edit_profile);
        btnLogout = findViewById(R.id.btn_logout);

        if (!isOwnProfile && ivProfileBack != null) {
            ivProfileBack.setVisibility(View.VISIBLE);
            ivProfileBack.setOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        rvProfilePosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new PostAdapter(this, postList, post -> {
            if (MusicService.isPlaying && post.getId().equals(MusicService.currentPostId)) {
                MusicService.toggle(this);
            } else {
                MusicService.play(this, post);
                MusicService.announce(this, post);
            }
        });
        rvProfilePosts.setAdapter(postAdapter);

        RecyclerView rvSaved = findViewById(R.id.rv_profile_saved_tracks);
        if (rvSaved != null) {
            rvSaved.setLayoutManager(new LinearLayoutManager(this));
            savedAdapter = new TrackListAdapter(this, savedTracks, (track, position) -> playSavedTrack(position));
            savedAdapter.setOnTrackLongClickListener((track, position) -> showSavedTrackOptions(track));
            rvSaved.setAdapter(savedAdapter);
        }
        View btnPlayAll = findViewById(R.id.btn_saved_play_all);
        if (btnPlayAll != null) {
            btnPlayAll.setOnClickListener(v -> {
                if (savedTracks.isEmpty()) return;
                MusicService.playQueue(this, SAVED_QUEUE_NAME, savedTracks, 0);
                if (!MusicService.isOfflineStream(this, savedTracks.get(0))) {
                    Toast.makeText(this, "Đang phát toàn bộ " + SAVED_QUEUE_NAME + " (" + savedTracks.size() + " bài)",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Bấm một bài trong Danh sách nhạc đã lưu: phát từ bài đó, các bài tiếp theo chỉ lấy trong danh sách đã lưu.
     * Bấm lại bài đang phát thì tạm dừng / phát tiếp.
     */
    private void playSavedTrack(int position) {
        PostModel track = savedTracks.get(position);
        if (MusicService.hasTrackLoaded && track.getId().equals(MusicService.currentPostId)
                && MusicService.isPlayingQueue(SAVED_QUEUE_NAME)) {
            MusicService.toggle(this);
            return;
        }
        MusicService.playQueue(this, SAVED_QUEUE_NAME, savedTracks, position);
        MusicService.announce(this, track);
    }

    /** Nhấn giữ một bản nhạc đã lưu: mở bài viết gốc hoặc bỏ khỏi danh sách. */
    private void showSavedTrackOptions(PostModel track) {
        String[] options = {"Mở bài viết gốc", "Bỏ khỏi " + SAVED_QUEUE_NAME};
        new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle(track.getSongTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, PostDetailActivity.class);
                        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, track.getId());
                        startActivity(intent);
                    } else {
                        dbHelper.updatePostBookmark(track, false);
                        Toast.makeText(this, "Đã bỏ \"" + track.getSongTitle() + "\" khỏi " + SAVED_QUEUE_NAME,
                                Toast.LENGTH_SHORT).show();
                        loadTabPosts();
                    }
                })
                .show();
    }

    private void setupListeners() {
        // Nút Cài đặt: chỉ hiện trên hồ sơ của chính mình
        View ivSettings = findViewById(R.id.iv_settings);
        if (ivSettings != null) {
            ivSettings.setVisibility(isOwnProfile ? View.VISIBLE : View.GONE);
            if (isOwnProfile) {
                ivSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
            }
        }

        if (isOwnProfile) {
            // Nút Sửa hồ sơ của tài khoản chính
            if (btnEditProfile != null) {
                btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
            }

            // Nút Đăng xuất tài khoản chính
            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> showLogoutConfirmDialog());
            }
        } else {
            // Khi xem hồ sơ của người dùng khác (từ danh sách followers/following hoặc bài viết)
            if (btnLogout != null) {
                btnLogout.setVisibility(View.GONE);
            }
            if (btnEditProfile != null && isTargetAdmin) {
                // Tài khoản quản trị hệ thống không phải thành viên mạng xã hội: không theo dõi được
                btnEditProfile.setVisibility(View.GONE);
            } else if (btnEditProfile != null) {
                btnEditProfile.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                isTargetFollowed = dbHelper.isUserFollowed(currentUsername, targetUsername);
                updateTargetFollowButtonUi();
                btnEditProfile.setOnClickListener(v -> {
                    if (isTargetFollowed) {
                        dbHelper.unfollowUser(currentUsername, targetUsername);
                        isTargetFollowed = false;
                        updateTargetFollowButtonUi();
                        adjustFollowersCountUi(-1);
                        Toast.makeText(this, "Đã bỏ theo dõi @" + targetUsername, Toast.LENGTH_SHORT).show();
                    } else {
                        dbHelper.followUser(currentUsername, targetUsername);
                        isTargetFollowed = true;
                        updateTargetFollowButtonUi();
                        adjustFollowersCountUi(1);
                        Toast.makeText(this, "Đã theo dõi @" + targetUsername, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        // Chuyển Tab
        tabMyPosts.setOnClickListener(v -> switchTab(TAB_MY_POSTS));
        if (tabLiked != null) {
            tabLiked.setOnClickListener(v -> switchTab(TAB_LIKED));
        }
        tabSaved.setOnClickListener(v -> switchTab(TAB_SAVED));

        // Click thống kê
        if (btnStatPosts != null) {
            btnStatPosts.setOnClickListener(v -> switchTab(TAB_MY_POSTS));
        }
        if (btnStatFollowers != null) {
            btnStatFollowers.setOnClickListener(v -> showFollowersDialog());
        }
        if (btnStatFollowing != null) {
            btnStatFollowing.setOnClickListener(v -> showFollowingDialog());
        }
    }

    private void switchTab(int tab) {
        currentTab = tab;
        int activeColor = ContextCompat.getColor(this, R.color.colorAccent);
        int inactiveColor = ContextCompat.getColor(this, R.color.colorTextSecondary);

        tabMyPosts.setTextColor(tab == TAB_MY_POSTS ? activeColor : inactiveColor);
        if (tabLiked != null) {
            tabLiked.setTextColor(tab == TAB_LIKED ? activeColor : inactiveColor);
        }
        if (tabSaved != null) {
            tabSaved.setTextColor(tab == TAB_SAVED ? activeColor : inactiveColor);
        }

        loadTabPosts();
    }

    private void loadTabPosts() {
        postList.clear();

        List<PostModel> myPosts = dbHelper.getUserPosts(targetUsername);
        if (!isOwnProfile) {
            // Bài riêng tư chỉ chủ bài viết mới xem được
            List<PostModel> publicPosts = new ArrayList<>();
            for (PostModel p : myPosts) {
                if (!p.isPrivate()) publicPosts.add(p);
            }
            myPosts = publicPosts;
        }

        // Nếu là nghệ sĩ / thành viên khác mà chưa có bài trực tiếp theo username, tìm theo tên nghệ sĩ
        if (myPosts.isEmpty() && !isOwnProfile) {
            String fullNameTarget = tvName != null ? tvName.getText().toString().toLowerCase() : "";
            for (PostModel p : dbHelper.getAllPosts()) {
                if (p.isPrivate()) continue;
                if (p.getAuthorUsername().equalsIgnoreCase(targetUsername) ||
                    (!fullNameTarget.isEmpty() && p.getArtistName().toLowerCase().contains(fullNameTarget))) {
                    myPosts.add(p);
                }
            }
        }

        if (isTargetAdmin) myPosts = new ArrayList<>();
        List<PostModel> likedPosts = isOwnProfile ? dbHelper.getLikedPosts(currentUsername) : new ArrayList<>();
        savedTracks.clear();
        if (isOwnProfile) savedTracks.addAll(dbHelper.getSavedTracks(currentUsername));

        // Cập nhật số lượng trên các tab
        tabMyPosts.setText("Bài đã chia sẻ (" + myPosts.size() + ")");
        if (isOwnProfile) {
            if (tabLiked != null) {
                tabLiked.setVisibility(View.VISIBLE);
                tabLiked.setText("Bài đã thích (" + likedPosts.size() + ")");
            }
            if (tabSaved != null) {
                tabSaved.setVisibility(View.VISIBLE);
                tabSaved.setText(SAVED_QUEUE_NAME + " (" + savedTracks.size() + ")");
            }
        } else {
            if (tabLiked != null) tabLiked.setVisibility(View.GONE);
            if (tabSaved != null) tabSaved.setVisibility(View.GONE);
        }

        boolean showSaved = isOwnProfile && currentTab == TAB_SAVED;
        if (layoutSavedTracks != null) layoutSavedTracks.setVisibility(showSaved && !savedTracks.isEmpty() ? View.VISIBLE : View.GONE);
        if (tvSavedCount != null) tvSavedCount.setText(savedTracks.size() + " bài hát");
        if (savedAdapter != null) savedAdapter.notifyDataSetChanged();

        if (currentTab == TAB_MY_POSTS || !isOwnProfile) {
            postList.addAll(myPosts);
            if (myPosts.isEmpty()) {
                tvProfileEmpty.setVisibility(View.VISIBLE);
                tvProfileEmpty.setText(isOwnProfile 
                        ? "Bạn chưa đăng bài viết nào.\nHãy nhấn nút Tạo bài (+) ở thanh điều hướng để chia sẻ giai điệu!"
                        : isTargetAdmin
                        ? "Đây là tài khoản quản trị hệ thống MelodyVerse, không đăng bài chia sẻ âm nhạc."
                        : "@" + targetUsername + " chưa chia sẻ bài viết nào trên MelodyVerse.");
                rvProfilePosts.setVisibility(View.GONE);
            } else {
                tvProfileEmpty.setVisibility(View.GONE);
                rvProfilePosts.setVisibility(View.VISIBLE);
            }
        } else if (currentTab == TAB_LIKED) {
            postList.addAll(likedPosts);
            if (likedPosts.isEmpty()) {
                tvProfileEmpty.setVisibility(View.VISIBLE);
                tvProfileEmpty.setText("Bạn chưa thích bài viết nào.\nHãy nhấn biểu tượng Trái tim trên bảng tin để lưu lại các bài hát yêu thích!");
                rvProfilePosts.setVisibility(View.GONE);
            } else {
                tvProfileEmpty.setVisibility(View.GONE);
                rvProfilePosts.setVisibility(View.VISIBLE);
            }
        } else {
            // Danh sách nhạc đã lưu hiển thị dạng bản nhạc (TrackListAdapter), không hiện bài viết
            rvProfilePosts.setVisibility(View.GONE);
            if (savedTracks.isEmpty()) {
                tvProfileEmpty.setVisibility(View.VISIBLE);
                tvProfileEmpty.setText("Danh sách nhạc đã lưu đang trống.\nHãy nhấn nút Lưu trên bài viết ở Bảng tin để lưu bài hát bạn muốn nghe lại!");
            } else {
                tvProfileEmpty.setVisibility(View.GONE);
            }
        }

        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }

        // Cập nhật số bài viết trên header
        if (tvStatPosts != null) {
            tvStatPosts.setText(String.valueOf(myPosts.size()));
        }
    }

    private void loadUserProfile() {
        UserModel user = dbHelper.getUser(targetUsername);
        if (user != null) {
            tvName.setText(user.getFullName());
            tvBio.setText(user.getBio());
            tvUsername.setText("@" + user.getUsername());
            currentFollowingCount = user.getFollowingCount();
            tvStatFollowing.setText(String.valueOf(currentFollowingCount));

            int followers = user.getFollowersCount();
            tvStatFollowers.setText(followers >= 1000 ? String.format("%.2fK", followers / 1000.0) : String.valueOf(followers));
        }
    }

    private void showFollowersDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_follow_list, null, false);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        // Nền cửa sổ trong suốt để khung bo góc của layout hiển thị trọn vẹn
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_follow_dialog_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_follow_dialog_subtitle);
        View btnClose = dialogView.findViewById(R.id.btn_close_follow);
        RecyclerView rvList = dialogView.findViewById(R.id.rv_follow_list);

        List<UserModel> followers = dbHelper.getFollowerUsers(targetUsername);
        if (tvTitle != null) {
            tvTitle.setText("Người theo dõi (" + tvStatFollowers.getText() + ")");
        }
        if (tvSubtitle != null) {
            tvSubtitle.setText(isOwnProfile ? "Các tài khoản đang theo dõi bạn trên MelodyVerse" : "Các tài khoản đang theo dõi @" + targetUsername);
        }

        FollowUserAdapter adapter = new FollowUserAdapter(this, followers, false, delta -> {
            if (isOwnProfile) {
                currentFollowingCount = dbHelper.getFollowingCount(currentUsername);
                tvStatFollowing.setText(String.valueOf(currentFollowingCount));
            }
        });

        if (rvList != null) {
            rvList.setLayoutManager(new LinearLayoutManager(this));
            rvList.setAdapter(adapter);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        activeFollowDialog = dialog;
        activeFollowAdapter = adapter;
        activeDialogTitle = tvTitle;
        isActiveDialogFollowingList = false;

        dialog.setOnDismissListener(d -> {
            activeFollowDialog = null;
            activeFollowAdapter = null;
            activeDialogTitle = null;
            loadUserProfile();
        });

        dialog.show();
        applyFollowDialogSize(dialog);
    }

    private void showFollowingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_follow_list, null, false);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        // Nền cửa sổ trong suốt để khung bo góc của layout hiển thị trọn vẹn
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_follow_dialog_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_follow_dialog_subtitle);
        View btnClose = dialogView.findViewById(R.id.btn_close_follow);
        RecyclerView rvList = dialogView.findViewById(R.id.rv_follow_list);

        List<UserModel> followings = dbHelper.getFollowingUsers(targetUsername);
        currentFollowingCount = followings.size();
        if (tvTitle != null) {
            tvTitle.setText("Đang theo dõi (" + currentFollowingCount + ")");
        }
        if (tvSubtitle != null) {
            tvSubtitle.setText(isOwnProfile ? "Các nghệ sĩ & bạn bè bạn đang theo dõi" : "Các tài khoản @" + targetUsername + " đang theo dõi");
        }

        FollowUserAdapter adapter = new FollowUserAdapter(this, followings, true, delta -> {
            if (!isOwnProfile) return;
            currentFollowingCount = dbHelper.getFollowingCount(currentUsername);
            tvStatFollowing.setText(String.valueOf(currentFollowingCount));
            if (tvTitle != null) {
                tvTitle.setText("Đang theo dõi (" + currentFollowingCount + ")");
            }
        });

        if (rvList != null) {
            rvList.setLayoutManager(new LinearLayoutManager(this));
            rvList.setAdapter(adapter);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        activeFollowDialog = dialog;
        activeFollowAdapter = adapter;
        activeDialogTitle = tvTitle;
        isActiveDialogFollowingList = true;

        dialog.setOnDismissListener(d -> {
            activeFollowDialog = null;
            activeFollowAdapter = null;
            activeDialogTitle = null;
            loadUserProfile();
        });

        dialog.show();
        applyFollowDialogSize(dialog);
    }

    /**
     * Ép chiều rộng cửa sổ hộp thoại Người theo dõi / Đang theo dõi giãn theo bề ngang màn hình ngay khi vừa mở
     * (nền trong suốt làm mất khung mặc định, nếu không đặt cửa sổ sẽ co theo nội dung ban đầu).
     */
    private void applyFollowDialogSize(AlertDialog dialog) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (int) (dm.widthPixels * 0.92f);
        window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(android.view.Gravity.CENTER);
    }

    private void updateTargetFollowButtonUi() {
        if (btnEditProfile == null) return;
        btnEditProfile.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (isTargetFollowed) {
            btnEditProfile.setText("Đang theo dõi");
            btnEditProfile.setBackgroundResource(R.drawable.bg_input_field);
            btnEditProfile.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
        } else {
            btnEditProfile.setText("+ Theo dõi");
            btnEditProfile.setBackgroundResource(R.drawable.bg_gradient_button);
            btnEditProfile.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void adjustFollowersCountUi(int delta) {
        if (tvStatFollowers == null) return;
        try {
            String text = tvStatFollowers.getText().toString().replace(".", "").replace(",", "").trim();
            if (text.endsWith("K") || text.endsWith("k") || text.endsWith("M") || text.endsWith("m")) {
                return;
            }
            int count = Integer.parseInt(text) + delta;
            if (count < 0) count = 0;
            tvStatFollowers.setText(String.valueOf(count));
        } catch (Exception ignored) {
        }
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog);
        builder.setTitle("Chỉnh Sửa Hồ Sơ Cá Nhân");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null, false);
        builder.setView(dialogView);

        EditText etNewName = dialogView.findViewById(R.id.et_edit_name);
        EditText etNewBio = dialogView.findViewById(R.id.et_edit_bio);

        if (etNewName != null) etNewName.setText(tvName.getText());
        if (etNewBio != null) etNewBio.setText(tvBio.getText());

        builder.setPositiveButton("Lưu thay đổi", (dialog, which) -> {
            String newName = etNewName != null ? etNewName.getText().toString().trim() : "";
            String newBio = etNewBio != null ? etNewBio.getText().toString().trim() : "";

            if (!newName.isEmpty()) {
                dbHelper.updateUserProfile(currentUsername, newName, newBio);
                tvName.setText(newName);
                tvBio.setText(newBio);
                Toast.makeText(this, SyncManager.get(this).isOnline()
                        ? "Đã lưu cập nhật hồ sơ!"
                        : "Đã lưu hồ sơ trên máy, sẽ tự đồng bộ khi có mạng", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showLogoutConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null, false);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View btnCancel = dialogView.findViewById(R.id.btn_dialog_logout_cancel);
        View btnConfirm = dialogView.findViewById(R.id.btn_dialog_logout_confirm);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                // Còn thao tác chưa đồng bộ: đăng xuất bây giờ sẽ mất các thao tác đó
                int pending = dbHelper.getPendingCount();
                if (pending > 0) {
                    new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                            .setTitle("Còn dữ liệu chưa đồng bộ")
                            .setMessage("Bạn còn " + pending + " thao tác (bình luận, lượt thích, bài đăng...) chưa được gửi lên máy chủ vì đang mất mạng."
                                    + "\n\nNếu đăng xuất bây giờ, các thao tác này sẽ bị mất. Hãy kết nối mạng để ứng dụng tự đồng bộ trước khi đăng xuất.")
                            .setPositiveButton("Ở lại", null)
                            .setNegativeButton("Vẫn đăng xuất", (d, w) -> performLogout())
                            .show();
                } else {
                    performLogout();
                }
            });
        }

        dialog.show();
    }

    /** Thoát Firebase Auth và xóa dữ liệu riêng của tài khoản khỏi SQLite. */
    private void performLogout() {
        AuthManager.signOut(this);
        Toast.makeText(this, "Đã đăng xuất khỏi MelodyVerse!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void refreshActiveFollowDialog() {
        if (activeFollowDialog != null && activeFollowDialog.isShowing() && activeFollowAdapter != null) {
            if (isActiveDialogFollowingList) {
                List<UserModel> updatedFollowing = dbHelper.getFollowingUsers(targetUsername);
                activeFollowAdapter.updateList(updatedFollowing);
                int count = updatedFollowing.size();
                if (activeDialogTitle != null) {
                    activeDialogTitle.setText("Đang theo dõi (" + count + ")");
                }
            } else {
                List<UserModel> updatedFollowers = dbHelper.getFollowerUsers(targetUsername);
                activeFollowAdapter.updateList(updatedFollowers);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đọc lại SQLite khi quay lại màn hình (ví dụ vừa thả tim ở trang chi tiết)
        onLocalDataChanged();
    }

    @Override
    public void updateMiniPlayerUi() {
        super.updateMiniPlayerUi();
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
        // Đĩa than / sóng nhạc của bản nhạc đang phát trong Danh sách nhạc đã lưu
        if (savedAdapter != null) {
            savedAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_profile;
    }
}
