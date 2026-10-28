package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Màn hình Tạo bài viết chia sẻ bài hát mới (Bài 4 & Bài 5 - User Interaction & Inputs).
 * Lưu ngay vào SQLite (đăng được cả khi offline) và tự đồng bộ lên Firebase.
 * Đăng xong KHÔNG tự phát nhạc: người dùng tự bấm Play trên bài viết khi muốn nghe.
 */
public class CreatePostActivity extends BaseActivity {

    /** Mở màn hình ở chế độ Chỉnh sửa bài viết có ID này. */
    public static final String EXTRA_EDIT_POST_ID = "EDIT_POST_ID";

    // Bài đang sửa (null = đang tạo bài mới)
    private PostModel editingPost;

    private EditText etCaption, etSongTitle, etArtistName, etMusicUrl, etHashtags;
    private Spinner spinnerGenre;
    private DatabaseHelper dbHelper;
    private int selectedRawAudio = R.raw.song_sample_1;
    /** Link của bài hát mẫu vừa chọn (chip / nút điền mẫu) — chỉ link này mới được phát bằng âm thanh mẫu. */
    private String sampleMusicUrl = null;
    private TextView tvMusicUrlError;
    private boolean isPrivate = false;
    private View btnTogglePrivacy;
    private android.widget.ImageView ivPrivacyIcon;
    private TextView tvPrivacyLabel;

    private final String[] genres = {"V-Pop", "Rap / Hip-Hop", "R&B", "Ballad", "Indie / Acoustic", "EDM"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Bơm giao diện Create Post vào FrameLayout của BaseActivity
        setContentLayout(R.layout.activity_create_post);

        dbHelper = DatabaseHelper.getInstance(this);

        etCaption = findViewById(R.id.et_caption);
        etSongTitle = findViewById(R.id.et_song_title);
        etArtistName = findViewById(R.id.et_artist_name);
        etMusicUrl = findViewById(R.id.et_music_url);
        etHashtags = findViewById(R.id.et_hashtags);
        spinnerGenre = findViewById(R.id.spinner_genre);
        tvMusicUrlError = findViewById(R.id.tv_music_url_error);
        // Người dùng sửa lại liên kết: ẩn cảnh báo cũ
        etMusicUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideMusicUrlError();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Hiển thị tên người đang đăng nhập làm tác giả bài viết
        TextView tvAuthorName = findViewById(R.id.tv_author_name);
        if (tvAuthorName != null) {
            tvAuthorName.setText(dbHelper.getUser(SessionManager.getCurrentUsername(this)).getFullName());
        }

        btnTogglePrivacy = findViewById(R.id.btn_toggle_privacy);
        ivPrivacyIcon = findViewById(R.id.iv_privacy_icon);
        tvPrivacyLabel = findViewById(R.id.tv_privacy_label);

        if (btnTogglePrivacy != null) {
            btnTogglePrivacy.setOnClickListener(v -> showPrivacySelectorDialog());
        }

        // Khởi tạo Spinner Thể loại nhạc (Bài 4 - Spinner)
        if (spinnerGenre != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, genres);
            spinnerGenre.setAdapter(adapter);
        }

        // Nút điền bài hát mẫu
        TextView btnFillSample = findViewById(R.id.btn_fill_sample_song);
        if (btnFillSample != null) {
            btnFillSample.setOnClickListener(v -> {
                fillSongData("Giai điệu bắt tai quá cả nhà ơi! Nghe là muốn cuốn theo luôn",
                        "Cắt Đôi Nỗi Sầu", "Tăng Duy Tân",
                        "https://example.com/audio/cat_doi_noi_sau.mp3",
                        0, "#CatDoiNoiSau #TangDuyTan #Trending", R.raw.song_sample_1);
            });
        }

        // Quick Chips chọn bài hát thịnh hành
        TextView chip1 = findViewById(R.id.chip_sample_1);
        if (chip1 != null) {
            chip1.setOnClickListener(v -> fillSongData(
                    "Một giai điệu quá đỗi cuốn hút và tràn ngập năng lượng tích cực!",
                    "Cắt Đôi Nỗi Sầu", "Tăng Duy Tân",
                    "https://example.com/audio/cat_doi_noi_sau.mp3",
                    0, "#CatDoiNoiSau #Trending", R.raw.song_sample_1));
        }

