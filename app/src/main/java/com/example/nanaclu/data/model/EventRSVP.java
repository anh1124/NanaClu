package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;

public class EventRSVP {
    public String attendeeId;
    public String rsvpId; // Alias for attendeeId (backward compatibility)
    public String userId;
    public String userName; // Cache tên user
    public String userAvatar; // Cache avatar user
    public String userAvatarUrl; // Alias for userAvatar
    public String attendanceStatus; // "attending" | "not_attending" | "maybe"
    public String status; // Alias for attendanceStatus (backward compatibility)
    public long responseTime; // Timestamp phản hồi
    public long respondedAt; // Alias for responseTime (backward compatibility)
    public long rsvpTime; // Alias for responseTime (for backward compatibility)
    public String note; // Ghi chú (optional)

    public enum Status {
        ATTENDING("attending"),
        MAYBE("maybe"),
        NOT_ATTENDING("not_attending"),
        NOT_RESPONDED("not_responded");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static Status fromString(String value) {
            for (Status status : Status.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return NOT_RESPONDED;
        }

        public static Status fromValue(String value) {
            return fromString(value);
        }

        // For backward compatibility with old field names
        public static Status fromOldString(String value) {
            switch (value) {
                case "going": return ATTENDING;
                case "not_going": return NOT_ATTENDING;
                case "maybe": return MAYBE;
                default: return NOT_RESPONDED;
            }
        }
    }
    
    public EventRSVP() {}

    public EventRSVP(String userId, String userName, String userAvatar, String status) {
        this.userId = userId;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.userAvatarUrl = userAvatar; // Set alias
        this.attendanceStatus = status;
        this.status = status; // Set alias for backward compatibility
        this.responseTime = System.currentTimeMillis();
        this.respondedAt = this.responseTime; // Set alias for backward compatibility
        this.rsvpTime = this.responseTime; // Set alias for backward compatibility
    }
    
    // Custom setter for Firestore Timestamp handling
    public void setRespondedAt(Object respondedAtValue) {
        if (respondedAtValue instanceof Timestamp) {
            this.respondedAt = ((Timestamp) respondedAtValue).toDate().getTime();
        } else if (respondedAtValue instanceof Long) {
            this.respondedAt = (Long) respondedAtValue;
        } else if (respondedAtValue instanceof Number) {
            this.respondedAt = ((Number) respondedAtValue).longValue();
        }
        this.responseTime = this.respondedAt; // Update new field
        this.rsvpTime = this.respondedAt; // Update alias
    }

    // Custom setter for responseTime to update aliases
    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
        this.respondedAt = responseTime; // Update alias
        this.rsvpTime = responseTime; // Update alias
    }

    // Custom setter for attendanceStatus to update alias
    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
        this.status = attendanceStatus; // Update alias
    }

    // Setter for userAvatar to update alias
    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
        this.userAvatarUrl = userAvatar;
    }
}
