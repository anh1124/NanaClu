package com.example.nanaclu.data.model;

import java.util.List;
import java.util.ArrayList;

public class Post {
    public String postId;
    public String authorId;
    public String groupId;        // NEW: Liên kết với group
    public String content;
    public List<String> imageIds; // CHANGED: Từ String thành List<String> để hỗ trợ multiple images
    public long createdAt;
    public Long deletedAt;
    public Long editedAt;
    public int likeCount;
    public int commentCount;

    public Post() {
        this.imageIds = new ArrayList<>(); // Initialize empty list
    }
}


