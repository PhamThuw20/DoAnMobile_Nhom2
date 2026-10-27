package com.example.melodyverse;

/**
 * Một thông báo trong ứng dụng, luôn thuộc về đúng MỘT tài khoản nhận (recipient).
 * Nguồn gốc: Firestore notifications/{id}; lưu đệm trong SQLite để xem offline.
 * Trạng thái đọc lưu theo từng thông báo (is_read) và đồng bộ lên Firestore.
 */
public class NotificationModel {

    public static final String TYPE_LIKE = "like";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_FOLLOW = "follow";
    public static final String TYPE_NEW_POST = "new_post";
    public static final String TYPE_WELCOME = "welcome";

    private final String id;
    private final String recipient;
    private final String type;
    private final String actor;
    private final String actorName;
    private final String postId;
    private final String commentId;
    private final String songTitle;
    private final String preview;
    private final long createdAt;
    private boolean read;
    private final boolean sample;

    public NotificationModel(String id, String recipient, String type, String actor, String actorName,
                             String postId, String commentId, String songTitle, String preview,
                             long createdAt, boolean read, boolean sample) {
        this.id = id;
        this.recipient = recipient;
        this.type = type != null ? type : TYPE_WELCOME;
        this.actor = actor != null ? actor : "";
        this.actorName = actorName != null && !actorName.isEmpty() ? actorName : this.actor;
        this.postId = postId != null ? postId : "";
        this.commentId = commentId != null ? commentId : "";
        this.songTitle = songTitle != null ? songTitle : "";
        this.preview = preview != null ? preview : "";
        this.createdAt = createdAt;
        this.read = read;
        this.sample = sample;
    }

    public String getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getActor() { return actor; }
    public String getActorName() { return actorName; }
    public String getPostId() { return postId; }
    public String getCommentId() { return commentId; }
    public String getSongTitle() { return songTitle; }
    public String getPreview() { return preview; }
    public long getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    /** Thông báo minh họa (sinh từ dữ liệu mẫu) — vẫn trỏ tới bài viết / hồ sơ mẫu có thật trên Firestore. */
    public boolean isSample() { return sample; }

    public String getTimeAgo() {
        return DatabaseHelper.formatTimeAgo(createdAt);
    }

    /** Câu hiển thị trong danh sách. */
    public String getSummary() {
        switch (type) {
            case TYPE_LIKE:
                return actorName + " đã thích bài hát bạn chia sẻ: “" + songTitle + "”";
            case TYPE_COMMENT:
                return actorName + " đã bình luận: “" + preview + "”";
            case TYPE_FOLLOW:
                return actorName + " đã bắt đầu theo dõi bạn";
            case TYPE_NEW_POST:
                return actorName + " vừa chia sẻ bài hát mới “" + songTitle + "”";
            default:
                return "Chào mừng " + actorName + " đến với MelodyVerse!";
        }
    }
}
