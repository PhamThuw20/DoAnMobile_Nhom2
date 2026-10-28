package com.example.melodyverse;

/**
 * Cờ đồng bộ của từng bản ghi trong SQLite so với Firebase Firestore.
 * <ul>
 *   <li>SYNCED: đã có trên Firebase.</li>
 *   <li>PENDING: tạo / sửa lúc offline (hoặc chưa kịp gửi), chờ đẩy lên Firebase.</li>
 *   <li>PENDING_UPDATE: bài viết đã có trên Firebase được sửa trên máy, chờ gửi bản sửa.</li>
 *   <li>PENDING_DELETE: bài viết tác giả đã xóa trên máy, chờ xóa trên Firebase;
 *       hoặc đã bỏ thích / bỏ lưu / bỏ theo dõi lúc offline, chờ xóa trên Firebase.</li>
 *   <li>FAILED: Firebase từ chối vĩnh viễn (ví dụ bình luận vào bài đã bị xóa), không thử lại.</li>
 * </ul>
 */
public final class SyncStatus {

    public static final String SYNCED = "SYNCED";
    public static final String PENDING = "PENDING";
    public static final String PENDING_DELETE = "PENDING_DELETE";
    public static final String PENDING_UPDATE = "PENDING_UPDATE";
    public static final String FAILED = "FAILED";

    /** Kết quả khi bật / tắt một quan hệ (thích, lưu, theo dõi). */
    public static final String REMOVE_ROW = "REMOVE_ROW";
    public static final String NO_CHANGE = "NO_CHANGE";

    private SyncStatus() {}

    /**
     * Tính trạng thái mới của một quan hệ người dùng - bài viết khi người dùng bật / tắt.
     *
     * @param current trạng thái hiện có trong SQLite (null nếu chưa có dòng nào)
     * @param active  true = thích / lưu / theo dõi, false = bỏ
     * @return trạng thái cần ghi, hoặc {@link #REMOVE_ROW} / {@link #NO_CHANGE}
     */
    public static String nextRelationState(String current, boolean active) {
        if (active) {
            if (current == null || FAILED.equals(current)) return PENDING;
            // Bỏ rồi bật lại khi chưa kịp đồng bộ: bản ghi trên Firebase vẫn còn nguyên
            if (PENDING_DELETE.equals(current)) return SYNCED;
            return NO_CHANGE;
        }
        if (current == null) return NO_CHANGE;
        // Chưa từng lên Firebase thì chỉ cần xóa ở máy
        if (PENDING.equals(current) || FAILED.equals(current)) return REMOVE_ROW;
        if (SYNCED.equals(current)) return PENDING_DELETE;
        return NO_CHANGE;
    }
}
