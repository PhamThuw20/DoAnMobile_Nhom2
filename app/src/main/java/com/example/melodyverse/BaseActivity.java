package com.example.melodyverse;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Activity cơ sở kế thừa cho toàn bộ màn hình (Bài 2 - Activities & Bài 6 - Broadcast & Services).
 * Tái sử dụng thanh điều hướng và Mini Player có đĩa quay Vinyl đồng bộ toàn cục.
 */
public abstract class BaseActivity extends AppCompatActivity implements SyncManager.Listener {

    private static final String EXTRA_TAB_SWITCH = "EXTRA_TAB_SWITCH";
    private static final String STATE_NOW_PLAYING_OPEN = "state_now_playing_open";

    /** Màn dọc: thanh điều hướng dưới (BottomNavigationView). Màn ngang: thanh điều hướng dọc (NavigationRailView). */
    protected NavigationBarView bottomNavigationView;
    protected View miniPlayerContainer;
    protected View frameMiniPlayerArt;
    protected ImageView ivMiniPlayerPlay;
    protected TextView tvMiniPlayerTitle;
    protected TextView tvMiniPlayerArtist;
    protected ImageView btnMiniPlayerClose;
    protected android.widget.ProgressBar miniPlayerProgress;
    protected TextView tvMiniPlayerTime;

    public static String currentSongTitle = "50 Cuộc Gọi Nhỡ";
    public static String currentSongArtist = "Tinh Hà Say Hi • Tập 8";
    public static boolean isGlobalPlaying = false;
    public static boolean isMiniPlayerDismissed = false;

    private RotateAnimation vinylRotateAnimation;
    private ObjectAnimator bigVinylAnimator;
    private ObjectAnimator miniVinylAnimator;
    private androidx.appcompat.app.AppCompatDialog nowPlayingDialog;
    /** Bài đang hiển thị trên bao đĩa (để chỉ tô lại khi đổi bài). */
    private String playerArtworkKey;
    private Boolean playerVinylOut;

