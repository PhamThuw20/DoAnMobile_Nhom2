package com.example.melodyverse;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Màn hình Chi tiết bài viết (Post Detail) của MelodyVerse.
 * Hiển thị đầy đủ thông tin bài đăng, phát nhạc với đĩa quay tâm chuẩn,
 * sóng âm thanh equalizer, bình luận cộng đồng (Firebase, lưu đệm SQLite để xem offline) và bài hát tương tự.
 */
public class PostDetailActivity extends BaseActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";
    public static final String EXTRA_SCROLL_TO_COMMENTS = "EXTRA_SCROLL_TO_COMMENTS";
    /** Mở từ thông báo bình luận: làm nổi bật đúng bình luận đó. */
    public static final String EXTRA_HIGHLIGHT_COMMENT_ID = "EXTRA_HIGHLIGHT_COMMENT_ID";
    /** Mở từ thông báo lượt thích / bài mới: phát ngay bài hát của bài viết (nếu chưa phát). */
    public static final String EXTRA_AUTO_PLAY = "EXTRA_AUTO_PLAY";

    /** Thông báo khi bài viết đang xem bị xóa khỏi hệ thống hoặc bị ẩn do vi phạm. */
    public static final String POST_UNAVAILABLE_MESSAGE =
            "Bài viết này hiện không còn khả dụng do đã bị gỡ bỏ hoặc vi phạm tiêu chuẩn cộng đồng MelodyVerse.";
    private boolean closingUnavailable = false;

    private boolean pendingAutoPlay = false;
    private String highlightCommentId;

    private DatabaseHelper dbHelper;
    private PostModel post;
    private String postId;

    // View references
    private androidx.core.widget.NestedScrollView scrollPostDetail;
    private ImageView ivAvatar, ivBack, ivShareTop, ivMusicPlay, ivLike, ivComment, ivShare, ivBookmark;
    private TextView tvAuthor, tvUsername, tvTime, tvCaption, tvSongTitle, tvSongArtist, tvGenre, tvHashtags;
    private TextView tvLikeCount, tvCommentCount, tvShareCount, tvCommentsHeaderCount, tvCommentsEmpty;
    private View btnCommentSort;
    private TextView tvCommentSortLabel;
    private int currentSortType = 0; // 0: Mới nhất, 1: Cũ nhất
    // Bình luận tải thêm từ Firebase (ngoài 20 bình luận mới nhất trong SQLite); null = chỉ dùng bộ nhớ đệm
    private List<CommentModel> serverComments;
    private int commentLimit = DatabaseHelper.LIMIT_COMMENTS_PER_POST;
    private boolean loadingComments = false;
    private TextView tvCommentsMore;
    private View layoutMusicCard, frameMusicArt, btnLike, btnComment, btnShare;
    private LinearLayout layoutEqWave;
    private View eqBar1, eqBar2, eqBar3, eqBar4;
    private RecyclerView rvComments;
    private CommentAdapter commentAdapter;
    private List<CommentModel> commentList = new ArrayList<>();
    private EditText etCommentInput;
    private AppCompatButton btnSendComment;

    private ObjectAnimator detailVinylAnimator;

    // Đồng bộ Firebase
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private View layoutSimilar;
    private final List<PostModel> similarList = new ArrayList<>();
    private SimilarPostAdapter similarAdapter;
    private boolean isOfflineMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_post_detail);

        dbHelper = DatabaseHelper.getInstance(this);

        // Quản trị viên mở xem bài từ trang Admin: không hiện thanh điều hướng của thành viên
        if (bottomNavigationView != null && dbHelper.getUser(SessionManager.getCurrentUsername(this)).isAdmin()) {
            bottomNavigationView.setVisibility(View.GONE);
        }

        // Lấy ID bài viết từ Intent
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null || postId.isEmpty()) {
            postId = "p1";
        }
        pendingAutoPlay = savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_AUTO_PLAY, false);
        highlightCommentId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_COMMENT_ID);

        initViews();
        loadPostData();
        if (post == null) {
            if (!SyncManager.get(this).isOnline()) {
                // Offline và bài chưa từng được lưu trong SQLite
                Toast.makeText(this, "Bài viết chưa được lưu để xem offline. Vui lòng kết nối mạng!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Bài chưa có trong bộ nhớ đệm: tải từ Firebase rồi mới hiển thị
            if (scrollPostDetail != null) scrollPostDetail.setVisibility(View.INVISIBLE);
        }
        setupListeners();
        setupCommentsRecyclerView();
        setupCommentSortButton();
        setupSimilarPosts();
        if (post != null) {
            dbHelper.markPostViewed(postId);
            loadComments();
            loadSimilarPosts();
            maybeAutoPlay();
        }
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorAccent, R.color.colorNeonPink);
            swipeRefresh.setOnRefreshListener(this::reloadFromServer);
            if (post != null) swipeRefresh.setRefreshing(SyncManager.get(this).isOnline());
        }
        reloadFromServer();

        // Tự động cuộn đến ô bình luận nếu được mở từ nút bình luận trên bảng tin
        boolean shouldScroll = getIntent().getBooleanExtra(EXTRA_SCROLL_TO_COMMENTS, false);
        if (shouldScroll && highlightCommentId != null && !highlightCommentId.isEmpty()) {
            // Từ thông báo bình luận: cuộn tới đúng bình luận được nhắc tới (không bật bàn phím)
            scrollToHighlightedComment();
        } else if (shouldScroll && scrollPostDetail != null && etCommentInput != null) {
            scrollPostDetail.post(() -> {
                scrollPostDetail.smoothScrollTo(0, etCommentInput.getTop() - 100);
                etCommentInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etCommentInput, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    private void initViews() {
        scrollPostDetail = findViewById(R.id.scroll_post_detail);
        swipeRefresh = findViewById(R.id.swipe_refresh_detail);
        layoutSimilar = findViewById(R.id.layout_detail_similar);
        ivBack = findViewById(R.id.iv_detail_back);
        ivShareTop = findViewById(R.id.iv_detail_share_top);
        ivAvatar = findViewById(R.id.iv_detail_avatar);
        tvAuthor = findViewById(R.id.tv_detail_author);
        tvUsername = findViewById(R.id.tv_detail_username);
        tvTime = findViewById(R.id.tv_detail_time);
        tvCaption = findViewById(R.id.tv_detail_caption);

        layoutMusicCard = findViewById(R.id.layout_detail_music_card);
        frameMusicArt = findViewById(R.id.frame_detail_music_art);
        tvSongTitle = findViewById(R.id.tv_detail_song_title);
        tvSongArtist = findViewById(R.id.tv_detail_song_artist);
        ivMusicPlay = findViewById(R.id.iv_detail_music_play);

        layoutEqWave = findViewById(R.id.layout_detail_eq_wave);
        eqBar1 = findViewById(R.id.detail_eq_bar_1);
        eqBar2 = findViewById(R.id.detail_eq_bar_2);
        eqBar3 = findViewById(R.id.detail_eq_bar_3);
        eqBar4 = findViewById(R.id.detail_eq_bar_4);

        tvGenre = findViewById(R.id.tv_detail_genre);
        tvHashtags = findViewById(R.id.tv_detail_hashtags);

        btnLike = findViewById(R.id.btn_detail_like);
        ivLike = findViewById(R.id.iv_detail_like);
        tvLikeCount = findViewById(R.id.tv_detail_like_count);

        btnComment = findViewById(R.id.btn_detail_comment);
        ivComment = findViewById(R.id.iv_detail_comment);
        tvCommentCount = findViewById(R.id.tv_detail_comment_count);

        btnShare = findViewById(R.id.btn_detail_share);
        ivShare = findViewById(R.id.iv_detail_share);
        tvShareCount = findViewById(R.id.tv_detail_share_count);

        ivBookmark = findViewById(R.id.iv_detail_bookmark);

        tvCommentsHeaderCount = findViewById(R.id.tv_detail_comments_header_count);
        tvCommentsEmpty = findViewById(R.id.tv_detail_comments_empty);
        btnCommentSort = findViewById(R.id.btn_comment_sort);
        tvCommentSortLabel = findViewById(R.id.tv_comment_sort_label);
        rvComments = findViewById(R.id.rv_detail_comments);
        tvCommentsMore = findViewById(R.id.tv_detail_comments_more);
        if (tvCommentsMore != null) tvCommentsMore.setOnClickListener(v -> {
            commentLimit += DatabaseHelper.LIMIT_COMMENTS_PER_POST;
            fetchServerComments();
        });
        etCommentInput = findViewById(R.id.et_detail_comment_input);
        btnSendComment = findViewById(R.id.btn_detail_send_comment);

        // Khởi tạo hiệu ứng quay đĩa vinyl quanh chính tâm
        if (frameMusicArt != null) {
            frameMusicArt.post(() -> {
                frameMusicArt.setPivotX(frameMusicArt.getWidth() / 2f);
                frameMusicArt.setPivotY(frameMusicArt.getHeight() / 2f);
            });
            detailVinylAnimator = ObjectAnimator.ofFloat(frameMusicArt, View.ROTATION, 0f, 360f);
            detailVinylAnimator.setDuration(6000);
            detailVinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
            detailVinylAnimator.setInterpolator(new LinearInterpolator());
        }
    }

    /**
     * Tải bài viết + 20 bình luận mới nhất + bài tương tự từ Firebase (sau khi đã đẩy dữ liệu chờ),
     * lưu vào SQLite rồi hiển thị lại. Offline: giữ nguyên dữ liệu đang hiển thị.
     */
    private void reloadFromServer() {
        SyncManager.get(this).refreshPostDetail(postId, (status, more) -> {
            if (isFinishing() || isDestroyed()) return;
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            if (status == SyncManager.Status.NOT_FOUND) {
                closeAsUnavailable();
                return;
            }
            isOfflineMode = status != SyncManager.Status.OK;
            loadPostData();
            if (post == null) {
                Toast.makeText(this, "Không tải được bài viết. Vui lòng thử lại khi có mạng!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (scrollPostDetail != null) scrollPostDetail.setVisibility(View.VISIBLE);
            loadComments();
            loadSimilarPosts();
            maybeAutoPlay();
            if (highlightCommentId != null && !highlightCommentId.isEmpty()) scrollToHighlightedComment();
        });
    }

    /** Phát đúng bài hát của bài viết này (một lần, khi được mở từ thông báo). */
    private void maybeAutoPlay() {
        if (!pendingAutoPlay || post == null) return;
        pendingAutoPlay = false;
        if (MusicService.isPlaying && post.getId().equals(MusicService.currentPostId)) return; // đang phát đúng bài
        MusicService.play(this, post);
        MusicService.announce(this, post);
    }

    /** Cuộn tới bình luận được thông báo nhắc tới và làm nổi bật nó; bình luận đã bị xóa thì báo cho người dùng. */
    private void scrollToHighlightedComment() {
        if (scrollPostDetail == null || rvComments == null || commentAdapter == null) return;
        int index = -1;
        for (int i = 0; i < commentList.size(); i++) {
            if (commentList.get(i).getId().equals(highlightCommentId)) {
                index = i;
                break;
            }
        }
        commentAdapter.setHighlightedId(highlightCommentId);
        if (index < 0) {
            // Chưa tải xong (sẽ thử lại sau khi tải từ Firebase) hoặc bình luận không còn
            scrollPostDetail.post(() -> scrollPostDetail.smoothScrollTo(0, Math.max(0, rvComments.getTop() - 80)));
            if (post != null && !isOfflineMode && commentList.size() > 0 && swipeRefresh != null && !swipeRefresh.isRefreshing()) {
                Toast.makeText(this, "Bình luận này không còn hoặc nằm ngoài 20 bình luận mới nhất.", Toast.LENGTH_SHORT).show();
                highlightCommentId = null;
            }
            return;
        }
        final int target = index;
        rvComments.post(() -> {
            View child = rvComments.getLayoutManager() != null ? rvComments.getLayoutManager().findViewByPosition(target) : null;
            int offset = child != null ? child.getTop() : 0;
            scrollPostDetail.smoothScrollTo(0, Math.max(0, rvComments.getTop() + offset - 120));
        });
    }

    @Override
    protected void onLocalDataChanged() {
        // Bình luận nhãn chờ vừa gửi xong / có thay đổi realtime từ Firebase -> đọc lại SQLite
        if (post == null || closingUnavailable) return;
        if (dbHelper.getPostById(postId) == null) {
            if (SyncStatus.PENDING_DELETE.equals(dbHelper.getPostSyncStatus(postId))) {
                finish(); // chính tác giả vừa xóa bài trên máy này
                return;
            }
            // Bài vừa bị xóa trên Firebase / bị Quản trị viên gỡ bỏ hoặc ẩn do vi phạm
            closeAsUnavailable();
            return;
        }
        loadPostData();
        loadComments();
    }

    @Override
    protected void onReconnectedRefresh() {
        reloadFromServer();
    }

    /**
     * Bài viết không còn khả dụng: dừng nhạc của bài (nếu đang phát), báo cho người dùng một cách lịch sự,
     * đóng màn hình Chi tiết và đưa về Bảng tin chính để không thao tác trên dữ liệu không còn tồn tại.
     */
    private void closeAsUnavailable() {
        if (closingUnavailable || isFinishing()) return;
        closingUnavailable = true;
        MusicService.stopIfPlaying(this, postId);
        Toast.makeText(getApplicationContext(), POST_UNAVAILABLE_MESSAGE, Toast.LENGTH_LONG).show();
        boolean isAdmin = dbHelper.getUser(SessionManager.getCurrentUsername(this)).isAdmin();
        if (!isAdmin) {
            // Quản trị viên mở bài từ trang Quản trị thì chỉ đóng lại (quay về trang Quản trị)
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            overridePendingTransition(0, 0);
        }
        finish();
    }

    private void loadPostData() {
        post = dbHelper.getPostById(postId);
        if (post == null) {
            // Bài viết không còn trong CSDL (đã bị xóa hoặc không tồn tại)
            return;
        }

        tvAuthor.setText(post.getAuthorName());
        tvUsername.setText(" • @" + post.getAuthorUsername());
        tvTime.setText(post.getTimeAgo());
        tvCaption.setText(post.getCaption());

        tvSongTitle.setText(post.getSongTitle());
        tvSongArtist.setText(post.getArtistName());
        tvGenre.setText(post.getGenre());
        tvHashtags.setText(post.getHashtags() != null ? post.getHashtags() : "#MelodyVerse");

        tvLikeCount.setText(PostAdapter.likeCountText(post));
        tvCommentCount.setText(String.valueOf(post.getCommentCount()));
        tvShareCount.setText(String.valueOf(post.getShareCount()));

        updateLikeUi();
        updateBookmarkUi();
        updateMusicUi();

        // Nút sửa chỉ dành cho tác giả
        View ivEdit = findViewById(R.id.iv_detail_edit_top);
        if (ivEdit != null) {
            boolean isMine = SessionManager.isCurrentUser(this, post.getAuthorUsername());
            ivEdit.setVisibility(isMine ? View.VISIBLE : View.GONE);
            ivEdit.setOnClickListener(v -> {
                Intent editIntent = new Intent(this, CreatePostActivity.class);
                editIntent.putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, postId);
                startActivity(editIntent);
            });
        }
        View ivDelete = findViewById(R.id.iv_detail_delete_top);
        if (ivDelete != null) {
            boolean isMine = SessionManager.isCurrentUser(this, post.getAuthorUsername());
            ivDelete.setVisibility(isMine ? View.VISIBLE : View.GONE);
            ivDelete.setOnClickListener(v -> PostAdapter.confirmDeletePost(this, post, this::finish));
        }
    }

    private void setupListeners() {
        // Nút quay lại
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        // Chạm vào Tác giả hoặc Avatar -> Mở Trang cá nhân ProfileActivity
        View.OnClickListener openAuthorProfileListener = v -> {
            if (post != null && post.getAuthorUsername() != null) {
                Intent pIntent = new Intent(this, ProfileActivity.class);
                pIntent.putExtra(ProfileActivity.EXTRA_USERNAME, post.getAuthorUsername());
                startActivity(pIntent);
            }
        };
        if (ivAvatar != null) ivAvatar.setOnClickListener(openAuthorProfileListener);
        if (tvAuthor != null) tvAuthor.setOnClickListener(openAuthorProfileListener);
        if (tvUsername != null) tvUsername.setOnClickListener(openAuthorProfileListener);

        // Tương tác phát nhạc bài viết
        View.OnClickListener playListener = v -> {
            if (MusicService.isPlaying && post.getId().equals(MusicService.currentPostId)) {
                MusicService.toggle(this);
            } else {
                MusicService.play(this, post);
                MusicService.announce(this, post);
            }
            updateMusicUi();
        };
        if (ivMusicPlay != null) ivMusicPlay.setOnClickListener(playListener);
        if (layoutMusicCard != null) layoutMusicCard.setOnClickListener(playListener);

        // Nút Like
        if (btnLike != null) {
            btnLike.setOnClickListener(v -> {
                boolean newLiked = !post.isLiked();
                post.setLiked(newLiked);
                post.setLikeCount(post.getLikeCount() + (newLiked ? 1 : -1));
                updateLikeUi();

                dbHelper.updatePostLike(post.getId(), newLiked, post.getLikeCount());
                // nhãn chờ = mới lưu trên máy, chờ Firebase xác nhận (tự mất khi đồng bộ xong)
                PostModel saved = dbHelper.getPostById(post.getId());
                post.setLikePending(saved != null && saved.isLikePending());
                tvLikeCount.setText(PostAdapter.likeCountText(post));

                ivLike.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction(() ->
                        ivLike.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                ).start();
            });
        }

        // Nút Bookmark
        if (ivBookmark != null) {
            ivBookmark.setOnClickListener(v -> {
                boolean newBookmarked = !post.isBookmarked();
                post.setBookmarked(newBookmarked);
                updateBookmarkUi();

                dbHelper.updatePostBookmark(post, newBookmarked);

                String msg = newBookmarked
                    ? "Đã lưu bài hát \"" + post.getSongTitle() + "\" vào Danh sách nhạc đã lưu"
                    : "Đã bỏ bài hát khỏi Danh sách nhạc đã lưu";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });
        }

        // Nút Share -> Hộp thoại chia sẻ: sao chép liên kết bài hát
        View.OnClickListener shareListener = v -> {
            ShareHelper.showShareDialog(this, post, newCount -> {
                tvShareCount.setText(String.valueOf(newCount));
            });
        };
        if (btnShare != null) btnShare.setOnClickListener(shareListener);
        if (ivShareTop != null) ivShareTop.setOnClickListener(shareListener);

        // Nút Comment -> focus vào ô nhập
        if (btnComment != null) {
            btnComment.setOnClickListener(v -> {
                if (etCommentInput != null) {
                    etCommentInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etCommentInput, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }

        // Nút Gửi bình luận
        if (btnSendComment != null) {
            btnSendComment.setOnClickListener(v -> handleSendComment());
        }
    }

    private void handleSendComment() {
        if (etCommentInput == null) return;
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nội dung bình luận!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lưu ngay vào SQLite (PENDING nhãn chờ) rồi SyncManager tự gửi lên Firebase (ngay lập tức nếu đang có mạng)
        boolean online = SyncManager.get(this).isOnline();
        boolean success = dbHelper.addComment(post.getId(), dbHelper.getUser(SessionManager.getCurrentUsername(this)).getFullName(), content) != null;
        if (success) {
            etCommentInput.setText("");
            // Ẩn bàn phím
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            post.setCommentCount(post.getCommentCount() + 1);
            tvCommentCount.setText(String.valueOf(post.getCommentCount()));

            loadComments();

            // Tự động cuộn mượt đến danh sách bình luận để hiển thị ngay bình luận vừa gửi
            if (scrollPostDetail != null && rvComments != null) {
                scrollPostDetail.post(() -> {
                    scrollPostDetail.smoothScrollTo(0, rvComments.getTop() - 80);
                });
            }

            Toast.makeText(this, online
                    ? "Đang gửi bình luận của bạn..."
                    : "Đang offline — bình luận đã được lưu và sẽ tự gửi khi có mạng", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Có lỗi xảy ra, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCommentsRecyclerView() {
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        commentAdapter = new CommentAdapter(this, commentList);
        rvComments.setAdapter(commentAdapter);
    }

    private void setupCommentSortButton() {
        if (btnCommentSort == null) return;
        btnCommentSort.setOnClickListener(v -> {
            String[] sortOptions = new String[]{"Mới nhất", "Cũ nhất"};
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Sắp xếp bình luận");
            builder.setSingleChoiceItems(sortOptions, currentSortType, (dialog, which) -> {
                boolean orderChanged = (currentSortType == 1) != (which == 1);
                currentSortType = which;
                if (tvCommentSortLabel != null) {
                    tvCommentSortLabel.setText(sortOptions[which]);
                }
                dialog.dismiss();
                if (orderChanged && post != null && post.getCommentCount() > commentList.size()) {
                    // Chưa có đủ bình luận trên máy: "Cũ nhất" phải lấy đúng các bình luận cũ nhất từ Firebase
                    commentLimit = DatabaseHelper.LIMIT_COMMENTS_PER_POST;
                    serverComments = null;
                    if (currentSortType == 1) {
                        fetchServerComments();
                        return;
                    }
                    loadComments();
                    return;
                }
                sortComments();
            });
            builder.setNegativeButton("Đóng", null);
            builder.show();
        });
    }

    private void sortComments() {
        if (commentList == null || commentList.isEmpty()) return;
        if (currentSortType == 0) {
            // Mới nhất: thời gian đăng giảm dần
            Collections.sort(commentList, (c1, c2) -> c1.getCreatedAt() != c2.getCreatedAt()
                    ? Long.compare(c2.getCreatedAt(), c1.getCreatedAt())
                    : c2.getId().compareTo(c1.getId()));
        } else {
            // Cũ nhất: thời gian đăng tăng dần
            Collections.sort(commentList, (c1, c2) -> c1.getCreatedAt() != c2.getCreatedAt()
                    ? Long.compare(c1.getCreatedAt(), c2.getCreatedAt())
                    : c1.getId().compareTo(c2.getId()));
        }
        if (commentAdapter != null) {
            commentAdapter.notifyDataSetChanged();
        }
    }

    /** Tải {@link #commentLimit} bình luận theo thứ tự đang chọn từ Firebase rồi hiển thị lại. */
    private void fetchServerComments() {
        if (post == null || loadingComments) return;
        loadingComments = true;
        if (tvCommentsMore != null) {
            tvCommentsMore.setText("Đang tải bình luận...");
            tvCommentsMore.setEnabled(false);
        }
        SyncManager.get(this).loadComments(post.getId(), currentSortType == 1, commentLimit, comments -> {
            loadingComments = false;
            if (isFinishing() || isDestroyed()) return;
            if (tvCommentsMore != null) tvCommentsMore.setEnabled(true);
            if (comments == null) {
                Toast.makeText(this, "Không tải được bình luận. Vui lòng kiểm tra mạng!", Toast.LENGTH_SHORT).show();
            } else {
                serverComments = comments;
            }
            loadComments();
        });
    }

    private void loadComments() {
        commentList.clear();
        // 20 bình luận mới nhất trong SQLite (gồm cả bình luận đang chờ gửi) + phần tải thêm từ Firebase (không trùng)
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CommentModel c : dbHelper.getCommentsByPost(post.getId())) {
            if (seen.add(c.getId())) commentList.add(c);
        }
        if (serverComments != null) {
            for (CommentModel c : serverComments) {
                if (seen.add(c.getId())) commentList.add(c);
            }
        }
        sortComments();

        int count = commentList.size();
        int total = Math.max(count, post.getCommentCount());
        if (tvCommentsMore != null) {
            boolean more = count < total && !isOfflineMode && SyncManager.get(this).isOnline();
            tvCommentsMore.setVisibility(more ? View.VISIBLE : View.GONE);
            if (more && !loadingComments) {
                tvCommentsMore.setText("Xem thêm bình luận (còn " + (total - count) + ")");
            }
        }
        // Bài chưa từng mở khi có mạng: chưa có bình luận nào được lưu để xem offline
        boolean noOfflineComments = !dbHelper.hasCachedComments(postId)
                && (isOfflineMode || !SyncManager.get(this).isOnline());
        if (tvCommentsHeaderCount != null) {
            tvCommentsHeaderCount.setText(Math.max(count, post.getCommentCount()) + " bình luận");
        }
        if (tvCommentsEmpty != null) {
            tvCommentsEmpty.setText(noOfflineComments
                    ? "Chưa có dữ liệu bình luận ngoại tuyến. Vui lòng kết nối mạng để tải bình luận."
                    : "Chưa có bình luận nào. Hãy là người đầu tiên chia sẻ cảm nghĩ!");
            tvCommentsEmpty.setVisibility(count == 0 || noOfflineComments ? View.VISIBLE : View.GONE);
        }
        if (rvComments != null) {
            rvComments.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            rvComments.requestLayout();
        }
    }

    private void setupSimilarPosts() {
        RecyclerView rvSimilar = findViewById(R.id.rv_detail_similar);
        if (rvSimilar == null) return;
        rvSimilar.setLayoutManager(new LinearLayoutManager(this));
        similarAdapter = new SimilarPostAdapter(this, similarList);
        rvSimilar.setAdapter(similarAdapter);
    }

    /** Bài tương tự đã lưu trong SQLite (tối đa 5 bài cùng thể loại). */
    private void loadSimilarPosts() {
        similarList.clear();
        for (PostModel p : dbHelper.getFeedPosts(DatabaseHelper.feedSimilar(postId))) {
            if (!p.getId().equals(postId)) similarList.add(p);
        }
        if (similarAdapter != null) similarAdapter.notifyDataSetChanged();
        if (layoutSimilar != null) layoutSimilar.setVisibility(similarList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateLikeUi() {
        int color = post.isLiked()
                ? ContextCompat.getColor(this, R.color.colorNeonPink)
                : ContextCompat.getColor(this, R.color.colorTextSecondary);
        ivLike.setImageTintList(ColorStateList.valueOf(color));
        tvLikeCount.setTextColor(color);
    }

    private void updateBookmarkUi() {
        int color = post.isBookmarked()
                ? ContextCompat.getColor(this, R.color.colorBookmarkActive)
                : ContextCompat.getColor(this, R.color.colorTextSecondary);
        ivBookmark.setImageTintList(ColorStateList.valueOf(color));
    }

    private void updateMusicUi() {
        boolean isThisPlaying = MusicService.isPlaying && post.getId().equals(MusicService.currentPostId);

        if (ivMusicPlay != null) {
            ivMusicPlay.setImageResource(isThisPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            ivMusicPlay.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, 
                    R.color.colorAccent)));
        }

        // Đĩa xoay quanh chính tâm
        if (detailVinylAnimator != null) {
            if (isThisPlaying) {
                if (detailVinylAnimator.isPaused()) {
                    detailVinylAnimator.resume();
                } else if (!detailVinylAnimator.isRunning()) {
                    detailVinylAnimator.start();
                }
            } else {
                if (detailVinylAnimator.isRunning()) {
                    detailVinylAnimator.pause();
                }
            }
        }

        // Sóng âm Equalizer
        if (layoutEqWave != null) {
            if (isThisPlaying) {
                layoutMusicCard.setBackgroundResource(R.drawable.bg_music_card_playing);
                if (tvSongTitle != null) tvSongTitle.setTextColor(android.graphics.Color.WHITE);
                if (tvSongArtist != null) tvSongArtist.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
                layoutEqWave.setVisibility(View.VISIBLE);
                startWaveAnimation(eqBar1, 350, 0.4f, 1.3f);
                startWaveAnimation(eqBar2, 280, 0.3f, 1.4f);
                startWaveAnimation(eqBar3, 420, 0.5f, 1.2f);
                startWaveAnimation(eqBar4, 310, 0.2f, 1.5f);
            } else {
                layoutMusicCard.setBackgroundResource(R.drawable.bg_music_card);
                if (tvSongTitle != null) tvSongTitle.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
                if (tvSongArtist != null) tvSongArtist.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
                layoutEqWave.setVisibility(View.GONE);
                if (eqBar1 != null) eqBar1.clearAnimation();
                if (eqBar2 != null) eqBar2.clearAnimation();
                if (eqBar3 != null) eqBar3.clearAnimation();
                if (eqBar4 != null) eqBar4.clearAnimation();
            }
        }
    }

    private void startWaveAnimation(View bar, long duration, float fromScale, float toScale) {
        if (bar == null) return;
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1f, fromScale, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 1f
        );
        scale.setDuration(duration);
        scale.setRepeatCount(Animation.INFINITE);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setInterpolator(new AccelerateDecelerateInterpolator());
        bar.startAnimation(scale);
    }

    @Override
    public void updateMiniPlayerUi() {
        super.updateMiniPlayerUi();
        // Đồng bộ giao diện phát nhạc của bài viết khi có trạng thái phát thay đổi
        if (post != null) {
            updateMusicUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (post != null) {
            // Quay lại từ màn Chỉnh sửa: hiện nội dung mới
            loadPostData();
            loadComments();
        }
        // Realtime: bài viết và bình luận thay đổi trên Firebase hiện ngay
        SyncManager.get(this).watchPost(postId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SyncManager.get(this).unwatchPost(postId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detailVinylAnimator != null) {
            detailVinylAnimator.cancel();
            detailVinylAnimator = null;
        }
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_home;
    }

    /** Chi tiết bài không phải trang gốc của tab Home: bấm Home sẽ quay về Bảng tin. */
    @Override
    protected boolean isNavRootScreen() {
        return false;
    }
}
