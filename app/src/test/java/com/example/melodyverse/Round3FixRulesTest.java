package com.example.melodyverse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Kiểm thử các quy tắc của đợt sửa lỗi: trình phát, liên kết âm thanh, tìm kiếm & bảng xếp hạng Khám phá. */
public class Round3FixRulesTest {

    private static PostModel post(String id, String title, String artist, int likes, long createdAt) {
        PostModel p = new PostModel(id, "Tác giả", "tacgia", "", "", title, artist,
                "https://example.com/a.mp3", "V-Pop", "", likes, 0, 0, false, false, 0);
        p.setCreatedAt(createdAt);
        return p;
    }

    // ---------- Lỗi 2: Nút "Bài trước" đối xứng với nút "Bài tiếp theo" ----------

    @Test
    public void previous_restartsOnlyWhenTrackPlayedAWhile() {
        assertTrue(MusicService.shouldRestartOnPrevious(MusicService.PREVIOUS_RESTART_THRESHOLD_MS + 1));
        assertTrue(MusicService.shouldRestartOnPrevious(90_000));
        assertFalse(MusicService.shouldRestartOnPrevious(0));
        assertFalse(MusicService.shouldRestartOnPrevious(MusicService.PREVIOUS_RESTART_THRESHOLD_MS));
    }

    @Test
    public void previous_goesToTrackBeforeInList() {
        assertEquals(1, MusicService.previousIndex(2, 5, true));
        assertEquals(0, MusicService.previousIndex(1, 5, false));
        // Bài đầu của Bảng tin: quay vòng về bài cuối (đối xứng với nút Tiếp theo quay vòng về bài đầu)
        assertEquals(4, MusicService.previousIndex(0, 5, true));
        // Bài đầu của Danh sách nhạc đã lưu: không có bài trước (phát lại từ đầu)
        assertEquals(-1, MusicService.previousIndex(0, 5, false));
        assertEquals(-1, MusicService.previousIndex(-1, 5, true));
        assertEquals(-1, MusicService.previousIndex(0, 0, true));
    }

    // ---------- Lỗi 3: Ràng buộc định dạng liên kết âm thanh ----------

    @Test
    public void audioLink_rejectsArbitraryOrMalformedLinks() {
        assertFalse(MusicService.isSupportedAudioLink("acb.com"));
        assertFalse(MusicService.isSupportedAudioLink("http://acb.com"));
        assertFalse(MusicService.isSupportedAudioLink("https://acb.com/trang-chu"));
        assertFalse(MusicService.isSupportedAudioLink("bai hat hay"));
        assertFalse(MusicService.isSupportedAudioLink("ftp://host.com/song.mp3"));
        assertFalse(MusicService.isSupportedAudioLink("https://localhost/song.mp3"));
        assertFalse(MusicService.isSupportedAudioLink("https://example.com/audio/song.mp3")); // link mẫu
        assertFalse(MusicService.isSupportedAudioLink("https://audius.co/"));
        assertFalse(MusicService.isSupportedAudioLink("https://audius.co/chi-co-nghe-si"));
        assertFalse(MusicService.isSupportedAudioLink(""));
        assertFalse(MusicService.isSupportedAudioLink(null));
    }

    @Test
    public void audioLink_acceptsAudiusAndDirectAudioFiles() {
        assertTrue(MusicService.isSupportedAudioLink("https://audius.co/nghesi/ten-bai-hat-123"));
        assertTrue(MusicService.isSupportedAudioLink("audius.co/nghesi/ten-bai-hat"));
        assertTrue(MusicService.isSupportedAudioLink("https://api.audius.co/v1/tracks/D7KyD/stream?app_name=MelodyVerse"));
        assertTrue(MusicService.isSupportedAudioLink("https://cdn.host.vn/nhac/bai-hat.mp3"));
        assertTrue(MusicService.isSupportedAudioLink(" https://cdn.host.vn/nhac/bai-hat.M4A?token=abc "));
        assertTrue(MusicService.isSupportedAudioLink("http://files.host.com/a/b/track.wav"));
    }

    // ---------- Lỗi 4: Số kết quả tìm kiếm khớp đúng số hàng hiển thị ----------

    @Test
    public void search_countsEachMatchingSongOnce() {
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("a1", "Từng Quen", "Wren Evans", 5, 3));
        posts.add(post("a2", "TỪNG QUEN", "wren evans", 50, 2)); // cùng bài hát -> chỉ 1 hàng
        posts.add(post("b", "Quen Lâu Rồi", "Ca sĩ B", 0, 1));
        posts.add(post("c", "Bình Yên", "Vũ.", 100, 0));
        PostModel secret = post("d", "Quên Đi", "Z", 999, 4);
        secret.setPrivate(true);
        posts.add(secret);

        List<PostModel> result = MusicChart.search(posts, "quen");
        assertEquals(2, result.size());
        assertEquals("a2", result.get(0).getId()); // bài viết nhiều tim nhất đại diện cho bài hát
        assertEquals("b", result.get(1).getId());

        assertEquals(0, MusicChart.search(posts, "khong co bai nay").size());
        assertEquals(0, MusicChart.search(posts, "   ").size());
    }

    // ---------- Lỗi 7: Bảng xếp hạng chỉ Top 3, không xếp bài 0 tim ----------

    @Test
    public void chart_isTop3AndSkipsSongsWithoutLikes() {
        assertEquals(3, MusicChart.TOP_SIZE);
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("a", "A", "X", 10, 1));
        posts.add(post("b", "B", "X", 30, 2));
        posts.add(post("c", "C", "X", 20, 3));
        posts.add(post("d", "D", "X", 5, 4));
        posts.add(post("z", "Z", "X", 0, 5));
        List<MusicChart.Entry> chart = MusicChart.rank(posts, MusicChart.TOP_SIZE);
        assertEquals(3, chart.size());
        assertEquals("b", chart.get(0).post.getId());
        assertEquals("c", chart.get(1).post.getId());
        assertEquals("a", chart.get(2).post.getId());

        List<PostModel> fewLikes = new ArrayList<>();
        fewLikes.add(post("a", "A", "X", 2, 1));
        fewLikes.add(post("z1", "Z1", "X", 0, 2));
        fewLikes.add(post("z2", "Z2", "X", 0, 3));
        List<MusicChart.Entry> small = MusicChart.rank(fewLikes, MusicChart.TOP_SIZE);
        assertEquals(1, small.size());
        assertEquals("a", small.get(0).post.getId());
    }

    // ---------- Lỗi 8: Thông báo phát nhạc ngắn gọn, đồng nhất ----------

    @Test
    public void nowPlayingMessage_isShortAndUniform() {
        assertEquals("Đang phát: Bình Yên", MusicService.nowPlayingMessage("Bình Yên"));
        String msg = MusicService.nowPlayingMessage("Từng Quen");
        assertFalse(msg.contains("⏳"));
        assertFalse(msg.contains("trực tuyến"));
        assertFalse(msg.contains(" - "));
    }
}
