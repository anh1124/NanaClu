package com.example.nanaclu.data.model;

import java.util.List;

public class User {
    public String userId;
    public long createdAt;
    public String email;
    public String displayName;
    public String photoUrl; // Google photo URL
    public String avatarImageId;
    public long lastLoginAt;
    public String status; // "online" | "offline"
    // Danh sách groupId mà user đã tham gia để hỗ trợ truy vấn feed hiệu quả
    public java.util.List<String> joinedGroupIds;
    public Boolean isadmin; // Admin: true nếu là admin, null hoặc false nếu không phải

    public User() {
        this.joinedGroupIds = new java.util.ArrayList<>();
    }
}


