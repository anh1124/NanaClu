package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.Friendship;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository cho quản lý friendships trong Firestore
 * Sử dụng collection "friendships" với pairKey làm document ID
 */
public class FriendshipRepository {
    private final FirebaseFirestore db;
    private static final String FRIENDSHIPS_COLLECTION = "friendships";

    public FriendshipRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Tạo pairKey duy nhất cho 2 users
     * Format: min(uidA, uidB) + "_" + max(uidA, uidB)
     */
    private static String createPairKey(String uidA, String uidB) {
        if (uidA == null || uidB == null) return null;
        if (uidA.equals(uidB)) return null; // Không thể kết bạn với chính mình
        
        String a = uidA.compareTo(uidB) < 0 ? uidA : uidB;
        String b = uidA.compareTo(uidB) < 0 ? uidB : uidA;
        return a + "_" + b;
    }

    /**
     * Gửi lời mời kết bạn
     * Logic: Nếu cả 2 đều gửi request → auto-accept
     */
    public Task<String> sendFriendRequest(String currentUid, String targetUid) {
        if (currentUid == null || targetUid == null || currentUid.equals(targetUid)) {
            return Tasks.forException(new IllegalArgumentException("Invalid user IDs"));
        }

        String pairKey = createPairKey(currentUid, targetUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Cannot create pair key"));
        }

        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                // Chưa có friendship → tạo mới với status "pending"
                List<String> members = Arrays.asList(currentUid, targetUid);
                Map<String, Object> data = new HashMap<>();
                data.put("pairKey", pairKey);
                data.put("members", members);
                data.put("status", "pending");
                data.put("requesterId", currentUid);
                data.put("addresseeId", targetUid);
                data.put("createdAt", FieldValue.serverTimestamp());
                data.put("updatedAt", FieldValue.serverTimestamp());

                transaction.set(docRef, data);
                
                // Tạo notice cho người nhận lời mời
                createFriendRequestNotice(currentUid, targetUid);
                
                return "pending";
            }

            // Đã có friendship
            String existingStatus = snapshot.getString("status");
            String existingRequesterId = snapshot.getString("requesterId");

            if ("accepted".equals(existingStatus)) {
                // Đã là bạn → không làm gì
                return "already_friends";
            }

            if ("blocked".equals(existingStatus)) {
                // Đã bị block → không thể gửi request
                return "blocked";
            }

