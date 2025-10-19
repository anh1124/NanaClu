package com.example.nanaclu.data.model;

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
