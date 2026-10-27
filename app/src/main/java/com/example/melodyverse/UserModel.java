package com.example.melodyverse;

public class UserModel {
    private String username;
    private String fullName;
    private String studentId;
    private String bio;
    private int avatarIndex;
    private int postsCount;
    private int followersCount;
    private int followingCount;
    private boolean isLocked;
    private String email = "";

    public UserModel(String username, String fullName, String studentId, String bio, 
                     int avatarIndex, int postsCount, int followersCount, int followingCount) {
        this(username, fullName, studentId, bio, avatarIndex, postsCount, followersCount, followingCount, false);
    }

    public UserModel(String username, String fullName, String studentId, String bio, 
                     int avatarIndex, int postsCount, int followersCount, int followingCount, boolean isLocked) {
        this.username = username;
        this.fullName = fullName;
        this.studentId = studentId;
        this.bio = bio;
        this.avatarIndex = avatarIndex;
        this.postsCount = postsCount;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.isLocked = isLocked;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getRole() { return studentId != null && !studentId.isEmpty() ? studentId : "Thành viên"; }
    public void setRole(String role) { this.studentId = role; }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(username) || 
               (studentId != null && (studentId.contains("Admin") || studentId.contains("Quản trị")));
    }

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { this.isLocked = locked; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public int getAvatarIndex() { return avatarIndex; }
    public void setAvatarIndex(int avatarIndex) { this.avatarIndex = avatarIndex; }

    public int getPostsCount() { return postsCount; }
    public void setPostsCount(int postsCount) { this.postsCount = postsCount; }

    public int getFollowersCount() { return followersCount; }
    public void setFollowersCount(int followersCount) { this.followersCount = followersCount; }

    public int getFollowingCount() { return followingCount; }
    public void setFollowingCount(int followingCount) { this.followingCount = followingCount; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email : ""; }
}
