package com.example.nanaclu.data.model;

public class Message {
    public String messageId;
    public String authorId;
    public String type; // "text" | "image" | "file"
    public String content; // nếu type = "image" => chứa storage url/id
    public long createdAt;

    // Optional metadata
    public Long editedAt;
    public Long deletedAt; // soft delete timestamp
    public String replyTo; // messageId được trả lời

    public Message() {}
}
