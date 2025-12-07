package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentRepository {
    private final FirebaseFirestore db;
    private static final String GROUPS_COLLECTION = "groups";
    private static final String POSTS_COLLECTION = "posts";
    private static final String COMMENTS_COLLECTION = "comments";

    // Callback interfaces
    public interface OnSuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface OnErrorCallback {
        void onError(Exception e);
    }

    public CommentRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Thêm comment mới
     */
    public Task<String> addComment(String groupId, String postId, String content, String parentCommentId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("User not logged in"));
        }

        Map<String, Object> commentData = new HashMap<>();
        commentData.put("authorId", currentUserId);
        commentData.put("content", content);
        commentData.put("replyCount", 0);
        commentData.put("createdAt", FieldValue.serverTimestamp());
        if (parentCommentId != null) {
            commentData.put("parentCommentId", parentCommentId);
        }

        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .add(commentData)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        String commentId = task.getResult().getId();
                        // Update post comment count
                        updatePostCommentCount(groupId, postId);
                        // If this is a reply, update parent comment reply count
                        if (parentCommentId != null) {
                            updateCommentReplyCount(groupId, postId, parentCommentId);
                        }
                        // Comment logging removed as per requirements

                        // Create notice for post author (skip self)
                        createCommentNotice(groupId, postId, commentId, currentUserId);
                        
                        return com.google.android.gms.tasks.Tasks.forResult(commentId);
                    } else {
                        Exception e = task.getException();
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CommentRepository", e);
                        return com.google.android.gms.tasks.Tasks.forException(e);
                    }
                });
    }

    /**
     * Lấy danh sách comments cho một post
     */
    public void getComments(String groupId, String postId, CommentsCallback callback) {
        android.util.Log.d("CommentRepository", "Loading comments for groupId: " + groupId + ", postId: " + postId);

        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CommentRepository", error);
                        callback.onError(error);
                        return;
                    }
                    
                    if (snapshot != null) {
                        List<Comment> comments = new ArrayList<>();
                        android.util.Log.d("CommentRepository", "Found " + snapshot.size() + " documents");

                        if (snapshot.isEmpty()) {
                            callback.onSuccess(comments);
                            return;
                        }

                        // Counter để track khi nào load xong tất cả user info
                        final int[] pendingUserLoads = {0};

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Comment comment = doc.toObject(Comment.class);
                            if (comment != null) {
                                comment.commentId = doc.getId();
                                // Chỉ lấy comments gốc (không có parentCommentId hoặc parentCommentId = null)
                                if (comment.parentCommentId == null || comment.parentCommentId.isEmpty()) {
                                    comments.add(comment);
                                    pendingUserLoads[0]++;
                                    android.util.Log.d("CommentRepository", "Added comment: " + comment.content);
                                }
                            }
                        }

                        if (comments.isEmpty()) {
                            callback.onSuccess(comments);
                            return;
                        }

                        // Load user info cho từng comment
                        for (Comment comment : comments) {
                            loadUserInfo(comment, () -> {
                                pendingUserLoads[0]--;
                                if (pendingUserLoads[0] == 0) {
                                    android.util.Log.d("CommentRepository", "Returning " + comments.size() + " comments with user info");
                                    callback.onSuccess(comments);
                                }
                            });
                        }
                    }
                });
    }

    /**
     * Load user info cho comment
     */
    private void loadUserInfo(Comment comment, Runnable onComplete) {
        if (comment.authorId == null || comment.authorId.isEmpty()) {
            android.util.Log.d("CommentRepository", "Comment has no authorId");
            comment.authorName = "Unknown User";
            comment.authorAvatar = null;
            onComplete.run();
            return;
        }

        android.util.Log.d("CommentRepository", "Loading user info for authorId: " + comment.authorId);
        db.collection("users")
                .document(comment.authorId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        // Use correct field names from User model
                        comment.authorName = userDoc.getString("displayName");
                        comment.authorAvatar = userDoc.getString("photoUrl");

                        android.util.Log.d("CommentRepository", "User loaded: " + comment.authorName + ", avatar: " + comment.authorAvatar);

                        if (comment.authorName == null || comment.authorName.isEmpty()) {
                            comment.authorName = "Unknown User";
                        }
                    } else {
                        android.util.Log.d("CommentRepository", "User not found for authorId: " + comment.authorId);
                        comment.authorName = "Unknown User";
                        comment.authorAvatar = null;
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CommentRepository", "Error loading user info for authorId: " + comment.authorId, e);
                    comment.authorName = "Unknown User";
                    comment.authorAvatar = null;
                    onComplete.run();
                });
    }

    /**
     * Lấy replies cho một comment
     */
    public void getReplies(String groupId, String postId, String parentCommentId, CommentsCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .whereEqualTo("parentCommentId", parentCommentId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Comment> replies = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Comment reply = doc.toObject(Comment.class);
                        if (reply != null) {
                            reply.commentId = doc.getId();
                            replies.add(reply);
                        }
                    }
                    callback.onSuccess(replies);
                })
                .addOnFailureListener(callback::onError);
    }

    private Task<Void> updatePostCommentCount(String groupId, String postId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .update("commentCount", FieldValue.increment(1));
    }

    private Task<Void> updateCommentReplyCount(String groupId, String postId, String commentId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .document(commentId)
                .update("replyCount", FieldValue.increment(1));
    }

    /**
     * Xóa comment và tất cả likes của comment đó
     */
    public Task<Void> deleteComment(String groupId, String postId, String commentId) {
        // First delete all likes for this comment (if any exist)
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .document(commentId)
                .collection("likes")
                .get()
                .continueWithTask(likesTask -> {
                    if (!likesTask.isSuccessful()) {
                        return com.google.android.gms.tasks.Tasks.forException(likesTask.getException());
                    }
                    
                    // Delete all likes in batch
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot likeDoc : likesTask.getResult()) {
                        batch.delete(likeDoc.getReference());
                    }
                    
                    return batch.commit();
                })
                .continueWithTask(likesDeleteTask -> {
                    // Then delete the comment itself
                    return db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .collection(POSTS_COLLECTION)
                            .document(postId)
                            .collection(COMMENTS_COLLECTION)
                            .document(commentId)
                            .delete();
                })
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        // Comment deletion logging removed as per requirements
                        // Giảm comment count của post
                        return db.collection(GROUPS_COLLECTION)
                                .document(groupId)
                                .collection(POSTS_COLLECTION)
                                .document(postId)
                                .update("commentCount", FieldValue.increment(-1));
                    } else {
                        return com.google.android.gms.tasks.Tasks.forException(task.getException());
                    }
                });
    }

    /**
     * Tạo thông báo khi có comment mới
     */
    private void createCommentNotice(String groupId, String postId, String commentId, String actorId) {
        android.util.Log.d("CommentRepository", "=== START createCommentNotice ===");
        android.util.Log.d("CommentRepository", "groupId: " + groupId);
        android.util.Log.d("CommentRepository", "postId: " + postId);
        android.util.Log.d("CommentRepository", "commentId: " + commentId);
        android.util.Log.d("CommentRepository", "actorId: " + actorId);
        
        // Lấy thông tin post để biết tác giả
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .get()
                .addOnSuccessListener(postDoc -> {
                    if (postDoc.exists()) {
                        String postAuthorId = postDoc.getString("authorId");
                        android.util.Log.d("CommentRepository", "Post author ID: " + postAuthorId);
                        
                        // Không tạo notice cho chính mình
                        if (postAuthorId != null && !postAuthorId.equals(actorId)) {
                            // Lấy thông tin user hiện tại
                            UserRepository userRepo = new UserRepository(db);
                            userRepo.getUserById(actorId, new UserRepository.UserCallback() {
                                @Override
                                public void onSuccess(com.example.nanaclu.data.model.User user) {
                                    String actorName = user != null ? user.displayName : "Người dùng";
                                    android.util.Log.d("CommentRepository", "Creating notice for post author: " + postAuthorId + ", actorName: " + actorName);
                                    
                                    // Tạo notice
                                    NoticeRepository noticeRepo = new NoticeRepository(db);
                                    noticeRepo.createPostCommented(groupId, postId, commentId, actorId, actorName, postAuthorId)
                                            .addOnSuccessListener(aVoid -> {
                                                android.util.Log.d("CommentRepository", "✅ Comment notice created successfully!");
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.e("CommentRepository", "❌ Failed to create comment notice", e);
                                            });
                                }
                                
                                @Override
                                public void onError(Exception e) {
                                    android.util.Log.e("CommentRepository", "Failed to get user info, using default name", e);
                                    NoticeRepository noticeRepo = new NoticeRepository(db);
                                    noticeRepo.createPostCommented(groupId, postId, commentId, actorId, "Người dùng", postAuthorId)
                                            .addOnSuccessListener(aVoid -> {
                                                android.util.Log.d("CommentRepository", "✅ Comment notice created successfully with default name!");
                                            })
                                            .addOnFailureListener(err -> {
                                                android.util.Log.e("CommentRepository", "❌ Failed to create comment notice with default name", err);
                                            });
                                }
                            });
                        } else {
                            android.util.Log.d("CommentRepository", "SKIP: User commenting on their own post");
                        }
                    } else {
                        android.util.Log.e("CommentRepository", "Post not found");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CommentRepository", "Failed to get post info", e);
                });
    }

    public interface CommentsCallback {
        void onSuccess(List<Comment> comments);
        void onError(Exception e);
    }
}
