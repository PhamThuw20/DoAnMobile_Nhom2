package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Màn hình Bảng tin âm nhạc chính (Feed): đọc SQLite trước, đồng bộ Firebase sau, phát nhạc qua MusicService.
 */
public class MainActivity extends BaseActivity {

    /** Tài khoản mới đăng nhập lần đầu: hiện bước gợi ý nghệ sĩ (tự chọn theo dõi hoặc bỏ qua). */
    public static final String EXTRA_SHOW_ARTIST_SUGGESTIONS = "extra_show_artist_suggestions";

    private List<PostModel> postList = new ArrayList<>();
    private List<PostModel> originalList = new ArrayList<>();
    private PostAdapter postAdapter;
    private DatabaseHelper dbHelper;
    private EditText etSearch;
    private ImageView ivClearSearch;
    private TextView tvFeedTitle, tvSearchSummary;
    private View layoutSearchEmpty;
    private TextView tvSearchEmptyMsg;
    private RecyclerView rvFeed;

    // Trạng thái chip lọc thể loại đang chọn (giữ nguyên khi quay lại màn hình)
    private String activeGenreLabel = "";
    private String[] activeGenreKeywords = new String[0];
    private List<TextView> genreChips = new ArrayList<>();
    private TextView chipGenreAll;
    private boolean isClearingSearchForGenre = false;

    // Đồng bộ Firebase: vuốt làm mới + lướt tới đâu tải thêm tới đó
    private SyncManager sync;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private TextView tvFeedFooter;
    private boolean isLoadingMore = false;
    private boolean hasMoreHome = true;
    private boolean isOfflineMode = false;
    // Chống gửi trùng yêu cầu tải lại khi người dùng bấm / vuốt liên tục
    private boolean isReloading = false;
    private boolean reloadQueued = false;
    private boolean queuedUserInitiated = false;
    private androidx.core.widget.NestedScrollView nestedScrollMain;
    private View layoutSearchBarRef;
    // Lượt tải hiện tại do người dùng chủ động (vuốt / bấm lại Home) -> xoay màn hình thì tải lại đúng kiểu đó
    private boolean currentReloadUserInitiated = false;

    // Bước gợi ý nghệ sĩ chưa được trả lời (xoay màn hình thì hiện lại, không bị mất bước này)
    private boolean artistSuggestionsPending = false;
    private androidx.appcompat.app.AlertDialog artistDialog;

    // Khóa lưu trạng thái khi Activity bị tạo lại (xoay màn hình)
    private static final String KEY_GENRE_LABEL = "active_genre_label";
    private static final String KEY_GENRE_KEYWORDS = "active_genre_keywords";
    private static final String KEY_SEARCH_BAR_VISIBLE = "search_bar_visible";
    private static final String KEY_HAS_MORE_HOME = "has_more_home";
    private static final String KEY_OFFLINE_MODE = "offline_mode";
    private static final String KEY_WAS_RELOADING = "was_reloading";
    private static final String KEY_RELOAD_USER_INITIATED = "reload_user_initiated";
    private static final String KEY_ARTIST_PENDING = "artist_suggestions_pending";
    private static final String KEY_FEED_ANCHOR_POS = "feed_anchor_pos";
    private static final String KEY_FEED_ANCHOR_OFFSET = "feed_anchor_offset";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Bơm giao diện trang chủ vào FrameLayout của BaseActivity
        setContentLayout(R.layout.activity_main_content);

        // Khởi tạo kênh thông báo
        NotificationHelper.createNotificationChannel(this);

