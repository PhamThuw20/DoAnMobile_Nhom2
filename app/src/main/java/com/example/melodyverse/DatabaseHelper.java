package com.example.melodyverse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bộ nhớ đệm SQLite cục bộ của MelodyVerse (Chuẩn môn học Bài 9 - 10.0 SQLite Primer).
 *
 * Dữ liệu gốc nằm trên Firebase Firestore; SQLite lưu lại những trang người dùng đã xem
 * để mở được khi mất mạng, và giữ các thao tác làm lúc offline (cờ sync_status = PENDING)
 * cho tới khi {@link SyncManager} đẩy được lên Firebase.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "melodyverse.db";
    private static final int DATABASE_VERSION = 10;

    // Giới hạn bộ nhớ đệm (xem mục "Quy tắc lưu trữ offline" trong TIEN_DO_DU_AN.md)
    public static final int LIMIT_HOME_FEED = 50;          // Bảng tin: 50 bài mới nhất
    public static final int LIMIT_DETAIL_POSTS = 30;       // Chi tiết bài + bình luận: 30 bài mở gần nhất
    public static final int LIMIT_COMMENTS_PER_POST = 20;  // 20 bình luận mới nhất mỗi bài
    public static final int LIMIT_GENRE_FEED = 20;         // 20 bài mỗi danh mục thể loại
    public static final int LIMIT_SIMILAR = 5;             // 5 bài tương tự mỗi bài
    public static final int LIMIT_OTHER_PROFILES = 10;     // Hồ sơ người khác: 10 hồ sơ xem gần nhất

    // Khóa của từng "trang" trong bảng feed_cache
    public static final String FEED_HOME = "home";
    /** Các bài nhiều tim nhất toàn hệ thống (nguồn của Bảng xếp hạng ở màn Khám phá). */
    public static final String FEED_CHART = "chart";
    public static String feedGenre(String label) { return "genre:" + label; }
    public static String feedSimilar(String postId) { return "similar:" + postId; }
    public static String feedUser(String username) { return "user:" + norm(username); }

    // Bảng Posts
    public static final String TABLE_POSTS = "posts";
    public static final String COL_ID = "id";
    public static final String COL_AUTHOR_NAME = "author_name";
    public static final String COL_AUTHOR_USERNAME = "author_username";
    public static final String COL_CAPTION = "caption";
    public static final String COL_SONG_TITLE = "song_title";
    public static final String COL_ARTIST_NAME = "artist_name";
    public static final String COL_MUSIC_URL = "music_url";
    public static final String COL_GENRE = "genre";
    public static final String COL_HASHTAGS = "hashtags";
    public static final String COL_LIKE_COUNT = "like_count";
    public static final String COL_COMMENT_COUNT = "comment_count";
    public static final String COL_SHARE_COUNT = "share_count";
    public static final String COL_AUDIO_RES = "audio_res";
    public static final String COL_IS_PRIVATE = "is_private";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_SYNC_STATUS = "sync_status";
    public static final String COL_SHARE_PENDING = "share_pending";
    public static final String COL_COMMENTS_CACHED_AT = "comments_cached_at";
    public static final String COL_LAST_VIEWED_AT = "last_viewed_at";

    // Bảng Users
    public static final String TABLE_USERS = "users";
    public static final String COL_U_USERNAME = "username";
    public static final String COL_U_FULLNAME = "full_name";
    public static final String COL_U_ROLE = "student_id";
    public static final String COL_U_BIO = "bio";
    public static final String COL_U_AVATAR_INDEX = "avatar_index";
    public static final String COL_U_IS_LOCKED = "is_locked";
    public static final String COL_U_EMAIL = "email";

    // Bảng Comments
    public static final String TABLE_COMMENTS = "comments";
    public static final String COL_C_ID = "comment_id";
    public static final String COL_C_POST_ID = "post_id";
    public static final String COL_C_AUTHOR_USERNAME = "author_username";
    public static final String COL_C_AUTHOR = "comment_author";
    public static final String COL_C_CONTENT = "comment_content";

    // Bảng Theo dõi (User Follows)
    public static final String TABLE_FOLLOWS = "user_follows";
    public static final String COL_F_FOLLOWER = "follower_username";
    public static final String COL_F_FOLLOWING = "following_username";

    // Bảng Thích / Lưu bài viết theo từng người dùng
    public static final String TABLE_LIKES = "user_likes";
    public static final String TABLE_BOOKMARKS = "user_bookmarks";
    public static final String COL_R_USERNAME = "username";
    public static final String COL_R_POST_ID = "post_id";
    // Danh sách nhạc đã lưu: mỗi dòng lưu riêng thông tin BẢN NHẠC (không phụ thuộc bài viết còn hay không)
    public static final String COL_B_SONG_TITLE = "song_title";
    public static final String COL_B_ARTIST_NAME = "artist_name";
    public static final String COL_B_MUSIC_URL = "music_url";
    public static final String COL_B_AUDIO_RES = "audio_res";
    public static final String COL_B_GENRE = "genre";

    // Bảng ghi nhớ bài viết thuộc trang nào (bảng tin, danh mục, bài tương tự, hồ sơ)
    public static final String TABLE_FEED_CACHE = "feed_cache";
    // Bài viết người dùng chọn "Ẩn" khỏi bảng tin của mình (lưu riêng trên máy, giữ lại khi đăng xuất)
    public static final String TABLE_HIDDEN = "hidden_posts";

    // Bảng Thông báo: mỗi dòng thuộc đúng một tài khoản nhận (recipient), trạng thái đọc lưu theo từng thông báo
    public static final String TABLE_NOTIFICATIONS = "notifications";
    public static final String COL_N_ID = "notif_id";
    public static final String COL_N_RECIPIENT = "recipient";
    public static final String COL_N_TYPE = "type";
    public static final String COL_N_ACTOR = "actor";
    public static final String COL_N_ACTOR_NAME = "actor_name";
    public static final String COL_N_POST_ID = "post_id";
    public static final String COL_N_COMMENT_ID = "comment_id";
    public static final String COL_N_SONG_TITLE = "song_title";
    public static final String COL_N_PREVIEW = "preview";
    public static final String COL_N_IS_READ = "is_read";
    public static final String COL_N_IS_SAMPLE = "is_sample";
    public static final String TABLE_FEED_META = "feed_meta";
    public static final String COL_FEED_KEY = "feed_key";
    public static final String COL_FEED_POST_ID = "post_id";
    public static final String COL_FEED_UPDATED_AT = "updated_at";

    private static DatabaseHelper instance;
    private final Context appContext;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
    }

    static String norm(String username) {
        return username == null ? "" : username.replace("@", "").trim().toLowerCase();
    }

    /** Username của người đang đăng nhập (dùng cho trạng thái thích / lưu của bài viết). */
    private String currentUser() {
        return norm(SessionManager.getCurrentUsername(appContext));
    }

    /** Báo cho SyncManager có thao tác mới cần đẩy lên Firebase (nếu đang có mạng sẽ gửi ngay). */
    private void onLocalChange() {
        SyncManager.get(appContext).requestPush();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_POSTS + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_AUTHOR_NAME + " TEXT, "
                + COL_AUTHOR_USERNAME + " TEXT, "
                + COL_CAPTION + " TEXT, "
                + COL_SONG_TITLE + " TEXT, "
                + COL_ARTIST_NAME + " TEXT, "
                + COL_MUSIC_URL + " TEXT, "
                + COL_GENRE + " TEXT, "
                + COL_HASHTAGS + " TEXT, "
                + COL_LIKE_COUNT + " INTEGER DEFAULT 0, "
                + COL_COMMENT_COUNT + " INTEGER DEFAULT 0, "
                + COL_SHARE_COUNT + " INTEGER DEFAULT 0, "
                + COL_AUDIO_RES + " TEXT, "
                + COL_IS_PRIVATE + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "', "
                + COL_SHARE_PENDING + " INTEGER DEFAULT 0, "
                + COL_COMMENTS_CACHED_AT + " INTEGER DEFAULT 0, "
                + COL_LAST_VIEWED_AT + " INTEGER DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_USERS + " ("
                + COL_U_USERNAME + " TEXT PRIMARY KEY, "
                + COL_U_FULLNAME + " TEXT, "
                + COL_U_ROLE + " TEXT, "
                + COL_U_BIO + " TEXT, "
                + COL_U_AVATAR_INDEX + " INTEGER DEFAULT 0, "
                + COL_U_IS_LOCKED + " INTEGER DEFAULT 0, "
                + COL_U_EMAIL + " TEXT, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "'"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_COMMENTS + " ("
                + COL_C_ID + " TEXT PRIMARY KEY, "
                + COL_C_POST_ID + " TEXT, "
                + COL_C_AUTHOR_USERNAME + " TEXT, "
                + COL_C_AUTHOR + " TEXT, "
                + COL_C_CONTENT + " TEXT, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "'"
                + ")");
        db.execSQL("CREATE INDEX idx_comments_post ON " + TABLE_COMMENTS + "(" + COL_C_POST_ID + ")");

        db.execSQL("CREATE TABLE " + TABLE_FOLLOWS + " ("
                + COL_F_FOLLOWER + " TEXT, "
                + COL_F_FOLLOWING + " TEXT, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "', "
                + "PRIMARY KEY (" + COL_F_FOLLOWER + ", " + COL_F_FOLLOWING + ")"
                + ")");

        db.execSQL(createUserPostRelationTable(TABLE_LIKES));
        db.execSQL(createUserPostRelationTable(TABLE_BOOKMARKS));
        addSavedTrackColumns(db);

        db.execSQL("CREATE TABLE " + TABLE_FEED_CACHE + " ("
                + COL_FEED_KEY + " TEXT, "
                + COL_FEED_POST_ID + " TEXT, "
                + "PRIMARY KEY (" + COL_FEED_KEY + ", " + COL_FEED_POST_ID + ")"
                + ")");
        db.execSQL("CREATE TABLE " + TABLE_FEED_META + " ("
                + COL_FEED_KEY + " TEXT PRIMARY KEY, "
                + COL_FEED_UPDATED_AT + " INTEGER DEFAULT 0, "
                + COL_LAST_VIEWED_AT + " INTEGER DEFAULT 0"
                + ")");
        createHiddenTable(db);
        createNotificationsTable(db);
    }

    private void createNotificationsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTIFICATIONS + " ("
                + COL_N_ID + " TEXT PRIMARY KEY, "
                + COL_N_RECIPIENT + " TEXT, "
                + COL_N_TYPE + " TEXT, "
                + COL_N_ACTOR + " TEXT, "
                + COL_N_ACTOR_NAME + " TEXT, "
                + COL_N_POST_ID + " TEXT, "
                + COL_N_COMMENT_ID + " TEXT, "
                + COL_N_SONG_TITLE + " TEXT, "
                + COL_N_PREVIEW + " TEXT, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_N_IS_READ + " INTEGER DEFAULT 0, "
                + COL_N_IS_SAMPLE + " INTEGER DEFAULT 0, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "'"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON " + TABLE_NOTIFICATIONS + "(" + COL_N_RECIPIENT + ")");
    }

    /** v10: bảng lưu bài chuyển thành Danh sách nhạc đã lưu (thêm thông tin bản nhạc vào từng dòng). */
    private void addSavedTrackColumns(SQLiteDatabase db) {
        for (String col : new String[]{COL_B_SONG_TITLE, COL_B_ARTIST_NAME, COL_B_MUSIC_URL, COL_B_AUDIO_RES, COL_B_GENRE}) {
            db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS + " ADD COLUMN " + col + " TEXT");
        }
    }

    private void createHiddenTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HIDDEN + " ("
                + COL_R_USERNAME + " TEXT, "
                + COL_R_POST_ID + " TEXT, "
                + "PRIMARY KEY (" + COL_R_USERNAME + ", " + COL_R_POST_ID + ")"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion >= 7) {
            // v8 thêm bảng bài đã ẩn, v9 thêm bảng thông báo theo tài khoản, v10 thêm thông tin bản nhạc đã lưu;
            // giữ nguyên bộ nhớ đệm và các thao tác đang chờ đồng bộ
            if (oldVersion == 7) createHiddenTable(db);
            createNotificationsTable(db);
            if (oldVersion < 10) addSavedTrackColumns(db);
            return;
        }
        // Từ v7 dữ liệu gốc nằm trên Firebase, SQLite chỉ là bộ nhớ đệm nên tạo lại bảng là an toàn
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COMMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FOLLOWS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LIKES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FEED_CACHE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FEED_META);
        onCreate(db);
    }

    private String createUserPostRelationTable(String table) {
        return "CREATE TABLE " + table + " ("
                + COL_R_USERNAME + " TEXT, "
                + COL_R_POST_ID + " TEXT, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_SYNC_STATUS + " TEXT DEFAULT '" + SyncStatus.SYNCED + "', "
                + "PRIMARY KEY (" + COL_R_USERNAME + ", " + COL_R_POST_ID + ")"
                + ")";
    }

    // ===================================================================================
    // Tài nguyên âm thanh: lưu theo TÊN (ổn định giữa các bản build) thay vì ID số
    // ===================================================================================

    public String audioResName(int resId) {
        try {
            return appContext.getResources().getResourceEntryName(resId != 0 ? resId : R.raw.song_sample_1);
        } catch (Exception e) {
            return "song_sample_1";
        }
    }

    public int audioResId(String name) {
        int id = name != null ? appContext.getResources().getIdentifier(name, "raw", appContext.getPackageName()) : 0;
        return id != 0 ? id : R.raw.song_sample_1;
    }

    // ===================================================================================
    // ĐỌC dữ liệu cho giao diện (luôn đọc từ SQLite → mất mạng vẫn hiển thị được)
    // ===================================================================================

    /** Câu SELECT bài viết kèm trạng thái thích / lưu của người đang đăng nhập (1 truy vấn, không N+1). */
    private static final String POST_SELECT = "SELECT p.*, "
            + "EXISTS(SELECT 1 FROM " + TABLE_LIKES + " l WHERE l." + COL_R_POST_ID + " = p." + COL_ID
            + " AND l." + COL_R_USERNAME + " = ? AND l." + COL_SYNC_STATUS + " != '" + SyncStatus.PENDING_DELETE + "') AS my_liked, "
            + "EXISTS(SELECT 1 FROM " + TABLE_BOOKMARKS + " b WHERE b." + COL_R_POST_ID + " = p." + COL_ID
            + " AND b." + COL_R_USERNAME + " = ? AND b." + COL_SYNC_STATUS + " != '" + SyncStatus.PENDING_DELETE + "') AS my_bookmarked, "
            // Thao tác thích / bỏ thích của mình đã lưu trên máy nhưng Firebase chưa xác nhận
            + "EXISTS(SELECT 1 FROM " + TABLE_LIKES + " lp WHERE lp." + COL_R_POST_ID + " = p." + COL_ID
            + " AND lp." + COL_R_USERNAME + " = ? AND lp." + COL_SYNC_STATUS + " IN ('" + SyncStatus.PENDING + "', '"
            + SyncStatus.PENDING_DELETE + "')) AS my_like_pending "
            + "FROM " + TABLE_POSTS + " p ";

    private List<PostModel> queryPosts(String tail, String... args) {
        String me = currentUser();
        String[] all = new String[args.length + 3];
        all[0] = me;
        all[1] = me;
        all[2] = me;
        System.arraycopy(args, 0, all, 3, args.length);
        List<PostModel> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(POST_SELECT + tail, all);
        while (cursor.moveToNext()) {
            list.add(cursorToPost(cursor));
        }
        cursor.close();
        return list;
    }

    /** Toàn bộ bài viết đang có trong bộ nhớ đệm (dùng cho tìm kiếm và phát bài tiếp theo). */
    public List<PostModel> getAllPosts() {
        return queryPosts("WHERE " + notHidden() + " ORDER BY p." + COL_CREATED_AT + " DESC");
    }

    /**
     * Bài viết dùng để tính Bảng xếp hạng: gồm cả bài người đang đăng nhập tự ẩn (ẩn chỉ là ẩn khỏi bảng tin của mình,
     * lượt tim của bài vẫn tính cho bài hát), chỉ loại bài đã xóa đang chờ xóa trên Firebase.
     */
    public List<PostModel> getChartPosts() {
        return queryPosts("WHERE p." + COL_SYNC_STATUS + " != '" + SyncStatus.PENDING_DELETE
                + "' ORDER BY p." + COL_CREATED_AT + " DESC");
    }

    /** Mã các bài người đang đăng nhập đã ẩn. */
    public java.util.Set<String> getHiddenPostIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COL_R_POST_ID + " FROM " + TABLE_HIDDEN
                + " WHERE " + COL_R_USERNAME + " = ?", new String[]{currentUser()});
        while (c.moveToNext()) ids.add(c.getString(0));
        c.close();
        return ids;
    }

    /** Điều kiện SQL loại các bài người đang đăng nhập đã ẩn và bài đã xóa đang chờ xóa trên Firebase. */
    private String notHidden() {
        return "p." + COL_SYNC_STATUS + " != '" + SyncStatus.PENDING_DELETE + "' AND p." + COL_ID
                + " NOT IN (SELECT " + COL_R_POST_ID + " FROM " + TABLE_HIDDEN
                + " WHERE " + COL_R_USERNAME + " = " + DatabaseUtils.sqlEscapeString(currentUser()) + ")";
    }

    /** Bài viết của một "trang" đã lưu (bảng tin, danh mục, bài tương tự...). */
    public List<PostModel> getFeedPosts(String feedKey) {
        return queryPosts("INNER JOIN " + TABLE_FEED_CACHE + " f ON f." + COL_FEED_POST_ID + " = p." + COL_ID
                + " WHERE f." + COL_FEED_KEY + " = ? AND " + notHidden() + " ORDER BY p." + COL_CREATED_AT + " DESC", feedKey);
    }

    /**
     * Danh sách nhạc đã lưu của {@code username}, bài lưu gần nhất trước.
     * Mỗi phần tử là một BẢN NHẠC (tên bài, nghệ sĩ, nguồn nhạc) lấy từ chính dòng đã lưu,
     * nên vẫn nghe được khi bài viết gốc không còn trong bộ nhớ đệm hoặc đã bị tác giả xóa.
     */
    public List<PostModel> getSavedTracks(String username) {
        List<PostModel> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT b." + COL_R_POST_ID + ", b." + COL_CREATED_AT + ", "
                + "COALESCE(b." + COL_B_SONG_TITLE + ", p." + COL_SONG_TITLE + "), "
                + "COALESCE(b." + COL_B_ARTIST_NAME + ", p." + COL_ARTIST_NAME + "), "
                + "COALESCE(b." + COL_B_MUSIC_URL + ", p." + COL_MUSIC_URL + "), "
                + "COALESCE(b." + COL_B_AUDIO_RES + ", p." + COL_AUDIO_RES + "), "
                + "COALESCE(b." + COL_B_GENRE + ", p." + COL_GENRE + "), "
                + "p." + COL_AUTHOR_NAME + ", p." + COL_AUTHOR_USERNAME
                + " FROM " + TABLE_BOOKMARKS + " b LEFT JOIN " + TABLE_POSTS + " p ON p." + COL_ID + " = b." + COL_R_POST_ID
                + " WHERE b." + COL_R_USERNAME + " = ? AND b." + COL_SYNC_STATUS + " != ?"
                + " ORDER BY b." + COL_CREATED_AT + " DESC",
                new String[]{norm(username), SyncStatus.PENDING_DELETE});
        while (c.moveToNext()) {
            String title = c.getString(2);
            if (title == null || title.trim().isEmpty()) continue; // dòng cũ chưa có thông tin bản nhạc
            PostModel track = new PostModel(c.getString(0),
                    c.isNull(7) ? "" : c.getString(7), c.isNull(8) ? "" : c.getString(8), "", "",
                    title, c.isNull(3) ? "" : c.getString(3), c.getString(4), c.getString(6), "",
                    0, 0, 0, false, true, audioResId(c.getString(5)));
            track.setCreatedAt(c.getLong(1));
            list.add(track);
        }
        c.close();
        return list;
    }

    /** Bản nhạc đã lưu (để gửi kèm lên Firebase), null nếu chưa lưu. */
    public PostModel getSavedTrack(String username, String postId) {
        for (PostModel t : getSavedTracks(username)) {
            if (t.getId().equals(postId)) return t;
        }
        return null;
    }

    public List<PostModel> getLikedPosts(String username) {
        return getPostsInRelation(TABLE_LIKES, username);
    }

    private List<PostModel> getPostsInRelation(String table, String username) {
        return queryPosts("INNER JOIN " + table + " r ON p." + COL_ID + " = r." + COL_R_POST_ID
                + " WHERE r." + COL_R_USERNAME + " = ? AND r." + COL_SYNC_STATUS + " != '" + SyncStatus.PENDING_DELETE + "'"
                + " ORDER BY p." + COL_CREATED_AT + " DESC", norm(username));
    }

    public List<PostModel> getUserPosts(String username) {
        return queryPosts("WHERE p." + COL_AUTHOR_USERNAME + " = ? AND " + notHidden()
                + " ORDER BY p." + COL_CREATED_AT + " DESC", norm(username));
    }

    public PostModel getPostById(String postId) {
        if (postId == null) return null;
        List<PostModel> list = queryPosts("WHERE p." + COL_ID + " = ? AND p." + COL_SYNC_STATUS + " != ?",
                postId, SyncStatus.PENDING_DELETE);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Ngày giờ (ms) trang được tải mới từ Firebase lần gần nhất, 0 nếu chưa từng tải. */
    public long getFeedUpdatedAt(String feedKey) {
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COL_FEED_UPDATED_AT + " FROM " + TABLE_FEED_META
                + " WHERE " + COL_FEED_KEY + " = ?", new String[]{feedKey});
        long t = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return t;
    }

    /** Mốc thời gian của bài cũ nhất đã tải trong trang (để lướt tiếp lấy các bài cũ hơn). */
    public long getOldestFeedCreatedAt(String feedKey) {
        Cursor c = getReadableDatabase().rawQuery("SELECT MIN(p." + COL_CREATED_AT + ") FROM " + TABLE_POSTS + " p INNER JOIN "
                + TABLE_FEED_CACHE + " f ON f." + COL_FEED_POST_ID + " = p." + COL_ID + " WHERE f." + COL_FEED_KEY + " = ? AND p."
                + COL_SYNC_STATUS + " = ?", new String[]{feedKey, SyncStatus.SYNCED});
        long t = c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0;
        c.close();
        return t;
    }

    private PostModel cursorToPost(Cursor cursor) {
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        PostModel post = new PostModel(
                cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR_USERNAME)),
                formatTimeAgo(createdAt),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_CAPTION)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SONG_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTIST_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_MUSIC_URL)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_GENRE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_HASHTAGS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_LIKE_COUNT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMMENT_COUNT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHARE_COUNT)),
                cursor.getInt(cursor.getColumnIndexOrThrow("my_liked")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("my_bookmarked")) == 1,
                audioResId(cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO_RES)))
        );
        post.setPrivate(cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_PRIVATE)) == 1);
        post.setCreatedAt(createdAt);
        post.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS)));
        int likePendingIdx = cursor.getColumnIndex("my_like_pending");
        post.setLikePending(likePendingIdx >= 0 && cursor.getInt(likePendingIdx) == 1);
        return post;
    }

    // ---------- Bình luận ----------

    public List<CommentModel> getCommentsByPost(String postId) {
        List<CommentModel> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_COMMENTS, null, COL_C_POST_ID + " = ?", new String[]{postId},
                null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) {
            list.add(cursorToComment(c));
        }
        c.close();
        return list;
    }

    private CommentModel cursorToComment(Cursor c) {
        return new CommentModel(
                c.getString(c.getColumnIndexOrThrow(COL_C_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_C_POST_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_C_AUTHOR_USERNAME)),
                c.getString(c.getColumnIndexOrThrow(COL_C_AUTHOR)),
                c.getString(c.getColumnIndexOrThrow(COL_C_CONTENT)),
                c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)),
                c.getString(c.getColumnIndexOrThrow(COL_SYNC_STATUS)));
    }

    /** true nếu bình luận của bài đã từng được tải từ Firebase (xem được khi offline). */
    public boolean hasCachedComments(String postId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COL_COMMENTS_CACHED_AT + " FROM " + TABLE_POSTS
                + " WHERE " + COL_ID + " = ?", new String[]{postId});
        boolean cached = c.moveToFirst() && c.getLong(0) > 0;
        c.close();
        return cached;
    }

    /** Đổi mốc thời gian (ms) thành chuỗi "x phút trước" thân thiện. */
    public static String formatTimeAgo(long createdAt) {
        long diff = Math.max(0, System.currentTimeMillis() - createdAt);
        long minutes = diff / 60000;
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        long hours = minutes / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        return days + " ngày trước";
    }

    // ---------- Người dùng ----------

    public UserModel getUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            username = currentUser();
        }
        String clean = norm(username);
        Cursor cursor = getReadableDatabase().query(TABLE_USERS, null, COL_U_USERNAME + " = ?", new String[]{clean}, null, null, null);
        if (cursor.moveToFirst()) {
            UserModel user = cursorToUser(cursor);
            cursor.close();
            return user;
        }
        cursor.close();

        // Chưa có trong bộ nhớ đệm: dùng thông tin nghệ sĩ mẫu nếu có
        UserModel predefined = getPredefinedUser(clean);
        if (predefined != null) {
            return predefined;
        }
        return new UserModel(clean, clean, "Thành viên", "Đam mê âm nhạc và chia sẻ cảm xúc qua từng giai điệu cùng MelodyVerse.", 0, 0, 0, 0, false);
    }

    private UserModel cursorToUser(Cursor cursor) {
        String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_U_USERNAME));
        UserModel user = new UserModel(
                username,
                cursor.getString(cursor.getColumnIndexOrThrow(COL_U_FULLNAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_U_ROLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_U_BIO)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_U_AVATAR_INDEX)),
                0, 0, 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_U_IS_LOCKED)) == 1
        );
        user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COL_U_EMAIL)));
        // Số người theo dõi / đang theo dõi luôn đếm trực tiếp từ bảng user_follows
        user.setFollowersCount(getFollowerCount(username));
        user.setFollowingCount(getFollowingCount(username));
        return user;
    }

    /** Thông tin nghệ sĩ mẫu (dùng khi hồ sơ chưa được tải về máy). */
    public UserModel getPredefinedUser(String username) {
        if (username == null) return null;
        for (UserModel u : SeedData.users()) {
            if (u.getUsername().equalsIgnoreCase(norm(username))) return u;
        }
        return null;
    }

    public boolean checkUserExists(String username) {
        Cursor c = getReadableDatabase().query(TABLE_USERS, new String[]{COL_U_USERNAME}, COL_U_USERNAME + " = ?",
                new String[]{norm(username)}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    public boolean isUserLocked(String username) {
        Cursor c = getReadableDatabase().query(TABLE_USERS, new String[]{COL_U_IS_LOCKED}, COL_U_USERNAME + " = ?",
                new String[]{norm(username)}, null, null, null);
        boolean locked = c.moveToFirst() && c.getInt(0) == 1;
        c.close();
        return locked;
    }

    public List<UserModel> getAllUsers() {
        List<UserModel> users = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE_USERS, null, null, null, null, null, COL_U_USERNAME + " ASC");
        while (cursor.moveToNext()) {
            users.add(cursorToUser(cursor));
        }
        cursor.close();
        return users;
    }

    // ---------- Theo dõi ----------

    /** Danh sách tài khoản mà {@code follower} đang theo dõi. */
    public List<UserModel> getFollowingUsers(String follower) {
        return getFollowRelatedUsers(COL_F_FOLLOWING, COL_F_FOLLOWER, follower);
    }

    /** Danh sách tài khoản đang theo dõi {@code username}. */
    public List<UserModel> getFollowerUsers(String username) {
        return getFollowRelatedUsers(COL_F_FOLLOWER, COL_F_FOLLOWING, username);
    }

    private List<UserModel> getFollowRelatedUsers(String selectCol, String whereCol, String username) {
        List<String> names = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_FOLLOWS, new String[]{selectCol},
                whereCol + " = ? AND " + COL_SYNC_STATUS + " != ?", new String[]{norm(username), SyncStatus.PENDING_DELETE},
                null, null, null);
        while (c.moveToNext()) names.add(c.getString(0));
        c.close();
        List<UserModel> list = new ArrayList<>();
        for (String name : names) {
            list.add(getUser(name));
        }
        return list;
    }

    public boolean isUserFollowed(String follower, String targetUsername) {
        String status = getFollowStatus(norm(follower), norm(targetUsername));
        return status != null && !SyncStatus.PENDING_DELETE.equals(status);
    }

    public int getFollowingCount(String follower) {
        return countFollows(COL_F_FOLLOWER, follower);
    }

    public int getFollowerCount(String username) {
        return countFollows(COL_F_FOLLOWING, username);
    }

    private int countFollows(String column, String username) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_FOLLOWS + " WHERE " + column + " = ? AND "
                + COL_SYNC_STATUS + " != ?", new String[]{norm(username), SyncStatus.PENDING_DELETE});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    // ---------- Thống kê (Admin xem khi offline) ----------

    public int getPostCount() {
        return countRows("SELECT COUNT(*) FROM " + TABLE_POSTS);
    }

    public int getCommentCount() {
        return countRows("SELECT COALESCE(SUM(" + COL_COMMENT_COUNT + "), 0) FROM " + TABLE_POSTS);
    }

    /** Tổng số thao tác đang chờ đẩy lên Firebase. */
    public int getPendingCount() {
        String p = "'" + SyncStatus.PENDING + "'";
        String pd = "'" + SyncStatus.PENDING_DELETE + "'";
        return countRows("SELECT "
                + "(SELECT COUNT(*) FROM " + TABLE_POSTS + " WHERE " + COL_SYNC_STATUS + " IN (" + p + ",'" + SyncStatus.PENDING_UPDATE
                + "'," + pd + ") OR " + COL_SHARE_PENDING + " > 0)"
                + " + (SELECT COUNT(*) FROM " + TABLE_COMMENTS + " WHERE " + COL_SYNC_STATUS + " = " + p + ")"
                + " + (SELECT COUNT(*) FROM " + TABLE_LIKES + " WHERE " + COL_SYNC_STATUS + " IN (" + p + "," + pd + "))"
                + " + (SELECT COUNT(*) FROM " + TABLE_BOOKMARKS + " WHERE " + COL_SYNC_STATUS + " IN (" + p + "," + pd + "))"
                + " + (SELECT COUNT(*) FROM " + TABLE_FOLLOWS + " WHERE " + COL_SYNC_STATUS + " IN (" + p + "," + pd + "))"
                + " + (SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE " + COL_SYNC_STATUS + " = " + p + ")"
                + " + (SELECT COUNT(*) FROM " + TABLE_NOTIFICATIONS + " WHERE " + COL_SYNC_STATUS + " = " + p + ")");
    }

    private int countRows(String sql) {
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    // ===================================================================================
    // GHI do người dùng thao tác: lưu ngay vào SQLite (giao diện đổi tức thì),
    // đánh dấu PENDING rồi nhờ SyncManager đẩy lên Firebase khi có mạng
    // ===================================================================================

    /** Đăng bài mới. Bài xuất hiện ngay trên Bảng tin và Hồ sơ, kèm nhãn chờ cho tới khi lên Firebase. */
    public boolean insertPost(PostModel post) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = postToValues(post, now);
        cv.put(COL_SYNC_STATUS, SyncStatus.PENDING);
        long rowId = db.insert(TABLE_POSTS, null, cv);
        if (rowId == -1) return false;
        post.setCreatedAt(now);
        addToFeed(db, FEED_HOME, post.getId());
        addToFeed(db, feedUser(post.getAuthorUsername()), post.getId());
        onLocalChange();
        return true;
    }

    /**
     * Sửa bài viết của mình: lưu ngay vào SQLite rồi đẩy bản sửa lên Firebase khi có mạng.
     * Bài chưa từng lên Firebase (PENDING) vẫn giữ PENDING — lần đẩy đầu sẽ gửi luôn nội dung mới.
     */
    public boolean updatePost(PostModel post) {
        String current = getPostSyncStatus(post.getId());
        if (current == null) return false;
        ContentValues cv = new ContentValues();
        cv.put(COL_CAPTION, post.getCaption());
        cv.put(COL_SONG_TITLE, post.getSongTitle());
        cv.put(COL_ARTIST_NAME, post.getArtistName());
        cv.put(COL_MUSIC_URL, post.getMusicUrl());
        cv.put(COL_GENRE, post.getGenre());
        cv.put(COL_HASHTAGS, post.getHashtags());
        cv.put(COL_AUDIO_RES, audioResName(post.getRawAudioRes()));
        cv.put(COL_IS_PRIVATE, post.isPrivate() ? 1 : 0);
        if (!SyncStatus.PENDING.equals(current)) {
            cv.put(COL_SYNC_STATUS, SyncStatus.PENDING_UPDATE);
        }
        int rows = getWritableDatabase().update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{post.getId()});
        onLocalChange();
        return rows > 0;
    }

    /** Ngày đăng (ms) của bài đang có trong bộ nhớ đệm, 0 nếu chưa có. */
    public long getPostCreatedAt(String postId) {
        Cursor c = getReadableDatabase().query(TABLE_POSTS, new String[]{COL_CREATED_AT}, COL_ID + " = ?",
                new String[]{postId}, null, null, null);
        long t = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return t;
    }

    public String getPostSyncStatus(String postId) {
        Cursor c = getReadableDatabase().query(TABLE_POSTS, new String[]{COL_SYNC_STATUS}, COL_ID + " = ?",
                new String[]{postId}, null, null, null);
        String status = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return status;
    }

    /**
     * Tác giả xóa bài: biến mất khỏi mọi màn hình ngay. Bài chưa từng lên Firebase thì xóa hẳn trên máy,
     * còn lại đánh dấu PENDING_DELETE để SyncManager xóa trên Firebase (kèm bình luận, lượt thích, lượt lưu).
     */
    public void deletePost(String postId) {
        String current = getPostSyncStatus(postId);
        if (current == null) return;
        if (SyncStatus.PENDING.equals(current) || SyncStatus.FAILED.equals(current)) {
            removePostLocal(postId);
            getWritableDatabase().delete(TABLE_COMMENTS, COL_C_POST_ID + " = ?", new String[]{postId});
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_SYNC_STATUS, SyncStatus.PENDING_DELETE);
            db.update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{postId});
            db.delete(TABLE_FEED_CACHE, COL_FEED_POST_ID + " = ?", new String[]{postId});
            db.delete(TABLE_COMMENTS, COL_C_POST_ID + " = ?", new String[]{postId});
            db.delete(TABLE_LIKES, COL_R_POST_ID + " = ?", new String[]{postId});
            // Danh sách nhạc đã lưu giữ nguyên: bản nhạc đã lưu không phụ thuộc bài viết
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        onLocalChange();
    }

    public List<String> getPendingPostDeletes() {
        List<String> ids = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_POSTS, new String[]{COL_ID}, COL_SYNC_STATUS + " = ?",
                new String[]{SyncStatus.PENDING_DELETE}, null, null, null);
        while (c.moveToNext()) ids.add(c.getString(0));
        c.close();
        return ids;
    }

    /** Ẩn bài viết khỏi bảng tin / tìm kiếm / hồ sơ của người đang đăng nhập. */
    public void hidePost(String postId) {
        ContentValues cv = new ContentValues();
        cv.put(COL_R_USERNAME, currentUser());
        cv.put(COL_R_POST_ID, postId);
        getWritableDatabase().insertWithOnConflict(TABLE_HIDDEN, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void unhidePost(String postId) {
        getWritableDatabase().delete(TABLE_HIDDEN, COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ?",
                new String[]{currentUser(), postId});
    }

    /** Tạo ID bài viết / bình luận duy nhất ngay trên máy (dùng làm ID document trên Firestore). */
    public static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** Thả tim / bỏ tim của người đang đăng nhập, đồng thời cập nhật tổng số lượt thích. */
    public boolean updatePostLike(String postId, boolean isLiked, int newLikeCount) {
        ContentValues cv = new ContentValues();
        cv.put(COL_LIKE_COUNT, Math.max(0, newLikeCount));
        int rows = getWritableDatabase().update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{postId});
        setUserPostRelation(TABLE_LIKES, currentUser(), postId, isLiked);
        onLocalChange();
        return rows > 0;
    }

    /**
     * Lưu / bỏ lưu BẢN NHẠC của bài viết vào Danh sách nhạc đã lưu của người đang đăng nhập.
     * Chỉ thông tin bản nhạc (tên bài, nghệ sĩ, nguồn nhạc, thể loại) được lưu, không lưu nội dung bài viết.
     */
    public boolean updatePostBookmark(PostModel post, boolean isBookmarked) {
        String me = currentUser();
        setUserPostRelation(TABLE_BOOKMARKS, me, post.getId(), isBookmarked);
        if (isBookmarked) {
            getWritableDatabase().update(TABLE_BOOKMARKS, savedTrackValues(post),
                    COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ?", new String[]{me, post.getId()});
        }
        onLocalChange();
        return true;
    }

    private ContentValues savedTrackValues(PostModel post) {
        ContentValues cv = new ContentValues();
        cv.put(COL_B_SONG_TITLE, post.getSongTitle());
        cv.put(COL_B_ARTIST_NAME, post.getArtistName());
        cv.put(COL_B_MUSIC_URL, post.getMusicUrl());
        cv.put(COL_B_AUDIO_RES, audioResName(post.getRawAudioRes()));
        cv.put(COL_B_GENRE, post.getGenre());
        return cv;
    }

    private void setUserPostRelation(String table, String username, String postId, boolean active) {
        if (username.isEmpty() || postId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        String where = COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ?";
        String[] args = {username, postId};
        String current = null;
        Cursor c = db.query(table, new String[]{COL_SYNC_STATUS}, where, args, null, null, null);
        if (c.moveToFirst()) current = c.getString(0);
        c.close();

        String next = SyncStatus.nextRelationState(current, active);
        if (SyncStatus.NO_CHANGE.equals(next)) return;
        if (SyncStatus.REMOVE_ROW.equals(next)) {
            db.delete(table, where, args);
        } else if (current == null) {
            ContentValues cv = new ContentValues();
            cv.put(COL_R_USERNAME, username);
            cv.put(COL_R_POST_ID, postId);
            cv.put(COL_CREATED_AT, System.currentTimeMillis());
            cv.put(COL_SYNC_STATUS, next);
            db.insert(table, null, cv);
        } else {
            ContentValues cv = new ContentValues();
            cv.put(COL_SYNC_STATUS, next);
            db.update(table, cv, where, args);
        }
    }

    /** Thêm bình luận của người đang đăng nhập (PENDING cho tới khi Firebase xác nhận). */
    public CommentModel addComment(String postId, String authorName, String content) {
        SQLiteDatabase db = getWritableDatabase();
        CommentModel comment = new CommentModel(newId("c_"), postId, currentUser(), authorName, content,
                System.currentTimeMillis(), SyncStatus.PENDING);
        ContentValues cv = new ContentValues();
        cv.put(COL_C_ID, comment.getId());
        cv.put(COL_C_POST_ID, postId);
        cv.put(COL_C_AUTHOR_USERNAME, comment.getAuthorUsername());
        cv.put(COL_C_AUTHOR, authorName);
        cv.put(COL_C_CONTENT, content);
        cv.put(COL_CREATED_AT, comment.getCreatedAt());
        cv.put(COL_SYNC_STATUS, SyncStatus.PENDING);
        if (db.insert(TABLE_COMMENTS, null, cv) == -1) return null;
        db.execSQL("UPDATE " + TABLE_POSTS + " SET " + COL_COMMENT_COUNT + " = " + COL_COMMENT_COUNT + " + 1 WHERE " + COL_ID + " = ?", new Object[]{postId});
        onLocalChange();
        return comment;
    }

    /** Tăng lượt chia sẻ; phần chưa gửi được giữ trong share_pending. */
    public boolean incrementShareCount(String postId) {
        getWritableDatabase().execSQL("UPDATE " + TABLE_POSTS + " SET " + COL_SHARE_COUNT + " = " + COL_SHARE_COUNT + " + 1, "
                + COL_SHARE_PENDING + " = " + COL_SHARE_PENDING + " + 1 WHERE " + COL_ID + " = ?", new Object[]{postId});
        onLocalChange();
        return true;
    }

    public boolean followUser(String follower, String targetUsername) {
        String f = norm(follower);
        String t = norm(targetUsername);
        if (f.isEmpty() || t.isEmpty() || f.equals(t)) return false; // Không tự theo dõi chính mình
        setFollow(f, t, true);
        onLocalChange();
        return true;
    }

    public boolean unfollowUser(String follower, String targetUsername) {
        String f = norm(follower);
        String t = norm(targetUsername);
        if (f.isEmpty() || t.isEmpty()) return false;
        setFollow(f, t, false);
        onLocalChange();
        return true;
    }

    private String getFollowStatus(String follower, String target) {
        Cursor c = getReadableDatabase().query(TABLE_FOLLOWS, new String[]{COL_SYNC_STATUS},
                COL_F_FOLLOWER + " = ? AND " + COL_F_FOLLOWING + " = ?", new String[]{follower, target}, null, null, null);
        String status = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return status;
    }

    private void setFollow(String follower, String target, boolean active) {
        SQLiteDatabase db = getWritableDatabase();
        String where = COL_F_FOLLOWER + " = ? AND " + COL_F_FOLLOWING + " = ?";
        String[] args = {follower, target};
        String current = getFollowStatus(follower, target);
        String next = SyncStatus.nextRelationState(current, active);
        if (SyncStatus.NO_CHANGE.equals(next)) return;
        if (SyncStatus.REMOVE_ROW.equals(next)) {
            db.delete(TABLE_FOLLOWS, where, args);
        } else if (current == null) {
            ContentValues cv = new ContentValues();
            cv.put(COL_F_FOLLOWER, follower);
            cv.put(COL_F_FOLLOWING, target);
            cv.put(COL_SYNC_STATUS, next);
            db.insert(TABLE_FOLLOWS, null, cv);
        } else {
            ContentValues cv = new ContentValues();
            cv.put(COL_SYNC_STATUS, next);
            db.update(TABLE_FOLLOWS, cv, where, args);
        }
    }

    public boolean updateUserProfile(String username, String fullName, String bio) {
        ContentValues cv = new ContentValues();
        cv.put(COL_U_FULLNAME, fullName);
        cv.put(COL_U_BIO, bio);
        cv.put(COL_SYNC_STATUS, SyncStatus.PENDING);
        int rows = getWritableDatabase().update(TABLE_USERS, cv, COL_U_USERNAME + " = ?", new String[]{norm(username)});
        onLocalChange();
        return rows > 0;
    }

    /** Đánh dấu thời điểm người dùng mở chi tiết bài (dùng để giữ 30 bài xem gần nhất). */
    public void markPostViewed(String postId) {
        ContentValues cv = new ContentValues();
        cv.put(COL_LAST_VIEWED_AT, System.currentTimeMillis());
        getWritableDatabase().update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{postId});
    }

    /** Đánh dấu thời điểm người dùng xem một trang (hồ sơ, danh mục...). */
    public void markFeedViewed(String feedKey) {
        SQLiteDatabase db = getWritableDatabase();
        ensureFeedMeta(db, feedKey);
        ContentValues cv = new ContentValues();
        cv.put(COL_LAST_VIEWED_AT, System.currentTimeMillis());
        db.update(TABLE_FEED_META, cv, COL_FEED_KEY + " = ?", new String[]{feedKey});
    }

    private void ensureFeedMeta(SQLiteDatabase db, String feedKey) {
        ContentValues cv = new ContentValues();
        cv.put(COL_FEED_KEY, feedKey);
        db.insertWithOnConflict(TABLE_FEED_META, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void addToFeed(SQLiteDatabase db, String feedKey, String postId) {
        ContentValues cv = new ContentValues();
        cv.put(COL_FEED_KEY, feedKey);
        cv.put(COL_FEED_POST_ID, postId);
        db.insertWithOnConflict(TABLE_FEED_CACHE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private ContentValues postToValues(PostModel post, long createdAt) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, post.getId());
        cv.put(COL_AUTHOR_NAME, post.getAuthorName());
        cv.put(COL_AUTHOR_USERNAME, norm(post.getAuthorUsername()));
        cv.put(COL_CAPTION, post.getCaption());
        cv.put(COL_SONG_TITLE, post.getSongTitle());
        cv.put(COL_ARTIST_NAME, post.getArtistName());
        cv.put(COL_MUSIC_URL, post.getMusicUrl());
        cv.put(COL_GENRE, post.getGenre());
        cv.put(COL_HASHTAGS, post.getHashtags());
        cv.put(COL_LIKE_COUNT, post.getLikeCount());
        cv.put(COL_COMMENT_COUNT, post.getCommentCount());
        cv.put(COL_SHARE_COUNT, post.getShareCount());
        cv.put(COL_AUDIO_RES, audioResName(post.getRawAudioRes()));
        cv.put(COL_IS_PRIVATE, post.isPrivate() ? 1 : 0);
        cv.put(COL_CREATED_AT, createdAt);
        return cv;
    }

    // ===================================================================================
    // Hàng chờ đồng bộ: SyncManager đọc các bản ghi PENDING để đẩy lên Firebase
    // ===================================================================================

    public List<PostModel> getPendingPosts() {
        return queryPosts("WHERE p." + COL_SYNC_STATUS + " = ? ORDER BY p." + COL_CREATED_AT + " ASC", SyncStatus.PENDING);
    }

    public List<PostModel> getPendingPostUpdates() {
        return queryPosts("WHERE p." + COL_SYNC_STATUS + " = ?", SyncStatus.PENDING_UPDATE);
    }

    public List<CommentModel> getPendingComments() {
        List<CommentModel> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_COMMENTS, null, COL_SYNC_STATUS + " = ?",
                new String[]{SyncStatus.PENDING}, null, null, COL_CREATED_AT + " ASC");
        while (c.moveToNext()) list.add(cursorToComment(c));
        c.close();
        return list;
    }

    /** Các quan hệ thích / lưu đang chờ: mỗi phần tử {username, postId, status}. */
    public List<String[]> getPendingRelations(String table) {
        List<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(table, new String[]{COL_R_USERNAME, COL_R_POST_ID, COL_SYNC_STATUS},
                COL_SYNC_STATUS + " IN (?, ?)", new String[]{SyncStatus.PENDING, SyncStatus.PENDING_DELETE},
                null, null, COL_CREATED_AT + " ASC");
        while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        c.close();
        return list;
    }

    /** Các quan hệ theo dõi đang chờ: mỗi phần tử {follower, following, status}. */
    public List<String[]> getPendingFollows() {
        List<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_FOLLOWS, new String[]{COL_F_FOLLOWER, COL_F_FOLLOWING, COL_SYNC_STATUS},
                COL_SYNC_STATUS + " IN (?, ?)", new String[]{SyncStatus.PENDING, SyncStatus.PENDING_DELETE}, null, null, null);
        while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        c.close();
        return list;
    }

    public List<UserModel> getPendingProfiles() {
        List<UserModel> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_USERS, null, COL_SYNC_STATUS + " = ?",
                new String[]{SyncStatus.PENDING}, null, null, null);
        while (c.moveToNext()) list.add(cursorToUser(c));
        c.close();
        return list;
    }

    /** Lượt chia sẻ chưa gửi: mỗi phần tử {postId, số lượt}. */
    public List<String[]> getPendingShares() {
        List<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_POSTS, new String[]{COL_ID, COL_SHARE_PENDING},
                COL_SHARE_PENDING + " > 0 AND " + COL_SYNC_STATUS + " = ?", new String[]{SyncStatus.SYNCED}, null, null, null);
        while (c.moveToNext()) list.add(new String[]{c.getString(0), String.valueOf(c.getInt(1))});
        c.close();
        return list;
    }

    public void setPostSyncStatus(String postId, String status) {
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNC_STATUS, status);
        getWritableDatabase().update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{postId});
    }

    public void setCommentSyncStatus(String commentId, String status) {
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNC_STATUS, status);
        getWritableDatabase().update(TABLE_COMMENTS, cv, COL_C_ID + " = ?", new String[]{commentId});
    }

    /** Gửi thành công: PENDING → SYNCED, PENDING_DELETE → xóa hẳn dòng. */
    public void completeRelation(String table, String username, String postId, String pushedStatus) {
        SQLiteDatabase db = getWritableDatabase();
        String where = COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = ?";
        String[] args = {username, postId, pushedStatus};
        if (SyncStatus.PENDING_DELETE.equals(pushedStatus)) {
            db.delete(table, where, args);
        } else {
            ContentValues cv = new ContentValues();
            cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
            db.update(table, cv, where, args);
        }
    }

    /** Bài viết không còn trên Firebase: bỏ thao tác thích / lưu đang chờ. */
    public void dropRelation(String table, String username, String postId) {
        getWritableDatabase().delete(table, COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ?", new String[]{username, postId});
    }

    public void completeFollow(String follower, String following, String pushedStatus) {
        SQLiteDatabase db = getWritableDatabase();
        String where = COL_F_FOLLOWER + " = ? AND " + COL_F_FOLLOWING + " = ? AND " + COL_SYNC_STATUS + " = ?";
        String[] args = {follower, following, pushedStatus};
        if (SyncStatus.PENDING_DELETE.equals(pushedStatus)) {
            db.delete(TABLE_FOLLOWS, where, args);
        } else {
            ContentValues cv = new ContentValues();
            cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
            db.update(TABLE_FOLLOWS, cv, where, args);
        }
    }

    public void setUserSynced(String username) {
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
        getWritableDatabase().update(TABLE_USERS, cv, COL_U_USERNAME + " = ?", new String[]{norm(username)});
    }

    public void consumePendingShares(String postId, int sent) {
        getWritableDatabase().execSQL("UPDATE " + TABLE_POSTS + " SET " + COL_SHARE_PENDING + " = MAX(0, "
                + COL_SHARE_PENDING + " - ?) WHERE " + COL_ID + " = ?", new Object[]{sent, postId});
    }

    // ===================================================================================
    // GHI dữ liệu mới tải từ Firebase (SyncManager gọi, chạy trên luồng nền, trong transaction)
    // ===================================================================================

    /** Ghi / cập nhật một bài từ Firebase nhưng giữ các thông tin riêng của máy (đã xem, lượt chia sẻ chờ gửi). */
    private void upsertServerPost(SQLiteDatabase db, PostModel post) {
        // Bản sửa của mình chưa gửi xong: không để dữ liệu cũ trên Firebase ghi đè
        Cursor c = db.query(TABLE_POSTS, new String[]{COL_SYNC_STATUS}, COL_ID + " = ?",
                new String[]{post.getId()}, null, null, null);
        String current = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        if (SyncStatus.PENDING_UPDATE.equals(current) || SyncStatus.PENDING_DELETE.equals(current)) return;
        ContentValues cv = postToValues(post, post.getCreatedAt());
        cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
        // Lượt chia sẻ đang chờ gửi vẫn được cộng vào số hiển thị
        int rows = db.update(TABLE_POSTS, cv, COL_ID + " = ?", new String[]{post.getId()});
        if (rows == 0) {
            db.insert(TABLE_POSTS, null, cv);
        } else {
            db.execSQL("UPDATE " + TABLE_POSTS + " SET " + COL_SHARE_COUNT + " = " + COL_SHARE_COUNT + " + " + COL_SHARE_PENDING
                    + " WHERE " + COL_ID + " = ?", new Object[]{post.getId()});
        }
        // Bộ đếm từ Firebase chưa tính các thao tác của mình đang chờ gửi: cộng lại để số hiển thị
        // không bị "nhảy ngược" khi tải lại / nhận realtime (lượt thích PENDING +1, bỏ thích PENDING_DELETE -1,
        // bình luận PENDING +1). Firebase xác nhận xong thì bộ đếm trên máy chủ đã gồm các thao tác này.
        String me = currentUser();
        db.execSQL("UPDATE " + TABLE_POSTS + " SET "
                + COL_LIKE_COUNT + " = MAX(0, " + COL_LIKE_COUNT
                + " + (SELECT COUNT(*) FROM " + TABLE_LIKES + " WHERE " + COL_R_POST_ID + " = ? AND " + COL_R_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = '" + SyncStatus.PENDING + "')"
                + " - (SELECT COUNT(*) FROM " + TABLE_LIKES + " WHERE " + COL_R_POST_ID + " = ? AND " + COL_R_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = '" + SyncStatus.PENDING_DELETE + "')), "
                + COL_COMMENT_COUNT + " = " + COL_COMMENT_COUNT
                + " + (SELECT COUNT(*) FROM " + TABLE_COMMENTS + " WHERE " + COL_C_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = '" + SyncStatus.PENDING + "')"
                + " WHERE " + COL_ID + " = ?",
                new Object[]{post.getId(), me, post.getId(), me, post.getId(), post.getId()});
    }

    /**
     * Áp trạng thái thích / lưu của người đang đăng nhập cho các bài vừa tải.
     * Bản ghi đang chờ đồng bộ (PENDING / PENDING_DELETE) được giữ nguyên.
     */
    private void applyMyRelations(SQLiteDatabase db, String table, Collection<PostModel> posts, Set<String> activeIds) {
        String me = currentUser();
        if (me.isEmpty()) return;
        for (PostModel p : posts) {
            String where = COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = ?";
            if (activeIds.contains(p.getId())) {
                ContentValues cv = new ContentValues();
                cv.put(COL_R_USERNAME, me);
                cv.put(COL_R_POST_ID, p.getId());
                cv.put(COL_CREATED_AT, System.currentTimeMillis());
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                if (TABLE_BOOKMARKS.equals(table)) {
                    // Bản nhạc đã lưu trên máy khác: ghi thông tin bản nhạc nếu dòng chưa có
                    db.update(table, savedTrackValues(p), COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ? AND "
                            + COL_B_SONG_TITLE + " IS NULL", new String[]{me, p.getId()});
                }
            } else {
                db.delete(table, where, new String[]{me, p.getId(), SyncStatus.SYNCED});
            }
        }
    }

    /**
     * Làm mới một trang: XÓA cache cũ của trang rồi LƯU lại từ đầu dữ liệu mới nhất (cùng một transaction,
     * nếu ghi lỗi thì cache cũ vẫn còn nguyên).
     *
     * @param replace true = tải lại trang (xóa cũ), false = lướt thêm (chỉ bổ sung)
     */
    public void saveFeedPage(String feedKey, List<PostModel> posts, Set<String> likedIds, Set<String> bookmarkedIds, boolean replace) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (replace) {
                // Giữ lại bài của mình đăng lúc offline (chưa có trên Firebase nên không nằm trong kết quả tải về)
                db.delete(TABLE_FEED_CACHE, COL_FEED_KEY + " = ? AND " + COL_FEED_POST_ID + " NOT IN (SELECT " + COL_ID
                        + " FROM " + TABLE_POSTS + " WHERE " + COL_SYNC_STATUS + " != ?)", new String[]{feedKey, SyncStatus.SYNCED});
            }
            for (PostModel p : posts) {
                upsertServerPost(db, p);
                addToFeed(db, feedKey, p.getId());
            }
            applyMyRelations(db, TABLE_LIKES, posts, likedIds);
            applyMyRelations(db, TABLE_BOOKMARKS, posts, bookmarkedIds);
            ensureFeedMeta(db, feedKey);
            ContentValues cv = new ContentValues();
            cv.put(COL_FEED_UPDATED_AT, System.currentTimeMillis());
            cv.put(COL_LAST_VIEWED_AT, System.currentTimeMillis());
            db.update(TABLE_FEED_META, cv, COL_FEED_KEY + " = ?", new String[]{feedKey});
            if (replace) {
                enforceCacheLimits(db);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Lưu chi tiết bài + 20 bình luận mới nhất (bình luận PENDING / FAILED của máy được giữ lại). */
    public void savePostDetail(PostModel post, boolean liked, boolean bookmarked, List<CommentModel> comments) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsertServerPost(db, post);
            List<PostModel> one = new ArrayList<>();
            one.add(post);
            Set<String> likedSet = new HashSet<>();
            if (liked) likedSet.add(post.getId());
            Set<String> savedSet = new HashSet<>();
            if (bookmarked) savedSet.add(post.getId());
            applyMyRelations(db, TABLE_LIKES, one, likedSet);
            applyMyRelations(db, TABLE_BOOKMARKS, one, savedSet);

            replaceServerComments(db, post.getId(), comments);
            ContentValues pv = new ContentValues();
            long now = System.currentTimeMillis();
            pv.put(COL_COMMENTS_CACHED_AT, now);
            pv.put(COL_LAST_VIEWED_AT, now);
            db.update(TABLE_POSTS, pv, COL_ID + " = ?", new String[]{post.getId()});
            enforceCacheLimits(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Thay các bình luận đã đồng bộ của bài bằng danh sách mới nhất (giữ bình luận PENDING / FAILED của máy). */
    private void replaceServerComments(SQLiteDatabase db, String postId, List<CommentModel> comments) {
        db.delete(TABLE_COMMENTS, COL_C_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = ?",
                new String[]{postId, SyncStatus.SYNCED});
        int n = 0;
        for (CommentModel cm : comments) {
            if (n++ >= LIMIT_COMMENTS_PER_POST) break;
            ContentValues cv = new ContentValues();
            cv.put(COL_C_ID, cm.getId());
            cv.put(COL_C_POST_ID, postId);
            cv.put(COL_C_AUTHOR_USERNAME, cm.getAuthorUsername());
            cv.put(COL_C_AUTHOR, cm.getAuthorName());
            cv.put(COL_C_CONTENT, cm.getContent());
            cv.put(COL_CREATED_AT, cm.getCreatedAt());
            cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
            db.insertWithOnConflict(TABLE_COMMENTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    // ---------- Realtime: thay đổi trên Firebase đẩy về máy (SyncManager gọi trên luồng nền) ----------

    /** Bài vừa được thêm / sửa trên Firebase: cập nhật bộ nhớ đệm (giữ trạng thái thích / lưu của máy). */
    public void applyRealtimePost(PostModel post, boolean addToHomeFeed) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsertServerPost(db, post);
            if (addToHomeFeed) addToFeed(db, FEED_HOME, post.getId());
            addToFeed(db, feedUser(post.getAuthorUsername()), post.getId());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Bình luận của bài vừa thay đổi trên Firebase. */
    public void applyRealtimeComments(String postId, List<CommentModel> comments) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            replaceServerComments(db, postId, comments);
            ContentValues pv = new ContentValues();
            pv.put(COL_COMMENTS_CACHED_AT, System.currentTimeMillis());
            db.update(TABLE_POSTS, pv, COL_ID + " = ?", new String[]{postId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Thay danh sách bài đã thích / đã lưu của người dùng bằng danh sách mới nhất từ Firebase. */
    public void saveRelationList(String table, String username, List<PostModel> posts) {
        SQLiteDatabase db = getWritableDatabase();
        String user = norm(username);
        db.beginTransaction();
        try {
            db.delete(table, COL_R_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = ?", new String[]{user, SyncStatus.SYNCED});
            for (PostModel p : posts) {
                upsertServerPost(db, p);
                ContentValues cv = new ContentValues();
                cv.put(COL_R_USERNAME, user);
                cv.put(COL_R_POST_ID, p.getId());
                cv.put(COL_CREATED_AT, System.currentTimeMillis());
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Realtime: đồng bộ lượt thích / bản nhạc đã lưu (đã đồng bộ) của {@code username} với đúng danh sách trên Firebase.
     * Bản ghi SYNCED không còn trên Firebase (ví dụ bị xóa trên Console) thì xóa; bản ghi mới thì thêm.
     * Thao tác đang chờ gửi (PENDING / PENDING_DELETE) được giữ nguyên.
     *
     * @param tracks thông tin bản nhạc theo postId (chỉ dùng cho bảng bookmarks, có thể thiếu)
     * @return true nếu SQLite có thay đổi
     */
    public boolean replaceSyncedRelations(String table, String username, Set<String> activePostIds, Map<String, PostModel> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        String user = norm(username);
        if (user.isEmpty()) return false;
        boolean bookmarks = TABLE_BOOKMARKS.equals(table);
        boolean changed = false;
        db.beginTransaction();
        try {
            List<String> stale = new ArrayList<>();
            Cursor c = db.query(table, new String[]{COL_R_POST_ID}, COL_R_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = ?",
                    new String[]{user, SyncStatus.SYNCED}, null, null, null);
            while (c.moveToNext()) {
                if (!activePostIds.contains(c.getString(0))) stale.add(c.getString(0));
            }
            c.close();
            for (String postId : stale) {
                db.delete(table, COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = ?",
                        new String[]{user, postId, SyncStatus.SYNCED});
                changed = true;
            }
            for (String postId : activePostIds) {
                PostModel track = tracks != null ? tracks.get(postId) : null;
                ContentValues cv = new ContentValues();
                cv.put(COL_R_USERNAME, user);
                cv.put(COL_R_POST_ID, postId);
                cv.put(COL_CREATED_AT, track != null && track.getCreatedAt() > 0 ? track.getCreatedAt() : System.currentTimeMillis());
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                if (db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1) changed = true;
                if (bookmarks && track != null) {
                    db.update(table, savedTrackValues(track), COL_R_USERNAME + " = ? AND " + COL_R_POST_ID + " = ? AND "
                            + COL_B_SONG_TITLE + " IS NULL", new String[]{user, postId});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    /**
     * Thay Danh sách nhạc đã lưu (đã đồng bộ) của {@code username} bằng bản mới nhất từ Firebase.
     * Bản nhạc lưu / bỏ lưu trên máy nhưng chưa gửi lên (PENDING / PENDING_DELETE) được giữ nguyên.
     */
    public void saveSavedTracks(String username, List<PostModel> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        String user = norm(username);
        db.beginTransaction();
        try {
            db.delete(TABLE_BOOKMARKS, COL_R_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = ?", new String[]{user, SyncStatus.SYNCED});
            for (PostModel t : tracks) {
                ContentValues cv = savedTrackValues(t);
                cv.put(COL_R_USERNAME, user);
                cv.put(COL_R_POST_ID, t.getId());
                cv.put(COL_CREATED_AT, t.getCreatedAt() > 0 ? t.getCreatedAt() : System.currentTimeMillis());
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Realtime: thay MỘT phía quan hệ theo dõi của {@code username} bằng danh sách mới nhất trên Firebase.
     * @param asFollower true = danh sách {@code username} đang theo dõi, false = danh sách người theo dõi {@code username}
     * @param others     username phía bên kia của từng quan hệ
     * Thao tác theo dõi / bỏ theo dõi đang chờ gửi được giữ nguyên.
     */
    public void replaceFollowSide(String username, boolean asFollower, Collection<String> others) {
        SQLiteDatabase db = getWritableDatabase();
        String user = norm(username);
        String mine = asFollower ? COL_F_FOLLOWER : COL_F_FOLLOWING;
        db.beginTransaction();
        try {
            db.delete(TABLE_FOLLOWS, mine + " = ? AND " + COL_SYNC_STATUS + " = ?", new String[]{user, SyncStatus.SYNCED});
            for (String other : others) {
                String o = norm(other);
                if (o.isEmpty() || o.equals(user) || isAdminAccount(db, o) || isAdminAccount(db, user)) continue;
                ContentValues cv = new ContentValues();
                cv.put(COL_F_FOLLOWER, asFollower ? user : o);
                cv.put(COL_F_FOLLOWING, asFollower ? o : user);
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(TABLE_FOLLOWS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Tài khoản quản trị hệ thống (admin gốc hoặc hồ sơ có vai trò Quản trị viên). */
    private boolean isAdminAccount(SQLiteDatabase db, String username) {
        if (SeedData.DEMO_ADMIN.equals(username)) return true;
        Cursor c = db.query(TABLE_USERS, null, COL_U_USERNAME + " = ?", new String[]{username}, null, null, null);
        boolean admin = false;
        if (c.moveToFirst()) {
            String role = c.getString(c.getColumnIndexOrThrow(COL_U_ROLE));
            admin = role != null && (role.contains("Admin") || role.contains("Quản trị"));
        }
        c.close();
        return admin;
    }

    /** Thay toàn bộ quan hệ theo dõi liên quan tới {@code username} (giữ các thao tác đang chờ). */
    public void saveFollows(String username, List<String[]> pairs) {
        SQLiteDatabase db = getWritableDatabase();
        String user = norm(username);
        db.beginTransaction();
        try {
            db.delete(TABLE_FOLLOWS, "(" + COL_F_FOLLOWER + " = ? OR " + COL_F_FOLLOWING + " = ?) AND " + COL_SYNC_STATUS + " = ?",
                    new String[]{user, user, SyncStatus.SYNCED});
            for (String[] pair : pairs) {
                // Tài khoản quản trị không tham gia quan hệ theo dõi của thành viên
                if (isAdminAccount(db, norm(pair[0])) || isAdminAccount(db, norm(pair[1]))) continue;
                ContentValues cv = new ContentValues();
                cv.put(COL_F_FOLLOWER, norm(pair[0]));
                cv.put(COL_F_FOLLOWING, norm(pair[1]));
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(TABLE_FOLLOWS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Ghi / cập nhật hồ sơ người dùng (không ghi đè hồ sơ của mình đang sửa dở lúc offline). */
    public void saveUsers(List<UserModel> users) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (UserModel u : users) {
                String name = norm(u.getUsername());
                ContentValues cv = new ContentValues();
                cv.put(COL_U_ROLE, u.getRole());
                cv.put(COL_U_AVATAR_INDEX, u.getAvatarIndex());
                cv.put(COL_U_IS_LOCKED, u.isLocked() ? 1 : 0);
                cv.put(COL_U_EMAIL, u.getEmail());
                int rows = db.update(TABLE_USERS, cv, COL_U_USERNAME + " = ?", new String[]{name});
                ContentValues names = new ContentValues();
                names.put(COL_U_FULLNAME, u.getFullName());
                names.put(COL_U_BIO, u.getBio());
                if (rows == 0) {
                    cv.putAll(names);
                    cv.put(COL_U_USERNAME, name);
                    cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                    db.insert(TABLE_USERS, null, cv);
                } else {
                    db.update(TABLE_USERS, names, COL_U_USERNAME + " = ? AND " + COL_SYNC_STATUS + " = ?",
                            new String[]{name, SyncStatus.SYNCED});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Bài đã bị xóa trên Firebase: dọn khỏi bộ nhớ đệm. */
    public void removePostLocal(String postId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_POSTS, COL_ID + " = ?", new String[]{postId});
            db.delete(TABLE_COMMENTS, COL_C_POST_ID + " = ? AND " + COL_SYNC_STATUS + " = ?", new String[]{postId, SyncStatus.SYNCED});
            db.delete(TABLE_LIKES, COL_R_POST_ID + " = ?", new String[]{postId});
            db.delete(TABLE_FEED_CACHE, COL_FEED_POST_ID + " = ?", new String[]{postId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Áp các giới hạn bộ nhớ đệm, xóa mục được xem lâu nhất trước.
     * Bài của chính mình, bài đã thích / đã lưu và mọi thao tác đang chờ không bao giờ bị xóa.
     */
    private void enforceCacheLimits(SQLiteDatabase db) {
        String me = currentUser();
        String synced = "'" + SyncStatus.SYNCED + "'";

        // 1. Bảng tin: giữ 50 bài mới nhất
        db.execSQL("DELETE FROM " + TABLE_FEED_CACHE + " WHERE " + COL_FEED_KEY + " = ? AND " + COL_FEED_POST_ID + " NOT IN ("
                + "SELECT f." + COL_FEED_POST_ID + " FROM " + TABLE_FEED_CACHE + " f INNER JOIN " + TABLE_POSTS + " p ON p." + COL_ID
                + " = f." + COL_FEED_POST_ID + " WHERE f." + COL_FEED_KEY + " = ? ORDER BY p." + COL_CREATED_AT + " DESC LIMIT "
                + LIMIT_HOME_FEED + ")", new Object[]{FEED_HOME, FEED_HOME});

        // 2. Hồ sơ người khác: chỉ giữ 10 hồ sơ xem gần nhất
        String myFeed = feedUser(me);
        db.execSQL("DELETE FROM " + TABLE_FEED_CACHE + " WHERE " + COL_FEED_KEY + " LIKE 'user:%' AND " + COL_FEED_KEY + " != ? AND "
                + COL_FEED_KEY + " NOT IN (SELECT " + COL_FEED_KEY + " FROM " + TABLE_FEED_META + " WHERE " + COL_FEED_KEY
                + " LIKE 'user:%' AND " + COL_FEED_KEY + " != ? ORDER BY " + COL_LAST_VIEWED_AT + " DESC LIMIT " + LIMIT_OTHER_PROFILES + ")",
                new Object[]{myFeed, myFeed});

        // 3. Chi tiết bài + bình luận: giữ 30 bài mở gần nhất
        db.execSQL("UPDATE " + TABLE_POSTS + " SET " + COL_COMMENTS_CACHED_AT + " = 0 WHERE " + COL_COMMENTS_CACHED_AT + " > 0 AND "
                + COL_ID + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_POSTS + " WHERE " + COL_COMMENTS_CACHED_AT + " > 0 ORDER BY "
                + COL_LAST_VIEWED_AT + " DESC LIMIT " + LIMIT_DETAIL_POSTS + ")");
        db.execSQL("DELETE FROM " + TABLE_COMMENTS + " WHERE " + COL_SYNC_STATUS + " = " + synced + " AND " + COL_C_POST_ID
                + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_POSTS + " WHERE " + COL_COMMENTS_CACHED_AT + " > 0)");

        // 4. Bài tương tự chỉ giữ cho các bài còn trong danh sách 30 bài chi tiết
        db.execSQL("DELETE FROM " + TABLE_FEED_CACHE + " WHERE " + COL_FEED_KEY + " LIKE 'similar:%' AND substr(" + COL_FEED_KEY
                + ", 9) NOT IN (SELECT " + COL_ID + " FROM " + TABLE_POSTS + " WHERE " + COL_COMMENTS_CACHED_AT + " > 0)");
        db.execSQL("DELETE FROM " + TABLE_FEED_META + " WHERE (" + COL_FEED_KEY + " LIKE 'similar:%' OR " + COL_FEED_KEY
                + " LIKE 'user:%') AND " + COL_FEED_KEY + " NOT IN (SELECT DISTINCT " + COL_FEED_KEY + " FROM " + TABLE_FEED_CACHE + ")");

        // 5. Xóa bài không còn thuộc trang nào
        db.execSQL("DELETE FROM " + TABLE_POSTS + " WHERE " + COL_SYNC_STATUS + " = " + synced
                + " AND " + COL_COMMENTS_CACHED_AT + " = 0"
                + " AND " + COL_SHARE_PENDING + " = 0"
                + " AND " + COL_AUTHOR_USERNAME + " != ?"
                + " AND " + COL_ID + " NOT IN (SELECT " + COL_FEED_POST_ID + " FROM " + TABLE_FEED_CACHE + ")"
                + " AND " + COL_ID + " NOT IN (SELECT " + COL_R_POST_ID + " FROM " + TABLE_LIKES + " WHERE " + COL_R_USERNAME + " = ?)"
                + " AND " + COL_ID + " NOT IN (SELECT " + COL_R_POST_ID + " FROM " + TABLE_BOOKMARKS + " WHERE " + COL_R_USERNAME + " = ?)"
                + " AND " + COL_ID + " NOT IN (SELECT " + COL_C_POST_ID + " FROM " + TABLE_COMMENTS + " WHERE " + COL_SYNC_STATUS + " != " + synced + ")",
                new Object[]{me, me, me});
        db.execSQL("DELETE FROM " + TABLE_LIKES + " WHERE " + COL_SYNC_STATUS + " = " + synced + " AND " + COL_R_POST_ID
                + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_POSTS + ")");
    }

    /** Đăng xuất: xóa toàn bộ dữ liệu riêng của tài khoản trên máy. */
    public void clearAllCache() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_POSTS, null, null);
            db.delete(TABLE_USERS, null, null);
            db.delete(TABLE_COMMENTS, null, null);
            db.delete(TABLE_FOLLOWS, null, null);
            db.delete(TABLE_LIKES, null, null);
            db.delete(TABLE_BOOKMARKS, null, null);
            db.delete(TABLE_FEED_CACHE, null, null);
            db.delete(TABLE_FEED_META, null, null);
            db.delete(TABLE_NOTIFICATIONS, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ===================================================================================
    // THÔNG BÁO theo từng tài khoản (Firestore là nguồn gốc, SQLite lưu đệm + trạng thái đọc chờ gửi)
    // ===================================================================================

    /** Thông báo của đúng tài khoản {@code recipient}, mới nhất trước. */
    public List<NotificationModel> getNotifications(String recipient) {
        List<NotificationModel> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_NOTIFICATIONS, null, COL_N_RECIPIENT + " = ?",
                new String[]{norm(recipient)}, null, null, COL_CREATED_AT + " DESC");
        while (c.moveToNext()) list.add(cursorToNotification(c));
        c.close();
        return list;
    }

    public int getUnreadNotificationCount(String recipient) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_NOTIFICATIONS + " WHERE "
                + COL_N_RECIPIENT + " = ? AND " + COL_N_IS_READ + " = 0", new String[]{norm(recipient)});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    private NotificationModel cursorToNotification(Cursor c) {
        return new NotificationModel(
                c.getString(c.getColumnIndexOrThrow(COL_N_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_N_RECIPIENT)),
                c.getString(c.getColumnIndexOrThrow(COL_N_TYPE)),
                c.getString(c.getColumnIndexOrThrow(COL_N_ACTOR)),
                c.getString(c.getColumnIndexOrThrow(COL_N_ACTOR_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_N_POST_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_N_COMMENT_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_N_SONG_TITLE)),
                c.getString(c.getColumnIndexOrThrow(COL_N_PREVIEW)),
                c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)),
                c.getInt(c.getColumnIndexOrThrow(COL_N_IS_READ)) == 1,
                c.getInt(c.getColumnIndexOrThrow(COL_N_IS_SAMPLE)) == 1);
    }

    /** Đánh dấu đã đọc MỘT thông báo (lưu ngay trên máy, PENDING cho tới khi Firestore xác nhận). */
    public boolean markNotificationRead(String id) {
        ContentValues cv = new ContentValues();
        cv.put(COL_N_IS_READ, 1);
        cv.put(COL_SYNC_STATUS, SyncStatus.PENDING);
        int rows = getWritableDatabase().update(TABLE_NOTIFICATIONS, cv,
                COL_N_ID + " = ? AND " + COL_N_IS_READ + " = 0", new String[]{id});
        if (rows > 0) onLocalChange();
        return rows > 0;
    }

    /** Đánh dấu đã đọc mọi thông báo của tài khoản {@code recipient}. */
    public int markAllNotificationsRead(String recipient) {
        ContentValues cv = new ContentValues();
        cv.put(COL_N_IS_READ, 1);
        cv.put(COL_SYNC_STATUS, SyncStatus.PENDING);
        int rows = getWritableDatabase().update(TABLE_NOTIFICATIONS, cv,
                COL_N_RECIPIENT + " = ? AND " + COL_N_IS_READ + " = 0", new String[]{norm(recipient)});
        if (rows > 0) onLocalChange();
        return rows;
    }

    /** ID các thông báo của {@code recipient} đã đọc trên máy nhưng chưa gửi trạng thái lên Firestore. */
    public List<String> getPendingNotificationReads(String recipient) {
        List<String> ids = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_NOTIFICATIONS, new String[]{COL_N_ID},
                COL_N_RECIPIENT + " = ? AND " + COL_SYNC_STATUS + " = ?", new String[]{norm(recipient), SyncStatus.PENDING},
                null, null, null);
        while (c.moveToNext()) ids.add(c.getString(0));
        c.close();
        return ids;
    }

    public void setNotificationSynced(String id) {
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
        getWritableDatabase().update(TABLE_NOTIFICATIONS, cv, COL_N_ID + " = ?", new String[]{id});
    }

    /**
     * Thay danh sách thông báo của {@code recipient} bằng bản mới nhất từ Firestore.
     * Thông báo đã đọc trên máy nhưng chưa gửi lên (PENDING) vẫn giữ trạng thái đã đọc.
     */
    public void saveNotifications(String recipient, List<NotificationModel> list) {
        String user = norm(recipient);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Set<String> pendingRead = new HashSet<>(getPendingNotificationReads(user));
            db.delete(TABLE_NOTIFICATIONS, COL_N_RECIPIENT + " = ? AND " + COL_SYNC_STATUS + " = ?",
                    new String[]{user, SyncStatus.SYNCED});
            for (NotificationModel n : list) {
                if (pendingRead.contains(n.getId())) continue;
                ContentValues cv = new ContentValues();
                cv.put(COL_N_ID, n.getId());
                cv.put(COL_N_RECIPIENT, user);
                cv.put(COL_N_TYPE, n.getType());
                cv.put(COL_N_ACTOR, n.getActor());
                cv.put(COL_N_ACTOR_NAME, n.getActorName());
                cv.put(COL_N_POST_ID, n.getPostId());
                cv.put(COL_N_COMMENT_ID, n.getCommentId());
                cv.put(COL_N_SONG_TITLE, n.getSongTitle());
                cv.put(COL_N_PREVIEW, n.getPreview());
                cv.put(COL_CREATED_AT, n.getCreatedAt());
                cv.put(COL_N_IS_READ, n.isRead() ? 1 : 0);
                cv.put(COL_N_IS_SAMPLE, n.isSample() ? 1 : 0);
                cv.put(COL_SYNC_STATUS, SyncStatus.SYNCED);
                db.insertWithOnConflict(TABLE_NOTIFICATIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
