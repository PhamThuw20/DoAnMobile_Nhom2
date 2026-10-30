package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Khám phá (Bài 2 - Activities & Bài 8 - Multimedia), chỉ gồm 2 khu vực:
 * 1. Bảng xếp hạng âm nhạc: tự xếp theo tổng lượt thả tim của từng bài hát, cập nhật khi có người thả / bỏ tim.
 * 2. Vòng xoay đĩa than: lướt chọn bài hát nổi bật một cách trực quan.
 */
public class DiscoverActivity extends BaseActivity {

    // Khóa lưu trạng thái khi xoay màn hình (Activity bị hủy và tạo lại)
    private static final String STATE_SEARCH_OPEN = "state_discover_search_open";
    private static final String STATE_WHEEL_REVEALED = "state_discover_wheel_revealed";
    private static final String STATE_WHEEL_CENTER_POST = "state_discover_wheel_center_post";

    private DatabaseHelper dbHelper;

    // Bảng xếp hạng
    private View cardTop1, headerChart;
    private TextView tvBannerTitle, tvBannerArtist, tvChartEmpty;
    private ImageView btnBannerPlay;
    private RecyclerView rvChart;
    private TrackListAdapter chartAdapter;
    private final List<MusicChart.Entry> chartEntries = new ArrayList<>();
    /** Các hàng đang hiển thị (đã lọc theo ô tìm kiếm). */
    private final List<PostModel> chartShown = new ArrayList<>();
    private final List<Integer> chartShownLikes = new ArrayList<>();

    // Vòng xoay đĩa than (cuối trang)
    private DiscWheelView discWheel;
    private View discWheelSection;
    private final List<PostModel> wheelPosts = new ArrayList<>();

    // Tìm kiếm tại chỗ: danh sách kết quả riêng
    private String searchQuery = "";
    private RecyclerView rvSearchResults;
    private TrackListAdapter searchAdapter;
    private final List<PostModel> searchResults = new ArrayList<>();
    private View layoutEmpty;
    private TextView tvEmptyMsg, tvSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bơm giao diện Khám phá vào FrameLayout của BaseActivity
        setContentLayout(R.layout.activity_discover);
        dbHelper = DatabaseHelper.getInstance(this);

        // Xoay màn hình: khôi phục ô tìm kiếm đang mở, hiệu ứng vòng xoay đã chạy và đĩa đang nằm giữa.
        // Nội dung ô tìm kiếm do EditText tự khôi phục (có id) → TextWatcher gọi lại applySearch().
        if (savedInstanceState != null) {
            wheelRevealed = savedInstanceState.getBoolean(STATE_WHEEL_REVEALED, false);
        }

