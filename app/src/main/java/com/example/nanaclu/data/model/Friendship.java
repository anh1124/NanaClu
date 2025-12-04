package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;
import java.util.List;

/**
 * Model cho friendship relationship giữa 2 users
 * Lưu trữ trong Firestore collection "friendships"
 */
public class Friendship {
    public String pairKey;        // min(uidA,uidB) + "_" + max(uidA,uidB)
    public List<String> members; // [uidA, uidB] - luôn có size = 2
    public String status;        // "pending" | "accepted" | "blocked"
    public String requesterId;   // Người gửi lời mời kết bạn
    public String addresseeId;   // Người nhận lời mời kết bạn
    public Long createdAt;       // Timestamp khi tạo friendship
    public Long updatedAt;       // Timestamp khi cập nhật lần cuối
    public Long acceptedAt;      // Timestamp khi accept (nullable)
    public String blockedBy;     // UserId của người block (nullable)

    public Friendship() {
        // Default constructor cho Firestore
    }

    public Friendship(String pairKey, List<String> members, String status,
                     String requesterId, String addresseeId) {
        this.pairKey = pairKey;
        this.members = members;
        this.status = status;
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

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

    // Custom setter to handle Timestamp from Firestore
    public void setUpdatedAt(Object updatedAt) {
        if (updatedAt instanceof Timestamp) {
            this.updatedAt = ((Timestamp) updatedAt).toDate().getTime();
        } else if (updatedAt instanceof Long) {
            this.updatedAt = (Long) updatedAt;
        } else if (updatedAt instanceof Number) {
            this.updatedAt = ((Number) updatedAt).longValue();
        }
    }

    // Custom setter to handle Timestamp from Firestore
    public void setAcceptedAt(Object acceptedAt) {
        if (acceptedAt == null) {
            this.acceptedAt = null;
        } else if (acceptedAt instanceof Timestamp) {
            this.acceptedAt = ((Timestamp) acceptedAt).toDate().getTime();
        } else if (acceptedAt instanceof Long) {
            this.acceptedAt = (Long) acceptedAt;
        } else if (acceptedAt instanceof Number) {
            this.acceptedAt = ((Number) acceptedAt).longValue();
        }
    }

    /**
     * Kiểm tra user có phải là thành viên của friendship này không
     */
    public boolean containsUser(String userId) {
        return members != null && members.contains(userId);
    }

    /**
     * Lấy userId của người còn lại (không phải currentUser)
     */
    public String getOtherUserId(String currentUserId) {
        if (members == null || members.size() != 2) return null;
        for (String memberId : members) {
            if (!memberId.equals(currentUserId)) {
                return memberId;
            }
        }
        return null;
    }
}
