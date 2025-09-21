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

    // Deprecated UI flag; kept for backward compatibility. Use requireApproval instead.
    public boolean isPublic;

    // New: if true, user requests must be approved before joining
    public boolean requireApproval;

    public int memberCount;
    public int postCount;

    public Group() {}
}
