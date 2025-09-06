package com.example.nanaclu.data.model;

public class Member {
    public String userId;
    public String role;   // "admin" | "member" | "owner"
    public long joinedAt;
    public String status; // "active" | "pending" | "banned"
    
    // Additional fields for display (loaded from User)
    public String userName;
    public String userEmail;
    public String avatarImageId;

    public Member() {}
}


