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
                        // Log comment addition
                        LogRepository logRepo = new LogRepository(db);
                        String snippet = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                        logRepo.logGroupAction(groupId, "comment_added", "comment", commentId, snippet, null);
                        return com.google.android.gms.tasks.Tasks.forResult(commentId);
                    } else {
                        return com.google.android.gms.tasks.Tasks.forException(task.getException());
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
                        // Log comment deletion
                        LogRepository logRepo = new LogRepository(db);
                        logRepo.logGroupAction(groupId, "comment_deleted", "comment", commentId, null, null);
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

    public interface CommentsCallback {
        void onSuccess(List<Comment> comments);
        void onError(Exception e);
    }
}
