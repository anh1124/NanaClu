package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;

public class Comment {
    public String commentId;
    public String authorId;
    public String content;
    public int likeCount;
    public int replyCount;
    public Timestamp createdAt;  // Đổi từ long thành Timestamp
    public String parentCommentId; // For replies

    // UI fields (loaded separately)
    public String authorName;
    public String authorAvatar;

    public Comment() {}

    // Helper method để lấy createdAt dưới dạng long
    public long getCreatedAtLong() {
        return createdAt != null ? createdAt.toDate().getTime() : 0;
    }
}


