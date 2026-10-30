package com.example.melodyverse;

/**
 * Model biểu diễn một bình luận trong bài viết MelodyVerse.
 * ID là chuỗi UUID tạo ngay trên máy để bình luận viết lúc offline
 * được gửi lên Firebase với đúng ID đó (gửi lại nhiều lần cũng không bị trùng).
 */
public class CommentModel {
    private String id;
    private String postId;
    private String authorUsername;
    private String authorName;
    private String content;
    private long createdAt;
    private String syncStatus;

    public CommentModel(String id, String postId, String authorUsername, String authorName,
                        String content, long createdAt, String syncStatus) {
        this.id = id;
        this.postId = postId;
        this.authorUsername = authorUsername;
        this.authorName = authorName;
        this.content = content;
        this.createdAt = createdAt;
        this.syncStatus = syncStatus != null ? syncStatus : SyncStatus.SYNCED;
    }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorName() { return authorName; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }
    public String getSyncStatus() { return syncStatus; }

    /** Thời gian hiển thị luôn tính lại từ mốc created_at thực tế. */
    public String getTimeAgo() {
        return DatabaseHelper.formatTimeAgo(createdAt);
    }

    public boolean isPending() { return SyncStatus.PENDING.equals(syncStatus); }
    public boolean isFailed() { return SyncStatus.FAILED.equals(syncStatus); }
}