            if ("pending".equals(existingStatus)) {
                if (currentUid.equals(existingRequesterId)) {
                    // Mình đã gửi request trước đó → không làm gì
                    return "already_requested";
                } else {
                    // Người kia đã gửi request cho mình → auto-accept
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "accepted");
                    updates.put("acceptedAt", FieldValue.serverTimestamp());
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(docRef, updates);
                    return "auto_accepted";
                }
            }

            return "unknown_status";
        });
    }

    /**
     * Accept lời mời kết bạn (chỉ addressee mới accept được)
     */
    public Task<Void> acceptFriendRequest(String currentUid, String requesterUid) {
        String pairKey = createPairKey(currentUid, requesterUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                throw new RuntimeException("Friendship not found");
            }

            String status = snapshot.getString("status");
            String addresseeId = snapshot.getString("addresseeId");
            String requesterId = snapshot.getString("requesterId");

            if (!"pending".equals(status)) {
                throw new RuntimeException("Friendship is not pending");
            }

            // Kiểm tra xem currentUid có phải là addressee không
            // (người nhận lời mời kết bạn)
            android.util.Log.d("FriendshipRepository", "Checking accept permission: currentUid=" + currentUid + ", addresseeId=" + addresseeId + ", requesterId=" + requesterId);
            if (!currentUid.equals(addresseeId)) {
                android.util.Log.e("FriendshipRepository", "Current user " + currentUid + " is not the addressee " + addresseeId + " for requester " + requesterId);
                throw new RuntimeException("Only addressee can accept friend request");
            }

            // Accept friend request
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "accepted");
            updates.put("acceptedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(docRef, updates);

            // Tạo notice cho người gửi lời mời
            createFriendAcceptedNotice(currentUid, requesterId);

            return null;
        });
    }

    /**
     * Decline lời mời kết bạn (xóa friendship document)
     */
    public Task<Void> declineFriendRequest(String currentUid, String requesterUid) {
        String pairKey = createPairKey(currentUid, requesterUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                return null; // Không tồn tại → coi như đã decline
            }

            String status = snapshot.getString("status");
            String addresseeId = snapshot.getString("addresseeId");

            if (!"pending".equals(status) || !currentUid.equals(addresseeId)) {
                throw new RuntimeException("Cannot decline this friendship");
            }

            // Xóa friendship document
            transaction.delete(docRef);
            return null;
        });
    }

    /**
     * Cancel lời mời kết bạn đã gửi (chỉ requester mới cancel được)
     */
    public Task<Void> cancelFriendRequest(String currentUid, String targetUid) {
        String pairKey = createPairKey(currentUid, targetUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                return null; // Không tồn tại → coi như đã cancel
            }

            String status = snapshot.getString("status");
            String requesterId = snapshot.getString("requesterId");

            if (!"pending".equals(status) || !currentUid.equals(requesterId)) {
                throw new RuntimeException("Cannot cancel this friendship");
            }

            // Xóa friendship document
            transaction.delete(docRef);
            return null;
        });
    }

    /**
     * Unfriend (xóa friendship document)
     */
    public Task<Void> unfriend(String currentUid, String otherUid) {
        String pairKey = createPairKey(currentUid, otherUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                return null; // Không tồn tại → coi như đã unfriend
            }

            // Kiểm tra currentUid có trong members không
            List<String> members = (List<String>) snapshot.get("members");
            if (members == null || !members.contains(currentUid)) {
                throw new RuntimeException("User is not a member of this friendship");
            }

            // Xóa friendship document
            transaction.delete(docRef);
            return null;
        });
    }

    /**
     * Block user
     */
    public Task<Void> block(String currentUid, String otherUid) {
        String pairKey = createPairKey(currentUid, otherUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            Map<String, Object> data = new HashMap<>();
            data.put("pairKey", pairKey);
            data.put("members", Arrays.asList(currentUid, otherUid));
            data.put("status", "blocked");
            data.put("requesterId", currentUid); // Người block
            data.put("addresseeId", otherUid);   // Người bị block
            data.put("blockedBy", currentUid);
            data.put("updatedAt", FieldValue.serverTimestamp());

            if (!snapshot.exists()) {
                // Tạo mới
                data.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(docRef, data);
            } else {
                // Cập nhật
                transaction.update(docRef, data);
            }

            return null;
        });
    }

    /**
     * Unblock user (xóa friendship document)
     */
    public Task<Void> unblock(String currentUid, String otherUid) {
        String pairKey = createPairKey(currentUid, otherUid);
        if (pairKey == null) {
            return Tasks.forException(new IllegalArgumentException("Invalid pair key"));
        }

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference docRef = db.collection(FRIENDSHIPS_COLLECTION).document(pairKey);
            DocumentSnapshot snapshot = transaction.get(docRef);

            if (!snapshot.exists()) {
                return null; // Không tồn tại → coi như đã unblock
            }

            String status = snapshot.getString("status");
            String blockedBy = snapshot.getString("blockedBy");

            if (!"blocked".equals(status) || !currentUid.equals(blockedBy)) {
                throw new RuntimeException("Cannot unblock this user");
            }

            // Xóa friendship document
            transaction.delete(docRef);
            return null;
        });
    }

    /**
     * Lấy trạng thái quan hệ giữa 2 users
     * Trả về: "none", "pending_sent", "pending_incoming", "accepted", "blocked_by_me", "blocked_by_them"
     */
    public Task<String> getStatus(String currentUid, String otherUid) {
        String pairKey = createPairKey(currentUid, otherUid);
        if (pairKey == null) {
            return Tasks.forResult("none");
        }

        return db.collection(FRIENDSHIPS_COLLECTION)
                .document(pairKey)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        return "none";
                    }

                    DocumentSnapshot snapshot = task.getResult();
                    String status = snapshot.getString("status");
                    String requesterId = snapshot.getString("requesterId");
                    String blockedBy = snapshot.getString("blockedBy");

                    switch (status) {
                        case "pending":
                            if (currentUid.equals(requesterId)) {
                                return "pending_sent";
                            } else {
                                return "pending_incoming";
                            }
                        case "accepted":
                            return "accepted";
                        case "blocked":
                            if (currentUid.equals(blockedBy)) {
                                return "blocked_by_me";
                            } else {
                                return "blocked_by_them";
                            }
                        default:
                            return "none";
                    }
                });
    }

    /**
     * Lấy danh sách bạn bè (accepted friends)
     */
    public Task<List<String>> listFriends(String uid, int limit) {
        return db.collection(FRIENDSHIPS_COLLECTION)
                .whereArrayContains("members", uid)
                .whereEqualTo("status", "accepted")
                .limit(limit)
                .get()
                .continueWith(task -> {
                    List<String> friends = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            List<String> members = (List<String>) doc.get("members");
                            if (members != null) {
                                for (String memberId : members) {
                                    if (!memberId.equals(uid)) {
                                        friends.add(memberId);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    return friends;
                });
    }

    /**
     * Lấy danh sách lời mời kết bạn đã nhận (incoming requests)
     */
    public Task<List<String>> listIncomingRequests(String uid, int limit) {
        return db.collection(FRIENDSHIPS_COLLECTION)
                .whereEqualTo("addresseeId", uid)
                .whereEqualTo("status", "pending")
                .limit(limit)
                .get()
                .continueWith(task -> {
                    List<String> requesters = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            String requesterId = doc.getString("requesterId");
                            if (requesterId != null) {
                                requesters.add(requesterId);
                            }
                        }
                    }
                    return requesters;
                });
    }

    /**
     * Lấy danh sách lời mời kết bạn đã gửi (outgoing requests)
     */
    public Task<List<String>> listOutgoingRequests(String uid, int limit) {
        return db.collection(FRIENDSHIPS_COLLECTION)
                .whereEqualTo("requesterId", uid)
                .whereEqualTo("status", "pending")
                .limit(limit)
                .get()
                .continueWith(task -> {
                    List<String> addressees = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            String addresseeId = doc.getString("addresseeId");
                            if (addresseeId != null) {
                                addressees.add(addresseeId);
                            }
                        }
                    }
                    return addressees;
                });
    }

    public Task<List<String>> listBlockedUsers(String uid, int limit) {
        return db.collection(FRIENDSHIPS_COLLECTION)
                .whereArrayContains("members", uid)
                .whereEqualTo("status", "blocked")
                .whereEqualTo("blockedBy", uid)
                .limit(limit)
                .get()
                .continueWith(task -> {
                    List<String> blockedUserIds = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Friendship friendship = doc.toObject(Friendship.class);
                            if (friendship != null && friendship.members != null) {
                                for (String memberId : friendship.members) {
                                    if (!memberId.equals(uid)) {
                                        blockedUserIds.add(memberId);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    return blockedUserIds;
                });
    }

    private void createFriendRequestNotice(String requesterId, String targetUserId) {
        // Lấy tên người gửi lời mời
        com.example.nanaclu.data.repository.UserRepository userRepo = new com.example.nanaclu.data.repository.UserRepository(db);
        userRepo.getUserById(requesterId, new com.example.nanaclu.data.repository.UserRepository.UserCallback() {
            @Override
            public void onSuccess(com.example.nanaclu.data.model.User user) {
                String requesterName = user != null ? user.displayName : "Người dùng";
                com.example.nanaclu.data.repository.NoticeRepository noticeRepo = new com.example.nanaclu.data.repository.NoticeRepository(db);
                noticeRepo.createFriendRequestNotice(requesterId, requesterName, targetUserId);
            }

            @Override
            public void onError(Exception e) {
                // Fallback với tên mặc định
                String requesterName = "Người dùng";
                com.example.nanaclu.data.repository.NoticeRepository noticeRepo = new com.example.nanaclu.data.repository.NoticeRepository(db);
                noticeRepo.createFriendRequestNotice(requesterId, requesterName, targetUserId);
            }
        });
    }

    private void createFriendAcceptedNotice(String accepterId, String requesterId) {
        // Lấy tên người chấp nhận
        com.example.nanaclu.data.repository.UserRepository userRepo = new com.example.nanaclu.data.repository.UserRepository(db);
        userRepo.getUserById(accepterId, new com.example.nanaclu.data.repository.UserRepository.UserCallback() {
            @Override
            public void onSuccess(com.example.nanaclu.data.model.User user) {
                String accepterName = user != null ? user.displayName : "Người dùng";
                com.example.nanaclu.data.repository.NoticeRepository noticeRepo = new com.example.nanaclu.data.repository.NoticeRepository(db);
                noticeRepo.createFriendAcceptedNotice(accepterId, accepterName, requesterId);
            }

            @Override
            public void onError(Exception e) {
                // Fallback với tên mặc định
                String accepterName = "Người dùng";
                com.example.nanaclu.data.repository.NoticeRepository noticeRepo = new com.example.nanaclu.data.repository.NoticeRepository(db);
                noticeRepo.createFriendAcceptedNotice(accepterId, accepterName, requesterId);
            }
        });
    }
}
