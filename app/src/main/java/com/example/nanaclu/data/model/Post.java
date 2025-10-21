package com.example.nanaclu.data.model;

import java.util.List;
import java.util.ArrayList;

public class Post {
    public String postId;
    public String authorId;
    public String groupId;        // NEW: Liên kết với group
    public String content;
    public List<String> imageUrls; // CHANGED: Từ imageIds thành imageUrls để lưu Firebase Storage URLs
    public long createdAt;
    public Long deletedAt;
    public Long editedAt;
    public int likeCount;
    public int commentCount;
    
    // Video fields
    public boolean hasVideo;
    public String videoUrl;
    public String videoThumbUrl;
    public long videoDurationMs;
    public int videoWidth;
    public int videoHeight;

    public Post() {
        this.imageUrls = new ArrayList<>(); // Initialize empty list
    }
}
