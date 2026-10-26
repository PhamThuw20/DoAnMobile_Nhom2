package com.example.melodyverse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** Kiểm thử các quy tắc của đợt sửa lỗi Khám phá / Nhạc đã lưu / Trình phát. */
public class Round2FixRulesTest {

    private static PostModel post(String id, String title, String artist, int likes, long createdAt) {
        PostModel p = new PostModel(id, "Tác giả", "tacgia", "", "", title, artist,
                "https://example.com/a.mp3", "V-Pop", "", likes, 0, 0, false, false, 0);
        p.setCreatedAt(createdAt);
        return p;
    }

    @After
    public void resetModes() {
        MusicService.isShuffleEnabled = false;
        MusicService.isRepeatEnabled = false;
    }

    // ---------- Lỗi 4: Bảng xếp hạng theo lượt thả tim ----------

    @Test
    public void chart_sortsByLikesDescending() {
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("a", "Bài A", "Ca sĩ A", 10, 1));
        posts.add(post("b", "Bài B", "Ca sĩ B", 500, 2));
        posts.add(post("c", "Bài C", "Ca sĩ C", 120, 3));

        List<MusicChart.Entry> chart = MusicChart.rank(posts, 10);
        assertEquals("b", chart.get(0).post.getId());
        assertEquals("c", chart.get(1).post.getId());
        assertEquals("a", chart.get(2).post.getId());
    }

    @Test
    public void chart_sumsLikesOfTheSameSongAcrossPosts() {
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("a1", "Từng Quen", "Wren Evans", 60, 1));
        posts.add(post("a2", "TỪNG QUEN ", "wren evans", 50, 2)); // cùng bài hát, khác cách viết
        posts.add(post("b", "Bài khác", "Ca sĩ B", 100, 3));

        List<MusicChart.Entry> chart = MusicChart.rank(posts, 10);
        assertEquals(2, chart.size());
        assertEquals(110, chart.get(0).totalLikes);
        assertEquals("a1", chart.get(0).post.getId()); // bài viết nhiều tim nhất đại diện cho bài hát
    }

    @Test
    public void chart_likeMovesSongUp() {
        PostModel low = post("low", "Bài thấp", "X", 99, 1);
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("top", "Bài cao", "Y", 100, 2));
        posts.add(low);
        assertEquals("top", MusicChart.rank(posts, 10).get(0).post.getId());

        low.setLikeCount(low.getLikeCount() + 2); // có người thả tim
        assertEquals("low", MusicChart.rank(posts, 10).get(0).post.getId());
    }

    @Test
    public void chart_skipsPrivatePostsAndRespectsLimit() {
        List<PostModel> posts = new ArrayList<>();
        PostModel secret = post("p", "Riêng tư", "Z", 9999, 1);
        secret.setPrivate(true);
        posts.add(secret);
        for (int i = 0; i < 15; i++) posts.add(post("x" + i, "Bài " + i, "Ca sĩ " + i, i, i));

        List<MusicChart.Entry> chart = MusicChart.rank(posts, MusicChart.TOP_SIZE);
        assertEquals(MusicChart.TOP_SIZE, chart.size());
        for (MusicChart.Entry e : chart) assertFalse(e.post.isPrivate());
    }

    // ---------- Lỗi 13: Lặp lại và Ngẫu nhiên loại trừ nhau ----------

    @Test
    public void playModes_areMutuallyExclusive() {
        MusicService.setRepeatEnabled(true);
        MusicService.setShuffleEnabled(true);
        assertTrue(MusicService.isShuffleEnabled);
        assertFalse(MusicService.isRepeatEnabled);

        MusicService.setRepeatEnabled(true);
        assertTrue(MusicService.isRepeatEnabled);
        assertFalse(MusicService.isShuffleEnabled);

        MusicService.setRepeatEnabled(false);
        assertFalse(MusicService.isRepeatEnabled);
        assertFalse(MusicService.isShuffleEnabled); // cả hai tắt = phát tuần tự
    }

    // ---------- Lỗi 1: Sao chép đúng đường dẫn bài hát ----------

    @Test
    public void shareLink_usesRealStreamUrlOrTrackPage() {
        PostModel streaming = post("s1", "A", "B", 0, 0);
        streaming.setMusicUrl(" https://cdn.audius.co/track/123.mp3 ");
        assertEquals("https://cdn.audius.co/track/123.mp3", ShareHelper.songLink(streaming));

        PostModel sample = post("p3", "A", "B", 0, 0); // link mẫu example.com -> trang bài hát
        assertEquals("https://melodyverse.vn/track/p3", ShareHelper.songLink(sample));
    }
}