        setupChart();
        setupSearch();
        setupDiscWheel(savedInstanceState);
        if (savedInstanceState != null) {
            View layoutSearchBar = findViewById(R.id.layout_discover_search_bar);
            if (layoutSearchBar != null && savedInstanceState.getBoolean(STATE_SEARCH_OPEN, false)) {
                layoutSearchBar.setVisibility(View.VISIBLE);
            }
        }
        loadChart();
        refreshChartFromServer();
    }

    // ===================================================================================
    // Bảng xếp hạng
    // ===================================================================================

    private void setupChart() {
        cardTop1 = findViewById(R.id.card_top1_banner);
        headerChart = findViewById(R.id.layout_chart_section_header);
        tvBannerTitle = findViewById(R.id.tv_banner_title);
        tvBannerArtist = findViewById(R.id.tv_banner_artist);
        btnBannerPlay = findViewById(R.id.btn_banner_play);
        tvChartEmpty = findViewById(R.id.tv_discover_chart_empty);
        rvChart = findViewById(R.id.rv_discover_chart);

        rvChart.setLayoutManager(new LinearLayoutManager(this));
        chartAdapter = new TrackListAdapter(this, chartShown, (track, position) -> togglePlay(track));
        chartAdapter.setRankStyle(true);
        chartAdapter.setRankOffset(1);
        chartAdapter.setSubtitleProvider((track, position) ->
                track.getArtistName() + " · " + MusicChart.likesLabel(chartShownLikes.get(position)));
        chartAdapter.setOnTrackLongClickListener((track, position) -> openPost(track));
        rvChart.setAdapter(chartAdapter);

        View.OnClickListener playTop1 = v -> {
            if (!chartEntries.isEmpty()) togglePlay(chartEntries.get(0).post);
        };
        if (btnBannerPlay != null) btnBannerPlay.setOnClickListener(playTop1);
        if (cardTop1 != null) cardTop1.setOnClickListener(playTop1);
    }

    /** Đọc lại bảng xếp hạng từ SQLite (gọi mỗi khi lượt tim thay đổi: mình thả tim, realtime, quay lại màn hình). */
    private void loadChart() {
        chartEntries.clear();
        // Bài mình tự ẩn vẫn được cộng tim cho bài hát (ẩn chỉ áp dụng cho bảng tin của mình)
        chartEntries.addAll(MusicChart.rank(dbHelper.getChartPosts(), MusicChart.TOP_SIZE, dbHelper.getHiddenPostIds()));
        applySearch();
    }

    /** Lấy các bài nhiều tim nhất trên toàn hệ thống từ Firebase (có mạng), rồi xếp hạng lại. */
    private void refreshChartFromServer() {
        SyncManager.get(this).refreshChart((status, more) -> {
            if (isFinishing() || isDestroyed()) return;
            if (status == SyncManager.Status.OK) {
                loadChart();
                loadWheelPosts();
            }
        });
    }

    private void togglePlay(PostModel post) {
        if (post.getId() != null && post.getId().equals(MusicService.currentPostId) && MusicService.isPlaying) {
            MusicService.toggle(this);
        } else {
            MusicService.play(this, post);
            MusicService.announce(this, post);
        }
    }

    private void openPost(PostModel post) {
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
        startActivity(intent);
    }

    // ===================================================================================
    // Tìm kiếm tại chỗ (danh sách kết quả riêng, không gộp Bảng xếp hạng / Vòng xoay đĩa than)
    // ===================================================================================

    private void setupSearch() {
        View layoutSearchBar = findViewById(R.id.layout_discover_search_bar);
        android.widget.EditText etSearch = findViewById(R.id.et_discover_search);
        ImageView ivClearSearch = findViewById(R.id.iv_clear_discover_search);
        View btnReset = findViewById(R.id.btn_reset_discover_search);
        tvSummary = findViewById(R.id.tv_discover_search_summary);
        layoutEmpty = findViewById(R.id.layout_discover_search_empty);
        tvEmptyMsg = findViewById(R.id.tv_discover_search_empty_msg);
        rvSearchResults = findViewById(R.id.rv_discover_search_results);
        if (rvSearchResults != null) {
            rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
            searchAdapter = new TrackListAdapter(this, searchResults, (track, position) -> togglePlay(track));
            searchAdapter.setOnTrackLongClickListener((track, position) -> openPost(track));
            rvSearchResults.setAdapter(searchAdapter);
        }

        findViewById(R.id.iv_discover_search).setOnClickListener(v -> {
            if (layoutSearchBar == null) return;
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

        if (ivClearSearch != null && etSearch != null) {
            ivClearSearch.setOnClickListener(v -> etSearch.setText(""));
        }
        if (btnReset != null && etSearch != null) {
            btnReset.setOnClickListener(v -> etSearch.setText(""));
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().trim();
                    if (ivClearSearch != null) {
                        ivClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    applySearch();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    /**
     * Không tìm kiếm: hiện Bảng xếp hạng + Vòng xoay đĩa than.
     * Đang tìm kiếm: chỉ hiện danh sách kết quả riêng (mỗi bài hát một hàng) và dòng tóm tắt đếm ĐÚNG số hàng đó,
     * không cộng thêm bài của Bảng xếp hạng hay Vòng xoay đĩa than.
     */
    private void applySearch() {
        String q = MusicChart.normalize(searchQuery);
        boolean searching = !q.isEmpty();

        // Hạng #1 đã nằm ở banner Top 1 phía trên: danh sách bắt đầu từ hạng #2 để không lặp lại
        chartShown.clear();
        chartShownLikes.clear();
        for (int i = 1; i < chartEntries.size(); i++) {
            chartShown.add(chartEntries.get(i).post);
            chartShownLikes.add(chartEntries.get(i).totalLikes);
        }
        chartAdapter.notifyDataSetChanged();

        // Banner Top 1: luôn là bài nhiều tim nhất
        MusicChart.Entry top1 = chartEntries.isEmpty() ? null : chartEntries.get(0);
        if (cardTop1 != null) cardTop1.setVisibility(top1 != null && !searching ? View.VISIBLE : View.GONE);
        if (top1 != null) {
            if (tvBannerTitle != null) tvBannerTitle.setText(top1.post.getSongTitle());
            if (tvBannerArtist != null) {
                tvBannerArtist.setText(top1.post.getArtistName() + " · " + MusicChart.likesLabel(top1.totalLikes));
            }
        }
        if (headerChart != null) headerChart.setVisibility(searching ? View.GONE : View.VISIBLE);
        rvChart.setVisibility(!searching && !chartShown.isEmpty() ? View.VISIBLE : View.GONE);
        if (tvChartEmpty != null) tvChartEmpty.setVisibility(!searching && chartEntries.isEmpty() ? View.VISIBLE : View.GONE);

        applyWheelFilter(searching);

        searchResults.clear();
        if (searching) searchResults.addAll(MusicChart.search(dbHelper.getAllPosts(), searchQuery));
        if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
        if (rvSearchResults != null) rvSearchResults.setVisibility(searchResults.isEmpty() ? View.GONE : View.VISIBLE);
        updatePlayButtons();

        if (!searching) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (tvSummary != null) tvSummary.setVisibility(View.GONE);
            return;
        }
        int matchCount = searchResults.size();
        if (tvSummary != null) {
            tvSummary.setVisibility(View.VISIBLE);
            tvSummary.setText(searchSummary(matchCount, searchQuery));
        }
        if (layoutEmpty != null) layoutEmpty.setVisibility(matchCount == 0 ? View.VISIBLE : View.GONE);
        if (tvEmptyMsg != null && matchCount == 0) {
            tvEmptyMsg.setText("Không tìm thấy bài hát nào phù hợp với từ khóa \"" + searchQuery + "\".");
        }
    }

    /** Dòng tóm tắt kết quả tìm kiếm; con số luôn bằng số hàng bài hát đang hiển thị. */
    static String searchSummary(int count, String query) {
        return "Tìm thấy " + count + " bài hát cho: \"" + query + "\"";
    }

    // ===================================================================================
    // Vòng xoay đĩa than
    // ===================================================================================

    /** Vòng xoay đĩa than: bài viết công khai trong app, hiện dần khi người dùng lướt tới. */
    private void setupDiscWheel(Bundle savedInstanceState) {
        discWheelSection = findViewById(R.id.layout_disc_wheel_section);
        discWheel = findViewById(R.id.rv_disc_wheel);
        if (discWheel == null || discWheelSection == null) return;
        // Xoay màn hình: đưa lại đúng đĩa đang nằm giữa trước khi nạp danh sách
        if (savedInstanceState != null) {
            discWheel.setPendingCenterPostId(savedInstanceState.getString(STATE_WHEEL_CENTER_POST));
        }

        discWheel.setListener(new DiscWheelView.Listener() {
            @Override
            public void onPlayClick(PostModel post) {
                if (post.getId() != null && post.getId().equals(MusicService.currentPostId) && MusicService.hasTrackLoaded) {
                    MusicService.toggle(DiscoverActivity.this);
                } else {
                    MusicService.play(DiscoverActivity.this, post);
                    MusicService.announce(DiscoverActivity.this, post);
                }
            }

            @Override
            public void onOpenPost(PostModel post) {
                openPost(post);
            }
        });

        loadWheelPosts();

        // Phần vòng xoay mờ và nằm thấp hơn một chút, lướt tới đâu hiện ra tới đó rồi vòng cung bung ra
        float density = getResources().getDisplayMetrics().density;
        discWheelSection.setAlpha(0f);
        discWheelSection.setTranslationY(56 * density);
        // Hiệu ứng bung vòng cung chỉ chạy một lần, không lặp lại sau khi xoay màn hình
        if (!wheelRevealed) discWheel.hideForIntro();

        androidx.core.widget.NestedScrollView scroll = findViewById(R.id.scroll_discover);
        if (scroll != null) {
            scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                    (v, x, y, oldX, oldY) -> revealDiscWheelIfVisible(scroll));
            scroll.post(() -> revealDiscWheelIfVisible(scroll));
        }
    }

    private boolean wheelRevealed = false;

    private void revealDiscWheelIfVisible(androidx.core.widget.NestedScrollView scroll) {
        if (discWheelSection == null) return;
        int[] sectionPos = new int[2];
        int[] scrollPos = new int[2];
        discWheelSection.getLocationOnScreen(sectionPos);
        scroll.getLocationOnScreen(scrollPos);
        int visibleTop = sectionPos[1] - scrollPos[1];
        int viewport = scroll.getHeight();
        if (viewport == 0) return;

        // Hiện dần theo quãng đường lướt: bắt đầu khi đầu phần lọt vào màn hình, rõ hẳn khi đã lên 1/3 màn hình
        float progress = (viewport - (float) visibleTop) / (viewport / 3f);
        progress = Math.max(0f, Math.min(1f, progress));
        float density = getResources().getDisplayMetrics().density;
        discWheelSection.setAlpha(progress);
        discWheelSection.setTranslationY((1f - progress) * 56 * density);

        if (!wheelRevealed && progress >= 0.6f) {
            wheelRevealed = true;
            discWheel.playIntro();
        }
    }

    private void loadWheelPosts() {
        if (discWheel == null) return;
        wheelPosts.clear();
        for (PostModel p : dbHelper.getAllPosts()) {
            if (!p.isPrivate() && p.getSongTitle() != null && !p.getSongTitle().trim().isEmpty()) {
                wheelPosts.add(p);
            }
        }
        applyWheelFilter(!MusicChart.normalize(searchQuery).isEmpty());
        discWheel.setPlayingPostId(MusicService.isPlaying ? MusicService.currentPostId : null);
    }

    /** Vòng xoay đĩa than là khu vực riêng: ẩn khi đang tìm kiếm (kết quả tìm kiếm có danh sách riêng). */
    private void applyWheelFilter(boolean searching) {
        if (discWheel == null) return;
        discWheel.setPosts(new ArrayList<>(wheelPosts));
        if (discWheelSection != null) {
            discWheelSection.setVisibility(searching || wheelPosts.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    // ===================================================================================
    // Đồng bộ dữ liệu & trạng thái phát nhạc
    // ===================================================================================

    @Override
    protected void onLocalDataChanged() {
        // Lượt tim thay đổi (realtime / vừa đồng bộ): xếp hạng lại ngay
        loadChart();
        loadWheelPosts();
    }

    @Override
    protected void onReconnectedRefresh() {
        refreshChartFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lại sau khi thả / bỏ tim ở Bảng tin hoặc Chi tiết bài viết: thứ hạng cập nhật theo
        if (dbHelper != null) loadChart();
    }

    /** Đồng bộ icon Play/Pause trên BXH ngay khi MusicService đổi trạng thái (Broadcast). */
    @Override
    public void updateMiniPlayerUi() {
        super.updateMiniPlayerUi();
        updatePlayButtons();
        if (chartAdapter != null) chartAdapter.notifyDataSetChanged();
        if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
    }

    private void updatePlayButtons() {
        boolean top1Playing = MusicService.isPlaying && !chartEntries.isEmpty()
                && chartEntries.get(0).post.getId().equals(MusicService.currentPostId);
        if (btnBannerPlay != null) {
            btnBannerPlay.setImageResource(top1Playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (discWheel != null) {
            discWheel.setPlayingPostId(MusicService.isPlaying ? MusicService.currentPostId : null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        View layoutSearchBar = findViewById(R.id.layout_discover_search_bar);
        outState.putBoolean(STATE_SEARCH_OPEN, layoutSearchBar != null && layoutSearchBar.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_WHEEL_REVEALED, wheelRevealed);
        if (discWheel != null) outState.putString(STATE_WHEEL_CENTER_POST, discWheel.getCenterPostId());
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_discover;
    }
}