    private boolean isSyncingNavSelection = false;
    private final android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // BroadcastReceiver lắng nghe trạng thái phát nhạc từ MusicService
    private final BroadcastReceiver musicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && MusicService.ACTION_MUSIC_STATUS_CHANGED.equals(intent.getAction())) {
                boolean playing = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false);
                String title = intent.getStringExtra(MusicService.EXTRA_SONG_TITLE);
                String artist = intent.getStringExtra(MusicService.EXTRA_ARTIST_NAME);

                if (title != null) currentSongTitle = title;
                if (artist != null) currentSongArtist = artist;
                isGlobalPlaying = playing;
                if (playing) {
                    isMiniPlayerDismissed = false; // Khi phát bài hát, luôn tự động hiện lại thanh mini
                }

                updateMiniPlayerUi();
                updateNowPlayingDialogUi();
                // Bài hát bị thu hồi (bài viết bị gỡ bỏ / ẩn): đóng luôn màn Đang phát
                if (!MusicService.hasTrackLoaded && !playing && nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
                    nowPlayingDialog.dismiss();
                }

                // Ví dụ: bấm Play / tự chuyển sang bài trực tuyến lúc mất mạng, hết danh sách nhạc đã lưu
                String message = intent.getStringExtra(MusicService.EXTRA_MESSAGE);
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(BaseActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Áp dụng Dark Mode đã lưu trước khi inflate layout
        boolean darkMode = getSharedPreferences("melodyverse_settings", MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);

        // Mọi màn hình bên trong app đều yêu cầu phiên đăng nhập Firebase hợp lệ (tài khoản tồn tại & không bị khóa)
        if (!AuthManager.hasValidSession(this)) {
            goToLogin(null);
        }

        // Theo dõi mạng ở mọi màn hình: tự đồng bộ khi có mạng trở lại, cảnh báo khi mất mạng (nếu bật)
        NetworkMonitor.start(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        miniPlayerContainer = findViewById(R.id.mini_player_container);
        if (miniPlayerContainer != null) {
            miniPlayerContainer.setClipToOutline(true);
            miniPlayerContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    float radius = 22f * getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }
        frameMiniPlayerArt = findViewById(R.id.frame_mini_player_art);
        ivMiniPlayerPlay = findViewById(R.id.mini_player_play);
        tvMiniPlayerTitle = findViewById(R.id.mini_player_title);
        tvMiniPlayerArtist = findViewById(R.id.mini_player_artist);
        btnMiniPlayerClose = findViewById(R.id.btn_mini_player_close);
        miniPlayerProgress = findViewById(R.id.mini_player_progress);
        tvMiniPlayerTime = findViewById(R.id.tv_mini_player_time);

        // Đảm bảo tâm xoay của đĩa than mini luôn ở chính giữa 50% width & height (đứng yên quay tại chỗ)
        if (frameMiniPlayerArt != null) {
            float density = getResources().getDisplayMetrics().density;
            frameMiniPlayerArt.setPivotX(22f * density);
            frameMiniPlayerArt.setPivotY(22f * density);
            frameMiniPlayerArt.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int w = r - l;
                int h = b - t;
                if (w > 0 && h > 0) {
                    v.setPivotX(w / 2f);
                    v.setPivotY(h / 2f);
                }
            });
        }

        // Khởi tạo hiệu ứng đĩa vinyl quay đều 360 độ
        vinylRotateAnimation = new RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnimation.setDuration(6000);
        vinylRotateAnimation.setRepeatCount(Animation.INFINITE);
        vinylRotateAnimation.setInterpolator(new LinearInterpolator());

        // Đồng bộ trạng thái hiện tại từ Service
        isGlobalPlaying = MusicService.isPlaying;
        currentSongTitle = MusicService.currentSongTitle;
        currentSongArtist = MusicService.currentArtistName;
        updateMiniPlayerUi();

        // Tương tác nút Play / Pause trên Mini Player
        if (ivMiniPlayerPlay != null) {
            ivMiniPlayerPlay.setOnClickListener(v -> {
                MusicService.toggle(this);
            });
        }

        // Tương tác chạm vào Mini Player -> Mở Trình phát chi tiết (Now Playing Modal)
        if (miniPlayerContainer != null) {
            miniPlayerContainer.setOnClickListener(v -> {
                showNowPlayingDialog();
            });
        }

        // Nút Ẩn Thanh Mini Player trực tiếp
        if (btnMiniPlayerClose != null) {
            btnMiniPlayerClose.setOnClickListener(v -> {
                isMiniPlayerDismissed = true;
                if (miniPlayerContainer != null) {
                    miniPlayerContainer.setVisibility(View.GONE);
                }
                Toast.makeText(this, "Đã ẩn thanh phát nhạc", Toast.LENGTH_SHORT).show();
            });
        }

        // Điều hướng màn hình (100% Activity)
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            // Bỏ qua sự kiện phát sinh khi chỉ đồng bộ tab đang chọn (setSelectedItemId)
            if (isSyncingNavSelection) {
                return true;
            }
            if (itemId == getCheckedItem() && isNavRootScreen()) {
                return true;
            }
            navigateToTab(itemId);
            return true;
        });

        // Bấm lại đúng tab đang mở (ví dụ Home khi đang ở Bảng tin): để màn hình tự xử lý (cuộn lên đầu / tải lại)
        bottomNavigationView.setOnItemReselectedListener(item -> {
            if (isSyncingNavSelection) return;
            if (item.getItemId() == getCheckedItem() && isNavRootScreen()) {
                onNavTabReselected();
            } else {
                // Ví dụ đang ở Chi tiết bài viết (tab Home được tô sáng): quay về màn gốc của tab theo luồng cũ
                navigateToTab(item.getItemId());
            }
        });

        // Đảm bảo tab hiện tại được highlight chính xác ngay khi khởi tạo
        syncBottomNavSelection();

        // Xoay màn hình làm Activity được tạo lại: mở lại màn Đang phát nếu trước đó đang mở
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_NOW_PLAYING_OPEN, false)
                && MusicService.hasTrackLoaded) {
            getWindow().getDecorView().post(() -> {
                if (!isFinishing() && !isDestroyed()) showNowPlayingDialog();
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_NOW_PLAYING_OPEN, nowPlayingDialog != null && nowPlayingDialog.isShowing());
    }

    /** Người dùng bấm lại tab của chính màn hình đang mở. Màn hình gốc override để cuộn lên đầu / làm mới. */
    protected void onNavTabReselected() {}

    /** Mở màn hình gốc của tab (dùng lại Activity đang có trong back stack nếu có). */
    private void navigateToTab(int itemId) {
        Intent intent = null;
        if (itemId == R.id.nav_home) {
            intent = new Intent(this, MainActivity.class);
        } else if (itemId == R.id.nav_discover) {
            intent = new Intent(this, DiscoverActivity.class);
        } else if (itemId == R.id.nav_create) {
            intent = new Intent(this, CreatePostActivity.class);
        } else if (itemId == R.id.nav_notifications) {
            intent = new Intent(this, NotificationsActivity.class);
        } else if (itemId == R.id.nav_profile) {
            intent = new Intent(this, ProfileActivity.class);
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(EXTRA_TAB_SWITCH, true);
            startActivity(intent);
            applyTabTransition();
        }
    }

    /** Hiệu ứng đổi tab theo cài đặt "smooth_anim": hiện dần (mượt) hoặc chuyển ngay. */
    private void applyTabTransition() {
        boolean smoothAnim = getSharedPreferences("melodyverse_settings", MODE_PRIVATE)
                .getBoolean("smooth_anim", true);
        if (smoothAnim) {
            overridePendingTransition(R.anim.tab_fade_in, R.anim.tab_hold);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    /**
     * Tab đã mở trước đó được đưa lại lên trên (REORDER_TO_FRONT): Android bỏ qua hiệu ứng đặt lúc startActivity
     * và chạy hiệu ứng mặc định, nên phải đặt lại ở đây.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_TAB_SWITCH, false)) {
            intent.removeExtra(EXTRA_TAB_SWITCH);
            applyTabTransition();
        }
    }

    /** Số thông báo chưa đọc hiện trên tab "Thông báo" của thanh điều hướng dưới (ẩn khi đã đọc hết). */
    protected void updateNotificationBadge() {
        if (bottomNavigationView == null || bottomNavigationView.getMenu().findItem(R.id.nav_notifications) == null) return;
        String me = SessionManager.getCurrentUsername(this);
        int unread = me == null ? 0 : DatabaseHelper.getInstance(this).getUnreadNotificationCount(me);
        if (unread > 0) {
            com.google.android.material.badge.BadgeDrawable badge =
                    bottomNavigationView.getOrCreateBadge(R.id.nav_notifications);
            badge.setNumber(unread);
            badge.setMaxCharacterCount(3); // 100+ hiện "99+"
            badge.setVisible(true);
        } else {
            bottomNavigationView.removeBadge(R.id.nav_notifications);
        }
    }

    /** Đánh dấu tab hiện tại mà không kích hoạt điều hướng. */
    protected void syncBottomNavSelection() {
        if (bottomNavigationView != null && getCheckedItem() != -1) {
            isSyncingNavSelection = true;
            bottomNavigationView.setSelectedItemId(getCheckedItem());
            isSyncingNavSelection = false;
        }
    }

    /**
     * true nếu màn hình là trang gốc của tab (bấm lại tab không cần điều hướng).
     * Ví dụ trang hồ sơ của người khác trả về false để bấm tab "Hồ sơ" quay về hồ sơ của mình.
     */
    protected boolean isNavRootScreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncBottomNavSelection();
        updateNotificationBadge();

        // Đăng ký nhận Broadcast từ MusicService
        try {
            IntentFilter filter = new IntentFilter(MusicService.ACTION_MUSIC_STATUS_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(musicReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(musicReceiver, filter);
            }
        } catch (Exception ignored) {}

        isGlobalPlaying = MusicService.isPlaying;
        currentSongTitle = MusicService.currentSongTitle;
        currentSongArtist = MusicService.currentArtistName;
        updateMiniPlayerUi();

        // Nhận sự kiện đồng bộ (đẩy xong dữ liệu chờ, có mạng lại, tài khoản bị khóa)
        SyncManager sync = SyncManager.get(this);
        sync.addListener(this);
        sync.checkMyAccount();
        // Thử gửi lại các thao tác còn PENDING (lần gửi trước có thể lỗi mạng giữa chừng)
        sync.requestPush();
        // Lắng nghe Firebase realtime (sửa trên Console là máy đang chạy cập nhật theo)
        sync.startRealtime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(musicReceiver);
        } catch (Exception ignored) {}
        SyncManager.get(this).removeListener(this);
    }

    /**
     * Sự kiện từ SyncManager. Màn hình con override {@link #onLocalDataChanged()} để đọc lại SQLite
     * và {@link #onReconnectedRefresh()} để tải lại trang từ Firebase.
     */
    @Override
    public void onSyncEvent(String event) {
        if (SyncManager.EVENT_ACCOUNT_LOCKED.equals(event)) {
            goToLogin("Tài khoản của bạn đã bị Quản trị viên khóa. Vui lòng liên hệ ban quản trị để được hỗ trợ.");
        } else if (SyncManager.EVENT_DATA_CHANGED.equals(event)) {
            onLocalDataChanged();
            updateNotificationBadge();
        } else if (SyncManager.EVENT_RECONNECTED.equals(event)) {
            onReconnectedRefresh();
        }
    }

    /** Dữ liệu trong SQLite vừa thay đổi do đồng bộ (ví dụ bình luận nhãn chờ đã gửi xong). */
    protected void onLocalDataChanged() {}

    /** Có mạng trở lại và đã đẩy xong dữ liệu chờ: tải lại trang hiện tại từ Firebase. */
    protected void onReconnectedRefresh() {
        onLocalDataChanged();
    }

    /** Thoát phiên và quay về màn Đăng nhập (kèm thông báo nếu có). */
    protected void goToLogin(String message) {
        AuthManager.signOut(this);
        Intent loginIntent = new Intent(this, LoginActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (message != null) loginIntent.putExtra(LoginActivity.EXTRA_MESSAGE, message);
        startActivity(loginIntent);
        finish();
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isGlobalPlaying) {
                updateProgressViews();
                progressHandler.postDelayed(this, 500);
            }
        }
    };

    /** Cập nhật thanh tiến trình & thời gian (gọi cả khi đang tạm dừng để không bị về 00:00). */
    private void updateProgressViews() {
                int pos = MusicService.getCurrentPosition();
                int dur = MusicService.getDuration();
                if (dur > 0) {
                    int percentProgress = (int) (((long) pos * 1000) / dur);
                    if (miniPlayerProgress != null) {
                        miniPlayerProgress.setProgress(percentProgress);
                    }
                    if (tvMiniPlayerTime != null) {
                        tvMiniPlayerTime.setText(formatTime(pos) + " / " + formatTime(dur));
                    }
                    if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
                        SeekBar sb = nowPlayingDialog.findViewById(R.id.seekbar_dialog_timeline);
                        TextView tvCur = nowPlayingDialog.findViewById(R.id.tv_dialog_time_current);
                        TextView tvTot = nowPlayingDialog.findViewById(R.id.tv_dialog_time_total);
                        if (sb != null && !sb.isPressed()) {
                            sb.setProgress((int) (((long) pos * sb.getMax()) / dur));
                        }
                        if (tvCur != null) tvCur.setText(formatTime(pos));
                        if (tvTot != null) tvTot.setText(formatTime(dur));
                    }
                }
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void updateMiniPlayerUi() {
        if (miniPlayerContainer != null) {
            // Chỉ hiển thị thanh mini khi đã có bài hát được phát (hasTrackLoaded hoặc isGlobalPlaying) và chưa bị ẩn
            boolean shouldShow = (MusicService.hasTrackLoaded || isGlobalPlaying) && !isMiniPlayerDismissed;
            miniPlayerContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
        if (tvMiniPlayerTitle != null) tvMiniPlayerTitle.setText(currentSongTitle);
        // Nhạc trực tuyến đang tải: hiện rõ "Đang tải", không giả là đang phát
        if (tvMiniPlayerArtist != null) {
            tvMiniPlayerArtist.setText(MusicService.isBuffering ? "Đang tải…" : currentSongArtist);
        }
        if (ivMiniPlayerPlay != null) {
            ivMiniPlayerPlay.setImageResource(isGlobalPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }

        // Đĩa vinyl xoay quanh chính tâm khi phát nhạc (đứng yên quay tại chỗ)
        if (frameMiniPlayerArt != null) {
            if (miniVinylAnimator == null) {
                float density = getResources().getDisplayMetrics().density;
                frameMiniPlayerArt.setPivotX(22f * density);
                frameMiniPlayerArt.setPivotY(22f * density);
                miniVinylAnimator = ObjectAnimator.ofFloat(frameMiniPlayerArt, View.ROTATION, 0f, 360f);
                miniVinylAnimator.setDuration(6000);
                miniVinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
                miniVinylAnimator.setInterpolator(new LinearInterpolator());
            }

            if (isGlobalPlaying) {
                if (miniVinylAnimator.isPaused()) {
                    miniVinylAnimator.resume();
                } else if (!miniVinylAnimator.isRunning()) {
                    miniVinylAnimator.start();
                }
            } else {
                if (miniVinylAnimator.isRunning()) {
                    miniVinylAnimator.pause();
                }
            }
        }

        progressHandler.removeCallbacks(progressRunnable);
        if (isGlobalPlaying) {
            progressHandler.post(progressRunnable);
        } else if (MusicService.hasTrackLoaded) {
            updateProgressViews();
        }
    }

    /**
     * Mở Dialog Trình phát nhạc chi tiết siêu sang (Now Playing)
     */
    protected void showNowPlayingDialog() {
        // Màn Đang phát toàn màn hình, trượt lên từ đáy
        nowPlayingDialog = new androidx.appcompat.app.AppCompatDialog(this, R.style.Theme_MelodyVerse_Player);
        View dialogView = LayoutInflater.from(nowPlayingDialog.getContext()).inflate(R.layout.dialog_now_playing, null, false);
        nowPlayingDialog.setContentView(dialogView);
        if (nowPlayingDialog.getWindow() != null) {
            nowPlayingDialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        playerArtworkKey = null;
        playerVinylOut = null;

        View frameBigVinyl = dialogView.findViewById(R.id.frame_big_vinyl);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_song_title);
        TextView tvDialogArtist = dialogView.findViewById(R.id.tv_dialog_song_artist);
        ImageView btnPlay = dialogView.findViewById(R.id.btn_dialog_play);
        ImageView btnNext = dialogView.findViewById(R.id.btn_dialog_next);
        ImageView btnPrev = dialogView.findViewById(R.id.btn_dialog_prev);
        ImageView btnShuffle = dialogView.findViewById(R.id.btn_dialog_shuffle);
        ImageView btnRepeat = dialogView.findViewById(R.id.btn_dialog_repeat);
        View btnClose = dialogView.findViewById(R.id.btn_close_player);
        SeekBar seekBar = dialogView.findViewById(R.id.seekbar_dialog_timeline);
        TextView tvCurTime = dialogView.findViewById(R.id.tv_dialog_time_current);
        TextView tvTotTime = dialogView.findViewById(R.id.tv_dialog_time_total);

        if (tvDialogTitle != null) tvDialogTitle.setText(currentSongTitle);
        if (tvDialogArtist != null) tvDialogArtist.setText(currentSongArtist);
        TextView tvDialogGenre = dialogView.findViewById(R.id.tv_dialog_genre);
        if (tvDialogGenre != null) tvDialogGenre.setText(MusicService.currentGenre);
        if (btnPlay != null) {
            btnPlay.setImageResource(isGlobalPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (btnShuffle != null) tintModeButton(btnShuffle, MusicService.isShuffleEnabled);
        if (btnRepeat != null) tintModeButton(btnRepeat, MusicService.isRepeatEnabled);

        int curPos = MusicService.getCurrentPosition();
        int curDur = MusicService.getDuration();
        if (tvCurTime != null) tvCurTime.setText(formatTime(curPos));
        if (tvTotTime != null) tvTotTime.setText(formatTime(curDur));
        if (seekBar != null && curDur > 0) {
            seekBar.setProgress((int) (((long) curPos * seekBar.getMax()) / curDur));
        }
        applyPlayerArtwork(dialogView);

        // Đĩa Vinyl lớn xoay quanh chính tâm (Pivot chuẩn 50% width & 50% height, không bị lệch trục)
        if (frameBigVinyl != null) {
            frameBigVinyl.post(() -> {
                frameBigVinyl.setPivotX(frameBigVinyl.getWidth() / 2f);
                frameBigVinyl.setPivotY(frameBigVinyl.getHeight() / 2f);
            });
            if (bigVinylAnimator != null) {
                bigVinylAnimator.cancel();
            }
            bigVinylAnimator = ObjectAnimator.ofFloat(frameBigVinyl, View.ROTATION, 0f, 360f);
            bigVinylAnimator.setDuration(6000);
            bigVinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
            bigVinylAnimator.setInterpolator(new LinearInterpolator());

            if (isGlobalPlaying) {
                bigVinylAnimator.start();
            }
        }

        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> MusicService.toggle(this));
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                MusicService.next(this);
                Toast.makeText(this, "Đang chuyển bài tiếp theo...", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                // Đối xứng với nút Tiếp theo: đã nghe được một đoạn thì tua lại đầu bài, bấm tiếp sẽ về bài trước
                boolean restart = MusicService.hasTrackLoaded
                        && MusicService.shouldRestartOnPrevious(MusicService.getCurrentPosition());
                MusicService.previous(this);
                Toast.makeText(this, restart ? "Phát lại bài hát từ đầu..." : "Đang chuyển về bài trước...",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Phát ngẫu nhiên và Phát lặp lại loại trừ nhau: bật chế độ này thì tự tắt chế độ kia
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> {
                boolean turnedOff = MusicService.isRepeatEnabled && !MusicService.isShuffleEnabled;
                MusicService.setShuffleEnabled(!MusicService.isShuffleEnabled);
                tintModeButton(btnShuffle, MusicService.isShuffleEnabled);
                if (btnRepeat != null) tintModeButton(btnRepeat, MusicService.isRepeatEnabled);
                Toast.makeText(this, MusicService.isShuffleEnabled
                        ? (turnedOff ? "Đã bật phát ngẫu nhiên (tự tắt lặp lại)" : "Đã bật phát ngẫu nhiên")
                        : "Đã tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnRepeat != null) {
            btnRepeat.setOnClickListener(v -> {
                boolean turnedOff = MusicService.isShuffleEnabled && !MusicService.isRepeatEnabled;
                MusicService.setRepeatEnabled(!MusicService.isRepeatEnabled);
                tintModeButton(btnRepeat, MusicService.isRepeatEnabled);
                if (btnShuffle != null) tintModeButton(btnShuffle, MusicService.isShuffleEnabled);
                Toast.makeText(this, MusicService.isRepeatEnabled
                        ? (turnedOff ? "Đã bật lặp lại bài hát (tự tắt phát ngẫu nhiên)" : "Đã bật lặp lại bài hát")
                        : "Đã tắt lặp lại", Toast.LENGTH_SHORT).show();
            });
        }

        // Kéo tua nhạc tương tác thực tế
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && tvCurTime != null) {
                        int dur = MusicService.getDuration();
                        int targetMs = (int) (((long) progress * dur) / seekBar.getMax());
                        tvCurTime.setText(formatTime(targetMs));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int dur = MusicService.getDuration();
                    int targetMs = (int) (((long) seekBar.getProgress() * dur) / seekBar.getMax());
                    MusicService.seekTo(BaseActivity.this, targetMs);
                }
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> nowPlayingDialog.dismiss());
        }

        nowPlayingDialog.setOnDismissListener(dialog -> {
            if (bigVinylAnimator != null) {
                bigVinylAnimator.cancel();
                bigVinylAnimator = null;
            }
        });

        nowPlayingDialog.show();
        // Mở màn hình xong mới cho đĩa trượt ra khỏi bao
        dialogView.postDelayed(() -> setPlayerVinylOut(dialogView, isGlobalPlaying, true), 380);
        progressHandler.removeCallbacks(progressRunnable);
        if (isGlobalPlaying) {
            progressHandler.post(progressRunnable);
        }
    }

    /** Tô bao đĩa, nhãn đĩa, ánh sáng nền và hình sóng âm theo bài đang phát. */
    private void applyPlayerArtwork(View root) {
        String key = MusicService.currentPostId != null ? MusicService.currentPostId : currentSongTitle;
        View seek = root.findViewById(R.id.seekbar_dialog_timeline);
        if (seek instanceof WaveformSeekBar) ((WaveformSeekBar) seek).setPlaying(isGlobalPlaying);
        if (key != null && key.equals(playerArtworkKey)) return;
        playerArtworkKey = key;

        int[] c = DiscPalette.colorsFor(key);
        String initial = DiscPalette.initialOf(currentSongTitle);

        View sleeve = root.findViewById(R.id.card_player_sleeve);
        if (sleeve != null) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, c);
            bg.setCornerRadius(22 * getResources().getDisplayMetrics().density);
            sleeve.setBackground(bg);
            sleeve.setClipToOutline(true);
        }
        View label = root.findViewById(R.id.view_player_vinyl_label);
        if (label != null) {
            android.graphics.drawable.GradientDrawable lb = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, c);
            lb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            label.setBackground(lb);
        }
        View glow = root.findViewById(R.id.view_player_glow);
        if (glow != null) {
            int top = (c[0] & 0x00FFFFFF) | 0x55000000;
            int mid = (c[1] & 0x00FFFFFF) | 0x1F000000;
            glow.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{top, mid, 0x00000000}));
        }
        TextView sleeveTitle = root.findViewById(R.id.tv_player_sleeve_title);
        if (sleeveTitle != null) sleeveTitle.setText(currentSongTitle);
        TextView sleeveLetter = root.findViewById(R.id.tv_player_sleeve_letter);
        if (sleeveLetter != null) sleeveLetter.setText(initial);
        TextView vinylInitial = root.findViewById(R.id.tv_player_vinyl_initial);
        if (vinylInitial != null) vinylInitial.setText(initial);
        if (seek instanceof WaveformSeekBar) ((WaveformSeekBar) seek).setSeed(key);
    }

    /** Đang phát: đĩa than trượt ra khỏi bao (bao lùi sang trái). Dừng: đĩa trượt vào lại. */
    private void setPlayerVinylOut(View root, boolean out, boolean animate) {
        if (playerVinylOut != null && playerVinylOut == out) return;
        playerVinylOut = out;
        View vinyl = root.findViewById(R.id.frame_big_vinyl);
        View sleeve = root.findViewById(R.id.card_player_sleeve);
        if (vinyl == null || sleeve == null) return;
        float shift = 56 * getResources().getDisplayMetrics().density;
        float vinylX = out ? shift : 0f;
        float sleeveX = out ? -shift : 0f;
        if (animate) {
            vinyl.animate().translationX(vinylX).setDuration(520)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
            sleeve.animate().translationX(sleeveX).setDuration(520)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        } else {
            vinyl.setTranslationX(vinylX);
            sleeve.setTranslationX(sleeveX);
        }
    }

    private void tintModeButton(ImageView button, boolean enabled) {
        button.setColorFilter(androidx.core.content.ContextCompat.getColor(this,
                enabled ? R.color.colorAccent : R.color.colorTextSecondary));
    }

    private void updateNowPlayingDialogUi() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
            TextView tvDialogTitle = nowPlayingDialog.findViewById(R.id.tv_dialog_song_title);
            TextView tvDialogArtist = nowPlayingDialog.findViewById(R.id.tv_dialog_song_artist);
            TextView tvDialogGenre = nowPlayingDialog.findViewById(R.id.tv_dialog_genre);
            ImageView btnPlay = nowPlayingDialog.findViewById(R.id.btn_dialog_play);

            if (tvDialogTitle != null) tvDialogTitle.setText(currentSongTitle);
            if (tvDialogArtist != null) tvDialogArtist.setText(currentSongArtist);
            if (tvDialogGenre != null) tvDialogGenre.setText(MusicService.currentGenre);
            if (btnPlay != null) {
                btnPlay.setImageResource(isGlobalPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            View playerRoot = nowPlayingDialog.findViewById(R.id.layout_player_root);
            if (playerRoot != null) {
                applyPlayerArtwork(playerRoot);
                setPlayerVinylOut(playerRoot, isGlobalPlaying, true);
            }
            if (bigVinylAnimator != null) {
                if (isGlobalPlaying) {
                    if (bigVinylAnimator.isPaused()) {
                        bigVinylAnimator.resume();
                    } else if (!bigVinylAnimator.isRunning()) {
                        bigVinylAnimator.start();
                    }
                } else {
                    if (bigVinylAnimator.isRunning()) {
                        bigVinylAnimator.pause();
                    }
                }
            }
        }
    }

    protected void setContentLayout(int layoutResID) {
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layoutResID, contentFrame, true);
    }

    @Override
    protected void onDestroy() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
            nowPlayingDialog.dismiss();
        }
        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
    }

    protected abstract int getCheckedItem();
}
