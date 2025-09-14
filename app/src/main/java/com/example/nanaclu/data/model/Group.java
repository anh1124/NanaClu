package com.example.nanaclu.data.model;

public class Group {
    public String groupId;
    public String name;
    public String code; // short join code (e.g., 6 chars)
    public String avatarImageId;
    public String coverImageId;
    public String description;
    public String createdBy;
    public long createdAt;
    public boolean isPublic;
    public int memberCount;
    public int postCount;

    public Group() {}
}
