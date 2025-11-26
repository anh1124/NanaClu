package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;
import java.util.Map;

public class GroupLog {
    public String logId;
    public String groupId;
    public String type;
    public String actorId;
    public String actorName;
    public String targetType;
    public String targetId;
    public String targetName;
    public String message;
    public Timestamp createdAt;
    public Map<String, Object> metadata;

    // Log type constants
    public static final String TYPE_POST_CREATED = "post_created";
    public static final String TYPE_POST_DELETED = "post_deleted";
    public static final String TYPE_COMMENT_ADDED = "comment_added";
    public static final String TYPE_COMMENT_DELETED = "comment_deleted";
    public static final String TYPE_EVENT_CREATED = "event_created";
    public static final String TYPE_EVENT_UPDATED = "event_updated";
    public static final String TYPE_EVENT_CANCELLED = "event_cancelled";
    public static final String TYPE_EVENT_RSVP = "event_rsvp";
    public static final String TYPE_GROUP_UPDATED = "group_updated";
    public static final String TYPE_GROUP_IMAGE_UPDATED = "group_image_updated";
    public static final String TYPE_MEMBER_APPROVED = "member_approved";
    public static final String TYPE_MEMBER_REJECTED = "member_rejected";
    public static final String TYPE_MEMBER_REMOVED = "member_removed";
    public static final String TYPE_MEMBER_BLOCKED = "member_blocked";
    public static final String TYPE_MEMBER_UNBLOCKED = "member_unblocked";
    public static final String TYPE_MEMBER_UNBLOCKED = "member_unblocked";
    public static final String TYPE_MEMBER_JOINED = "member_joined";
    public static final String TYPE_OWNERSHIP_TRANSFERRED = "ownership_transferred";
    public static final String TYPE_ROLE_CHANGED = "role_changed";
    public static final String TYPE_POLICY_CHANGED = "policy_changed";
    public static final String TYPE_GROUP_DELETED = "group_deleted";

    // Target type constants
    public static final String TARGET_GROUP = "group";
    public static final String TARGET_POST = "post";
    public static final String TARGET_EVENT = "event";
    public static final String TARGET_MEMBER = "member";
    public static final String TARGET_COMMENT = "comment";
    public static final String TARGET_SETTINGS = "settings";

    public GroupLog() {}

    public GroupLog(String logId, String groupId, String type, String actorId, String actorName,
                   String targetType, String targetId, String targetName, String message,
                   Timestamp createdAt, Map<String, Object> metadata) {
        this.logId = logId;
        this.groupId = groupId;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetName = targetName;
        this.message = message;
        this.createdAt = createdAt;
        this.metadata = metadata;
    }
}
