package com.example.nanaclu.data.model;

public class Chat {
    public String chatId;
    public Long createdAt; // server timestamp as millis
    public String type; // "private" | "group"
    public int memberCount; // dữ liệu thành viên ở subcollection ChatMember

    // Optional fields
    public String groupId; // for group chat
    public String lastMessage;
    public Long lastMessageAt;
    public String lastMessageAuthorId;

    // For private chat lookup convenience (sorted uidA_uidB)
    public String pairKey;

    public Chat() {}
}
