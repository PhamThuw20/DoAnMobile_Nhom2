package com.example.melodyverse;

/**
 * Bảng màu bìa đĩa dùng chung cho vòng xoay Khám phá và màn Đang phát.
 * Mỗi bài luôn nhận cùng một cặp màu (tính từ id bài), giống bộ ảnh bìa bg_cover_1..6.
 */
public final class DiscPalette {

    private static final int[][] COLORS = {
            {0xFF7C3AED, 0xFFEC4899},
            {0xFF6366F1, 0xFF06B6D4},
            {0xFFEC4899, 0xFFF97316},
            {0xFFA78BFA, 0xFF06B6D4},
            {0xFF1E1B4B, 0xFF7C3AED},
            {0xFFF59E0B, 0xFFEC4899},
    };

    private DiscPalette() {}

    /** Cặp màu {đầu, cuối} của bài hát. */
    public static int[] colorsFor(String key) {
        int idx = Math.abs((key != null ? key : "").hashCode() % COLORS.length);
        return COLORS[idx];
    }

    /** Chữ cái đầu của tên bài để in lên nhãn đĩa / bao đĩa. */
    public static String initialOf(String title) {
        String t = title != null ? title.trim() : "";
        if (t.isEmpty()) return "♪";
        return t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase();
    }
}
