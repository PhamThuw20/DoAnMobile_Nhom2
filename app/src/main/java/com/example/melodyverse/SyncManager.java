package com.example.melodyverse;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Đồng bộ hai chiều giữa SQLite (bộ nhớ đệm offline) và Firebase Firestore.
 *
 * Quy tắc:
 * 1. Mở / tải lại trang khi có mạng: đẩy hết thao tác đang chờ → tải trang từ Firebase →
 *    xóa cache cũ của trang và lưu lại từ đầu (cùng 1 transaction SQLite).
 * 2. Lướt tiếp: tải thêm 10 bài và lưu bổ sung.
 * 3. Mất mạng: màn hình chỉ đọc SQLite; thao tác mới được lưu với cờ PENDING.
 * 4. Có mạng trở lại: tự đẩy các bản ghi PENDING, xong thì báo màn hình đang mở làm mới.
 *
 * 5. Realtime: khi app đang mở, lắng nghe Firestore (addSnapshotListener) — ai sửa / thêm / xóa bài,
 *    bình luận hoặc Admin khóa tài khoản trên Firebase thì SQLite và màn hình cập nhật ngay.
 *
 * Mọi thao tác mạng chạy tuần tự trên MỘT luồng nền nên không bao giờ chặn giao diện
 * và không bị ghi chồng lên nhau.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";

    /** Đã đẩy xong thao tác chờ → màn hình nên đọc lại SQLite (đổi nhãn chờ thành bình thường). */
    public static final String EVENT_DATA_CHANGED = "data_changed";
    /** Có mạng trở lại và đã đẩy xong → màn hình đang mở nên tải lại từ Firebase. */
    public static final String EVENT_RECONNECTED = "reconnected";
    /** Tài khoản đang đăng nhập vừa bị Quản trị viên khóa. */
    public static final String EVENT_ACCOUNT_LOCKED = "account_locked";

    public enum Status { OK, OFFLINE, NOT_FOUND, ERROR }

    public interface Listener {
        void onSyncEvent(String event);
    }

    public interface Callback {
        void onDone(Status status, boolean hasMore);
    }

    /** Danh sách bài bị báo cáo vi phạm cập nhật theo thời gian thực (trang Quản trị). */
    public interface ReportsListener {
        void onReports(List<ReportedPost> reports);
    }

    public interface AdminDataCallback {
        void onDone(Status status, List<PostModel> posts, List<UserModel> users, List<ReportedPost> reports);
    }

    /** Một bài viết bị thành viên báo cáo vi phạm (gộp mọi lượt báo cáo của bài). */
    public static class ReportedPost {
        public final PostModel post;
        public final int reportCount;
        /** Các lý do khác nhau, ví dụ "Spam, Nội dung phản cảm". */
        public final String reasons;
        public final long lastReportedAt;

        ReportedPost(PostModel post, int reportCount, String reasons, long lastReportedAt) {
            this.post = post;
            this.reportCount = reportCount;
            this.reasons = reasons;
            this.lastReportedAt = lastReportedAt;
        }
    }

    public static final int PAGE_SIZE = 10;
    /** Số trang tải liên tiếp tối đa khi các trang toàn bài bị lọc (riêng tư / bị ẩn). */
    private static final int MAX_EMPTY_PAGES = 5;
    /** Bài cuối cùng của lần tải Bảng tin gần nhất: con trỏ phân trang cho "Tải thêm". */
    private volatile DocumentSnapshot homeCursor;
    private static final int SCAN_SIZE = 60;
    private static final long TIMEOUT_SECONDS = 20;
    private static final long ACCOUNT_CHECK_INTERVAL = 60_000;

    // Tên các collection trên Firestore
    static final String C_USERS = "users";
    static final String C_POSTS = "posts";
    static final String C_COMMENTS = "comments";
    static final String C_LIKES = "likes";
    static final String C_BOOKMARKS = "bookmarks";
    static final String C_FOLLOWS = "follows";
    static final String C_ADMINS = "admins";
    static final String C_META = "meta";
    static final String C_NOTIFICATIONS = "notifications";
    /** Báo cáo vi phạm: reports/{postId}_{người báo cáo}. */
    static final String C_REPORTS = "reports";
    private static final int CHART_SCAN_SIZE = 30;
    private static final long NOTIFICATION_FETCH_INTERVAL = 30_000;

    private static SyncManager instance;

    private final Context app;
    private final DatabaseHelper db;
    private final FirebaseFirestore fs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pushScheduled = new AtomicBoolean(false);
    private long lastAccountCheck = 0;

    // Realtime listeners (Firestore addSnapshotListener)
    private ListenerRegistration postsReg, meReg, notifReg, watchedPostReg, watchedCommentsReg;
    private ListenerRegistration followingReg, followersReg;
    /** Lượt thả tim / bản nhạc đã lưu của tài khoản đang đăng nhập (xóa trên Console là máy cập nhật ngay). */
    private ListenerRegistration likesReg, bookmarksReg;
    /**
     * Bài thêm thẳng trên Firebase Console mà thiếu trường createdAt (truy vấn orderBy("createdAt") bỏ qua các bài này):
     * realtime vẫn nhận được và giữ lại để lần tải lại bảng tin không làm mất bài.
     */
    private final Map<String, PostModel> undatedPosts = new java.util.concurrent.ConcurrentHashMap<>();
    private long lastNotificationFetch = 0;
    /** username → uid Firebase Auth (để gắn thông báo cho đúng tài khoản nhận). */
    private final Map<String, String> uidCache = new java.util.concurrent.ConcurrentHashMap<>();
    private String realtimeUser = null;
    private String watchedPostId = null;
    private boolean postsSnapshotSeen = false;

    public static synchronized SyncManager get(Context context) {
        if (instance == null) {
            instance = new SyncManager(context.getApplicationContext());
        }
        return instance;
    }

    private SyncManager(Context context) {
        app = context;
        db = DatabaseHelper.getInstance(context);
        fs = FirebaseFirestore.getInstance();
        try {
            // Tắt bộ nhớ offline có sẵn của Firestore: SQLite là nơi lưu offline DUY NHẤT của ứng dụng
            fs.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build());
        } catch (IllegalStateException ignored) {
            // Firestore đã được dùng trước đó, giữ nguyên cấu hình
        }
    }

    FirebaseFirestore firestore() {
        return fs;
    }

    // ===================================================================================
    // Trạng thái & sự kiện
    // ===================================================================================

    /**
     * Có kết nối THỰC SỰ ra Internet: mạng phải có khả năng INTERNET và đã được hệ thống xác minh (VALIDATED).
     * Chỉ bật Wi-Fi nhưng chưa vào được Internet (Wi-Fi cần đăng nhập, mất mạng thượng nguồn) vẫn coi là offline.
     */
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private boolean isSignedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null && !me().isEmpty();
    }

    private String me() {
        return DatabaseHelper.norm(SessionManager.getCurrentUsername(app));
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void emit(String event) {
        main.post(() -> {
            for (Listener l : listeners) l.onSyncEvent(event);
        });
    }

    private void deliver(Callback cb, Status status, boolean hasMore) {
        if (cb != null) main.post(() -> cb.onDone(status, hasMore));
    }

    /** Chạy một việc trên luồng đồng bộ (dùng chung với AuthManager để không ghi chồng). */
    void runInBackground(Runnable task) {
        executor.execute(task);
    }

    <T> T await(Task<T> task) throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        return Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Lỗi vĩnh viễn (bị từ chối / không tồn tại) - khác với lỗi mạng tạm thời. */
    static boolean isPermanentError(Exception e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) cause).getCode();
            return code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || code == FirebaseFirestoreException.Code.NOT_FOUND
                    || code == FirebaseFirestoreException.Code.INVALID_ARGUMENT;
        }
        return false;
    }

    // ===================================================================================
    // ĐẨY thao tác chờ lên Firebase (Auto-Push)
    // ===================================================================================

    /** Có thao tác mới: nếu đang online thì đẩy ngay (gộp nhiều yêu cầu liên tiếp thành 1 lượt). */
    public void requestPush() {
        if (!isOnline() || !isSignedIn()) return;
        if (pushScheduled.compareAndSet(false, true)) {
            executor.execute(() -> {
                pushScheduled.set(false);
                if (pushPendingBlocking()) emit(EVENT_DATA_CHANGED);
            });
        }
    }

    /** NetworkMonitor gọi khi điện thoại có Internet trở lại. */
    public void onReconnected() {
        if (!isSignedIn()) return;
        executor.execute(() -> {
            pushPendingBlocking();
            emit(EVENT_RECONNECTED);
        });
    }

    /**
     * Quét các bản ghi PENDING trong SQLite và gửi lên Firebase theo đúng thứ tự:
     * bài viết → bình luận → thích → lưu → theo dõi → hồ sơ → lượt chia sẻ.
     * Gặp lỗi mạng thì dừng, giữ nguyên PENDING để lần sau gửi tiếp.
     *
     * @return true nếu có bản ghi đổi trạng thái
     */
    private boolean pushPendingBlocking() {
        if (!isOnline() || !isSignedIn()) return false;
        String me = me();
        boolean changed = false;
        try {
            for (PostModel p : db.getPendingPosts()) {
                if (!me.equals(DatabaseHelper.norm(p.getAuthorUsername()))) continue;
                DocumentReference ref = fs.collection(C_POSTS).document(p.getId());
                Map<String, Object> data = postToMap(p);
                // Bài mới bắt đầu từ 0; bình luận / lượt thích chờ gửi sẽ tự cộng sau
                data.put("likeCount", 0L);
                data.put("commentCount", 0L);
                data.put("shareCount", 0L);
                try {
                    await(fs.runTransaction(tx -> {
                        if (!tx.get(ref).exists()) tx.set(ref, data);
                        return null;
                    }));
                    db.setPostSyncStatus(p.getId(), SyncStatus.SYNCED);
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    db.setPostSyncStatus(p.getId(), SyncStatus.FAILED);
                }
                changed = true;
            }

            for (String postId : db.getPendingPostDeletes()) {
                try {
                    deletePostRemote(postId, false);
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                }
                db.removePostLocal(postId);
                changed = true;
            }

            for (PostModel p : db.getPendingPostUpdates()) {
                if (!me.equals(DatabaseHelper.norm(p.getAuthorUsername()))) continue;
                try {
                    await(fs.collection(C_POSTS).document(p.getId()).update(editableFields(p)));
                    db.setPostSyncStatus(p.getId(), SyncStatus.SYNCED);
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    // Bài đã bị xóa trên Firebase: bỏ bản sửa
                    db.removePostLocal(p.getId());
                }
                changed = true;
            }

            for (CommentModel c : db.getPendingComments()) {
                if (!me.equals(DatabaseHelper.norm(c.getAuthorUsername()))) continue;
                DocumentReference postRef = fs.collection(C_POSTS).document(c.getPostId());
                DocumentReference ref = postRef.collection(C_COMMENTS).document(c.getId());
                Map<String, Object> data = commentToMap(c);
                String[] postInfo = new String[2]; // {tác giả bài, tên bài hát}
                try {
                    await(fs.runTransaction(tx -> {
                        DocumentSnapshot ps = tx.get(postRef);
                        if (!ps.exists()) {
                            throw new FirebaseFirestoreException("Bài viết đã bị xóa", FirebaseFirestoreException.Code.NOT_FOUND);
                        }
                        postInfo[0] = ps.getString("authorUsername");
                        postInfo[1] = ps.getString("songTitle");
                        // Gửi lại lần 2 (do rớt mạng giữa chừng) không tạo bình luận trùng
                        if (!tx.get(ref).exists()) {
                            tx.set(ref, data);
                            tx.update(postRef, "commentCount", FieldValue.increment(1));
                        }
                        return null;
                    }));
                    db.setCommentSyncStatus(c.getId(), SyncStatus.SYNCED);
                    // Báo cho tác giả bài viết (ID cố định theo bình luận nên gửi lại không tạo trùng)
                    createNotificationBestEffort(postInfo[0], NotificationModel.TYPE_COMMENT, "comment_" + c.getId(),
                            c.getPostId(), c.getId(), postInfo[1], c.getContent());
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    db.setCommentSyncStatus(c.getId(), SyncStatus.FAILED);
                }
                changed = true;
            }

            for (String[] r : db.getPendingRelations(DatabaseHelper.TABLE_LIKES)) {
                String user = r[0], postId = r[1], status = r[2];
                if (!me.equals(user)) continue;
                DocumentReference postRef = fs.collection(C_POSTS).document(postId);
                DocumentReference ref = fs.collection(C_LIKES).document(postId + "_" + user);
                boolean like = SyncStatus.PENDING.equals(status);
                String[] postInfo = new String[2]; // {tác giả bài, tên bài hát}
                try {
                    await(fs.runTransaction(tx -> {
                        DocumentSnapshot ps = tx.get(postRef);
                        if (!ps.exists()) {
                            throw new FirebaseFirestoreException("Bài viết đã bị xóa", FirebaseFirestoreException.Code.NOT_FOUND);
                        }
                        postInfo[0] = ps.getString("authorUsername");
                        postInfo[1] = ps.getString("songTitle");
                        boolean exists = tx.get(ref).exists();
                        if (like && !exists) {
                            tx.set(ref, relationMap(postId, user));
                            tx.update(postRef, "likeCount", FieldValue.increment(1));
                        } else if (!like && exists) {
                            tx.delete(ref);
                            tx.update(postRef, "likeCount", FieldValue.increment(-1));
                        }
                        return null;
                    }));
                    db.completeRelation(DatabaseHelper.TABLE_LIKES, user, postId, status);
                    // Thông báo cho tác giả theo TRẠNG THÁI CUỐI: thích → có thông báo, bỏ thích → gỡ thông báo
                    String notifId = "like_" + postId + "_" + user;
                    if (like) {
                        createNotificationBestEffort(postInfo[0], NotificationModel.TYPE_LIKE, notifId, postId, null, postInfo[1], null);
                    } else {
                        deleteNotificationBestEffort(notifId);
                    }
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    db.dropRelation(DatabaseHelper.TABLE_LIKES, user, postId);
                }
                changed = true;
            }

            for (String[] r : db.getPendingRelations(DatabaseHelper.TABLE_BOOKMARKS)) {
                String user = r[0], postId = r[1], status = r[2];
                if (!me.equals(user)) continue;
                DocumentReference ref = fs.collection(C_BOOKMARKS).document(postId + "_" + user);
                try {
                    if (SyncStatus.PENDING.equals(status)) {
                        // Lưu BẢN NHẠC (tên bài, nghệ sĩ, nguồn nhạc) để Danh sách nhạc đã lưu không phụ thuộc bài viết
                        await(ref.set(savedTrackMap(postId, user, db.getSavedTrack(user, postId))));
                    } else {
                        await(ref.delete());
                    }
                    db.completeRelation(DatabaseHelper.TABLE_BOOKMARKS, user, postId, status);
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    db.dropRelation(DatabaseHelper.TABLE_BOOKMARKS, user, postId);
                }
                changed = true;
            }

            for (String[] f : db.getPendingFollows()) {
                String follower = f[0], following = f[1], status = f[2];
                if (!me.equals(follower)) continue;
                DocumentReference ref = fs.collection(C_FOLLOWS).document(follower + "_" + following);
                try {
                    String notifId = "follow_" + follower + "_" + following;
                    if (SyncStatus.PENDING.equals(status)) {
                        await(ref.set(followMap(follower, following)));
                        createNotificationBestEffort(following, NotificationModel.TYPE_FOLLOW, notifId, null, null, null, null);
                    } else {
                        await(ref.delete());
                        deleteNotificationBestEffort(notifId);
                    }
                    db.completeFollow(follower, following, status);
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    db.completeFollow(follower, following, status);
                }
                changed = true;
            }

            for (UserModel u : db.getPendingProfiles()) {
                if (!me.equals(DatabaseHelper.norm(u.getUsername()))) continue;
                Map<String, Object> data = new HashMap<>();
                data.put("fullName", u.getFullName());
                data.put("bio", u.getBio());
                try {
                    await(fs.collection(C_USERS).document(me).update(data));
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                }
                db.setUserSynced(me);
                changed = true;
            }

            for (String[] s : db.getPendingShares()) {
                String postId = s[0];
                int count = Integer.parseInt(s[1]);
                DocumentReference postRef = fs.collection(C_POSTS).document(postId);
                try {
                    await(fs.runTransaction(tx -> {
                        if (tx.get(postRef).exists()) {
                            tx.update(postRef, "shareCount", FieldValue.increment(count));
                        }
                        return null;
                    }));
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                }
                db.consumePendingShares(postId, count);
                changed = true;
            }

            // Trạng thái đã đọc của từng thông báo (để thiết bị khác của cùng tài khoản cũng thấy đã đọc)
            for (String notifId : db.getPendingNotificationReads(me)) {
                Map<String, Object> read = new HashMap<>();
                read.put("read", true);
                read.put("readAt", System.currentTimeMillis());
                try {
                    await(fs.collection(C_NOTIFICATIONS).document(notifId).update(read));
                } catch (Exception e) {
                    if (!isPermanentError(e)) throw e;
                    // Thông báo không còn trên máy chủ / không có quyền: giữ trạng thái đã đọc trên máy
                }
                db.setNotificationSynced(notifId);
                changed = true;
            }
        } catch (Exception e) {
            // Lỗi mạng tạm thời: giữ nguyên các bản ghi PENDING còn lại để lần sau gửi tiếp
            Log.w(TAG, "Tạm dừng đồng bộ: " + e.getMessage());
        }
        return changed;
    }

    // ===================================================================================
    // TẢI dữ liệu từ Firebase về SQLite theo từng trang
    // ===================================================================================

    private interface BlockingJob {
        Status run() throws Exception;
    }

    /** Khung chung: offline → báo OFFLINE (màn hình giữ dữ liệu SQLite); online → đẩy chờ rồi tải. */
    private void runRefresh(Callback cb, boolean[] hasMore, BlockingJob job) {
        if (!isOnline() || !isSignedIn()) {
            deliver(cb, Status.OFFLINE, false);
            return;
        }
        executor.execute(() -> {
            Status status;
            try {
                pushPendingBlocking();
                status = job.run();
            } catch (Exception e) {
                Log.w(TAG, "Tải dữ liệu thất bại: " + e.getMessage());
                status = Status.ERROR;
            }
            deliver(cb, status, hasMore != null && hasMore[0]);
        });
    }

    /**
     * Bảng tin.
     * @param reset true = mở / vuốt làm mới (thay toàn bộ cache), false = lướt tới cuối, tải thêm 10 bài
     */
    public void refreshHome(boolean reset, Callback cb) {
        boolean[] hasMore = {false};
        runRefresh(cb, hasMore, () -> {
            Query base = fs.collection(C_POSTS).orderBy("createdAt", Query.Direction.DESCENDING);
            DocumentSnapshot cursor = reset ? null : homeCursor;
            long oldest = reset || cursor != null ? 0 : db.getOldestFeedCreatedAt(DatabaseHelper.FEED_HOME);
            List<PostModel> page = new ArrayList<>();
            // Con trỏ là bài CUỐI CÙNG đã tải về (kể cả bài bị lọc: riêng tư / bị ẩn), nên luôn tiến lên.
            // Cả trang bị lọc hết thì tải tiếp trang sau, tối đa vài lượt, để không kẹt ở "Tải thêm".
            for (int round = 0; round < MAX_EMPTY_PAGES && page.isEmpty(); round++) {
                Query q = base;
                if (cursor != null) q = q.startAfter(cursor);
                else if (oldest > 0) q = q.startAfter(oldest);
                QuerySnapshot snap = await(q.limit(PAGE_SIZE).get());
                hasMore[0] = snap.size() >= PAGE_SIZE;
                if (!snap.isEmpty()) {
                    cursor = snap.getDocuments().get(snap.size() - 1);
                    homeCursor = cursor;
                }
                page.addAll(visiblePosts(snap.getDocuments()));
                if (!hasMore[0]) break;
            }
            if (reset) {
                for (PostModel p : undatedPosts.values()) {
                    if (isVisible(p)) page.add(p);
                }
            }
            savePage(DatabaseHelper.FEED_HOME, page, reset);
            return Status.OK;
        });
    }

    /**
     * Bảng xếp hạng: các bài nhiều lượt thả tim nhất trên TOÀN hệ thống (không chỉ các bài đã lướt qua).
     * MusicChart cộng dồn lượt tim theo từng bài hát rồi xếp hạng.
     */
    public void refreshChart(Callback cb) {
        runRefresh(cb, null, () -> {
            QuerySnapshot snap = await(fs.collection(C_POSTS)
                    .orderBy("likeCount", Query.Direction.DESCENDING).limit(CHART_SCAN_SIZE).get());
            savePage(DatabaseHelper.FEED_CHART, visiblePosts(snap.getDocuments()), true);
            return Status.OK;
        });
    }

    /** Danh sách bài theo danh mục thể loại (tối đa 20 bài mới nhất khớp từ khóa). */
    public void refreshGenre(String label, String[] keywords, Callback cb) {
        runRefresh(cb, null, () -> {
            QuerySnapshot snap = await(fs.collection(C_POSTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(SCAN_SIZE).get());
            List<PostModel> matched = new ArrayList<>();
            for (PostModel p : visiblePosts(snap.getDocuments())) {
                if (matchesAny(p, keywords)) matched.add(p);
                if (matched.size() >= DatabaseHelper.LIMIT_GENRE_FEED) break;
            }
            savePage(DatabaseHelper.feedGenre(label), matched, true);
            return Status.OK;
        });
    }

    /** Chi tiết bài + 20 bình luận mới nhất + 5 bài tương tự. NOT_FOUND nếu bài đã bị xóa. */
    public void refreshPostDetail(String postId, Callback cb) {
        runRefresh(cb, null, () -> {
            DocumentReference ref = fs.collection(C_POSTS).document(postId);
            DocumentSnapshot doc = await(ref.get());
            PostModel post = doc.exists() ? docToPost(doc) : null;
            PostModel local = db.getPostById(postId);
            if (post == null && local != null && !SyncStatus.SYNCED.equals(local.getSyncStatus())) {
                // Bài mình đăng lúc offline, chưa lên được Firebase: giữ nguyên bản trên máy
                return Status.OK;
            }
            if (post == null || !isVisible(post)) {
                db.removePostLocal(postId);
                onPostUnavailable(postId);
                return Status.NOT_FOUND;
            }
            QuerySnapshot cs = await(ref.collection(C_COMMENTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(DatabaseHelper.LIMIT_COMMENTS_PER_POST).get());
            List<CommentModel> comments = new ArrayList<>();
            for (DocumentSnapshot d : cs.getDocuments()) comments.add(docToComment(postId, d));
            boolean liked = await(fs.collection(C_LIKES).document(postId + "_" + me()).get()).exists();
            boolean saved = await(fs.collection(C_BOOKMARKS).document(postId + "_" + me()).get()).exists();
            db.savePostDetail(post, liked, saved, comments);

            // Bài tương tự: cùng thể loại, mới nhất trước
            QuerySnapshot ss = await(fs.collection(C_POSTS).whereEqualTo("genre", post.getGenre()).limit(SCAN_SIZE).get());
            List<PostModel> similar = new ArrayList<>();
            for (PostModel p : visiblePosts(ss.getDocuments())) {
                if (!p.getId().equals(postId)) similar.add(p);
            }
            similar.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            if (similar.size() > DatabaseHelper.LIMIT_SIMILAR) {
                similar = new ArrayList<>(similar.subList(0, DatabaseHelper.LIMIT_SIMILAR));
            }
            savePage(DatabaseHelper.feedSimilar(postId), similar, true);
            return Status.OK;
        });
    }

    public interface UsersCallback {
        void onDone(List<UserModel> users);
    }

    public interface CommentsCallback {
        /** @param comments null nếu offline / lỗi */
        void onDone(List<CommentModel> comments);
    }

    /**
     * Tải {@code limit} bình luận của bài từ Firebase theo thứ tự thời gian (cũ nhất trước hoặc mới nhất trước).
     * Dùng cho "Xem thêm bình luận" và sắp xếp "Cũ nhất" (bộ nhớ đệm SQLite chỉ giữ 20 bình luận mới nhất).
     */
    public void loadComments(String postId, boolean oldestFirst, int limit, CommentsCallback cb) {
        if (!isOnline() || !isSignedIn()) {
            main.post(() -> cb.onDone(null));
            return;
        }
        executor.execute(() -> {
            List<CommentModel> comments = null;
            try {
                QuerySnapshot cs = await(fs.collection(C_POSTS).document(postId).collection(C_COMMENTS)
                        .orderBy("createdAt", oldestFirst ? Query.Direction.ASCENDING : Query.Direction.DESCENDING)
                        .limit(limit).get());
                comments = new ArrayList<>();
                for (DocumentSnapshot d : cs.getDocuments()) comments.add(docToComment(postId, d));
            } catch (Exception e) {
                Log.w(TAG, "Tải bình luận thất bại: " + e.getMessage());
            }
            List<CommentModel> result = comments;
            main.post(() -> cb.onDone(result));
        });
    }

    /**
     * Gợi ý theo dõi cho thành viên mới: chỉ lấy tài khoản đã đăng ký thật trên Firestore
     * (bỏ chính mình, tài khoản quản trị và tài khoản bị khóa). Offline / lỗi → danh sách rỗng.
     */
    public void loadSuggestedUsers(String me, int limit, UsersCallback cb) {
        String self = DatabaseHelper.norm(me);
        if (!isOnline() || !isSignedIn()) {
            main.post(() -> cb.onDone(new ArrayList<>()));
            return;
        }
        executor.execute(() -> {
            List<UserModel> users = new ArrayList<>();
            try {
                for (DocumentSnapshot d : await(fs.collection(C_USERS).limit(200).get()).getDocuments()) {
                    UserModel u = docToUser(d);
                    String name = DatabaseHelper.norm(u.getUsername());
                    String role = u.getRole() == null ? "" : u.getRole();
                    if (name.equals(self) || SeedData.DEMO_ADMIN.equals(name) || u.isLocked()
                            || role.contains("Admin") || role.contains("Quản trị")) continue;
                    users.add(u);
                    if (users.size() >= limit) break;
                }
                db.saveUsers(users);
            } catch (Exception e) {
                Log.w(TAG, "Tải gợi ý theo dõi thất bại: " + e.getMessage());
            }
            main.post(() -> cb.onDone(users));
        });
    }

    /** Hồ sơ: thông tin, bài đã đăng, người theo dõi / đang theo dõi; hồ sơ của mình thêm bài đã thích / đã lưu. */
    public void refreshProfile(String username, Callback cb) {
        runRefresh(cb, null, () -> {
            String u = DatabaseHelper.norm(username);
            boolean own = u.equals(me());
            List<UserModel> users = new ArrayList<>();
            DocumentSnapshot ud = await(fs.collection(C_USERS).document(u).get());
            UserModel profile = ud.exists() ? docToUser(ud) : null;
            if (profile != null) users.add(profile);

            QuerySnapshot ps = await(fs.collection(C_POSTS).whereEqualTo("authorUsername", u).limit(50).get());
            List<PostModel> posts = visiblePosts(ps.getDocuments());
            savePage(DatabaseHelper.feedUser(u), posts, true);

            List<String[]> pairs = new ArrayList<>();
            Set<String> related = new LinkedHashSet<>();
            for (DocumentSnapshot d : await(fs.collection(C_FOLLOWS).whereEqualTo("follower", u).get()).getDocuments()) {
                pairs.add(new String[]{u, str(d, "following")});
                related.add(str(d, "following"));
            }
            for (DocumentSnapshot d : await(fs.collection(C_FOLLOWS).whereEqualTo("following", u).get()).getDocuments()) {
                pairs.add(new String[]{str(d, "follower"), u});
                related.add(str(d, "follower"));
            }
            for (DocumentSnapshot d : getDocsByIds(fs.collection(C_USERS), related)) {
                users.add(docToUser(d));
            }
            db.saveUsers(users);
            db.saveFollows(u, pairs);

            if (own) {
                db.saveRelationList(DatabaseHelper.TABLE_LIKES, u, fetchRelationPosts(C_LIKES, u));
                db.saveSavedTracks(u, fetchSavedTracks(u));
                if (profile != null && profile.isLocked()) emit(EVENT_ACCOUNT_LOCKED);
            }
            return Status.OK;
        });
    }

    /** Kiểm tra định kỳ tài khoản đang đăng nhập có bị khóa không (tối đa 1 lần / phút). */
    public void checkMyAccount() {
        long now = System.currentTimeMillis();
        if (now - lastAccountCheck < ACCOUNT_CHECK_INTERVAL || !isOnline() || !isSignedIn()) return;
        lastAccountCheck = now;
        executor.execute(() -> {
            try {
                DocumentSnapshot ud = await(fs.collection(C_USERS).document(me()).get());
                if (!ud.exists()) return;
                UserModel user = docToUser(ud);
                List<UserModel> one = new ArrayList<>();
                one.add(user);
                db.saveUsers(one);
                if (user.isLocked()) emit(EVENT_ACCOUNT_LOCKED);
            } catch (Exception ignored) {
            }
        });
    }

    // ===================================================================================
    // THÔNG BÁO theo tài khoản: notifications/{id} có recipient + recipientUid của người nhận
    // ===================================================================================

    /**
     * Tải thông báo của tài khoản đang đăng nhập về SQLite (sau khi đã đẩy trạng thái đã đọc đang chờ).
     * @param force false = bỏ qua nếu vừa tải trong 30 giây gần đây (realtime vẫn cập nhật liên tục)
     */
    public void refreshNotifications(boolean force, Callback cb) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotificationFetch < NOTIFICATION_FETCH_INTERVAL) {
            deliver(cb, Status.OK, false);
            return;
        }
        lastNotificationFetch = now;
        runRefresh(cb, null, () -> {
            fetchNotificationsBlocking();
            return Status.OK;
        });
    }

    private void fetchNotificationsBlocking() throws Exception {
        String uid = FirebaseAuth.getInstance().getUid();
        String me = me();
        if (uid == null || me.isEmpty()) return;
        QuerySnapshot qs = await(fs.collection(C_NOTIFICATIONS).whereEqualTo("recipientUid", uid).limit(100).get());
        db.saveNotifications(me, notificationsOf(me, qs.getDocuments()));
    }

    /** Chỉ giữ thông báo gửi đúng cho {@code me} (phòng dữ liệu cũ gắn sai tài khoản). */
    private List<NotificationModel> notificationsOf(String me, List<DocumentSnapshot> docs) {
        List<NotificationModel> list = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            NotificationModel n = docToNotification(d);
            if (me.equals(DatabaseHelper.norm(n.getRecipient()))) list.add(n);
        }
        return list;
    }

    /**
     * Lần đăng nhập đầu của mỗi tài khoản: tạo thông báo chào mừng riêng (ID welcome_{username}, không trùng).
     * Chạy trên luồng nền (AuthManager gọi sau khi đăng nhập).
     */
    void ensureWelcomeNotificationBlocking(String username, String fullName) {
        String uid = FirebaseAuth.getInstance().getUid();
        String u = DatabaseHelper.norm(username);
        if (uid == null || u.isEmpty()) return;
        uidCache.put(u, uid);
        long now = System.currentTimeMillis();
        try {
            createNotificationIfMissing("welcome_" + u, notificationMap(u, uid, NotificationModel.TYPE_WELCOME, u,
                    fullName, null, null, null, null, now, false));
            fetchNotificationsBlocking();
            lastNotificationFetch = System.currentTimeMillis();
        } catch (Exception e) {
            Log.w(TAG, "Không tạo / tải được thông báo: " + e.getMessage());
        }
    }

    private void createNotificationIfMissing(String id, Map<String, Object> data) throws Exception {
        DocumentReference ref = fs.collection(C_NOTIFICATIONS).document(id);
        if (await(ref.get()).exists()) return; // đã có (giữ nguyên trạng thái đã đọc)
        await(ref.set(data));
    }

    /**
     * Tạo thông báo cho người nhận khi mình thích / bình luận / theo dõi (sau khi Firebase đã xác nhận thao tác).
     * Không gửi cho chính mình và cho hồ sơ minh họa (không có tài khoản đăng nhập nên không ai nhận).
     * Lỗi (mất mạng, Rules cũ) chỉ ghi log — không ảnh hưởng thao tác chính đã đồng bộ.
     */
    private void createNotificationBestEffort(String recipient, String type, String id, String postId,
                                              String commentId, String songTitle, String preview) {
        String r = DatabaseHelper.norm(recipient);
        String me = me();
        if (r.isEmpty() || r.equals(me)) return;
        try {
            String recipientUid = uidOf(r);
            if (recipientUid == null || recipientUid.isEmpty()) return;
            String text = preview != null && preview.length() > 140 ? preview.substring(0, 140) + "…" : preview;
            await(fs.collection(C_NOTIFICATIONS).document(id).set(notificationMap(r, recipientUid, type, me,
                    db.getUser(me).getFullName(), postId, commentId, songTitle, text, System.currentTimeMillis(), false)));
        } catch (Exception e) {
            Log.w(TAG, "Không tạo được thông báo " + id + ": " + e.getMessage());
        }
    }

    private void deleteNotificationBestEffort(String id) {
        try {
            await(fs.collection(C_NOTIFICATIONS).document(id).delete());
        } catch (Exception e) {
            Log.w(TAG, "Không gỡ được thông báo " + id + ": " + e.getMessage());
        }
    }

    /** uid Firebase Auth của một username (null nếu là hồ sơ minh họa chưa gắn tài khoản). */
    private String uidOf(String username) throws Exception {
        String cached = uidCache.get(username);
        if (cached != null) return cached;
        DocumentSnapshot ud = await(fs.collection(C_USERS).document(username).get());
        String uid = ud.exists() ? ud.getString("uid") : null;
        if (uid != null && !uid.isEmpty()) uidCache.put(username, uid);
        return uid;
    }

    static Map<String, Object> notificationMap(String recipient, String recipientUid, String type, String actor,
                                               String actorName, String postId, String commentId, String songTitle,
                                               String preview, long createdAt, boolean isSample) {
        Map<String, Object> m = new HashMap<>();
        m.put("recipient", recipient);
        m.put("recipientUid", recipientUid);
        m.put("type", type);
        m.put("actor", actor != null ? actor : "");
        m.put("actorName", actorName != null ? actorName : "");
        m.put("postId", postId != null ? postId : "");
        m.put("commentId", commentId != null ? commentId : "");
        m.put("songTitle", songTitle != null ? songTitle : "");
        m.put("preview", preview != null ? preview : "");
        m.put("createdAt", createdAt);
        m.put("read", false);
        m.put("isSample", isSample);
        return m;
    }

    static NotificationModel docToNotification(DocumentSnapshot d) {
        return new NotificationModel(d.getId(), str(d, "recipient"), str(d, "type"), str(d, "actor"),
                str(d, "actorName"), str(d, "postId"), str(d, "commentId"), str(d, "songTitle"),
                str(d, "preview"), num(d, "createdAt"), bool(d, "read"), bool(d, "isSample"));
    }

    private List<PostModel> fetchRelationPosts(String collection, String username) throws Exception {
        QuerySnapshot rs = await(fs.collection(collection).whereEqualTo("username", username).limit(100).get());
        Set<String> ids = new LinkedHashSet<>();
        for (DocumentSnapshot d : rs.getDocuments()) ids.add(str(d, "postId"));
        return visiblePosts(getDocsByIds(fs.collection(C_POSTS), ids));
    }

    /**
     * Danh sách nhạc đã lưu của {@code username} trên Firebase. Bản nhạc lấy từ chính document đã lưu;
     * document cũ (chỉ có postId) thì lấy thông tin bản nhạc từ bài viết nếu bài còn.
     */
    private List<PostModel> fetchSavedTracks(String username) throws Exception {
        List<DocumentSnapshot> docs = await(fs.collection(C_BOOKMARKS).whereEqualTo("username", username).limit(200).get())
                .getDocuments();
        Set<String> missing = new LinkedHashSet<>();
        for (DocumentSnapshot d : docs) {
            if (str(d, "songTitle").isEmpty()) missing.add(str(d, "postId"));
        }
        Map<String, PostModel> fromPosts = new HashMap<>();
        for (PostModel p : visiblePosts(getDocsByIds(fs.collection(C_POSTS), missing))) fromPosts.put(p.getId(), p);

        List<PostModel> tracks = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            String postId = str(d, "postId");
            PostModel t;
            if (!str(d, "songTitle").isEmpty()) {
                t = new PostModel(postId, "", "", "", "", str(d, "songTitle"), str(d, "artistName"),
                        str(d, "musicUrl"), str(d, "genre"), "", 0, 0, 0, false, true, db.audioResId(str(d, "audioRes")));
            } else if (fromPosts.containsKey(postId)) {
                t = fromPosts.get(postId);
            } else {
                continue; // document cũ mà bài viết đã bị xóa: không còn thông tin bản nhạc
            }
            t.setCreatedAt(num(d, "createdAt"));
            tracks.add(t);
        }
        return tracks;
    }

    /** Document lưu bản nhạc: bookmarks/{postId}_{username}. */
    Map<String, Object> savedTrackMap(String postId, String username, PostModel track) {
        Map<String, Object> m = relationMap(postId, username);
        if (track != null) {
            m.put("songTitle", track.getSongTitle());
            m.put("artistName", track.getArtistName());
            m.put("musicUrl", track.getMusicUrl());
            m.put("audioRes", db.audioResName(track.getRawAudioRes()));
            m.put("genre", track.getGenre());
        }
        return m;
    }

    /** Lưu 1 trang bài viết kèm trạng thái thích / lưu của người đang đăng nhập. */
    private void savePage(String feedKey, List<PostModel> posts, boolean replace) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        for (PostModel p : posts) ids.add(p.getId() + "_" + me());
        Set<String> liked = new HashSet<>();
        for (DocumentSnapshot d : getDocsByIds(fs.collection(C_LIKES), ids)) liked.add(str(d, "postId"));
        Set<String> saved = new HashSet<>();
        for (DocumentSnapshot d : getDocsByIds(fs.collection(C_BOOKMARKS), ids)) saved.add(str(d, "postId"));
        db.saveFeedPage(feedKey, posts, liked, saved, replace);
    }

    /** Đọc nhiều document theo ID (Firestore giới hạn 30 ID mỗi truy vấn "in"). */
    private List<DocumentSnapshot> getDocsByIds(CollectionReference col, Collection<String> ids) throws Exception {
        List<DocumentSnapshot> result = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isEmpty()) all.add(id);
        }
        for (int i = 0; i < all.size(); i += 30) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 30));
            result.addAll(await(col.whereIn(FieldPath.documentId(), new ArrayList<>(chunk)).get()).getDocuments());
        }
        return result;
    }

    // ===================================================================================
    // REALTIME: lắng nghe thay đổi trên Firestore khi app đang mở
    // ===================================================================================

    /**
     * Bắt đầu lắng nghe (gọi mỗi khi một màn hình mở ra; gọi lại nhiều lần không sao).
     * Firestore tự giữ kết nối và tự nối lại khi có mạng trở lại.
     */
    public synchronized void startRealtime() {
        if (!isSignedIn()) return;
        String me = me();
        if (me.equals(realtimeUser) && postsReg != null) return;
        stopRealtime();
        realtimeUser = me;
        postsSnapshotSeen = false;

        // Toàn bộ collection posts (không orderBy): bài Quản trị viên thêm thẳng trên Firebase Console
        // dù thiếu / sai kiểu trường createdAt vẫn được nhận và hiện ngay trên bảng tin
        postsReg = fs.collection(C_POSTS)
                .addSnapshotListener(executor, this::onPostsSnapshot);

        // Quan hệ theo dõi của mình: xóa / thêm lượt theo dõi trên Firebase thì nút Theo dõi đổi trạng thái ngay
        followingReg = fs.collection(C_FOLLOWS).whereEqualTo("follower", me)
                .addSnapshotListener(executor, (snap, e) -> onFollowsSnapshot(me, true, snap, e));
        followersReg = fs.collection(C_FOLLOWS).whereEqualTo("following", me)
                .addSnapshotListener(executor, (snap, e) -> onFollowsSnapshot(me, false, snap, e));

        // Lượt thả tim của mình: document bị xóa / thêm trên Firebase Console thì icon tim trên mọi màn hình đổi theo
        likesReg = fs.collection(C_LIKES).whereEqualTo("username", me)
                .addSnapshotListener(executor, (snap, e) -> onMyRelationsSnapshot(me, false, snap, e));
        // Bản nhạc đã lưu của mình: gỡ trên Console thì icon Bookmark và tab Danh sách nhạc đã lưu cập nhật ngay
        bookmarksReg = fs.collection(C_BOOKMARKS).whereEqualTo("username", me)
                .addSnapshotListener(executor, (snap, e) -> onMyRelationsSnapshot(me, true, snap, e));

        // Hồ sơ của mình: Admin khóa tài khoản / đổi vai trò, sửa tên trên Console
        meReg = fs.collection(C_USERS).document(me)
                .addSnapshotListener(executor, (doc, e) -> {
                    if (e != null || doc == null || !doc.exists() || !isSignedIn()) return;
                    UserModel user = docToUser(doc);
                    List<UserModel> one = new ArrayList<>();
                    one.add(user);
                    db.saveUsers(one);
                    emit(user.isLocked() ? EVENT_ACCOUNT_LOCKED : EVENT_DATA_CHANGED);
                });

        // Thông báo của RIÊNG tài khoản này (đọc trên máy khác cũng cập nhật ở đây)
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid != null) {
            notifReg = fs.collection(C_NOTIFICATIONS).whereEqualTo("recipientUid", myUid).limit(100)
                    .addSnapshotListener(executor, (snap, e) -> {
                        if (e != null) {
                            Log.w(TAG, "Realtime thông báo lỗi: " + e.getMessage());
                            return;
                        }
                        if (snap == null || !isSignedIn() || !me.equals(me())) return;
                        db.saveNotifications(me, notificationsOf(me, snap.getDocuments()));
                        emit(EVENT_DATA_CHANGED);
                    });
        }
    }

    private void onFollowsSnapshot(String me, boolean asFollower, QuerySnapshot snap, FirebaseFirestoreException e) {
        if (e != null) {
            Log.w(TAG, "Realtime theo dõi lỗi: " + e.getMessage());
            return;
        }
        if (snap == null || !isSignedIn() || !me.equals(me())) return;
        List<String> others = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            others.add(str(d, asFollower ? "following" : "follower"));
        }
        db.replaceFollowSide(me, asFollower, others);
        emit(EVENT_DATA_CHANGED);
    }

    /**
     * Realtime lượt thích / bản nhạc đã lưu của tài khoản đang đăng nhập: thay các bản ghi ĐÃ ĐỒNG BỘ trong SQLite
     * bằng đúng danh sách trên Firebase (thao tác đang chờ gửi được giữ nguyên).
     * Bỏ qua ảnh chụp từ bộ nhớ tạm của Firestore (chưa xác nhận với máy chủ, ví dụ lúc mất mạng trả về rỗng).
     */
    private void onMyRelationsSnapshot(String me, boolean bookmarks, QuerySnapshot snap, FirebaseFirestoreException e) {
        if (e != null) {
            Log.w(TAG, "Realtime " + (bookmarks ? "nhạc đã lưu" : "lượt thích") + " lỗi: " + e.getMessage());
            return;
        }
        if (snap == null || snap.getMetadata().isFromCache() || !isSignedIn() || !me.equals(me())) return;
        Set<String> ids = new LinkedHashSet<>();
        Map<String, PostModel> tracks = new HashMap<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            String postId = str(d, "postId");
            if (postId.isEmpty()) continue;
            ids.add(postId);
            if (bookmarks && !str(d, "songTitle").isEmpty()) {
                PostModel t = new PostModel(postId, "", "", "", "", str(d, "songTitle"), str(d, "artistName"),
                        str(d, "musicUrl"), str(d, "genre"), "", 0, 0, 0, false, true, db.audioResId(str(d, "audioRes")));
                t.setCreatedAt(num(d, "createdAt"));
                tracks.put(postId, t);
            }
        }
        if (db.replaceSyncedRelations(bookmarks ? DatabaseHelper.TABLE_BOOKMARKS : DatabaseHelper.TABLE_LIKES, me, ids, tracks)) {
            emit(EVENT_DATA_CHANGED);
        }
    }

    /**
     * Bài viết không còn khả dụng (bị xóa trên Firebase / bị Quản trị viên ẩn hoặc gỡ bỏ):
     * nếu bài hát của bài đó đang phát thì dừng ngay, đóng thông báo phát nhạc và thu hồi Mini Player.
     */
    private void onPostUnavailable(String postId) {
        if (postId == null) return;
        main.post(() -> MusicService.stopIfPlaying(app, postId));
    }

    /** Dừng lắng nghe (đăng xuất). */
    public synchronized void stopRealtime() {
        if (postsReg != null) postsReg.remove();
        if (meReg != null) meReg.remove();
        if (notifReg != null) notifReg.remove();
        if (followingReg != null) followingReg.remove();
        if (followersReg != null) followersReg.remove();
        if (likesReg != null) likesReg.remove();
        if (bookmarksReg != null) bookmarksReg.remove();
        likesReg = null;
        bookmarksReg = null;
        postsReg = null;
        meReg = null;
        notifReg = null;
        followingReg = null;
        followersReg = null;
        undatedPosts.clear();
        lastNotificationFetch = 0;
        realtimeUser = null;
        unwatchPost(watchedPostId);
    }

    private void onPostsSnapshot(QuerySnapshot snap, FirebaseFirestoreException e) {
        if (e != null) {
            Log.w(TAG, "Realtime bài viết lỗi: " + e.getMessage());
            return;
        }
        if (snap == null || !isSignedIn()) return;
        boolean initial = !postsSnapshotSeen;
        postsSnapshotSeen = true;
        boolean changed = false;

        if (initial) {
            // Lần đầu: chỉ nạp các bài mới nhất vào bảng tin (đúng phạm vi 50 bài đang lưu trong SQLite)
            List<PostModel> all = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                PostModel p = docToPost(d);
                trackUndated(d, p);
                all.add(p);
            }
            all.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            for (int i = 0; i < all.size() && i < DatabaseHelper.LIMIT_HOME_FEED; i++) {
                changed |= applyServerPost(all.get(i), true);
            }
            if (changed) emit(EVENT_DATA_CHANGED);
            return;
        }

        for (DocumentChange dc : snap.getDocumentChanges()) {
            DocumentSnapshot d = dc.getDocument();
            if (dc.getType() == DocumentChange.Type.REMOVED) {
                // Lắng nghe cả collection nên REMOVED nghĩa là bài đã bị xóa thật trên Firebase
                undatedPosts.remove(d.getId());
                if (SyncStatus.SYNCED.equals(db.getPostSyncStatus(d.getId()))) {
                    db.removePostLocal(d.getId());
                    changed = true;
                }
                onPostUnavailable(d.getId());
            } else {
                PostModel p = docToPost(d);
                trackUndated(d, p);
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    // Bài mới thêm (kể cả thêm thẳng trên Console) hiện ngay trên bảng tin
                    changed |= applyServerPost(p, true);
                } else if (db.getPostSyncStatus(p.getId()) != null) {
                    // Bài đang có trên máy vừa được sửa / đổi lượt tim, ẩn hoặc chuyển riêng tư
                    changed |= applyServerPost(p, false);
                }
            }
        }
        if (changed) emit(EVENT_DATA_CHANGED);
    }

    private void trackUndated(DocumentSnapshot d, PostModel p) {
        if (num(d, "createdAt") <= 0) {
            undatedPosts.put(p.getId(), p);
        } else {
            undatedPosts.remove(p.getId());
        }
    }

    /** Ghi bài từ Firebase vào SQLite; bài chuyển sang riêng tư của người khác thì gỡ khỏi máy. */
    private boolean applyServerPost(PostModel p, boolean addToHome) {
        if (isVisible(p)) {
            db.applyRealtimePost(p, addToHome);
        } else {
            db.removePostLocal(p.getId());
            onPostUnavailable(p.getId());
        }
        return true;
    }

    /** Màn Chi tiết bài viết đang mở: lắng nghe riêng bài đó và bình luận của nó. */
    public synchronized void watchPost(String postId) {
        if (postId == null || !isSignedIn()) return;
        if (postId.equals(watchedPostId) && watchedPostReg != null) return;
        unwatchPost(watchedPostId);
        watchedPostId = postId;
        DocumentReference ref = fs.collection(C_POSTS).document(postId);

        watchedPostReg = ref.addSnapshotListener(executor, (doc, e) -> {
            if (e != null || doc == null || !isSignedIn()) return;
            if (!doc.exists()) {
                // "Không tồn tại" lấy từ bộ nhớ tạm (đang mất mạng) chưa chắc bài đã bị xóa: bỏ qua
                if (doc.getMetadata().isFromCache()) return;
                // Bài mình đăng lúc offline chưa lên Firebase thì giữ nguyên
                if (SyncStatus.SYNCED.equals(db.getPostSyncStatus(postId))) {
                    db.removePostLocal(postId);
                    onPostUnavailable(postId);
                    emit(EVENT_DATA_CHANGED);
                }
                return;
            }
            if (applyServerPost(docToPost(doc), false)) emit(EVENT_DATA_CHANGED);
        });

        watchedCommentsReg = ref.collection(C_COMMENTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(DatabaseHelper.LIMIT_COMMENTS_PER_POST)
                .addSnapshotListener(executor, (snap, e) -> {
                    if (e != null || snap == null || !isSignedIn()) return;
                    // Mở lại bài lúc mất mạng: Firestore trả danh sách RỖNG từ bộ nhớ tạm (không phải dữ liệu thật
                    // của máy chủ). Không được dùng nó ghi đè các bình luận đã lưu đệm trong SQLite để xem offline.
                    if (snap.getMetadata().isFromCache()) return;
                    if (db.getPostSyncStatus(postId) == null) return;
                    List<CommentModel> comments = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) comments.add(docToComment(postId, d));
                    db.applyRealtimeComments(postId, comments);
                    emit(EVENT_DATA_CHANGED);
                });
    }

    public synchronized void unwatchPost(String postId) {
        if (postId == null || !postId.equals(watchedPostId)) return;
        if (watchedPostReg != null) watchedPostReg.remove();
        if (watchedCommentsReg != null) watchedCommentsReg.remove();
        watchedPostReg = null;
        watchedCommentsReg = null;
        watchedPostId = null;
    }

    // ===================================================================================
    // QUẢN TRỊ VIÊN (chỉ thực hiện khi có mạng)
    // ===================================================================================

    public void loadAdminData(AdminDataCallback cb) {
        if (!isOnline() || !isSignedIn()) {
            main.post(() -> cb.onDone(Status.OFFLINE, null, null, null));
            return;
        }
        executor.execute(() -> {
            try {
                try {
                    pushPendingBlocking();
                } catch (Exception e) {
                    Log.w(TAG, "Đẩy thay đổi chờ thất bại (bỏ qua để vẫn tải dữ liệu quản trị): " + e.getMessage());
                }
                List<PostModel> posts = new ArrayList<>();
                Map<String, PostModel> byId = new HashMap<>();
                for (DocumentSnapshot d : await(fs.collection(C_POSTS)
                        .orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get()).getDocuments()) {
                    PostModel p = docToPost(d);
                    posts.add(p);
                    byId.put(p.getId(), p);
                }
                for (PostModel p : undatedPosts.values()) {
                    if (!byId.containsKey(p.getId())) {
                        posts.add(0, p);
                        byId.put(p.getId(), p);
                    }
                }
                List<UserModel> users = new ArrayList<>();
                for (DocumentSnapshot d : await(fs.collection(C_USERS).limit(500).get()).getDocuments()) {
                    users.add(docToUser(d));
                }
                users.sort((a, b) -> a.getUsername().compareTo(b.getUsername()));
                db.saveUsers(users);
                // Đọc reports cần quyền Admin (admins/{uid}); lỗi ở đây không được làm mất cả bài hát lẫn người dùng
                List<ReportedPost> reports;
                try {
                    reports = loadReportsBlocking(byId);
                } catch (Exception e) {
                    Log.w(TAG, "Tải báo cáo vi phạm thất bại (thiếu quyền admins/{uid}?): " + e.getMessage());
                    reports = null;
                }
                List<ReportedPost> finalReports = reports;
                main.post(() -> cb.onDone(Status.OK, posts, users, finalReports));
            } catch (Exception e) {
                Log.w(TAG, "Tải dữ liệu quản trị thất bại: " + e.getMessage());
                main.post(() -> cb.onDone(Status.ERROR, null, null, null));
            }
        });
    }

    /**
     * Bài viết đang bị báo cáo và chưa được xử lý (gộp theo bài, nhiều lượt báo cáo nhất trước).
     * Báo cáo của bài đã bị xóa thì tự dọn.
     */
    private List<ReportedPost> loadReportsBlocking(Map<String, PostModel> knownPosts) throws Exception {
        return buildReportsBlocking(await(fs.collection(C_REPORTS).limit(500).get()).getDocuments(), knownPosts);
    }

    /**
     * Trang Quản trị: lắng nghe collection reports theo thời gian thực. Báo cáo được xử lý / xóa (trên Console
     * hoặc bởi Quản trị viên khác) thì danh sách và thẻ thống kê cập nhật ngay. Gọi remove() trên kết quả để dừng.
     */
    public ListenerRegistration watchReports(ReportsListener listener) {
        if (!isSignedIn()) return null;
        return fs.collection(C_REPORTS).limit(500).addSnapshotListener(executor, (snap, e) -> {
            if (e != null) {
                Log.w(TAG, "Realtime báo cáo vi phạm lỗi: " + e.getMessage());
                return;
            }
            if (snap == null || snap.getMetadata().isFromCache()) return;
            try {
                List<ReportedPost> reports = buildReportsBlocking(snap.getDocuments(), new HashMap<>());
                main.post(() -> listener.onReports(reports));
            } catch (Exception ex) {
                Log.w(TAG, "Không dựng được danh sách báo cáo: " + ex.getMessage());
            }
        });
    }

    private List<ReportedPost> buildReportsBlocking(List<DocumentSnapshot> docs, Map<String, PostModel> knownPosts) throws Exception {
        Map<String, List<DocumentSnapshot>> byPost = new java.util.LinkedHashMap<>();
        for (DocumentSnapshot d : docs) {
            String postId = str(d, "postId");
            if (postId.isEmpty()) continue;
            if (!byPost.containsKey(postId)) byPost.put(postId, new ArrayList<>());
            byPost.get(postId).add(d);
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String id : byPost.keySet()) if (!knownPosts.containsKey(id)) unknown.add(id);
        Map<String, PostModel> posts = new HashMap<>(knownPosts);
        for (DocumentSnapshot d : getDocsByIds(fs.collection(C_POSTS), unknown)) posts.put(d.getId(), docToPost(d));

        List<ReportedPost> result = new ArrayList<>();
        List<DocumentReference> orphan = new ArrayList<>();
        for (Map.Entry<String, List<DocumentSnapshot>> e : byPost.entrySet()) {
            PostModel post = posts.get(e.getKey());
            if (post == null) {
                for (DocumentSnapshot d : e.getValue()) orphan.add(d.getReference());
                continue;
            }
            Set<String> reasons = new LinkedHashSet<>();
            long last = 0;
            for (DocumentSnapshot d : e.getValue()) {
                if (!str(d, "reason").isEmpty()) reasons.add(str(d, "reason"));
                last = Math.max(last, num(d, "createdAt"));
            }
            result.add(new ReportedPost(post, e.getValue().size(), android.text.TextUtils.join(", ", reasons), last));
        }
        if (!orphan.isEmpty()) {
            try {
                commitDeletes(orphan);
            } catch (Exception ignored) {
            }
        }
        result.sort((a, b) -> a.reportCount != b.reportCount
                ? Integer.compare(b.reportCount, a.reportCount)
                : Long.compare(b.lastReportedAt, a.lastReportedAt));
        return result;
    }

    /** Thành viên báo cáo một bài viết vi phạm (mỗi người chỉ báo cáo một bài một lần). */
    public void reportPost(String postId, String reason, Callback cb) {
        if (!isOnline() || !isSignedIn()) {
            deliver(cb, Status.OFFLINE, false);
            return;
        }
        String me = me();
        executor.execute(() -> {
            try {
                Map<String, Object> m = new HashMap<>();
                m.put("postId", postId);
                m.put("reporter", me);
                m.put("reason", reason);
                m.put("createdAt", System.currentTimeMillis());
                await(fs.collection(C_REPORTS).document(postId + "_" + me).set(m));
                deliver(cb, Status.OK, false);
            } catch (Exception e) {
                Log.w(TAG, "Không gửi được báo cáo: " + e.getMessage());
                deliver(cb, Status.ERROR, false);
            }
        });
    }

    /** Ẩn / hiện lại bài viết vi phạm (bài ẩn không hiển thị với thành viên). Ẩn xong thì đóng các báo cáo của bài. */
    public void adminSetPostHidden(String postId, boolean hidden, Callback cb) {
        runAdmin(cb, () -> {
            await(fs.collection(C_POSTS).document(postId).update("isHidden", hidden));
            if (hidden) {
                deleteReportsOf(postId);
                db.removePostLocal(postId);
                onPostUnavailable(postId);
            }
        });
    }

    /** Bỏ qua báo cáo: bài không vi phạm, xóa các báo cáo của bài. */
    public void adminDismissReports(String postId, Callback cb) {
        runAdmin(cb, () -> deleteReportsOf(postId));
    }

    private void deleteReportsOf(String postId) throws Exception {
        List<DocumentReference> refs = new ArrayList<>();
        for (DocumentSnapshot d : await(fs.collection(C_REPORTS).whereEqualTo("postId", postId).get()).getDocuments()) {
            refs.add(d.getReference());
        }
        commitDeletes(refs);
    }

    /**
     * Quản trị viên xóa bài vi phạm cùng toàn bộ bình luận, lượt thích, báo cáo của bài;
     * bản nhạc vi phạm cũng được gỡ khỏi Danh sách nhạc đã lưu của mọi thành viên.
     */
    public void adminDeletePost(String postId, Callback cb) {
        runAdmin(cb, () -> {
            deletePostRemote(postId, true);
            deleteReportsOf(postId);
            db.removePostLocal(postId);
            onPostUnavailable(postId);
        });
    }

    /**
     * Xóa bài trên Firestore cùng bình luận, lượt thích (Admin hoặc chính tác giả).
     * @param removeSavedTracks true (Admin gỡ bài vi phạm) = gỡ luôn bản nhạc khỏi Danh sách nhạc đã lưu của mọi người;
     *                          false (tác giả tự xóa bài) = bản nhạc người khác đã lưu vẫn được giữ
     */
    private void deletePostRemote(String postId, boolean removeSavedTracks) throws Exception {
        DocumentReference postRef = fs.collection(C_POSTS).document(postId);
        List<DocumentReference> refs = new ArrayList<>();
        for (DocumentSnapshot d : await(postRef.collection(C_COMMENTS).get()).getDocuments()) refs.add(d.getReference());
        for (DocumentSnapshot d : await(fs.collection(C_LIKES).whereEqualTo("postId", postId).get()).getDocuments()) refs.add(d.getReference());
        if (removeSavedTracks) {
            for (DocumentSnapshot d : await(fs.collection(C_BOOKMARKS).whereEqualTo("postId", postId).get()).getDocuments()) refs.add(d.getReference());
        }
        // Xóa bài sau cùng để Security Rules còn đọc được tác giả của bài khi xóa bình luận / lượt thích
        commitDeletes(refs);
        await(postRef.delete());
    }

    public void adminSetLocked(String username, boolean locked, Callback cb) {
        runAdmin(cb, () -> await(fs.collection(C_USERS).document(DatabaseHelper.norm(username)).update("isLocked", locked)));
    }

    private interface AdminJob {
        void run() throws Exception;
    }

    private void runAdmin(Callback cb, AdminJob job) {
        if (!isOnline() || !isSignedIn()) {
            deliver(cb, Status.OFFLINE, false);
            return;
        }
        executor.execute(() -> {
            try {
                job.run();
                deliver(cb, Status.OK, false);
            } catch (Exception e) {
                Log.w(TAG, "Thao tác quản trị thất bại: " + e.getMessage());
                deliver(cb, Status.ERROR, false);
            }
        });
    }

    private void commitDeletes(List<DocumentReference> refs) throws Exception {
        for (int i = 0; i < refs.size(); i += 400) {
            WriteBatch batch = fs.batch();
            for (DocumentReference r : refs.subList(i, Math.min(refs.size(), i + 400))) batch.delete(r);
            await(batch.commit());
        }
    }

    // ===================================================================================
    // DỮ LIỆU MẪU (nạp lên Firestore 1 lần duy nhất)
    // ===================================================================================

    /** Nạp dữ liệu mẫu nếu Firestore chưa có (chạy trên luồng nền). */
    void ensureSeededBlocking() {
        try {
            if (await(fs.collection(C_META).document("seed").get()).exists()) return;
            await(seedBatch(System.currentTimeMillis()).commit());
        } catch (Exception e) {
            // Máy khác đã nạp cùng lúc hoặc lỗi mạng: lần đăng nhập sau sẽ thử lại
            Log.w(TAG, "Không nạp được dữ liệu mẫu: " + e.getMessage());
        }
    }

    private WriteBatch seedBatch(long now) {
        WriteBatch batch = fs.batch();
        for (UserModel u : SeedData.users()) {
            Map<String, Object> data = new HashMap<>();
            data.put("username", u.getUsername());
            data.put("fullName", u.getFullName());
            data.put("role", u.getRole());
            data.put("bio", u.getBio());
            data.put("avatarIndex", (long) u.getAvatarIndex());
            data.put("email", u.getEmail());
            data.put("isLocked", false);
            // merge: không ghi đè uid của tài khoản đã đăng nhập trước đó
            batch.set(fs.collection(C_USERS).document(u.getUsername()), data, SetOptions.merge());
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", 1L);
        meta.put("seededAt", now);
        batch.set(fs.collection(C_META).document("seed"), meta);
        return batch;
    }

    // ===================================================================================
    // Chuyển đổi Firestore Document <-> Model
    // ===================================================================================

    private boolean isVisible(PostModel p) {
        // Bài bị Quản trị viên ẩn (vi phạm) không hiển thị; bài riêng tư chỉ tác giả mới thấy
        if (p.isHiddenByAdmin()) return false;
        return !p.isPrivate() || me().equals(DatabaseHelper.norm(p.getAuthorUsername()));
    }

    private List<PostModel> visiblePosts(List<DocumentSnapshot> docs) {
        List<PostModel> list = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            PostModel p = docToPost(d);
            if (isVisible(p)) list.add(p);
        }
        return list;
    }

    /** Khớp danh mục: thể loại, hashtag, tên bài hoặc nghệ sĩ chứa một trong các từ khóa. */
    static boolean matchesAny(PostModel p, String[] keywords) {
        String haystack = (p.getGenre() + " " + p.getHashtags() + " " + p.getSongTitle() + " "
                + p.getArtistName() + " " + p.getCaption()).toLowerCase();
        for (String k : keywords) {
            if (k != null && !k.trim().isEmpty() && haystack.contains(k.trim().toLowerCase())) return true;
        }
        return false;
    }

    /** Đọc chuỗi; trường nhập tay trên Firebase Console sai kiểu (số, boolean...) vẫn đọc được. */
    static String str(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        return v != null ? String.valueOf(v) : "";
    }

    /** Đọc số; chấp nhận cả kiểu timestamp / chuỗi số khi dữ liệu được thêm tay trên Firebase Console. */
    static long num(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) v).toDate().getTime();
        if (v instanceof java.util.Date) return ((java.util.Date) v).getTime();
        if (v instanceof String) {
            try {
                return (long) Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** Đọc boolean; chấp nhận chuỗi "true" khi dữ liệu được thêm tay trên Firebase Console. */
    static boolean bool(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        if (v instanceof Boolean) return (Boolean) v;
        return v instanceof String && "true".equalsIgnoreCase(((String) v).trim());
    }

    Map<String, Object> postToMap(PostModel p) {
        Map<String, Object> m = new HashMap<>();
        m.put("authorName", p.getAuthorName());
        m.put("authorUsername", DatabaseHelper.norm(p.getAuthorUsername()));
        m.put("caption", p.getCaption());
        m.put("songTitle", p.getSongTitle());
        m.put("artistName", p.getArtistName());
        m.put("musicUrl", p.getMusicUrl());
        m.put("genre", p.getGenre());
        m.put("hashtags", p.getHashtags());
        m.put("likeCount", (long) p.getLikeCount());
        m.put("commentCount", (long) p.getCommentCount());
        m.put("shareCount", (long) p.getShareCount());
        m.put("audioRes", db.audioResName(p.getRawAudioRes()));
        m.put("isPrivate", p.isPrivate());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    /** Các trường tác giả được sửa (không đụng tới bộ đếm và ngày đăng). */
    Map<String, Object> editableFields(PostModel p) {
        Map<String, Object> m = new HashMap<>();
        m.put("caption", p.getCaption());
        m.put("songTitle", p.getSongTitle());
        m.put("artistName", p.getArtistName());
        m.put("musicUrl", p.getMusicUrl());
        m.put("genre", p.getGenre());
        m.put("hashtags", p.getHashtags());
        m.put("audioRes", db.audioResName(p.getRawAudioRes()));
        m.put("isPrivate", p.isPrivate());
        m.put("editedAt", System.currentTimeMillis());
        return m;
    }

    PostModel docToPost(DocumentSnapshot d) {
        PostModel p = new PostModel(d.getId(), str(d, "authorName"), str(d, "authorUsername"), "",
                str(d, "caption"), str(d, "songTitle"), str(d, "artistName"), str(d, "musicUrl"),
                str(d, "genre"), str(d, "hashtags"),
                (int) num(d, "likeCount"), (int) num(d, "commentCount"), (int) num(d, "shareCount"),
                false, false, db.audioResId(str(d, "audioRes")));
        p.setPrivate(bool(d, "isPrivate"));
        p.setHiddenByAdmin(bool(d, "isHidden"));
        long createdAt = num(d, "createdAt");
        if (createdAt <= 0) {
            // Bài thêm tay trên Console thiếu ngày đăng: giữ mốc đã lưu trên máy, chưa có thì coi là vừa đăng
            createdAt = db.getPostCreatedAt(d.getId());
            if (createdAt <= 0) createdAt = System.currentTimeMillis();
        }
        p.setCreatedAt(createdAt);
        return p;
    }

    static Map<String, Object> commentToMap(CommentModel c) {
        Map<String, Object> m = new HashMap<>();
        m.put("postId", c.getPostId());
        m.put("authorUsername", DatabaseHelper.norm(c.getAuthorUsername()));
        m.put("authorName", c.getAuthorName());
        m.put("content", c.getContent());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    static CommentModel docToComment(String postId, DocumentSnapshot d) {
        return new CommentModel(d.getId(), postId, str(d, "authorUsername"), str(d, "authorName"),
                str(d, "content"), num(d, "createdAt"), SyncStatus.SYNCED);
    }

    static UserModel docToUser(DocumentSnapshot d) {
        UserModel u = new UserModel(d.getId(), str(d, "fullName"), str(d, "role"), str(d, "bio"),
                (int) num(d, "avatarIndex"), 0, 0, 0, bool(d, "isLocked"));
        u.setEmail(str(d, "email"));
        return u;
    }

    static Map<String, Object> relationMap(String postId, String username) {
        Map<String, Object> m = new HashMap<>();
        m.put("postId", postId);
        m.put("username", username);
        m.put("createdAt", System.currentTimeMillis());
        return m;
    }

    static Map<String, Object> followMap(String follower, String following) {
        Map<String, Object> m = new HashMap<>();
        m.put("follower", follower);
        m.put("following", following);
        m.put("createdAt", System.currentTimeMillis());
        return m;
    }
}
