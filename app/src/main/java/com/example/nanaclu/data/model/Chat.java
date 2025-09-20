package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;

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

    // Custom setter to handle Timestamp from Firestore
    public void setCreatedAt(Object createdAt) {
        if (createdAt instanceof Timestamp) {
            this.createdAt = ((Timestamp) createdAt).toDate().getTime();
        } else if (createdAt instanceof Long) {
            this.createdAt = (Long) createdAt;
        } else if (createdAt instanceof Number) {
            this.createdAt = ((Number) createdAt).longValue();
        }
    }

    // Custom setter for lastMessageAt
    public void setLastMessageAt(Object lastMessageAt) {
        if (lastMessageAt == null) {
            this.lastMessageAt = null;
        } else if (lastMessageAt instanceof Timestamp) {
            this.lastMessageAt = ((Timestamp) lastMessageAt).toDate().getTime();
        } else if (lastMessageAt instanceof Long) {
            this.lastMessageAt = (Long) lastMessageAt;
        } else if (lastMessageAt instanceof Number) {
            this.lastMessageAt = ((Number) lastMessageAt).longValue();
        }
    }
}
