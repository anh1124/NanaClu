package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;

public class Message {
    public String messageId;
    public String authorId;
    public String authorName; // Thêm tên author để tiết kiệm chi phí đọc
    public String type; // "text" | "image" | "file"
    public String content; // nếu type = "image" => chứa storage url/id
    public long createdAt;

    // Optional metadata
    public Long editedAt;
    public Long deletedAt; // soft delete timestamp
    public String replyTo; // messageId được trả lời

    public Message() {}

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

    // Custom setter for editedAt
    public void setEditedAt(Object editedAt) {
        if (editedAt == null) {
            this.editedAt = null;
        } else if (editedAt instanceof Timestamp) {
            this.editedAt = ((Timestamp) editedAt).toDate().getTime();
        } else if (editedAt instanceof Long) {
            this.editedAt = (Long) editedAt;
        } else if (editedAt instanceof Number) {
            this.editedAt = ((Number) editedAt).longValue();
        }
    }

    // Custom setter for deletedAt
    public void setDeletedAt(Object deletedAt) {
        if (deletedAt == null) {
            this.deletedAt = null;
        } else if (deletedAt instanceof Timestamp) {
            this.deletedAt = ((Timestamp) deletedAt).toDate().getTime();
        } else if (deletedAt instanceof Long) {
            this.deletedAt = (Long) deletedAt;
        } else if (deletedAt instanceof Number) {
            this.deletedAt = ((Number) deletedAt).longValue();
        }
    }
}
