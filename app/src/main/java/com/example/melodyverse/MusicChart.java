package com.example.melodyverse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bảng xếp hạng âm nhạc: xếp bài hát theo TỔNG số lượt thả tim trên toàn ứng dụng.
 * Nhiều bài viết chia sẻ cùng một bài hát (trùng tên bài + nghệ sĩ) được cộng dồn lượt tim.
 */
public final class MusicChart {

    /** Chỉ vinh danh Top 3 (Quán quân, Á quân, Top 3) để bảng gọn, không trùng công năng với Vòng xoay đĩa than. */
    public static final int TOP_SIZE = 3;

    /** Một hàng trên bảng xếp hạng. */
    public static final class Entry {
        /** Bài viết đại diện (nhiều tim nhất) — dùng để phát nhạc và mở chi tiết. */
        public final PostModel post;
        public final int totalLikes;

        Entry(PostModel post, int totalLikes) {
            this.post = post;
            this.totalLikes = totalLikes;
        }
    }

    private MusicChart() {}

    /**
     * Xếp hạng giảm dần theo tổng lượt tim; bằng tim thì bài được đăng sớm hơn đứng trên (giữ ổn định thứ tự).
     * Bỏ qua bài riêng tư, bài không có tên bài hát và bài hát chưa có lượt tim nào (0 tim không lên bảng).
     */
    public static List<Entry> rank(List<PostModel> posts, int limit) {
        return rank(posts, limit, java.util.Collections.emptySet());
    }

    /**
     * @param hiddenIds bài người xem đã tự ẩn: vẫn cộng lượt tim cho bài hát, nhưng chỉ dùng làm bài đại diện
     *                  khi bài hát không còn bài viết nào khác
     */
    public static List<Entry> rank(List<PostModel> posts, int limit, java.util.Set<String> hiddenIds) {
        Map<String, PostModel> best = new LinkedHashMap<>();
        Map<String, Integer> likes = new LinkedHashMap<>();
        Map<String, Long> firstShared = new LinkedHashMap<>();
        for (PostModel p : posts) {
            if (p == null || p.isPrivate() || p.getSongTitle() == null || p.getSongTitle().trim().isEmpty()) continue;
            String key = songKey(p);
            int l = Math.max(0, p.getLikeCount());
            likes.put(key, likes.containsKey(key) ? likes.get(key) + l : l);
            PostModel current = best.get(key);
            boolean pHidden = hiddenIds.contains(p.getId());
            boolean currentHidden = current != null && hiddenIds.contains(current.getId());
            if (current == null || (currentHidden && !pHidden)
                    || (currentHidden == pHidden && l > current.getLikeCount())) best.put(key, p);
            Long first = firstShared.get(key);
            if (first == null || p.getCreatedAt() < first) firstShared.put(key, p.getCreatedAt());
        }
        List<String> keys = new ArrayList<>(best.keySet());
        keys.sort((a, b) -> {
            int byLikes = Integer.compare(likes.get(b), likes.get(a));
            if (byLikes != 0) return byLikes;
            int byTime = Long.compare(firstShared.get(a), firstShared.get(b));
            return byTime != 0 ? byTime : a.compareTo(b);
        });
        List<Entry> result = new ArrayList<>();
        for (String key : keys) {
            if (result.size() >= limit) break;
            if (likes.get(key) <= 0) break; // đã sắp giảm dần: từ đây trở đi đều là bài 0 tim
            result.add(new Entry(best.get(key), likes.get(key)));
        }
        return result;
    }

    /**
     * Kết quả tìm kiếm của màn Khám phá: các BÀI HÁT công khai khớp từ khóa (tên bài, nghệ sĩ, thể loại, người đăng),
     * mỗi bài hát chỉ xuất hiện một lần dù được nhiều bài viết chia sẻ. Số lượng phần tử chính là số hàng hiển thị.
     */
    public static List<PostModel> search(List<PostModel> posts, String query) {
        List<PostModel> result = new ArrayList<>();
        String q = normalize(query);
        if (q.isEmpty()) return result;
        Map<String, Integer> indexByKey = new LinkedHashMap<>();
        for (PostModel p : posts) {
            if (p == null || p.isPrivate() || p.getSongTitle() == null || p.getSongTitle().trim().isEmpty()) continue;
            if (!matchesQuery(q, p)) continue;
            String key = songKey(p);
            Integer idx = indexByKey.get(key);
            if (idx == null) {
                indexByKey.put(key, result.size());
                result.add(p);
            } else if (p.getLikeCount() > result.get(idx).getLikeCount()) {
                result.set(idx, p); // bài viết nhiều tim nhất đại diện cho bài hát
            }
        }
        return result;
    }

    /** @param queryNorm từ khóa đã qua {@link #normalize(String)} */
    static boolean matchesQuery(String queryNorm, PostModel p) {
        for (String src : new String[]{p.getSongTitle(), p.getArtistName(), p.getGenre(), p.getAuthorName()}) {
            if (src != null && normalize(src).contains(queryNorm)) return true;
        }
        return false;
    }

    /**
     * true nếu {@code tag} (đã bỏ dấu #) trùng nguyên vẹn một hashtag của bài: các thẻ trong ô hashtag,
     * các #thẻ trong chú thích, hoặc thể loại. So sánh không dấu, bỏ ký tự đặc biệt ("#vpop" khớp "V-Pop").
     * "#rap" không khớp "#Trap".
     */
    public static boolean hasHashtag(String tag, String hashtags, String caption, String genre) {
        String want = tagKey(tag);
        if (want.isEmpty()) return false;
        if (want.equals(tagKey(genre))) return true;
        if (hashtags != null) {
            for (String t : hashtags.split("[\\s,;#]+")) {
                if (want.equals(tagKey(t))) return true;
            }
        }
        if (caption != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("#([^\\s#,;.!?]+)").matcher(caption);
            while (m.find()) {
                if (want.equals(tagKey(m.group(1)))) return true;
            }
        }
        return false;
    }

    private static String tagKey(String s) {
        return normalize(s).replaceAll("[^a-z0-9]", "");
    }

    /** Khóa nhận diện một bài hát: tên bài + nghệ sĩ, bỏ dấu, không phân biệt hoa thường. */
    static String songKey(PostModel p) {
        return normalize(p.getSongTitle()) + "|" + normalize(p.getArtistName());
    }

    static String normalize(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return nfd.replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** "1.024 lượt yêu thích" */
    public static String likesLabel(int likes) {
        return String.format(Locale.GERMANY, "%,d", likes) + " lượt yêu thích";
    }
}
