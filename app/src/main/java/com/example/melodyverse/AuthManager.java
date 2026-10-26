package com.example.melodyverse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Đăng nhập / Đăng ký bằng Firebase Authentication (mật khẩu KHÔNG còn lưu trong SQLite - sửa lỗi #24).
 * Hồ sơ người dùng nằm ở Firestore: users/{username}, liên kết với tài khoản Auth qua trường uid.
 */
public class AuthManager {

    public enum Status { OK, LOCKED, FAILED }

    /** Ô nhập liệu gây lỗi để màn hình hiển thị setError đúng chỗ. */
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PASSWORD = "password";

    public static class Result {
        public final Status status;
        public final String username;
        public final boolean isAdmin;
        public final String message;
        public final String field;
        /** Tài khoản mới tạo, chưa qua bước gợi ý nghệ sĩ để theo dõi. */
        public final boolean needsOnboarding;

        Result(Status status, String username, boolean isAdmin, String message, String field) {
            this(status, username, isAdmin, message, field, false);
        }

        Result(Status status, String username, boolean isAdmin, String message, String field, boolean needsOnboarding) {
            this.status = status;
            this.username = username;
            this.isAdmin = isAdmin;
            this.message = message;
            this.field = field;
            this.needsOnboarding = needsOnboarding;
        }

        static Result fail(String message, String field) {
            return new Result(Status.FAILED, null, false, message, field);
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    private static final String MSG_WRONG = "Tài khoản hoặc mật khẩu không chính xác!";
    private static final String MSG_OFFLINE = "Cần kết nối Internet để đăng nhập / đăng ký. Vui lòng kiểm tra mạng!";
    private static final String MSG_NETWORK = "Không kết nối được máy chủ Firebase. Vui lòng thử lại!";
    private static final String MSG_GOOGLE_ACCOUNT = "Tài khoản này được tạo bằng Google. Vui lòng chọn \"Tiếp tục với Google\" để đăng nhập!";
    private static final String MSG_OTHER_METHOD = "Email của tài khoản Google này đã được đăng ký bằng tên đăng nhập + mật khẩu. "
            + "Vui lòng đăng nhập bằng mật khẩu để tránh tạo trùng hồ sơ!";

    /** Phương thức tạo tài khoản, lưu trong trường provider của hồ sơ. */
    static final String PROVIDER_PASSWORD = "password";
    static final String PROVIDER_GOOGLE = "google";

    private final Context app;
    private final SyncManager sync;
    private final FirebaseFirestore fs;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AuthManager(Context context) {
        app = context.getApplicationContext();
        sync = SyncManager.get(app);
        fs = sync.firestore();
    }

    private void deliver(Callback cb, Result r) {
        main.post(() -> cb.onResult(r));
    }

    /** Đăng nhập bằng username hoặc email. */
    public void signIn(String identifier, String password, Callback cb) {
        if (!sync.isOnline()) {
            deliver(cb, Result.fail(MSG_OFFLINE, null));
            return;
        }
        sync.runInBackground(() -> {
            try {
                deliver(cb, signInBlocking(identifier.trim().toLowerCase(), password));
            } catch (Exception e) {
                auth.signOut();
                deliver(cb, Result.fail(MSG_NETWORK, null));
            }
        });
    }

    private Result signInBlocking(String id, String password) throws Exception {
        String email;
        String username = null;
        String provider = null;
        if (id.contains("@")) {
            email = id;
        } else {
            username = DatabaseHelper.norm(id);
            DocumentSnapshot ud = sync.await(fs.collection(SyncManager.C_USERS).document(username).get());
            email = ud.exists() ? ud.getString("email") : SeedData.demoEmail(username);
            provider = ud.exists() ? ud.getString("provider") : null;
            // Hồ sơ nghệ sĩ mẫu không có email / mật khẩu nên không đăng nhập được
            if (email == null || email.isEmpty()) return Result.fail(MSG_WRONG, FIELD_PASSWORD);
        }

        try {
            sync.await(auth.signInWithEmailAndPassword(email, password));
        } catch (ExecutionException e) {
            if (!isCredentialError(e)) throw e;
            if (provider == null) {
                QuerySnapshot q = sync.await(fs.collection(SyncManager.C_USERS).whereEqualTo("email", email).limit(1).get());
                if (!q.isEmpty()) provider = q.getDocuments().get(0).getString("provider");
            }
            if (PROVIDER_GOOGLE.equals(provider)) return Result.fail(MSG_GOOGLE_ACCOUNT, FIELD_PASSWORD);
            // Lần đầu dùng tài khoản demo trên project Firebase mới: tự tạo tài khoản Auth
            if (!SeedData.isDemoCredential(email, password)) return Result.fail(MSG_WRONG, FIELD_PASSWORD);
            try {
                sync.await(auth.createUserWithEmailAndPassword(email, password));
            } catch (ExecutionException e2) {
                return Result.fail(MSG_WRONG, FIELD_PASSWORD);
            }
        }
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) return Result.fail(MSG_WRONG, FIELD_PASSWORD);

        // Firestore còn trống thì nạp dữ liệu mẫu (chỉ 1 lần cho cả nhóm)
        sync.ensureSeededBlocking();

        if (username == null) {
            QuerySnapshot q = sync.await(fs.collection(SyncManager.C_USERS).whereEqualTo("email", email).limit(1).get());
            username = !q.isEmpty() ? q.getDocuments().get(0).getId() : SeedData.demoUsername(email);
        }
        if (username == null) {
            auth.signOut();
            return Result.fail("Không tìm thấy hồ sơ của tài khoản này!", FIELD_USERNAME);
        }

        DocumentReference userRef = fs.collection(SyncManager.C_USERS).document(username);
        DocumentSnapshot ud = sync.await(userRef.get());
        if (!ud.exists()) {
            auth.signOut();
            return Result.fail("Không tìm thấy hồ sơ của tài khoản này!", FIELD_USERNAME);
        }
        String docUid = ud.getString("uid");
        if (docUid == null || docUid.isEmpty()) {
            // Hồ sơ mẫu chưa gắn tài khoản Auth: nhận quyền sở hữu nếu đúng email
            if (!email.equalsIgnoreCase(ud.getString("email"))) {
                auth.signOut();
                return Result.fail(MSG_WRONG, FIELD_PASSWORD);
            }
            sync.await(userRef.update("uid", fu.getUid()));
        } else if (!docUid.equals(fu.getUid())) {
            auth.signOut();
            return Result.fail(MSG_WRONG, FIELD_PASSWORD);
        }
        return completeLogin(fu, ud);
    }

    /**
     * Bước chung sau khi Firebase Authentication đã xác thực và hồ sơ đã khớp uid:
     * kiểm tra khóa, quyền Admin, lưu phiên + hồ sơ của mình vào SQLite (mở app lúc offline vẫn vào được).
     */
    private Result completeLogin(FirebaseUser fu, DocumentSnapshot ud) {
        String username = ud.getId();
        UserModel user = SyncManager.docToUser(ud);
        if (user.isLocked()) {
            auth.signOut();
            return new Result(Status.LOCKED, username, false, null, null);
        }
        if (user.isAdmin()) {
            ensureAdminMarker(fu.getUid(), username);
        }

        // Dữ liệu của tài khoản trước (nếu còn) không được lẫn sang tài khoản này
        DatabaseHelper.getInstance(app).clearAllCache();
        List<UserModel> one = new ArrayList<>();
        one.add(user);
        DatabaseHelper.getInstance(app).saveUsers(one);
        SessionManager.login(app, username);
        // Thông báo chào mừng riêng của tài khoản (ID cố định nên không bao giờ tạo trùng)
        sync.ensureWelcomeNotificationBlocking(username, user.getFullName());
        boolean needsOnboarding = Boolean.TRUE.equals(ud.getBoolean("needsOnboarding"));
        return new Result(Status.OK, username, user.isAdmin(), null, null, needsOnboarding);
    }

    // ===================================================================================
    // Quên mật khẩu (Firebase Authentication gửi email đặt lại mật khẩu từ máy chủ Google)
    // ===================================================================================

    /** Gửi link đặt lại mật khẩu tới email của tài khoản (nhập username hoặc email). */
    public void sendPasswordReset(String identifier, Callback cb) {
        if (!sync.isOnline()) {
            deliver(cb, Result.fail(MSG_OFFLINE, null));
            return;
        }
        sync.runInBackground(() -> {
            try {
                deliver(cb, passwordResetBlocking(identifier.trim().toLowerCase()));
            } catch (Exception e) {
                deliver(cb, Result.fail(sync.isOnline() ? MSG_NETWORK : MSG_OFFLINE, null));
            }
        });
    }

    private Result passwordResetBlocking(String id) throws Exception {
        boolean byUsername = !id.contains("@");
        String email;
        String provider = null;
        boolean profileFound = true;
        if (byUsername) {
            DocumentSnapshot ud = sync.await(fs.collection(SyncManager.C_USERS).document(DatabaseHelper.norm(id)).get());
            if (!ud.exists()) return Result.fail("Không tìm thấy tài khoản @" + id + "!", FIELD_USERNAME);
            email = ud.getString("email");
            provider = ud.getString("provider");
            if (email == null || email.isEmpty()) {
                return Result.fail("Tài khoản này chưa có email nên không thể đặt lại mật khẩu!", FIELD_USERNAME);
            }
        } else {
            email = id;
            QuerySnapshot q = sync.await(fs.collection(SyncManager.C_USERS).whereEqualTo("email", email).limit(1).get());
            profileFound = !q.isEmpty();
            if (profileFound) provider = q.getDocuments().get(0).getString("provider");
        }

        if (SeedData.demoUsername(email) != null) {
            return Result.fail("Tài khoản demo dùng email mẫu nên không nhận được thư. "
                    + "Hãy thử với tài khoản đăng ký bằng email thật!", FIELD_USERNAME);
        }
        // Firebase (chống dò email) vẫn báo "thành công" với email lạ mà không gửi thư → tự báo rõ cho người dùng
        if (!profileFound) {
            return Result.fail("Chưa có tài khoản nào được liên kết với email này!", FIELD_USERNAME);
        }
        if (PROVIDER_GOOGLE.equals(provider)) {
            return Result.fail("Tài khoản này được tạo bằng Google nên không có mật khẩu. "
                    + "Vui lòng chọn \"Tiếp tục với Google\" để đăng nhập!", FIELD_USERNAME);
        }

        auth.setLanguageCode("vi"); // email đặt lại mật khẩu bằng tiếng Việt
        try {
            sync.await(auth.sendPasswordResetEmail(email));
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof FirebaseAuthInvalidUserException) {
                return Result.fail("Chưa có tài khoản nào được liên kết với email này!", FIELD_USERNAME);
            }
            if (c instanceof FirebaseAuthInvalidCredentialsException) {
                return Result.fail("Email không hợp lệ!", FIELD_USERNAME);
            }
            if (c instanceof FirebaseTooManyRequestsException) {
                return Result.fail("Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau ít phút!", null);
            }
            throw e;
        }
        // Nhập bằng username thì chỉ hiện email đã che bớt, không lộ email của người khác
        String shown = byUsername ? maskEmail(email) : email;
        return new Result(Status.OK, null, false,
                "Đã gửi link đặt lại mật khẩu tới " + shown + ".\n\n"
                        + "Hãy mở email (kiểm tra cả mục Spam), bấm vào link để đặt mật khẩu mới rồi đăng nhập lại.", null);
    }

    /** minhthu@gmail.com → mi*****@gmail.com */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String name = email.substring(0, at);
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        StringBuilder sb = new StringBuilder(visible);
        for (int i = visible.length(); i < Math.max(name.length(), visible.length() + 3); i++) sb.append('*');
        return sb.append(email.substring(at)).toString();
    }

    // ===================================================================================
    // Đăng nhập / Đăng ký bằng Google (Google ID token → GoogleAuthProvider → Firebase Auth)
    // ===================================================================================

    /** Đăng nhập bằng Google; tài khoản Google chưa có hồ sơ sẽ được tạo hồ sơ mới (không trùng). */
    public void signInWithGoogle(String idToken, Callback cb) {
        if (!sync.isOnline()) {
            deliver(cb, Result.fail(MSG_OFFLINE, null));
            return;
        }
        sync.runInBackground(() -> {
            try {
                deliver(cb, googleBlocking(idToken));
            } catch (Exception e) {
                auth.signOut();
                deliver(cb, Result.fail(sync.isOnline() ? MSG_NETWORK : MSG_OFFLINE, null));
            }
        });
    }

    private Result googleBlocking(String idToken) throws Exception {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        AuthResult authResult;
        try {
            authResult = sync.await(auth.signInWithCredential(credential));
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof FirebaseAuthUserCollisionException) {
                return Result.fail(MSG_OTHER_METHOD, null);
            }
            if (c instanceof FirebaseAuthInvalidUserException) {
                return Result.fail("Tài khoản Google này đã bị vô hiệu hóa trên hệ thống!", null);
            }
            if (c instanceof FirebaseAuthInvalidCredentialsException) {
                return Result.fail("Xác thực Google thất bại. Vui lòng kiểm tra cấu hình Google Sign-In (SHA-1, Web client ID) và thử lại!", null);
            }
            throw e;
        }
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) return Result.fail(MSG_NETWORK, null);
        boolean newAuthUser = authResult.getAdditionalUserInfo() != null && authResult.getAdditionalUserInfo().isNewUser();
        String email = fu.getEmail() != null ? fu.getEmail().trim().toLowerCase() : "";

        sync.ensureSeededBlocking();

        // 1. Hồ sơ đã gắn với tài khoản Firebase này (đăng nhập lại, hoặc Google được liên kết vào tài khoản cũ)
        QuerySnapshot byUid = sync.await(fs.collection(SyncManager.C_USERS).whereEqualTo("uid", fu.getUid()).limit(1).get());
        if (!byUid.isEmpty()) {
            return completeLogin(fu, byUid.getDocuments().get(0));
        }

        // 2. Đã có hồ sơ cùng email
        if (!email.isEmpty()) {
            QuerySnapshot byEmail = sync.await(fs.collection(SyncManager.C_USERS).whereEqualTo("email", email).limit(1).get());
            if (!byEmail.isEmpty()) {
                DocumentSnapshot existing = byEmail.getDocuments().get(0);
                String docUid = existing.getString("uid");
                if (docUid == null || docUid.isEmpty()) {
                    // Hồ sơ chưa gắn tài khoản Auth, đúng email đã được Google xác minh: nhận quyền sở hữu
                    sync.await(existing.getReference().update("uid", fu.getUid()));
                    return completeLogin(fu, sync.await(existing.getReference().get()));
                }
                // Hồ sơ thuộc tài khoản Firebase khác (đăng ký bằng mật khẩu): không tạo hồ sơ trùng
                if (newAuthUser) {
                    try {
                        sync.await(fu.delete()); // không để lại tài khoản Auth rác
                    } catch (Exception ignored) {
                    }
                }
                auth.signOut();
                return Result.fail(MSG_OTHER_METHOD, null);
            }
        }

        // 3. Người dùng mới: tạo hồ sơ với username chưa ai dùng
        DocumentSnapshot created = createGoogleProfile(fu, email);
        if (created == null) {
            auth.signOut();
            return Result.fail("Không tạo được hồ sơ cho tài khoản Google này. Vui lòng thử lại!", null);
        }
        return completeLogin(fu, created);
    }

    /** Tạo users/{username} cho tài khoản Google (transaction: không bao giờ ghi đè hồ sơ đã có). */
    private DocumentSnapshot createGoogleProfile(FirebaseUser fu, String email) throws Exception {
        String base = usernameBase(email, fu.getDisplayName());
        String fullName = fu.getDisplayName() != null && !fu.getDisplayName().trim().isEmpty()
                ? fu.getDisplayName().trim() : base;
        java.util.Random random = new java.util.Random();
        for (int attempt = 0; attempt < 15; attempt++) {
            String candidate = attempt == 0 ? base : base + (attempt < 5 ? attempt : 100 + random.nextInt(9900));
            DocumentReference ref = fs.collection(SyncManager.C_USERS).document(candidate);
            Map<String, Object> data = newProfileMap(candidate, fullName, email,
                    Math.abs(fu.getUid().hashCode() % 5), fu.getUid(), PROVIDER_GOOGLE);
            Boolean ok = sync.await(fs.runTransaction(tx -> {
                if (tx.get(ref).exists()) return false;
                tx.set(ref, data);
                return true;
            }));
            if (Boolean.TRUE.equals(ok)) return sync.await(ref.get());
        }
        return null;
    }

    /** Username gợi ý từ email Google: chỉ gồm chữ thường không dấu, số, dấu chấm, gạch dưới (3-20 ký tự). */
    static String usernameBase(String email, String displayName) {
        String raw = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : displayName;
        if (raw == null) raw = "";
        String plain = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase();
        String clean = plain.replaceAll("[^a-z0-9._]", "");
        if (clean.length() > 20) clean = clean.substring(0, 20);
        if (clean.length() < 3) clean = "user" + clean;
        return clean;
    }

    /** Hồ sơ mặc định của thành viên mới (không kèm quan hệ theo dõi nào). */
    private static Map<String, Object> newProfileMap(String username, String fullName, String email, int avatarIndex,
                                                     String uid, String provider) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("fullName", fullName);
        data.put("role", "Thành viên mới");
        data.put("bio", "Xin chào! Mình vừa tham gia MelodyVerse. Cùng chia sẻ âm nhạc nhé!");
        data.put("avatarIndex", (long) avatarIndex);
        data.put("email", email);
        data.put("isLocked", false);
        data.put("uid", uid);
        data.put("provider", provider);
        // Lần đăng nhập đầu tiên sẽ hỏi người dùng có muốn theo dõi nghệ sĩ gợi ý không
        data.put("needsOnboarding", true);
        data.put("createdAt", System.currentTimeMillis());
        return data;
    }

    /** Ghi uid vào collection admins để Security Rules cấp quyền quản trị. */
    private void ensureAdminMarker(String uid, String username) {
        try {
            DocumentReference ref = fs.collection(SyncManager.C_ADMINS).document(uid);
            if (!sync.await(ref.get()).exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("username", username);
                sync.await(ref.set(data));
            }
        } catch (Exception ignored) {
            // Chỉ tài khoản admin gốc hoặc người được Admin cấp quyền mới ghi được
        }
    }

    private static boolean isCredentialError(ExecutionException e) {
        Throwable c = e.getCause();
        return c instanceof FirebaseAuthInvalidCredentialsException || c instanceof FirebaseAuthInvalidUserException;
    }

    /** Đăng ký tài khoản mới (Firebase Auth + hồ sơ users/{username}). */
    public void register(String username, String fullName, String email, String password, int avatarIndex, Callback cb) {
        if (!sync.isOnline()) {
            deliver(cb, Result.fail(MSG_OFFLINE, null));
            return;
        }
        String user = DatabaseHelper.norm(username);
        String mail = email.trim().toLowerCase();
        sync.runInBackground(() -> {
            try {
                DocumentReference userRef = fs.collection(SyncManager.C_USERS).document(user);
                if (sync.await(userRef.get()).exists()) {
                    deliver(cb, Result.fail("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!", FIELD_USERNAME));
                    return;
                }
                try {
                    sync.await(auth.createUserWithEmailAndPassword(mail, password));
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof FirebaseAuthUserCollisionException) {
                        deliver(cb, Result.fail("Email này đã được sử dụng cho tài khoản khác!", FIELD_EMAIL));
                    } else if (c instanceof FirebaseAuthWeakPasswordException) {
                        deliver(cb, Result.fail("Mật khẩu quá yếu, vui lòng chọn mật khẩu khác!", FIELD_PASSWORD));
                    } else if (c instanceof FirebaseAuthInvalidCredentialsException) {
                        deliver(cb, Result.fail("Email không đúng định dạng!", FIELD_EMAIL));
                    } else {
                        deliver(cb, Result.fail(MSG_NETWORK, null));
                    }
                    return;
                }
                FirebaseUser fu = auth.getCurrentUser();
                if (fu == null) {
                    deliver(cb, Result.fail(MSG_NETWORK, null));
                    return;
                }

                // Chỉ tạo hồ sơ; KHÔNG tự tạo quan hệ theo dõi (người dùng tự chọn ở bước gợi ý nghệ sĩ)
                Map<String, Object> data = newProfileMap(user, fullName, mail, avatarIndex, fu.getUid(), PROVIDER_PASSWORD);
                try {
                    sync.await(userRef.set(data));
                } catch (Exception e) {
                    // Tên đăng nhập vừa bị người khác lấy mất: hủy tài khoản Auth vừa tạo
                    try {
                        sync.await(fu.delete());
                    } catch (Exception ignored) {
                    }
                    auth.signOut();
                    deliver(cb, Result.fail("Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!", FIELD_USERNAME));
                    return;
                }
                auth.signOut();
                deliver(cb, new Result(Status.OK, user, false, null, null));
            } catch (Exception e) {
                auth.signOut();
                deliver(cb, Result.fail(MSG_NETWORK, null));
            }
        });
    }

    /** Đã qua bước gợi ý nghệ sĩ (theo dõi hoặc bỏ qua): không hỏi lại ở các lần đăng nhập sau. */
    public static void markOnboardingDone(Context context, String username) {
        String user = DatabaseHelper.norm(username);
        context.getApplicationContext().getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
                .edit().putBoolean("done_" + user, true).apply();
        SyncManager sync = SyncManager.get(context);
        if (!sync.isOnline()) return;
        sync.runInBackground(() -> {
            try {
                sync.await(sync.firestore().collection(SyncManager.C_USERS).document(user).update("needsOnboarding", false));
            } catch (Exception ignored) {
                // Rules cũ chưa cho sửa trường này: cờ trên máy vẫn ngăn hỏi lại
            }
        });
    }

    /** true nếu tài khoản cần hiện bước gợi ý nghệ sĩ trên máy này. */
    public static boolean shouldShowOnboarding(Context context, String username, boolean serverFlag) {
        return serverFlag && !context.getApplicationContext().getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
                .getBoolean("done_" + DatabaseHelper.norm(username), false);
    }

    private static final String PREF_ONBOARDING = "melodyverse_onboarding";

    /** Đăng xuất: thoát Firebase Auth và xóa toàn bộ dữ liệu riêng của tài khoản trên máy. */
    public static void signOut(Context context) {
        // Dừng hẳn nhạc của tài khoản cũ (không chỉ tạm dừng) để người đăng nhập sau không thấy Mini Player cũ
        MusicService.stopAndReset(context);
        SyncManager.get(context).stopRealtime();
        FirebaseAuth.getInstance().signOut();
        SessionManager.logout(context);
        DatabaseHelper.getInstance(context).clearAllCache();
    }

    /** Phiên đăng nhập hợp lệ: có tài khoản Firebase và hồ sơ trong máy không bị khóa. */
    public static boolean hasValidSession(Context context) {
        String user = SessionManager.getCurrentUsername(context);
        if (user == null || FirebaseAuth.getInstance().getCurrentUser() == null) return false;
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        return db.checkUserExists(user) && !db.isUserLocked(user);
    }
}
