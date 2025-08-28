package com.example.nanaclu.data.model;

public class Chat {
    public String chatId;
    public long createdAt;
    public String type; // "private" | "group"
    public int memberCount; // dữ liệu thành viên ở subcollection ChatMember

    public Chat() {}
}