        // Android 13+ yêu cầu xin quyền hiển thị thông báo lúc chạy (Runtime Permission)
        // Chỉ hỏi khi mở màn hình lần đầu, không hỏi lại mỗi lần xoay màn hình
        if (savedInstanceState == null
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
        dbHelper = DatabaseHelper.getInstance(this);
        sync = SyncManager.get(this);

        // Vuốt xuống để tải lại trang từ Firebase
        swipeRefresh = findViewById(R.id.swipe_refresh_main);
        tvFeedFooter = findViewById(R.id.tv_feed_footer);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorAccent, R.color.colorNeonPink);
            // Vuốt xuống: tải lại bảng tin mới nhất (offline thì đọc lại SQLite và báo cho người dùng biết)
            swipeRefresh.setOnRefreshListener(() -> reloadFromServer(true, true));
        }

        // Nút Kính lúp Tìm kiếm bên cạnh Thông báo -> Đóng / Mở thanh tìm kiếm
        View btnHeaderSearch = findViewById(R.id.btn_header_search);
        View layoutSearchBar = findViewById(R.id.layout_search_bar);
        if (btnHeaderSearch != null && layoutSearchBar != null) {
            btnHeaderSearch.setOnClickListener(v -> {
                if (layoutSearchBar.getVisibility() == View.VISIBLE) {
                    layoutSearchBar.setVisibility(View.GONE);
                    if (etSearch != null) etSearch.setText("");
                } else {
                    layoutSearchBar.setVisibility(View.VISIBLE);
                    if (etSearch != null) {
                        etSearch.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }

        // Tự động thu gọn thanh tìm kiếm khi người dùng lướt bảng tin xuống
        androidx.core.widget.NestedScrollView nestedScroll = findViewById(R.id.nested_scroll_main);
        nestedScrollMain = nestedScroll;
        layoutSearchBarRef = layoutSearchBar;
        if (nestedScroll != null) {
            nestedScroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                // Lướt gần cuối bảng tin -> tải thêm bài cũ hơn
                View content = ((androidx.core.widget.NestedScrollView) v).getChildAt(0);
                if (content != null && scrollY > oldScrollY
                        && scrollY >= content.getMeasuredHeight() - v.getMeasuredHeight() - 400) {
                    loadMoreHome();
                }
                if (scrollY > oldScrollY + 15) {
                    if (layoutSearchBar != null && layoutSearchBar.getVisibility() == View.VISIBLE) {
                        layoutSearchBar.setVisibility(View.GONE);
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null && etSearch != null) {
                            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                        }
                    }
                }
            });
        }

        ivClearSearch = findViewById(R.id.iv_clear_search);
        tvFeedTitle = findViewById(R.id.tv_feed_title);
        tvSearchSummary = findViewById(R.id.tv_search_summary);
        layoutSearchEmpty = findViewById(R.id.layout_search_empty);
        tvSearchEmptyMsg = findViewById(R.id.tv_search_empty_msg);
        rvFeed = findViewById(R.id.rv_feed);

        if (ivClearSearch != null) {
            ivClearSearch.setOnClickListener(v -> {
                if (etSearch != null) etSearch.setText("");
            });
        }

        // Màn hình không có kết quả: nút xem lại toàn bộ bảng tin
        View btnResetSearch = findViewById(R.id.btn_reset_search);

        if (btnResetSearch != null) btnResetSearch.setOnClickListener(v -> { if (etSearch != null) etSearch.setText(""); });

        // Ô tìm kiếm thời gian thực (Lọc theo tên bài hát, nghệ sĩ hoặc thể loại)
        etSearch = findViewById(R.id.et_search);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (ivClearSearch != null) {
                        ivClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    // Gõ từ khóa tìm kiếm thì bỏ chọn chip thể loại để tránh hai bộ lọc chồng nhau
                    if (!isClearingSearchForGenre && s.toString().trim().length() > 0 && !activeGenreLabel.isEmpty()) {
                        highlightGenreChip(chipGenreAll);
                        activeGenreLabel = "";
                        activeGenreKeywords = new String[0];
                    }
                    refreshFeed();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Khởi tạo các chip lọc thể loại âm nhạc nhanh
        setupGenreFilterChips();

        // Xoay màn hình: khôi phục bộ lọc thể loại, thanh tìm kiếm và trạng thái tải trước khi đọc SQLite
        if (savedInstanceState != null) {
            restoreFeedState(savedInstanceState, layoutSearchBar);
        }

        // Khởi tạo danh sách Bảng tin (Feed)
        if (rvFeed != null) {
            rvFeed.setLayoutManager(new LinearLayoutManager(this));
            postAdapter = new PostAdapter(this, postList, post -> {
                // Phát nhạc thực sự thông qua MusicService
                if (MusicService.isPlaying && post.getId().equals(MusicService.currentPostId)) {
                    MusicService.toggle(this);
                } else {
                    MusicService.play(this, post);
                    MusicService.announce(this, post);
                }
            });

            // Lắng nghe sự kiện click Hashtag -> Tự động điền ô tìm kiếm & lọc bài viết ngay lập tức
            postAdapter.setOnHashtagClickListener(hashtag -> {
                if (layoutSearchBar != null) {
                    layoutSearchBar.setVisibility(View.VISIBLE);
                }
                if (etSearch != null) {
                    etSearch.setText(hashtag);
                    etSearch.setSelection(hashtag.length());
                }
                Toast.makeText(this, "Đang lọc bài viết theo hashtag: " + hashtag, Toast.LENGTH_SHORT).show();
            });

            rvFeed.setAdapter(postAdapter);
        }

        // Từ khóa truyền qua Intent chỉ xử lý một lần (xoay màn hình thì giữ từ khóa người dùng đang gõ)
        if (savedInstanceState == null) handleIntentSearchQuery(getIntent());

        // Hiển thị ngay dữ liệu đã lưu trong SQLite, sau đó tải bản mới nhất từ Firebase
        refreshFeed();
        if (savedInstanceState == null) {
            reloadFromServer(true, false);
        } else if (savedInstanceState.getBoolean(KEY_WAS_RELOADING, false)) {
            // Đang tải dở thì bị xoay màn hình: kết quả trả về Activity cũ bị bỏ qua -> tải lại một lần
            reloadFromServer(true, savedInstanceState.getBoolean(KEY_RELOAD_USER_INITIATED, false));
        }
        // Xoay màn hình bình thường: dữ liệu đã có sẵn trong SQLite, không tải lại từ Firebase

        // Tài khoản mới: hỏi có muốn theo dõi nghệ sĩ gợi ý không (không tự động theo dõi ai)
        if (getIntent().getBooleanExtra(EXTRA_SHOW_ARTIST_SUGGESTIONS, false)) {
            getIntent().removeExtra(EXTRA_SHOW_ARTIST_SUGGESTIONS);
            if (savedInstanceState == null && rvFeed != null) rvFeed.post(this::showArtistSuggestions);
        } else if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_ARTIST_PENDING, false)
                && rvFeed != null) {
            // Hộp thoại gợi ý đang mở / đang tải khi xoay màn hình -> hiện lại cho người dùng trả lời
            rvFeed.post(this::showArtistSuggestions);
        }
    }

    /** Khôi phục bộ lọc + cờ trạng thái đã lưu trong onSaveInstanceState (gọi trước refreshFeed). */
    private void restoreFeedState(Bundle state, View layoutSearchBar) {
        activeGenreLabel = state.getString(KEY_GENRE_LABEL, "");
        String[] keywords = state.getStringArray(KEY_GENRE_KEYWORDS);
        activeGenreKeywords = keywords != null ? keywords : new String[0];
        if (!activeGenreLabel.isEmpty()) {
            for (TextView chip : genreChips) {
                if (chip != null && activeGenreLabel.equals(chip.getText().toString())) {
                    highlightGenreChip(chip);
                    break;
                }
            }
        }
        hasMoreHome = state.getBoolean(KEY_HAS_MORE_HOME, true);
        isOfflineMode = state.getBoolean(KEY_OFFLINE_MODE, false);
        if (layoutSearchBar != null && state.getBoolean(KEY_SEARCH_BAR_VISIBLE, false)) {
            layoutSearchBar.setVisibility(View.VISIBLE);
        }
        restoreFeedScroll(state.getInt(KEY_FEED_ANCHOR_POS, -1), state.getInt(KEY_FEED_ANCHOR_OFFSET, 0));
    }

    /**
     * Cuộn lại đúng bài viết đang xem trước khi xoay. Không dùng scrollY thô vì bố cục dọc / ngang
     * khác nhau (chip thể loại nằm trên danh sách ở màn dọc, nằm ở cột trái ở màn ngang).
     */
    private void restoreFeedScroll(int anchorPos, int anchorOffset) {
        if (anchorPos < 0 || rvFeed == null || nestedScrollMain == null) return;
        rvFeed.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            private int attempts = 0;

            @Override
            public void onGlobalLayout() {
                RecyclerView.LayoutManager lm = rvFeed.getLayoutManager();
                View anchor = lm != null ? lm.findViewByPosition(anchorPos) : null;
                // Danh sách chưa vẽ xong: đợi thêm vài lượt layout
                if (anchor == null && ++attempts < 5 && anchorPos < postList.size()) return;
                rvFeed.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (anchor != null) {
                    int y = Math.max(0, rvFeed.getTop() + anchor.getTop() + anchorOffset);
                    nestedScrollMain.post(() -> nestedScrollMain.scrollTo(0, y));
                }
            }
        });
    }

    /**
     * Tải lại trang hiện tại (bảng tin hoặc danh mục đang chọn) từ Firebase.
     * Offline: chỉ đọc lại SQLite, giữ nguyên giao diện và dữ liệu đang chờ đồng bộ.
     *
     * @param userInitiated người dùng chủ động làm mới (vuốt / bấm lại Home) → hiện vòng tải và kết quả
     */
    private void reloadFromServer(boolean reset, boolean userInitiated) {
        if (isReloading) {
            // Đang có một lượt tải: không gửi trùng, chỉ hẹn tải lại một lần sau khi lượt hiện tại xong
            reloadQueued = true;
            queuedUserInitiated |= userInitiated;
            return;
        }
        isReloading = true;
        currentReloadUserInitiated = userInitiated;
        if (userInitiated && swipeRefresh != null) swipeRefresh.setRefreshing(true);
        String label = activeGenreLabel;
        String[] keywords = activeGenreKeywords;
        SyncManager.Callback cb = (status, more) -> {
            isReloading = false;
            if (isFinishing() || isDestroyed()) return;
            boolean stillCurrent = label.equals(activeGenreLabel);
            isOfflineMode = status == SyncManager.Status.OFFLINE;
            if (status == SyncManager.Status.OK && label.isEmpty()) hasMoreHome = more;
            refreshFeed();
            if (reloadQueued || !stillCurrent) {
                // Bộ lọc đã đổi trong lúc tải hoặc người dùng bấm thêm: tải lại đúng trang hiện tại một lần nữa
                boolean again = queuedUserInitiated || userInitiated;
                reloadQueued = false;
                queuedUserInitiated = false;
                reloadFromServer(true, again);
                return;
            }
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            if (userInitiated) {
                if (status == SyncManager.Status.OK) {
                    Toast.makeText(this, "Đã cập nhật bảng tin mới nhất", Toast.LENGTH_SHORT).show();
                } else if (status == SyncManager.Status.OFFLINE) {
                    Toast.makeText(this, "Đang offline — đang hiển thị dữ liệu đã lưu trên máy", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Không tải được bảng tin mới. Vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                }
            }
        };
        if (label.isEmpty()) {
            sync.refreshHome(reset, cb);
        } else {
            sync.refreshGenre(label, keywords, cb);
        }
    }

    /**
     * Bấm lại tab Home khi đang ở Bảng tin:
     * lần 1 → thoát tìm kiếm (nếu có) và cuộn lên đầu bảng tin; lần 2 (đã ở đầu) → tải lại dữ liệu mới nhất.
     */
    @Override
    protected void onNavTabReselected() {
        boolean searching = etSearch != null && etSearch.getText().toString().trim().length() > 0;
        boolean searchBarOpen = layoutSearchBarRef != null && layoutSearchBarRef.getVisibility() == View.VISIBLE;
        boolean atTop = nestedScrollMain == null || nestedScrollMain.getScrollY() == 0;
        if (searching || searchBarOpen || !atTop) {
            if (searching) etSearch.setText("");
            if (layoutSearchBarRef != null) layoutSearchBarRef.setVisibility(View.GONE);
            if (etSearch != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
            if (nestedScrollMain != null) nestedScrollMain.smoothScrollTo(0, 0);
        } else {
            reloadFromServer(true, true);
        }
    }

    /**
     * Bước gợi ý theo dõi cho tài khoản mới: chỉ gợi ý các thành viên đã đăng ký thật; chỉ ghi quan hệ
     * theo dõi sau khi người dùng xác nhận, có thể bỏ qua. Chưa có ai để gợi ý thì bỏ qua bước này.
     */
    private void showArtistSuggestions() {
        String me = SessionManager.getCurrentUsername(this);
        if (me == null || isFinishing()) return;
        artistSuggestionsPending = true;
        sync.loadSuggestedUsers(me, 8, users -> {
            if (isFinishing() || isDestroyed()) return;
            List<UserModel> candidates = new ArrayList<>();
            for (UserModel u : users) {
                if (!dbHelper.isUserFollowed(me, u.getUsername())) candidates.add(u);
            }
            if (candidates.isEmpty()) {
                artistSuggestionsPending = false;
                AuthManager.markOnboardingDone(this, me);
                return;
            }
            showArtistSuggestionsDialog(me, candidates);
        });
    }

    private void showArtistSuggestionsDialog(String me, List<UserModel> candidates) {
        View view = getLayoutInflater().inflate(R.layout.dialog_artist_suggestions, null, false);
        android.widget.LinearLayout container = view.findViewById(R.id.layout_artist_options);
        androidx.appcompat.widget.AppCompatButton btnConfirm = view.findViewById(R.id.btn_artist_confirm);
        View btnSkip = view.findViewById(R.id.btn_artist_skip);
        java.util.Set<String> selected = new java.util.LinkedHashSet<>();
        Runnable updateConfirm = () -> {
            btnConfirm.setText(selected.isEmpty() ? "Theo dõi" : "Theo dõi (" + selected.size() + ")");
            btnConfirm.setEnabled(!selected.isEmpty());
            btnConfirm.setAlpha(selected.isEmpty() ? 0.5f : 1f);
        };
        float density = getResources().getDisplayMetrics().density;
        for (UserModel u : candidates) {
            String artist = u.getUsername();
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(u.getFullName() + "  •  @" + artist + "\n" + u.getRole());
            cb.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            cb.setTextSize(14);
            cb.setPadding((int) (8 * density), (int) (10 * density), 0, (int) (10 * density));
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selected.add(artist); else selected.remove(artist);
                updateConfirm.run();
            });
            container.addView(cb);
        }
        updateConfirm.run();

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();
        artistDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnSkip.setOnClickListener(v -> {
            artistSuggestionsPending = false;
            AuthManager.markOnboardingDone(this, me);
            dialog.dismiss();
            Toast.makeText(this, "Bạn có thể theo dõi thành viên khác bất cứ lúc nào từ trang cá nhân của họ.", Toast.LENGTH_LONG).show();
        });
        btnConfirm.setOnClickListener(v -> {
            int count = 0;
            for (String artist : selected) {
                if (dbHelper.followUser(me, artist)) count++;
            }
            artistSuggestionsPending = false;
            AuthManager.markOnboardingDone(this, me);
            dialog.dismiss();
            Toast.makeText(this, "Đã theo dõi " + count + " thành viên!", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void loadMoreHome() {
        boolean searching = etSearch != null && etSearch.getText().toString().trim().length() > 0;
        if (isLoadingMore || !hasMoreHome || searching || !activeGenreLabel.isEmpty() || !sync.isOnline()) return;
        isLoadingMore = true;
        updateFeedFooter();
        sync.refreshHome(false, (status, more) -> {
            if (isFinishing() || isDestroyed()) return;
            isLoadingMore = false;
            if (status == SyncManager.Status.OK) hasMoreHome = more;
            refreshFeed();
        });
    }

    /** Dòng trạng thái cuối danh sách. */
    private void updateFeedFooter() {
        if (tvFeedFooter == null) return;
        boolean searching = etSearch != null && etSearch.getText().toString().trim().length() > 0;
        String text = null;
        if (searching) {
            text = null;
        } else if (isOfflineMode) {
            String key = activeGenreLabel.isEmpty() ? DatabaseHelper.FEED_HOME : DatabaseHelper.feedGenre(activeGenreLabel);
            long updatedAt = dbHelper.getFeedUpdatedAt(key);
            text = "Đang offline — hiển thị dữ liệu đã lưu"
                    + (updatedAt > 0 ? " (cập nhật " + DatabaseHelper.formatTimeAgo(updatedAt).toLowerCase() + ")" : "");
        } else if (isLoadingMore) {
            text = "Đang tải thêm bài viết...";
        } else if (activeGenreLabel.isEmpty() && !hasMoreHome && !postList.isEmpty()) {
            text = "Bạn đã xem hết bài viết mới";
        }
        tvFeedFooter.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) tvFeedFooter.setText(text);
    }

    @Override
    protected void onLocalDataChanged() {
        refreshFeed();
        updateNotificationBadge();
    }

    @Override
    protected void onReconnectedRefresh() {
        reloadFromServer(true, false);
    }

    private void setupGenreFilterChips() {
        chipGenreAll = findViewById(R.id.chip_genre_all);
        TextView chipVpop = findViewById(R.id.chip_genre_vpop);
        TextView chipRap = findViewById(R.id.chip_genre_rap);
        TextView chipChill = findViewById(R.id.chip_genre_chill);
        TextView chipRnb = findViewById(R.id.chip_genre_rnb);

        genreChips = Arrays.asList(chipGenreAll, chipVpop, chipRap, chipChill, chipRnb);

        bindGenreChip(chipGenreAll);
        bindGenreChip(chipVpop, "V-Pop");
        bindGenreChip(chipRap, "Rap", "Hip-Hop");
        bindGenreChip(chipChill, "Indie", "Chill", "Acoustic", "Ballad");
        bindGenreChip(chipRnb, "R&B", "Retro");
    }

    /** Gắn sự kiện cho chip; không truyền từ khóa nghĩa là chip "Tất cả". */
    private void bindGenreChip(TextView chip, String... keywords) {
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            highlightGenreChip(chip);
            activeGenreLabel = keywords.length == 0 ? "" : chip.getText().toString();
            activeGenreKeywords = keywords;
            // Xóa ô tìm kiếm để kết quả chỉ theo thể loại vừa chọn
            if (etSearch != null && etSearch.getText().length() > 0) {
                isClearingSearchForGenre = true;
                etSearch.setText("");
                isClearingSearchForGenre = false;
            }
            // Hiện ngay bản đã lưu của danh mục, rồi tải bản mới nhất
            refreshFeed();
            reloadFromServer(true, false);
        });
    }

    private void highlightGenreChip(TextView selected) {
        int whiteColor = getResources().getColor(R.color.white);
        int primaryText = getResources().getColor(R.color.colorTextPrimary);

        for (TextView chip : genreChips) {
            if (chip == null) continue;
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(whiteColor);
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(primaryText);
                chip.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    /** Áp dụng bộ lọc hiện hành: ưu tiên từ khóa tìm kiếm, sau đó đến chip thể loại. */
    private void refreshFeed() {
        String query = etSearch != null ? etSearch.getText().toString().trim() : "";
        loadPostsFromDatabase(query);
        if (!query.isEmpty()) {
            applyFilter(query, query);
        } else if (!activeGenreLabel.isEmpty()) {
            applyFilter(activeGenreLabel, activeGenreKeywords);
        } else {
            applyFilter("");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentSearchQuery(intent);
    }

    private void handleIntentSearchQuery(Intent intent) {
        if (intent != null && intent.hasExtra("SEARCH_QUERY")) {
            String q = intent.getStringExtra("SEARCH_QUERY");
            intent.removeExtra("SEARCH_QUERY");
            if (q != null && !q.trim().isEmpty()) {
                View layoutSearchBar = findViewById(R.id.layout_search_bar);
                if (layoutSearchBar != null) {
                    layoutSearchBar.setVisibility(View.VISIBLE);
                }
                if (etSearch != null) {
                    etSearch.setText(q);
                    etSearch.setSelection(q.length());
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đọc lại SQLite khi quay lại (ví dụ vừa đăng bài / thả tim ở màn hình khác)
        // Luôn lọc lại (giữ chip thể loại / từ khóa đang chọn, ẩn bài riêng tư của người khác)
        refreshFeed();
        handleIntentSearchQuery(getIntent());
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
        updateNotificationBadge();
        // Tải thông báo của tài khoản hiện tại để chấm đỏ trên chuông luôn đúng
        sync.refreshNotifications(false, (status, more) -> {
            if (!isFinishing() && !isDestroyed()) updateNotificationBadge();
        });
    }

    /**
     * Override để đồng bộ UI bài viết ngay khi trạng thái nhạc thay đổi
     * (phát / dừng từ mini player hoặc bất kỳ nơi nào) mà không cần đợi
     * người dùng chuyển trang rồi quay lại.
     */
    @Override
    public void updateMiniPlayerUi() {
        super.updateMiniPlayerUi();
        // Cập nhật ngay trạng thái nút play / hiệu ứng equalizer / đĩa vinyl
        // trên từng card bài viết trong feed
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Nguồn dữ liệu từ SQLite: tìm kiếm thì tìm trong mọi bài đã lưu,
     * danh mục thì lấy trang danh mục đã lưu, còn lại là bảng tin.
     */
    private void loadPostsFromDatabase(String query) {
        List<PostModel> source;
        if (!query.isEmpty()) {
            source = dbHelper.getAllPosts();
        } else if (!activeGenreLabel.isEmpty()) {
            source = dbHelper.getFeedPosts(DatabaseHelper.feedGenre(activeGenreLabel));
            // Danh mục chưa từng tải: lọc tạm trong các bài đã lưu
            if (source.isEmpty()) source = dbHelper.getAllPosts();
        } else {
            source = dbHelper.getFeedPosts(DatabaseHelper.FEED_HOME);
            if (source.isEmpty()) source = dbHelper.getAllPosts();
        }
        originalList.clear();
        originalList.addAll(source);
    }

    /**
     * Lọc bảng tin.
     * @param label    chuỗi hiển thị trong thông báo kết quả (rỗng = hiện tất cả)
     * @param keywords bài viết khớp nếu chứa bất kỳ từ khóa nào
     */
    private void applyFilter(String label, String... keywords) {
        String cleanQuery = label != null ? label.trim() : "";
        List<PostModel> filtered = new ArrayList<>();
        if (cleanQuery.isEmpty()) {
            for (PostModel p : originalList) {
                // Bài riêng tư của người khác không hiển thị trên bảng tin công khai
                if (!p.isPrivate() || SessionManager.isCurrentUser(this, p.getAuthorUsername())) {
                    filtered.add(p);
                }
            }
            if (tvSearchSummary != null) tvSearchSummary.setVisibility(View.GONE);
            if (layoutSearchEmpty != null) layoutSearchEmpty.setVisibility(View.GONE);
            if (rvFeed != null) rvFeed.setVisibility(View.VISIBLE);
            if (tvFeedTitle != null) tvFeedTitle.setText("Bảng tin âm nhạc");
        } else {
            for (PostModel post : originalList) {
                // Kiểm tra quyền xem nếu là bài riêng tư
                if (post.isPrivate() && !SessionManager.isCurrentUser(this, post.getAuthorUsername())) {
                    continue;
                }

                for (String keyword : keywords) {
                    String kw = keyword != null ? keyword.trim() : "";
                    if (kw.isEmpty()) continue;
                    String normQuery = normalize(kw);
                    String noHashQuery = normQuery.startsWith("#") ? normQuery.substring(1) : normQuery;
                    if (matchesPost(post, kw.toLowerCase(), normQuery, noHashQuery)) {
                        filtered.add(post);
                        break;
                    }
                }
            }

            if (filtered.isEmpty()) {
                if (rvFeed != null) rvFeed.setVisibility(View.GONE);
                if (layoutSearchEmpty != null) layoutSearchEmpty.setVisibility(View.VISIBLE);
                if (tvSearchSummary != null) tvSearchSummary.setVisibility(View.GONE);
                if (tvSearchEmptyMsg != null) {
                    tvSearchEmptyMsg.setText("Không tìm thấy kết quả phù hợp cho: \"" + cleanQuery + "\".\nHãy thử tìm theo tên bài hát, nghệ sĩ, thể loại hoặc hashtag khác!");
                }
                if (tvFeedTitle != null) tvFeedTitle.setText("Không có kết quả");
            } else {
                if (rvFeed != null) rvFeed.setVisibility(View.VISIBLE);
                if (layoutSearchEmpty != null) layoutSearchEmpty.setVisibility(View.GONE);
                if (tvSearchSummary != null) {
                    tvSearchSummary.setVisibility(View.VISIBLE);
                    tvSearchSummary.setText("Tìm thấy " + filtered.size() + " kết quả cho: \"" + cleanQuery + "\"");
                }
                if (tvFeedTitle != null) tvFeedTitle.setText("Kết quả tìm kiếm (" + filtered.size() + ")");
            }
        }
        postList.clear();
        postList.addAll(filtered);
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
        updateFeedFooter();
    }

    private boolean matchesPost(PostModel p, String lower, String normQuery, String noHashQuery) {
        // Tìm theo hashtag (#rap): phải khớp đúng nguyên hashtag / thể loại, "#rap" không khớp "#Trap"
        if (normQuery.startsWith("#")) {
            return MusicChart.hasHashtag(noHashQuery, p.getHashtags(), p.getCaption(), p.getGenre());
        }
        return checkContains(p.getSongTitle(), lower, normQuery, noHashQuery)
                || checkContains(p.getArtistName(), lower, normQuery, noHashQuery)
                || checkContains(p.getAuthorName(), lower, normQuery, noHashQuery)
                || checkContains(p.getAuthorUsername(), lower, normQuery, noHashQuery)
                || checkContains(p.getGenre(), lower, normQuery, noHashQuery)
                || checkContains(p.getHashtags(), lower, normQuery, noHashQuery)
                || checkContains(p.getCaption(), lower, normQuery, noHashQuery);
    }

    private boolean checkContains(String target, String lower, String normQuery, String noHashQuery) {
        if (target == null) return false;
        String tLower = target.toLowerCase();
        if (tLower.contains(lower)) return true;

        String tNorm = normalize(target);
        return tNorm.contains(normQuery) || (!noHashQuery.isEmpty() && tNorm.contains(noHashQuery));
    }

    private String normalize(String str) {
        if (str == null) return "";
        String nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        return java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                .matcher(nfd)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase()
                .trim();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (etSearch != null) {
            outState.putString("search_query", etSearch.getText().toString());
        }
        outState.putString(KEY_GENRE_LABEL, activeGenreLabel);
        outState.putStringArray(KEY_GENRE_KEYWORDS, activeGenreKeywords);
        outState.putBoolean(KEY_SEARCH_BAR_VISIBLE,
                layoutSearchBarRef != null && layoutSearchBarRef.getVisibility() == View.VISIBLE);
        outState.putBoolean(KEY_HAS_MORE_HOME, hasMoreHome);
        outState.putBoolean(KEY_OFFLINE_MODE, isOfflineMode);
        outState.putBoolean(KEY_WAS_RELOADING, isReloading || reloadQueued);
        outState.putBoolean(KEY_RELOAD_USER_INITIATED, currentReloadUserInitiated || queuedUserInitiated);
        outState.putBoolean(KEY_ARTIST_PENDING, artistSuggestionsPending);
        saveFeedAnchor(outState);
    }

    /**
     * Ghi lại bài viết đang nằm ở đầu vùng nhìn thấy (vị trí trong adapter + độ lệch).
     * RecyclerView nằm trong NestedScrollView nên scrollY tự lưu không còn khớp khi đổi bố cục dọc / ngang.
     */
    private void saveFeedAnchor(Bundle outState) {
        if (rvFeed == null || nestedScrollMain == null || rvFeed.getVisibility() != View.VISIBLE) return;
        int scrollY = nestedScrollMain.getScrollY();
        int rvTop = rvFeed.getTop();
        if (scrollY <= rvTop) return;
        for (int i = 0; i < rvFeed.getChildCount(); i++) {
            View child = rvFeed.getChildAt(i);
            if (rvTop + child.getBottom() > scrollY) {
                int pos = rvFeed.getChildAdapterPosition(child);
                if (pos != RecyclerView.NO_POSITION) {
                    outState.putInt(KEY_FEED_ANCHOR_POS, pos);
                    outState.putInt(KEY_FEED_ANCHOR_OFFSET, scrollY - (rvTop + child.getTop()));
                }
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        // Đóng hộp thoại gợi ý nghệ sĩ để tránh rò rỉ cửa sổ khi xoay màn hình (Activity mới sẽ hiện lại)
        if (artistDialog != null && artistDialog.isShowing()) {
            artistDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString("search_query", "");
            if (etSearch != null && !savedQuery.isEmpty()) {
                etSearch.setText(savedQuery);
            }
        }
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_home;
    }
}