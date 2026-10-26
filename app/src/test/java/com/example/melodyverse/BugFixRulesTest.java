package com.example.melodyverse;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Kiểm thử các quy tắc thuần logic của đợt sửa lỗi: username từ tài khoản Google, dữ liệu nạp sẵn chỉ gồm
 * tài khoản demo, nội dung thông báo theo từng loại.
 */
public class BugFixRulesTest {

    @Test
    public void usernameFromGoogleEmail_isSanitized() {
        assertEquals("nguyen.van_a", AuthManager.usernameBase("Nguyen.Van_A@gmail.com", null));
        // Bỏ dấu tiếng Việt và ký tự không hợp lệ
        assertEquals("tranthiduc", AuthManager.usernameBase("trần+thị-đức@gmail.com", null));
        // Quá ngắn → thêm tiền tố để đủ 3 ký tự
        assertEquals("userab", AuthManager.usernameBase("ab@gmail.com", null));
        // Quá dài → cắt còn 20 ký tự
        assertEquals(20, AuthManager.usernameBase("abcdefghijklmnopqrstuvwxyz@gmail.com", null).length());
        // Không có email → dùng tên hiển thị
        assertEquals("phamminhthu", AuthManager.usernameBase(null, "Phạm Minh Thư"));
    }

    @Test
    public void passwordResetEmail_isMasked() {
        assertEquals("mi*****@gmail.com", AuthManager.maskEmail("minhthu@gmail.com"));
        // Tên email ngắn vẫn che ít nhất 3 ký tự
        assertEquals("a***@gmail.com", AuthManager.maskEmail("ab@gmail.com"));
        assertEquals("ab***@gmail.com", AuthManager.maskEmail("abc@gmail.com"));
    }

    @Test
    public void seedUsers_onlyContainDemoLoginAccounts() {
        // Không còn hồ sơ nghệ sĩ minh họa: chỉ nạp sẵn 2 tài khoản demo đăng nhập được
        Set<String> names = new HashSet<>();
        for (UserModel u : SeedData.users()) {
            names.add(u.getUsername());
            assertNotNull("Tài khoản nạp sẵn phải có email đăng nhập: " + u.getUsername(), SeedData.demoEmail(u.getUsername()));
        }
        assertEquals(2, names.size());
        assertTrue(names.contains(SeedData.DEMO_USER));
        assertTrue(names.contains(SeedData.DEMO_ADMIN));
    }

    @Test
    public void notificationSummary_matchesType() {
        NotificationModel like = new NotificationModel("x", "a", NotificationModel.TYPE_LIKE, "b", "Bé B",
                "p1", null, "Bài A", null, 0, false, false);
        assertTrue(like.getSummary().contains("Bé B") && like.getSummary().contains("Bài A"));
        NotificationModel comment = new NotificationModel("y", "a", NotificationModel.TYPE_COMMENT, "b", "Bé B",
                "p1", "c1", "Bài A", "Hay quá", 0, true, false);
        assertTrue(comment.getSummary().contains("Hay quá"));
        assertTrue(comment.isRead());
    }
}
