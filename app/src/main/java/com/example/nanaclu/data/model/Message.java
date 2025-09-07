package com.example.nanaclu.data.model;

public class Message {
    public String messageId;
    public String authorId;
    public String type; // "text" | "image" | "file"
    public String content; // nếu type = "image" => chứa imageId
    public long createdAt;

    public Message() {}
}


