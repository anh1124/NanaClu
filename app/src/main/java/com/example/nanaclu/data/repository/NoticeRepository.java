package com.example.nanaclu.data.repository;

import android.util.Log;

import com.example.nanaclu.data.model.Notice;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NoticeRepository {
    private static final String TAG = "NoticeRepository";
    private final FirebaseFirestore db;

    public NoticeRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Lắng nghe thông báo mới nhất cho user
     */
    public ListenerRegistration listenLatest(String uid, int limit, EventListener<QuerySnapshot> listener) {
        return db.collection("users")
                .document(uid)
                .collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener(listener);
    }

    /**
     * Phân trang thông báo
     */
    public Task<List<Notice>> paginate(String uid, int limit, DocumentSnapshot cursor) {
        Query query = db.collection("users")
                .document(uid)
                .collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit);

        if (cursor != null) {
            query = query.startAfter(cursor);
        }

        return query.get().continueWith(task -> {
            if (task.isSuccessful()) {
                List<Notice> notices = new ArrayList<>();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    notices.add(Notice.from(doc));
                }
                return notices;
            } else {
                throw task.getException();
            }
        });
    }

    /**
     * Đánh dấu thông báo đã xem
     */
    public Task<Void> markSeen(String uid, String noticeId) {
        return db.collection("users")
                .document(uid)
                .collection("notices")
                .document(noticeId)
                .update("seen", true);
    }

    /**
     * Đánh dấu tất cả thông báo đã xem
     */
    public Task<Void> markAllSeen(String uid) {
        return db.collection("users")
                .document(uid)
                .update("lastSeenAt", System.currentTimeMillis());
    }

    /**
     * Đánh dấu nhiều thông báo đã xem (batch)
     */
    public Task<Void> markSeenBatch(String uid, List<String> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Tasks.forResult(null);
        }
        WriteBatch batch = db.batch();
        for (String id : noticeIds) {
            DocumentReference ref = db.collection("users")
                    .document(uid)
                    .collection("notices")
                    .document(id);
            batch.update(ref, "seen", true);
        }
        return batch.commit();
    }

    /**
     * Tạo thông báo khi có bài viết mới trong group cho danh sách memberIds
     * Lưu vào users/{uid}/notices để hiển thị trong NotificationsActivity
     */
    public Task<Void> createGroupPostNotice(String groupId,
                                            String postId,
                                            String actorId,
                                            String actorName,
                                            List<String> memberIds,
                                            String postSnippet) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Tasks.forResult(null);
        }

        WriteBatch batch = db.batch();
        String title = "Bài viết mới trong nhóm";
        String baseMessage;
        if (actorName != null && !actorName.trim().isEmpty()) {
            baseMessage = actorName + " đã đăng một bài viết mới";
        } else {
            baseMessage = "Có bài viết mới trong nhóm";
        }

        if (postSnippet != null && !postSnippet.trim().isEmpty()) {
            baseMessage = baseMessage + ": \"" + postSnippet + "\"";
        }

        for (String memberId : memberIds) {
            if (actorId != null && actorId.equals(memberId)) {
                continue; // Không tạo thông báo cho chính mình
            }

            String noticeId = UUID.randomUUID().toString();
            Notice notice = new Notice(
                    noticeId,
                    "new_post",
                    actorId,
                    actorName,
                    "post",
                    postId,
                    groupId,
                    title,
                    baseMessage,
                    memberId
            );

            DocumentReference noticeRef = db.collection("users")
                    .document(memberId)
                    .collection("notices")
                    .document(noticeId);

            batch.set(noticeRef, notice.toMap());
        }

        return batch.commit();
    }

    /**
     * Đếm số thông báo chưa xem
     */
    public Task<Integer> getUnreadCount(String uid) {
        return db.collection("users")
                .document(uid)
                .collection("notices")
                .whereEqualTo("seen", false)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return task.getResult().size();
                    } else {
                        Log.e(TAG, "Error getting unread count", task.getException());
                        return 0;
                    }
                });
    }

    /**
     * Lấy danh sách ID các thông báo chưa xem
     */
    public Task<List<String>> getUnseenNoticeIds(String uid) {
        return db.collection("users")
                .document(uid)
                .collection("notices")
                .whereEqualTo("seen", false)
                .get()
                .continueWith(task -> {
                    List<String> ids = new ArrayList<>();
                    if (task.isSuccessful()) {
                        for (DocumentSnapshot d : task.getResult().getDocuments()) {
                            ids.add(d.getId());
                        }
                    } else {
                        Log.e(TAG, "Error fetching unseen notice IDs", task.getException());
                    }
                    return ids;
                });
    }

    /**
     * Tạo thông báo khi có người like bài viết
     */
    public Task<Void> createPostLiked(String groupId, String postId, String actorId, String actorName, String targetUid) {
        if (actorId.equals(targetUid)) {
            return Tasks.forResult(null); // Không tạo thông báo cho chính mình
        }

        String noticeId = UUID.randomUUID().toString();
        Notice notice = new Notice(
                noticeId,
                "like",
                actorId,
                actorName,
                "post",
                postId,
                groupId,
                "Bài viết được thích",
                actorName + " đã thích bài viết của bạn",
                targetUid
        );

        return db.collection("users")
                .document(targetUid)
                .collection("notices")
                .document(noticeId)
                .set(notice.toMap());
    }

    /**
     * Tạo thông báo khi có người comment bài viết
     */
    public Task<Void> createPostCommented(String groupId, String postId, String commentId, String actorId, String actorName, String targetUid) {
        Log.d(TAG, "=== START createPostCommented ===");
        Log.d(TAG, "groupId: " + groupId);
        Log.d(TAG, "postId: " + postId);
        Log.d(TAG, "commentId: " + commentId);
        Log.d(TAG, "actorId: " + actorId);
        Log.d(TAG, "actorName: " + actorName);
        Log.d(TAG, "targetUid: " + targetUid);
        
        if (actorId.equals(targetUid)) {
            Log.d(TAG, "SKIP: Actor and target are the same user");
            return Tasks.forResult(null); // Không tạo thông báo cho chính mình
        }

        String noticeId = UUID.randomUUID().toString();
        Log.d(TAG, "Generated noticeId: " + noticeId);
        
        Notice notice = new Notice(
                noticeId,
                "comment",
                actorId,
                actorName,
                "post",
                postId,
                groupId,
                "Bài viết có bình luận mới",
                actorName + " đã bình luận bài viết của bạn",
                targetUid
        );
        
        Log.d(TAG, "Created notice object:");
        Log.d(TAG, "  - id: " + notice.getId());
        Log.d(TAG, "  - type: " + notice.getType());
        Log.d(TAG, "  - title: " + notice.getTitle());
        Log.d(TAG, "  - message: " + notice.getMessage());
        Log.d(TAG, "  - targetUserId: " + notice.getTargetUserId());

        Log.d(TAG, "Saving notice to Firestore...");
        return db.collection("users")
                .document(targetUid)
                .collection("notices")
                .document(noticeId)
                .set(notice.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Notice saved successfully to Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save notice to Firestore", e);
                });
    }

    /**
     * Tạo thông báo khi bài viết được duyệt
     */
    public Task<Void> createPostApproved(String groupId, String postId, String actorId, String actorName, String targetUid) {
        String noticeId = UUID.randomUUID().toString();
        Notice notice = new Notice(
                noticeId,
                "post_approved",
                actorId,
                actorName,
                "post",
                postId,
                groupId,
                "Bài viết đã được duyệt",
                "Bài viết của bạn đã được " + actorName + " duyệt",
                targetUid
        );

        return db.collection("users")
                .document(targetUid)
                .collection("notices")
                .document(noticeId)
                .set(notice.toMap());
    }

    /**
     * Tạo thông báo tin nhắn mới
     */
    public Task<Void> createMessageNotice(String chatId, String chatType, String groupId, String actorId, String actorName, List<String> targetUids, String previewText) {
        if (targetUids == null || targetUids.isEmpty()) {
            return Tasks.forResult(null);
        }

        WriteBatch batch = db.batch();
        String title = "group".equals(chatType) ? "Tin nhắn nhóm mới" : "Tin nhắn mới";
        String message = actorName + " đã gửi tin nhắn";

        for (String targetUid : targetUids) {
            if (actorId.equals(targetUid)) {
                continue; // Không tạo thông báo cho chính mình
            }

            String noticeId = UUID.randomUUID().toString();
            Notice notice = new Notice(
                    noticeId,
                    "message",
                    actorId,
                    actorName,
                    "chat",
                    chatId,
                    groupId,
                    title,
                    message,
                    targetUid
            );

            DocumentReference noticeRef = db.collection("users")
                    .document(targetUid)
                    .collection("notices")
                    .document(noticeId);

            batch.set(noticeRef, notice.toMap());
        }

        return batch.commit();
    }

    /**
     * Tạo thông báo khi có event mới trong group
     */
    public Task<Void> createGroupEventNotice(String groupId, String eventId, String actorId, String actorName, List<String> memberIds) {
        return createGroupEventNotice(groupId, eventId, actorId, actorName, memberIds, null);
    }

    /**
     * Tạo thông báo khi có event mới trong group (với tên event)
     */
    public Task<Void> createGroupEventNotice(String groupId, String eventId, String actorId, String actorName, List<String> memberIds, String eventTitle) {
        android.util.Log.d("NoticeRepository", "createGroupEventNotice called with groupId: " + groupId + ", eventId: " + eventId + ", actorId: " + actorId + ", actorName: " + actorName + ", memberIds: " + (memberIds != null ? memberIds.size() : "null"));
        
        if (memberIds == null || memberIds.isEmpty()) {
            android.util.Log.w("NoticeRepository", "memberIds is null or empty, returning early");
            return Tasks.forResult(null);
        }

        WriteBatch batch = db.batch();
        String title = "Sự kiện mới";
        String message;
        if (eventTitle != null && !eventTitle.trim().isEmpty()) {
            message = actorName + " đã tạo sự kiện \"" + eventTitle + "\" trong nhóm";
        } else {
            message = actorName + " đã tạo sự kiện mới trong nhóm";
        }
        
        int noticeCount = 0;
        for (String memberId : memberIds) {
            if (actorId.equals(memberId)) {
                android.util.Log.d("NoticeRepository", "Skipping notice for actor (self): " + memberId);
                continue; // Không tạo thông báo cho chính mình
            }

            String noticeId = UUID.randomUUID().toString();
            Notice notice = new Notice(
                    noticeId,
                    "event_created",
                    actorId,
                    actorName,
                    "event",
                    eventId,
                    groupId,
                    title,
                    message,
                    memberId
            );
            notice.setEventId(eventId);

            DocumentReference noticeRef = db.collection("users")
                    .document(memberId)
                    .collection("notices")
                    .document(noticeId);

            batch.set(noticeRef, notice.toMap());
            noticeCount++;
            android.util.Log.d("NoticeRepository", "Added notice to batch for member: " + memberId + ", noticeId: " + noticeId);
        }

        final int finalNoticeCount = noticeCount;
        android.util.Log.d("NoticeRepository", "Committing batch with " + noticeCount + " notices");
        return batch.commit().addOnSuccessListener(aVoid -> {
            android.util.Log.d("NoticeRepository", "Successfully committed " + finalNoticeCount + " event notices to Firestore");
        }).addOnFailureListener(e -> {
            android.util.Log.e("NoticeRepository", "Failed to commit event notices batch", e);
        });
    }

    /**
     * Tạo thông báo khi gửi lời mời kết bạn
     */
    public Task<Void> createFriendRequestNotice(String requesterId, String requesterName, String targetUserId) {
        if (requesterId.equals(targetUserId)) {
            return Tasks.forResult(null); // Không tạo thông báo cho chính mình
        }

        String noticeId = UUID.randomUUID().toString();
        Notice notice = new Notice(
                noticeId,
                "friend_request",
                requesterId,
                requesterName,
                "user",
                targetUserId,
                null,
                "Lời mời kết bạn",
                requesterName + " đã gửi lời mời kết bạn",
                targetUserId
        );

        DocumentReference noticeRef = db.collection("users")
                .document(targetUserId)
                .collection("notices")
                .document(noticeId);

        return noticeRef.set(notice.toMap());
    }

    /**
     * Tạo thông báo khi chấp nhận lời mời kết bạn
     */
    public Task<Void> createFriendAcceptedNotice(String accepterId, String accepterName, String requesterId) {
        if (accepterId.equals(requesterId)) {
            return Tasks.forResult(null); // Không tạo thông báo cho chính mình
        }

        String noticeId = UUID.randomUUID().toString();
        Notice notice = new Notice(
                noticeId,
                "friend_accepted",
                accepterId,
                accepterName,
                "user",
                requesterId,
                null,
                "Đã chấp nhận kết bạn",
                accepterName + " đã chấp nhận lời mời kết bạn",
                requesterId
        );

        DocumentReference noticeRef = db.collection("users")
                .document(requesterId)
                .collection("notices")
                .document(noticeId);

        return noticeRef.set(notice.toMap());
    }

    /**
     * Xóa tất cả thông báo của user
     */
    public Task<Void> deleteAllNotifications(String uid) {
        Log.d(TAG, "Deleting all notifications for user: " + uid);
        
        return db.collection("users")
                .document(uid)
                .collection("notices")
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        return batch.commit();
                    } else {
                        Log.e(TAG, "Error fetching notifications to delete", task.getException());
                        return Tasks.forException(task.getException());
                    }
                })
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return null;
                    } else {
                        throw task.getException();
                    }
                });
    }
}