        TextView chip2 = findViewById(R.id.chip_sample_2);
        if (chip2 != null) {
            chip2.setOnClickListener(v -> fillSongData(
                    "Hoàng hôn buông xuống, thả hồn theo giọng ca mộc mạc của Hoàng tử Indie",
                    "Bình Yên", "Vũ.",
                    "https://example.com/audio/binh_yen_vu.mp3",
                    4, "#BinhYen #Vu #IndieVibes", R.raw.song_sample_2));
        }

        TextView chip3 = findViewById(R.id.chip_sample_3);
        if (chip3 != null) {
            chip3.setOnClickListener(v -> fillSongData(
                    "Hành trình trở về nơi ấm áp nhất luôn là hành trình bình yên nhất",
                    "Đi Về Nhà", "Đen Vâu x JustaTee",
                    "https://example.com/audio/di_ve_nha.mp3",
                    1, "#DiVeNha #DenVau #RapViet", R.raw.song_sample_1));
        }

        setupEditMode();

        // Nút tìm nhạc qua Audius API
        View btnSearchAudius = findViewById(R.id.btn_search_audius);
        if (btnSearchAudius != null) {
            btnSearchAudius.setOnClickListener(v -> showAudiusSearchDialog());
        }

        // Nút ĐĂNG BÀI
        findViewById(R.id.btn_post).setOnClickListener(v -> {
            String pasted = etMusicUrl.getText().toString().trim();
            // Chặn đăng ngay khi liên kết sai định dạng (không gọi mạng, không gán âm thanh mẫu thay thế)
            if (!pasted.isEmpty() && !isAcceptedMusicUrl(pasted)) {
                showMusicUrlError(MusicService.INVALID_AUDIO_LINK_MESSAGE);
                return;
            }
            if (AudiusApi.isAudiusPageLink(pasted)) {
                // Link trang audius.co -> gọi API /resolve để lấy thông tin & link phát thật rồi mới đăng
                v.setEnabled(false);
                Toast.makeText(this, "Đang lấy bài hát từ Audius...", Toast.LENGTH_SHORT).show();
                AudiusApi.resolve(pasted, new AudiusApi.Callback<AudiusApi.Track>() {
                    @Override
                    public void onSuccess(AudiusApi.Track track) {
                        v.setEnabled(true);
                        applyAudiusTrack(track, false);
                        submitPost();
                    }

                    @Override
                    public void onError(String message) {
                        v.setEnabled(true);
                        showMusicUrlError(SyncManager.get(CreatePostActivity.this).isOnline()
                                ? MusicService.INVALID_AUDIO_LINK_MESSAGE
                                : "Cần kết nối Internet để lấy bài hát từ Audius. Vui lòng thử lại khi có mạng.");
                    }
                });
                return;
            }
            submitPost();
        });
    }

    private void submitPost() {
        String title = etSongTitle.getText().toString().trim();
        String artist = etArtistName.getText().toString().trim();
        String url = etMusicUrl.getText().toString().trim();
        String caption = etCaption.getText().toString().trim();
        String hashtags = etHashtags.getText().toString().trim();
        String genre = spinnerGenre != null ? spinnerGenre.getSelectedItem().toString() : "V-Pop";

        if (title.isEmpty()) {
            etSongTitle.setError("Vui lòng nhập tên bài hát!");
            etSongTitle.requestFocus();
            return;
        }

        if (artist.isEmpty()) {
            etArtistName.setError("Vui lòng nhập tên ca sĩ / nghệ sĩ!");
            etArtistName.requestFocus();
            return;
        }

        if (url.isEmpty()) {
            showMusicUrlError("Vui lòng nhập đường link bài hát hoặc bấm Tìm nhạc trên Audius!");
            return;
        }

        if (!isAcceptedMusicUrl(url)) {
            showMusicUrlError(MusicService.INVALID_AUDIO_LINK_MESSAGE);
            return;
        }
        // Chỉ bài hát mẫu (chip) mới dùng âm thanh mẫu đóng gói trong app
        if (!url.equals(sampleMusicUrl) && (editingPost == null || !url.equals(editingPost.getMusicUrl()))) {
            selectedRawAudio = 0;
        }

        if (caption.isEmpty()) {
            caption = "Chia sẻ giai điệu yêu thích này đến cộng đồng MelodyVerse!";
        }

        if (hashtags.isEmpty()) {
            hashtags = "#" + genre.replace(" ", "") + " #MelodyVerse";
        }

        if (editingPost != null) {
            saveEdit(title, artist, url, caption, hashtags, genre);
            return;
        }

        // ID tạo ngay trên máy -> đăng lúc offline vẫn giữ đúng ID khi đẩy lên Firebase
        String newId = DatabaseHelper.newId("post_");
        UserModel author = dbHelper.getUser(SessionManager.getCurrentUsername(this));
        PostModel newPost = new PostModel(
                newId,
                author.getFullName(),
                author.getUsername(),
                "Vừa xong",
                caption,
                title,
                artist,
                url,
                genre,
                hashtags,
                0, 0, 0,
                false, false,
                selectedRawAudio
        );
        newPost.setPrivate(isPrivate);

        // Lưu ngay vào SQLite (PENDING), SyncManager tự đăng lên Firebase khi có mạng
        boolean online = SyncManager.get(this).isOnline();
        boolean success = dbHelper.insertPost(newPost);

        if (success) {
            // Gửi thông báo hệ thống
            String privacyNotice = isPrivate ? " [Chế độ: Riêng tư]" : " [Chế độ: Công khai]";
            NotificationHelper.showNotification(
                    this, 
                    "MelodyVerse — Đăng bài thành công", 
                    "Bài hát \"" + title + "\" của bạn đã được " + (online ? "đăng" : "lưu, sẽ tự đăng khi có mạng") + privacyNotice
            );

            String toastMsg = !online
                    ? "Đang offline — bài viết đã được lưu và sẽ tự đăng khi có mạng trở lại."
                    : isPrivate
                    ? "Đã đăng bài viết Riêng tư. Chỉ mình bạn nhìn thấy bài này (trên Bảng tin và Hồ sơ của bạn)."
                    : "Đã đăng bài viết Công khai lên Bảng tin MelodyVerse!";
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();

            // Chuyển về màn hình Trang chủ (MainActivity)
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        } else {
            Toast.makeText(this, "Có lỗi xảy ra khi lưu bài viết!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Liên kết được phép đăng: nguồn phát được hỗ trợ (Audius / file âm thanh trực tiếp),
     * hoặc đúng link bài hát mẫu vừa chọn, hoặc link cũ của bài đang sửa (chưa đổi).
     */
    private boolean isAcceptedMusicUrl(String url) {
        if (url.equals(sampleMusicUrl)) return true;
        if (editingPost != null && url.equals(editingPost.getMusicUrl())) return true;
        return MusicService.isSupportedAudioLink(url);
    }

    /** Hiện cảnh báo rõ ràng ngay dưới ô Liên kết âm thanh và đưa con trỏ về ô đó. */
    private void showMusicUrlError(String message) {
        etMusicUrl.setError(message);
        if (tvMusicUrlError != null) {
            tvMusicUrlError.setText(message);
            tvMusicUrlError.setVisibility(View.VISIBLE);
        }
        etMusicUrl.requestFocus();
    }

    private void hideMusicUrlError() {
        etMusicUrl.setError(null);
        if (tvMusicUrlError != null) tvMusicUrlError.setVisibility(View.GONE);
    }

    /** Mở từ menu "Chỉnh sửa bài viết": nạp bài cũ vào form, đổi tiêu đề và nút thành LƯU. */
    private void setupEditMode() {
        String editId = getIntent() != null ? getIntent().getStringExtra(EXTRA_EDIT_POST_ID) : null;
        if (editId == null) return;
        PostModel post = dbHelper.getPostById(editId);
        if (post == null || !SessionManager.isCurrentUser(this, post.getAuthorUsername())) {
            Toast.makeText(this, "Không thể sửa bài viết này", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        editingPost = post;

        TextView tvTitle = findViewById(R.id.tv_create_title);
        if (tvTitle != null) tvTitle.setText("Chỉnh sửa bài viết");
        TextView btnPost = findViewById(R.id.btn_post);
        if (btnPost != null) btnPost.setText("Lưu");

        etCaption.setText(post.getCaption());
        etSongTitle.setText(post.getSongTitle());
        etArtistName.setText(post.getArtistName());
        etMusicUrl.setText(post.getMusicUrl());
        etHashtags.setText(post.getHashtags());
        selectedRawAudio = post.getRawAudioRes();
        if (spinnerGenre != null) {
            for (int i = 0; i < genres.length; i++) {
                if (genres[i].equalsIgnoreCase(post.getGenre())) {
                    spinnerGenre.setSelection(i);
                    break;
                }
            }
        }
        isPrivate = post.isPrivate();
        updatePrivacyUi();
    }

    /** Lưu bản sửa vào SQLite (đánh dấu chờ gửi), SyncManager tự cập nhật lên Firebase. */
    private void saveEdit(String title, String artist, String url, String caption, String hashtags, String genre) {
        editingPost.setSongTitle(title);
        editingPost.setArtistName(artist);
        editingPost.setMusicUrl(url);
        editingPost.setCaption(caption);
        editingPost.setHashtags(hashtags);
        editingPost.setGenre(genre);
        editingPost.setRawAudioRes(selectedRawAudio);
        editingPost.setPrivate(isPrivate);

        if (!dbHelper.updatePost(editingPost)) {
            Toast.makeText(this, "Bài viết không còn tồn tại trên máy!", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean online = SyncManager.get(this).isOnline();
        Toast.makeText(this, online
                ? "Đã lưu thay đổi bài viết"
                : "Đang offline — thay đổi đã lưu và sẽ tự cập nhật khi có mạng", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Hộp thoại tìm bài hát: gọi GET /v1/tracks/search của Audius và hiện danh sách kết quả. */
    private void showAudiusSearchDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_audius_search, null);
        EditText etQuery = view.findViewById(R.id.et_audius_query);
        TextView tvStatus = view.findViewById(R.id.tv_audius_status);
        android.widget.ListView lvResults = view.findViewById(R.id.lv_audius_results);

        java.util.List<AudiusApi.Track> results = new java.util.ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new java.util.ArrayList<>());
        lvResults.setAdapter(adapter);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle("Tìm nhạc trên Audius")
                .setView(view)
                .setNegativeButton("Đóng", null)
                .create();

        Runnable doSearch = () -> {
            String q = etQuery.getText().toString().trim();
            if (q.isEmpty()) {
                etQuery.setError("Nhập tên bài hát cần tìm");
                return;
            }
            tvStatus.setText("Đang gọi Audius API...");
            AudiusApi.search(q, new AudiusApi.Callback<java.util.List<AudiusApi.Track>>() {
                @Override
                public void onSuccess(java.util.List<AudiusApi.Track> tracks) {
                    results.clear();
                    results.addAll(tracks);
                    adapter.clear();
                    for (AudiusApi.Track t : tracks) {
                        adapter.add(t.title + "\n" + t.artist + " • " + t.getDurationText());
                    }
                    adapter.notifyDataSetChanged();
                    tvStatus.setText(tracks.isEmpty()
                            ? "Không tìm thấy bài nào, thử từ khóa khác (VD: lofi, chill, piano)"
                            : "Tìm thấy " + tracks.size() + " bài — chạm để chọn");
                }

                @Override
                public void onError(String message) {
                    tvStatus.setText("Lỗi khi gọi Audius API: " + message + "\nKiểm tra kết nối mạng rồi thử lại.");
                }
            });
        };

        view.findViewById(R.id.btn_audius_go).setOnClickListener(v -> doSearch.run());
        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            doSearch.run();
            return true;
        });
        lvResults.setOnItemClickListener((parent, itemView, position, id) -> {
            applyAudiusTrack(results.get(position), true);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Điền thông tin bài hát lấy từ Audius vào form; overwrite = false thì giữ những gì người dùng đã gõ. */
    private void applyAudiusTrack(AudiusApi.Track track, boolean overwrite) {
        if (overwrite || etSongTitle.getText().toString().trim().isEmpty()) {
            etSongTitle.setText(track.title);
        }
        if (overwrite || etArtistName.getText().toString().trim().isEmpty()) {
            etArtistName.setText(track.artist);
        }
        hideMusicUrlError();
        etMusicUrl.setText(track.getStreamUrl());
        if (overwrite && spinnerGenre != null) {
            int idx = mapAudiusGenre(track.genre);
            if (idx >= 0) spinnerGenre.setSelection(idx);
        }
        if (overwrite) {
            Toast.makeText(this, "Đã chọn: " + track.title + " — " + track.artist, Toast.LENGTH_SHORT).show();
        }
    }

    /** Đổi thể loại của Audius sang thể loại trong app; -1 nếu không khớp. */
    private int mapAudiusGenre(String genre) {
        if (genre == null) return -1;
        String g = genre.toLowerCase();
        if (g.contains("hip-hop") || g.contains("rap") || g.contains("trap")) return 1;
        if (g.contains("r&b") || g.contains("soul")) return 2;
        if (g.contains("electronic") || g.contains("house") || g.contains("techno") || g.contains("dubstep")
                || g.contains("edm") || g.contains("trance") || g.contains("drum")) return 5;
        if (g.contains("acoustic") || g.contains("alternative") || g.contains("folk") || g.contains("lo-fi")) return 4;
        if (g.contains("pop")) return 0;
        return -1;
    }

    private void showPrivacySelectorDialog() {
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_post_privacy, null, false);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View optPublic = dialogView.findViewById(R.id.option_privacy_public);
        View optPrivate = dialogView.findViewById(R.id.option_privacy_private);
        // Đánh dấu lựa chọn hiện tại
        optPublic.setBackgroundResource(isPrivate ? R.drawable.bg_privacy_option : R.drawable.bg_privacy_option_selected);
        optPrivate.setBackgroundResource(isPrivate ? R.drawable.bg_privacy_option_selected : R.drawable.bg_privacy_option);
        dialogView.findViewById(R.id.iv_privacy_public_check).setVisibility(isPrivate ? View.INVISIBLE : View.VISIBLE);
        dialogView.findViewById(R.id.iv_privacy_private_check).setVisibility(isPrivate ? View.VISIBLE : View.INVISIBLE);

        optPublic.setOnClickListener(v -> { dialog.dismiss(); applyPrivacy(false); });
        optPrivate.setOnClickListener(v -> { dialog.dismiss(); applyPrivacy(true); });
        dialogView.findViewById(R.id.btn_dialog_privacy_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void applyPrivacy(boolean makePrivate) {
        isPrivate = makePrivate;
        updatePrivacyUi();
        String modeMsg = isPrivate ? "Đã chuyển sang chế độ: Riêng tư" : "Đã chuyển sang chế độ: Công khai";
        Toast.makeText(this, modeMsg, Toast.LENGTH_SHORT).show();
    }

    private void updatePrivacyUi() {
        if (tvPrivacyLabel != null && ivPrivacyIcon != null) {
            if (isPrivate) {
                tvPrivacyLabel.setText("Riêng tư");
                tvPrivacyLabel.setTextColor(getResources().getColor(R.color.colorGold));
                ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
                ivPrivacyIcon.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorGold)));
            } else {
                tvPrivacyLabel.setText("Công khai");
                tvPrivacyLabel.setTextColor(getResources().getColor(R.color.colorNeonCyan));
                ivPrivacyIcon.setImageResource(R.drawable.ic_public);
                ivPrivacyIcon.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorNeonCyan)));
            }
        }
    }

    private void fillSongData(String caption, String title, String artist, String url, int genreIndex, String hashtags, int rawAudio) {
        etCaption.setText(caption);
        etSongTitle.setText(title);
        etArtistName.setText(artist);
        sampleMusicUrl = url;
        etMusicUrl.setText(url);
        etHashtags.setText(hashtags);
        if (spinnerGenre != null && genreIndex < genres.length) {
            spinnerGenre.setSelection(genreIndex);
        }
        selectedRawAudio = rawAudio;
        Toast.makeText(this, "Đã điền thông tin bài hát: " + title, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_raw_audio", selectedRawAudio);
        outState.putBoolean("is_private", isPrivate);
        outState.putString("sample_music_url", sampleMusicUrl);
        if (spinnerGenre != null) {
            outState.putInt("selected_genre_pos", spinnerGenre.getSelectedItemPosition());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            selectedRawAudio = savedInstanceState.getInt("selected_raw_audio", R.raw.song_sample_1);
            isPrivate = savedInstanceState.getBoolean("is_private", false);
            sampleMusicUrl = savedInstanceState.getString("sample_music_url");
            updatePrivacyUi();
            if (spinnerGenre != null) {
                spinnerGenre.setSelection(savedInstanceState.getInt("selected_genre_pos", 0));
            }
        }
    }

    @Override
    protected int getCheckedItem() {
        return R.id.nav_create;
    }
}
