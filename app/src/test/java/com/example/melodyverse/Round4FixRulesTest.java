package com.example.melodyverse;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kiểm thử các quy tắc thuần logic của đợt sửa lỗi thứ 4: link Audius có "www.", tìm hashtag chính xác,
 * Bảng xếp hạng vẫn tính tim của bài mình tự ẩn.
 */
public class Round4FixRulesTest {

    private static PostModel post(String id, String title, String artist, int likes) {
        PostModel p = new PostModel(id, "Tác giả", "tacgia", "", "", title, artist,
                "https://example.com/a.mp3", "V-Pop", "", likes, 0, 0, false, false, 0);
        p.setCreatedAt(1000);
        return p;
    }

    @Test
    public void audiusPageLink_acceptsWwwAndMatchesValidator() {
        String[] links = {
                "https://audius.co/nghesi/bai-hat",
                "https://www.audius.co/nghesi/bai-hat",
                "www.audius.co/nghesi/bai-hat",
                "audius.co/nghesi/bai-hat",
        };
        for (String link : links) {
            assertTrue(link, MusicService.isAudiusPageLink(link));
            // Qua bước kiểm tra thì cũng phải được phân giải (không đăng link trang web làm link nhạc)
            assertTrue(link, MusicService.isSupportedAudioLink(link));
        }
        assertFalse(MusicService.isAudiusPageLink("https://audius.co/nghesi"));
        assertFalse(MusicService.isAudiusPageLink("https://notaudius.co/a/b"));
        assertFalse(MusicService.isAudiusPageLink("https://example.com/a/b"));
    }

    @Test
    public void hashtagSearch_matchesWholeTagOnly() {
        // "#rap" không được khớp "#Trap"
        assertFalse(MusicChart.hasHashtag("rap", "#Trap #chill", "Nghe thử nhé", "Trap"));
        assertTrue(MusicChart.hasHashtag("rap", "#trap #Rap", null, "V-Pop"));
        // Hashtag trong chú thích và thể loại
        assertTrue(MusicChart.hasHashtag("rap", "", "Bài này đỉnh quá #rap!", "Ballad"));
        assertTrue(MusicChart.hasHashtag("rap", null, null, "Rap"));
        // Không dấu, bỏ ký tự đặc biệt: #vpop khớp thể loại V-Pop, #nhạcviệt khớp #nhacviet
        assertTrue(MusicChart.hasHashtag("vpop", null, null, "V-Pop"));
        assertTrue(MusicChart.hasHashtag("nhacviet", "#nhạcviệt", null, null));
        assertFalse(MusicChart.hasHashtag("", "#rap", null, "Rap"));
    }

    @Test
    public void chart_countsLikesOfSelfHiddenPosts() {
        List<PostModel> posts = new ArrayList<>();
        posts.add(post("hidden", "Song A", "Artist", 10));
        posts.add(post("visible", "Song A", "Artist", 2));
        posts.add(post("other", "Song B", "Artist", 8));

        List<MusicChart.Entry> chart = MusicChart.rank(posts, MusicChart.TOP_SIZE, Collections.singleton("hidden"));
        assertEquals(2, chart.size());
        // Bài hát A vẫn được cộng đủ 12 tim, nhưng bài đại diện là bài mình chưa ẩn
        assertEquals("visible", chart.get(0).post.getId());
        assertEquals(12, chart.get(0).totalLikes);
        assertEquals("other", chart.get(1).post.getId());

        // Bài hát chỉ còn bài viết đã ẩn: vẫn lên bảng, dùng chính bài đó làm đại diện
        List<PostModel> onlyHidden = new ArrayList<>();
        onlyHidden.add(post("hidden", "Song A", "Artist", 10));
        List<MusicChart.Entry> single = MusicChart.rank(onlyHidden, MusicChart.TOP_SIZE, Collections.singleton("hidden"));
        assertEquals(1, single.size());
        assertEquals("hidden", single.get(0).post.getId());
    }
}
