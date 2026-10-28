package com.example.melodyverse;

import java.util.ArrayList;
import java.util.List;

/**
 * Hai tài khoản demo của MelodyVerse (minhthu / admin). Hồ sơ được nạp lên Firestore MỘT lần duy nhất
 * (document meta/seed) khi có người đăng nhập lần đầu. Không còn nghệ sĩ / bài viết / bình luận mẫu:
 * mọi nội dung trên app đều do người dùng đã đăng ký tạo ra.
 */
public final class SeedData {

    public static final String DEMO_USER = "minhthu";
    public static final String DEMO_USER_EMAIL = "minhthu@melodyverse.vn";
    public static final String DEMO_USER_PASSWORD = "12345678";
    public static final String DEMO_ADMIN = "admin";
    public static final String DEMO_ADMIN_EMAIL = "admin@melodyverse.vn";
    public static final String DEMO_ADMIN_PASSWORD = "admin123";

    private SeedData() {}

    /** Email của tài khoản demo theo username (null nếu không phải tài khoản demo). */
    public static String demoEmail(String username) {
        if (DEMO_USER.equalsIgnoreCase(username)) return DEMO_USER_EMAIL;
        if (DEMO_ADMIN.equalsIgnoreCase(username)) return DEMO_ADMIN_EMAIL;
        return null;
    }

    /** Username của tài khoản demo theo email (null nếu không phải tài khoản demo). */
    public static String demoUsername(String email) {
        if (DEMO_USER_EMAIL.equalsIgnoreCase(email)) return DEMO_USER;
        if (DEMO_ADMIN_EMAIL.equalsIgnoreCase(email)) return DEMO_ADMIN;
        return null;
    }

    /** true nếu đúng email + mật khẩu của một tài khoản demo (được phép tự tạo trên Firebase Auth). */
    public static boolean isDemoCredential(String email, String password) {
        return (DEMO_USER_EMAIL.equalsIgnoreCase(email) && DEMO_USER_PASSWORD.equals(password))
                || (DEMO_ADMIN_EMAIL.equalsIgnoreCase(email) && DEMO_ADMIN_PASSWORD.equals(password));
    }

    public static List<UserModel> users() {
        List<UserModel> list = new ArrayList<>();
        UserModel minhthu = new UserModel(DEMO_USER, "Phạm Minh Thư", "Thành viên tích cực",
                "Đam mê âm nhạc và chia sẻ cảm xúc qua từng giai điệu\nThành viên MelodyVerse", 0, 0, 0, 0);
        minhthu.setEmail(DEMO_USER_EMAIL);
        list.add(minhthu);
        UserModel admin = new UserModel(DEMO_ADMIN, "Quản Trị Viên MelodyVerse", "Quản trị viên (Admin)",
                "Tài khoản điều hành & kiểm duyệt nội dung cộng đồng MelodyVerse", 1, 0, 0, 0);
        admin.setEmail(DEMO_ADMIN_EMAIL);
        list.add(admin);
        return list;
    }
}
