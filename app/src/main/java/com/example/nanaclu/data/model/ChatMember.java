package com.example.nanaclu.data.model;

public class ChatMember {
    public String userId;
    public long joinedAt;
    public Long lastReadAt; // hỗ trợ tính unread client-side
    public String role; // optional: "admin" | "member"

    public ChatMember() {}
}


