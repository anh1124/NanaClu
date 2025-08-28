package com.example.nanaclu.data.model;

import java.util.List;

public class Message {
    public String messageId;
    public String authorId;
    public String type; // "text" | "image" | "file"
    public String content; // nếu type = "image" => chứa imageId
    public long createdAt;
    public List<String> deletedFor; // userId đã xoá,sẽ không hiển thị cho những user này đọcđọc

    public Message() {}
}


