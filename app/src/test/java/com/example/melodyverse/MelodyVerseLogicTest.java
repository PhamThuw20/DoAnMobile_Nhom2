package com.example.melodyverse;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho logic nghiệp vụ MelodyVerse (Chuẩn môn học Bài 3 - 03.2 App Testing).
 * Bao gồm kiểm thử PostModel, UserModel phân quyền, và trạng thái khóa/mở khóa tài khoản.
 */
public class MelodyVerseLogicTest {

    @Test
    public void testPostModel_likeToggleAndCountIncrement() {
        PostModel post = new PostModel(
                "test_p1", "Minh Thư", "minhthu", "Vừa xong",
                "Bài hát hay", "Không Thể Say", "HIEUTHUHAI",
                "https://example.com/audio.mp3", "Rap / Hip-Hop", "#Chill",
                100, 20, 5, false, false, 0
        );

        assertFalse("Bài viết ban đầu chưa được thích", post.isLiked());
        assertEquals(100, post.getLikeCount());

        // Mô phỏng người dùng thả tim
        post.setLiked(true);
        post.setLikeCount(post.getLikeCount() + 1);

        assertTrue("Bài viết đã được thích", post.isLiked());
        assertEquals("Số lượt thích tăng lên 101", 101, post.getLikeCount());

        // Mô phỏng hủy thả tim
        post.setLiked(false);
        post.setLikeCount(post.getLikeCount() - 1);

        assertFalse("Bài viết đã hủy thích", post.isLiked());
        assertEquals("Số lượt thích trở về 100", 100, post.getLikeCount());
    }

    @Test
    public void testPostModel_bookmarkToggle() {
        PostModel post = new PostModel(
                "test_p2", "Quang Hùng", "quanghung_masterd", "1 giờ trước",
                "Remix mới", "Thủy Triều", "MasterD",
                "https://example.com/audio.mp3", "V-Pop", "#SayHi",
                500, 50, 20, false, false, 0
        );

        assertFalse("Ban đầu chưa lưu bài viết", post.isBookmarked());
        post.setBookmarked(true);
        assertTrue("Sau khi lưu, trạng thái bookmark phải là true", post.isBookmarked());
    }

    @Test
    public void testUserModel_adminRoleVerification() {
        UserModel normalUser = new UserModel("minhthu", "Phạm Minh Thư", "Thành viên", "Bio...", 0, 1, 3, 3);
        UserModel adminUser = new UserModel("admin", "Ban Quản Trị", "Quản trị viên (Admin)", "Bio Admin", 1, 99, 9999, 12);
        UserModel moderatorUser = new UserModel("mod_nguyen", "Nguyễn Mod", "Quản trị viên", "Bio Mod", 0, 10, 50, 20);

        assertFalse("Tài khoản thường không phải là admin", normalUser.isAdmin());
        assertTrue("Tài khoản @admin phải có quyền admin", adminUser.isAdmin());
        assertTrue("Tài khoản có chức danh Quản trị viên phải có quyền admin", moderatorUser.isAdmin());
    }

    @Test
    public void testUserModel_lockStatus() {
        UserModel user = new UserModel("test_user", "Người Dùng Mới", "Thành viên", "Bio", 0, 0, 0, 0);

        assertFalse("Tài khoản mới tạo mặc định không bị khóa", user.isLocked());

        // Quản trị viên khóa tài khoản
        user.setLocked(true);
        assertTrue("Tài khoản phải ở trạng thái bị khóa sau khi Admin khóa", user.isLocked());

        // Quản trị viên mở khóa tài khoản
        user.setLocked(false);
        assertFalse("Tài khoản phải hoạt động trở lại sau khi Admin mở khóa", user.isLocked());
    }

    @Test
    public void testGenreDefaultFallback() {
        PostModel post = new PostModel(
                "test_p3", "Ca sĩ", "casi", "2 giờ trước",
                "Caption", "Tên bài", "Nghệ sĩ",
                "https://example.com/audio.mp3", null, "#Tag",
                10, 2, 1, false, false, 0
        );

        assertNotNull("Thể loại bài hát không được phép null", post.getGenre());
        assertEquals("V-Pop", post.getGenre());
    }
}
