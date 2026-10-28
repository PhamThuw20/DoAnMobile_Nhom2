package com.example.melodyverse;

public class PostModel {
    private String id;
    private String authorName;
    private String authorUsername;
    private String timeAgo;
    private String caption;
    private String songTitle;
    private String artistName;
    private String musicUrl;
    private String genre;
    private String hashtags;
    private int likeCount;
    private int commentCount;
    private int shareCount;
    private boolean isLiked;
    private boolean isBookmarked;
    private boolean isPlaying;
    private int rawAudioRes;
    private boolean isPrivate = false;
    private long createdAt = 0;
    // Trạng thái đồng bộ với Firebase: SYNCED / PENDING (đăng lúc offline, chờ đẩy lên)
    private String syncStatus = SyncStatus.SYNCED;

    public PostModel(String id, String authorName, String authorUsername, String timeAgo, 
                     String caption, String songTitle, String artistName, String musicUrl, 
                     String genre, String hashtags, int likeCount, int commentCount, int shareCount,
                     boolean isLiked, boolean isBookmarked, int rawAudioRes) {
        this.id = id;
        this.authorName = authorName;
        this.authorUsername = authorUsername;
        this.timeAgo = timeAgo;
        this.caption = caption;
        this.songTitle = songTitle;
        this.artistName = artistName;
        this.musicUrl = musicUrl;
        this.genre = genre;
        this.hashtags = hashtags;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.shareCount = shareCount;
        this.isLiked = isLiked;
        this.isBookmarked = isBookmarked;
        this.isPlaying = false;
        this.rawAudioRes = rawAudioRes;
    }

    // Constructor tiện ích cho bài viết tạo nhanh
    public PostModel(String id, String authorName, String authorUsername, String timeAgo, 
                     String caption, String songTitle, String artistName, String musicUrl, 
                     String hashtags, int likeCount, int commentCount, int shareCount) {
        this(id, authorName, authorUsername, timeAgo, caption, songTitle, artistName, musicUrl,
             "V-Pop", hashtags, likeCount, commentCount, shareCount, false, false, R.raw.song_sample_1);
    }

    public String getId() { return id; }
    public String getAuthorName() { return authorName; }
    public String getAuthorUsername() { return authorUsername; }
    public String getTimeAgo() { return timeAgo; }
    public String getCaption() { return caption; }
    public String getSongTitle() { return songTitle; }
    public String getArtistName() { return artistName; }
    public String getMusicUrl() { return musicUrl; }
    public String getGenre() { return genre != null ? genre : "V-Pop"; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getHashtags() { return hashtags; }

    // Dùng khi tác giả chỉnh sửa bài viết
    public void setCaption(String caption) { this.caption = caption; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }
    public void setArtistName(String artistName) { this.artistName = artistName; }
    public void setMusicUrl(String musicUrl) { this.musicUrl = musicUrl; }
    public void setHashtags(String hashtags) { this.hashtags = hashtags; }
    
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public int getShareCount() { return shareCount; }
    public void setShareCount(int shareCount) { this.shareCount = shareCount; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public boolean isBookmarked() { return isBookmarked; }
    public void setBookmarked(boolean bookmarked) { isBookmarked = bookmarked; }

    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }

    public int getRawAudioRes() { return rawAudioRes; }
    public void setRawAudioRes(int rawAudioRes) { this.rawAudioRes = rawAudioRes; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public boolean isPendingSync() { return SyncStatus.PENDING.equals(syncStatus); }

    /** Lượt thích / bỏ thích của mình mới lưu trên máy, Firebase chưa xác nhận (hiện nhãn chờ cạnh số tim). */
    private boolean likePending = false;
    public boolean isLikePending() { return likePending; }
    public void setLikePending(boolean likePending) { this.likePending = likePending; }

    /** Quản trị viên đã ẩn bài (vi phạm): không hiển thị với thành viên, chỉ còn trong trang Quản trị. */
    private boolean hiddenByAdmin = false;
    public boolean isHiddenByAdmin() { return hiddenByAdmin; }
    public void setHiddenByAdmin(boolean hiddenByAdmin) { this.hiddenByAdmin = hiddenByAdmin; }
}
