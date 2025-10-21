package com.example.nanaclu.data.model;

import com.google.firebase.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

public class Notice {
    public String id;
    public String type; // post_approved, message, comment, like, event_created
    public String actorId;
    public String actorName;
    public String actorAvatar;
    public String objectType; // post, comment, chat, event
    public String objectId;
    public String groupId;
    public String eventId;
    public String title;
    public String message;
    public long createdAt;
    public boolean seen; // Đã xem hay chưa (dùng cho icon badge và highlight item)
    public String targetUserId; // redundant for rules

    public Notice() {
        // Default constructor required for Firestore
    }

    public Notice(String id, String type, String actorId, String actorName, String objectType, 
                  String objectId, String groupId, String title, String message, String targetUserId) {
        this.id = id;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.objectType = objectType;
        this.objectId = objectId;
        this.groupId = groupId;
        this.title = title;
        this.message = message;
        this.targetUserId = targetUserId;
        this.createdAt = System.currentTimeMillis();
        this.seen = false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("actorId", actorId);
        map.put("actorName", actorName);
        map.put("actorAvatar", actorAvatar);
        map.put("objectType", objectType);
        map.put("objectId", objectId);
        map.put("groupId", groupId);
        map.put("eventId", eventId);
        map.put("title", title);
        map.put("message", message);
        map.put("createdAt", createdAt);
        map.put("seen", seen);
        map.put("targetUserId", targetUserId);
        return map;
    }

    public static Notice from(DocumentSnapshot document) {
        Notice notice = new Notice();
        notice.id = document.getId();
        notice.type = document.getString("type");
        notice.actorId = document.getString("actorId");
        notice.actorName = document.getString("actorName");
        notice.actorAvatar = document.getString("actorAvatar");
        notice.objectType = document.getString("objectType");
        notice.objectId = document.getString("objectId");
        notice.groupId = document.getString("groupId");
        notice.eventId = document.getString("eventId");
        notice.title = document.getString("title");
        notice.message = document.getString("message");
        notice.createdAt = document.getLong("createdAt") != null ? document.getLong("createdAt") : 0;
        notice.seen = Boolean.TRUE.equals(document.getBoolean("seen"));
        notice.targetUserId = document.getString("targetUserId");
        return notice;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorAvatar() { return actorAvatar; }
    public void setActorAvatar(String actorAvatar) { this.actorAvatar = actorAvatar; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Notice notice = (Notice) obj;
        return seen == notice.seen &&
                createdAt == notice.createdAt &&
                java.util.Objects.equals(id, notice.id) &&
                java.util.Objects.equals(type, notice.type) &&
                java.util.Objects.equals(actorId, notice.actorId) &&
                java.util.Objects.equals(actorName, notice.actorName) &&
                java.util.Objects.equals(actorAvatar, notice.actorAvatar) &&
                java.util.Objects.equals(objectType, notice.objectType) &&
                java.util.Objects.equals(objectId, notice.objectId) &&
                java.util.Objects.equals(groupId, notice.groupId) &&
                java.util.Objects.equals(eventId, notice.eventId) &&
                java.util.Objects.equals(title, notice.title) &&
                java.util.Objects.equals(message, notice.message) &&
                java.util.Objects.equals(targetUserId, notice.targetUserId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, type, actorId, actorName, actorAvatar, objectType, objectId, groupId, eventId, title, message, createdAt, seen, targetUserId);
    }
}
