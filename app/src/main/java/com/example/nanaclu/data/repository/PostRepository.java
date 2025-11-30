package com.example.nanaclu.data.repository;

import android.util.Log;

import com.example.nanaclu.data.model.GroupLog;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.model.UserImage;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import androidx.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.graphics.Matrix;
import java.io.ByteArrayOutputStream;
import java.io.File;

import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class PostRepository {
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private static final String POSTS_COLLECTION = "posts";
    private static final String PENDING_POSTS_COLLECTION = "pendingPosts";
    private static final String LIKES_COLLECTION = "likes";
    private static final String COMMENTS_COLLECTION = "comments";
    private static final String IMAGES_COLLECTION = "images";
    private static final String GROUPS_COLLECTION = "groups";

    public PostRepository(FirebaseFirestore db) {
        this.db = db;
        this.storage = FirebaseStorage.getInstance();
    }

    public interface PostCallback {
        void onSuccess(Post post);
        void onError(Exception e);
    }
    
    // Interface callback cho việc lấy danh sách user
    private interface OnUsersFetchedListener {
        void onUsersFetched(List<String> userIds);
    }

    public interface PostsCallback {
        void onSuccess(List<Post> posts);
        void onError(Exception e);
    }

    public interface PagedPostsCallback {
        void onSuccess(List<Post> posts, com.google.firebase.firestore.DocumentSnapshot lastVisible);
        void onError(Exception e);
    }

    /**
     * Tạo một post mới trong group
     * @param post Post object với groupId đã set
     * @param callback Callback để trả về kết quả
     */
    public void createPost(Post post, PostCallback callback) {
        try {
            // Validate required fields
            if (post.groupId == null || post.groupId.isEmpty()) {
                callback.onError(new IllegalArgumentException("GroupId is required"));
                return;
            }

            if (post.authorId == null || post.authorId.isEmpty()) {
                callback.onError(new IllegalArgumentException("AuthorId is required"));
                return;
            }

            // Generate postId if not set
            if (post.postId == null || post.postId.isEmpty()) {
                post.postId = UUID.randomUUID().toString();
            }

            // Set timestamps
            long currentTime = System.currentTimeMillis();
            post.createdAt = currentTime;
            post.likeCount = 0;
            post.commentCount = 0;

            // Create batch write for atomic operation
            WriteBatch batch = db.batch();

            // 1. Save post to groups/{groupId}/posts/{postId}
            DocumentReference postRef = db.collection(GROUPS_COLLECTION)
                    .document(post.groupId)
                    .collection(POSTS_COLLECTION)
                    .document(post.postId);
            batch.set(postRef, post);

            // 2. Images are saved separately via saveImageData method
            // No need to create placeholder UserImage here

            // 3. Update group's post count (optional)
            DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(post.groupId);
            batch.update(groupRef, "postCount", com.google.firebase.firestore.FieldValue.increment(1));

            // Execute batch
            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(post);
                        // Log post creation
                        LogRepository logRepo = new LogRepository(db);
                        String snippet = post.content != null && post.content.length() > 60 
                            ? post.content.substring(0, 60) + "..." : post.content;
                        logRepo.logGroupAction(post.groupId, "post_created", "post", post.postId, snippet, null);
                        
                        // Gửi thông báo cho các thành viên
                        notifyGroupMembersAboutNewPost(post);
                    })
                    .addOnFailureListener(e -> {
                        callback.onError(e);
                    });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Create a pending post under groups/{groupId}/pendingPosts/{postId} */
    public void createPendingPost(Post post, PostCallback callback) {
        try {
            if (post.groupId == null || post.groupId.isEmpty()) {
                callback.onError(new IllegalArgumentException("GroupId is required"));
                return;
            }
            if (post.authorId == null || post.authorId.isEmpty()) {
                callback.onError(new IllegalArgumentException("AuthorId is required"));
                return;
            }

            if (post.postId == null || post.postId.isEmpty()) {
                post.postId = java.util.UUID.randomUUID().toString();
            }

            long currentTime = System.currentTimeMillis();
            post.createdAt = currentTime;
            post.likeCount = 0;
            post.commentCount = 0;

            com.google.firebase.firestore.DocumentReference pendingRef = db.collection(GROUPS_COLLECTION)
                    .document(post.groupId)
                    .collection(PENDING_POSTS_COLLECTION)
                    .document(post.postId);

            pendingRef.set(post)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(post);
                        // Log pending post creation
                        LogRepository logRepo = new LogRepository(db);
                        String snippet = post.content != null && post.content.length() > 60
                                ? post.content.substring(0, 60) + "..." : post.content;
                        logRepo.logGroupAction(post.groupId, "pending_post_created", "post", post.postId, snippet, null);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Get pending posts list */
    public void getPendingPosts(String groupId, PostsCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(PENDING_POSTS_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<Post> posts = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            post.groupId = groupId;
                            posts.add(post);
                        }
                    }
                    callback.onSuccess(posts);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Approve a pending post: move to posts and remove from pending */
    public void approvePost(String groupId, String postId, PostCallback callback) {
        com.google.firebase.firestore.DocumentReference pendingRef = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(PENDING_POSTS_COLLECTION)
                .document(postId);
        pendingRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onError(new Exception("Pending post not found"));
                return;
            }
            Post post = doc.toObject(Post.class);
            if (post == null) {
                callback.onError(new Exception("Invalid post data"));
                return;
            }

            com.google.firebase.firestore.WriteBatch batch = db.batch();
            com.google.firebase.firestore.DocumentReference postRef = db.collection(GROUPS_COLLECTION)
                    .document(groupId)
                    .collection(POSTS_COLLECTION)
                    .document(postId);
            batch.set(postRef, post);
            batch.delete(pendingRef);
            com.google.firebase.firestore.DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
            batch.update(groupRef, "postCount", com.google.firebase.firestore.FieldValue.increment(1));
            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(post);
                        // Log approval
                        LogRepository logRepo = new LogRepository(db);
                        logRepo.logGroupAction(groupId, "post_approved", "post", postId, null, null);
                    })
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    /** Reject a pending post: delete from pending */
    public void rejectPost(String groupId, String postId, PostCallback callback) {
        com.google.firebase.firestore.DocumentReference pendingRef = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(PENDING_POSTS_COLLECTION)
                .document(postId);
        pendingRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onError(new Exception("Pending post not found"));
                return;
            }
            Post post = doc.toObject(Post.class);
            pendingRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(post);
                        // Log rejection
                        LogRepository logRepo = new LogRepository(db);
                        logRepo.logGroupAction(groupId, "post_rejected", "post", postId, null, null);
                    })
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    /**
     * Lấy tất cả posts của một group
     * @param groupId ID của group
     * @param callback Callback để trả về danh sách posts
     */
    public void getGroupPosts(String groupId, PostsCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Post> posts = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            // Ensure groupId is set even if missing in old documents
                            post.groupId = groupId;
                            posts.add(post);
                        }
                    }
                    callback.onSuccess(posts);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e);
                });
    }

    /**
     * Lấy danh sách post theo trang (5 mỗi lần), orderBy createdAt desc
     */
    public void getGroupPostsPaged(String groupId, int pageSize,
                                   @Nullable com.google.firebase.firestore.DocumentSnapshot lastVisible,
                                   PagedPostsCallback callback) {
        com.google.firebase.firestore.Query base = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(pageSize);

        com.google.firebase.firestore.Query query = lastVisible != null ? base.startAfter(lastVisible) : base;

        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Post> posts = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            post.groupId = groupId; // backfill groupId from path
                            posts.add(post);
                        }
                    }
                    com.google.firebase.firestore.DocumentSnapshot newLast =
                            querySnapshot.isEmpty() ? null : querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    callback.onSuccess(posts, newLast);
                })
                .addOnFailureListener(callback::onError);
    }

    // Overload: dùng 2 lambda (onSuccess, onFailure) như cách gọi trong Activity
    public void getGroupPostsPaged(String groupId, int pageSize,
                                   @Nullable com.google.firebase.firestore.DocumentSnapshot lastVisible,
                                   java.util.function.BiConsumer<java.util.List<Post>, com.google.firebase.firestore.DocumentSnapshot> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        com.google.firebase.firestore.Query base = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(pageSize);

        com.google.firebase.firestore.Query query = lastVisible != null ? base.startAfter(lastVisible) : base;

        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<Post> posts = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) posts.add(post);
                    }
                    com.google.firebase.firestore.DocumentSnapshot newLast =
                            querySnapshot.isEmpty() ? null : querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    if (onSuccess != null) onSuccess.accept(posts, newLast);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Upload ảnh lên Firebase Storage và trả về URL
     * @param imageData byte array của ảnh
     * @param fileName tên file ảnh
     * @param chatId ID của chat (để organize theo chat)
     * @param callback callback trả về URL
     */
    public void uploadImageToStorage(byte[] imageData, String fileName, String chatId,
                                   com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        // Sử dụng unified storage structure: /chats/{chatId}/images/
        String storagePath = chatId != null ? 
            "chats/" + chatId + "/images/" + fileName : 
            "images/post_images/" + fileName; // Fallback cho backward compatibility
            
        StorageReference imageRef = storage.getReference().child(storagePath);
        
        UploadTask uploadTask = imageRef.putBytes(imageData);
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            // Upload thành công, lấy download URL
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                onSuccess.onSuccess(uri.toString());
            }).addOnFailureListener(onFailure);
        }).addOnFailureListener(onFailure);
    }

    /**
     * Upload ảnh lên Firebase Storage và trả về URL (backward compatibility)
     * @param imageData byte array của ảnh
     * @param fileName tên file ảnh
     * @param callback callback trả về URL
     */
    public void uploadImageToStorage(byte[] imageData, String fileName, 
                                   com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        // Gọi method mới với chatId = null để sử dụng fallback path
        uploadImageToStorage(imageData, fileName, null, onSuccess, onFailure);
    }

    /**
     * Upload nhiều ảnh lên Firebase Storage (backward compatibility)
     * @param imageDataList danh sách byte array của các ảnh
     * @param callback callback trả về danh sách URLs
     */
    public void uploadMultipleImages(List<byte[]> imageDataList,
                                   com.google.android.gms.tasks.OnSuccessListener<List<String>> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        // Gọi method mới với chatId = null để sử dụng fallback path
        uploadMultipleImages(imageDataList, null, onSuccess, onFailure);
    }

    /**
     * Upload nhiều ảnh lên Firebase Storage
     * @param imageDataList danh sách byte array của các ảnh
     * @param chatId ID của chat (để organize theo chat)
     * @param callback callback trả về danh sách URLs
     */
    public void uploadMultipleImages(List<byte[]> imageDataList, String chatId,
                                   com.google.android.gms.tasks.OnSuccessListener<List<String>> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        if (imageDataList == null || imageDataList.isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }

        List<Task<String>> uploadTasks = new ArrayList<>();
        
        for (int i = 0; i < imageDataList.size(); i++) {
            byte[] imageData = imageDataList.get(i);
            String fileName = "image_" + System.currentTimeMillis() + "_" + i + ".jpg";
            
            // Sử dụng unified storage structure: /chats/{chatId}/images/
            String storagePath = chatId != null ? 
                "chats/" + chatId + "/images/" + fileName : 
                "images/post_images/" + fileName; // Fallback cho backward compatibility
                
            StorageReference imageRef = storage.getReference().child(storagePath);
            
            Task<String> uploadTask = imageRef.putBytes(imageData)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return imageRef.getDownloadUrl();
                    })
                    .continueWith(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return task.getResult().toString();
                    });
            
            uploadTasks.add(uploadTask);
        }
        
        Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(urls -> {
            List<String> urlList = new ArrayList<>();
            for (Object url : urls) {
                urlList.add((String) url);
            }
            onSuccess.onSuccess(urlList);
        }).addOnFailureListener(onFailure);
    }

    /**
     * Lấy base64 ảnh người dùng theo imageId (giữ lại để tương thích với avatar)
     * Ảnh được lưu trong users/{authorId}/images/{imageId}
     */
    public void getUserImageBase64(String authorId, String imageId,
                                   com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        db.collection("users").document(authorId)
                .collection(IMAGES_COLLECTION).document(imageId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists()) {
                        Object value = snap.get("base64Code");
                        onSuccess.onSuccess(value instanceof String ? (String) value : null);
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }
    /**
     * Test Firestore rules với data đơn giản
     */
    public void testFirestoreRules(String authorId, PostCallback callback) {
        System.out.println("PostRepository: Testing Firestore rules for authorId = " + authorId);
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("testField", "testValue");
        testData.put("timestamp", System.currentTimeMillis());
        
        String testPath = "users/" + authorId + "/test/" + UUID.randomUUID().toString();
        System.out.println("PostRepository: Test path = " + testPath);
        
        db.collection("users")
                .document(authorId)
                .collection("test")
                .document(UUID.randomUUID().toString())
                .set(testData)
                .addOnSuccessListener(aVoid -> {
                    System.out.println("PostRepository: Test SUCCESS - Firestore rules OK");
                    Post dummyPost = new Post();
                    dummyPost.postId = "test";
                    callback.onSuccess(dummyPost);
                })
                .addOnFailureListener(e -> {
                    System.out.println("PostRepository: Test FAILED - " + e.getMessage());
                    callback.onError(e);
                });
    }

    /**
     * Xóa một post và tất cả subcollections (comments, likes)
     * @param groupId ID của group
     * @param postId ID của post
     * @param callback Callback để trả về kết quả
     */
    /**
     * Gửi thông báo khi có bài viết mới tới tất cả thành viên nhóm và người rời nhóm trong 7 ngày
     */
    private void notifyGroupMembersAboutNewPost(Post post) {
        // Lấy danh sách người cần thông báo (thành viên hiện tại + đã rời trong 7 ngày)
        getUsersToNotifyForNewPost(post.groupId, post.authorId, userList -> {
            if (userList == null || userList.isEmpty()) {
                return;
            }

            // Chuẩn bị snippet nội dung bài viết để hiển thị trong thông báo
            String content = post.content != null ? post.content.trim() : "";
            String snippet = content.length() > 60 ? content.substring(0, 60) + "..." : content;

            // Tạo Notice cho từng user trong users/{uid}/notices
            NoticeRepository noticeRepo = new NoticeRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
            noticeRepo.createGroupPostNotice(
                    post.groupId,
                    post.postId,
                    post.authorId,
                    null,           // actorName sẽ được fill sau nếu cần, hiện tại để null
                    userList,
                    snippet
            );
        });
    }

    /**
     * Lấy danh sách user cần thông báo khi có bài viết mới
     * - Thành viên hiện tại (trừ tác giả)
     * - Thành viên đã rời nhóm trong vòng 7 ngày
     */
    private void getUsersToNotifyForNewPost(String groupId, String authorId, OnUsersFetchedListener listener) {
        Set<String> userIds = new HashSet<>();
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
        
        // Lấy danh sách thành viên hiện tại
        db.collection("groups")
          .document(groupId)
          .collection("members")
          .get()
          .addOnSuccessListener(memberDocs -> {
              for (QueryDocumentSnapshot doc : memberDocs) {
                  String memberId = doc.getId();
                  if (!memberId.equals(authorId)) {
                      userIds.add(memberId);
                  }
              }
              
              // Lấy danh sách thành viên đã rời trong 7 ngày
              db.collection("groups")
                .document(groupId)
                .collection("left_members")
                .whereGreaterThanOrEqualTo("leftAt", new Timestamp(new Date(sevenDaysAgo)))
                .get()
                .addOnSuccessListener(leftMemberDocs -> {
                    for (QueryDocumentSnapshot doc : leftMemberDocs) {
                        userIds.add(doc.getId());
                    }
                    listener.onUsersFetched(new ArrayList<>(userIds));
                })
                .addOnFailureListener(e -> {
                    Log.e("PostRepository", "Lỗi khi lấy danh sách thành viên đã rời", e);
                    listener.onUsersFetched(new ArrayList<>(userIds)); // Vẫn trả về danh sách đã có nếu lỗi
                });
          })
          .addOnFailureListener(e -> {
              Log.e("PostRepository", "Lỗi khi lấy danh sách thành viên", e);
              listener.onUsersFetched(new ArrayList<>());
          });
    }

    /**
     * Tạo thông báo bài viết mới cho 1 người dùng
     * Kiểm tra trùng lặp bằng notificationId
     */
    private void createPostNotification(Post post, String targetUserId) {
        // Tạo ID thông báo duy nhất cho mỗi cặp (bài viết + người nhận)
        String notificationId = "post_" + post.postId + "_" + targetUserId;
        
        // Kiểm tra xem thông báo đã tồn tại chưa
        db.collection("notifications")
          .document(notificationId)
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (!documentSnapshot.exists()) {
                  // Tạo thông báo mới nếu chưa tồn tại
                  Map<String, Object> notification = new HashMap<>();
                  notification.put("type", "new_post");
                  notification.put("postId", post.postId);
                  notification.put("groupId", post.groupId);
                  notification.put("authorId", post.authorId);
                  notification.put("targetUserId", targetUserId);
                  notification.put("createdAt", FieldValue.serverTimestamp());
                  notification.put("read", false);
                  
                  // Lưu thông báo
                  db.collection("notifications")
                    .document(notificationId)
                    .set(notification)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("PostRepository", "Đã gửi thông báo cho user: " + targetUserId);
                        // Gửi push notification
                        sendPushNotification(post, targetUserId);
                    });
              }
          });
    }

    /**
     * Gửi push notification qua FCM
     */
    private void sendPushNotification(Post post, String targetUserId) {
        // TODO: Implement FCM push notification
        // Lấy FCM token của targetUser và gửi thông báo
        Log.d("PostRepository", "Gửi push notification cho user: " + targetUserId);
    }

/**
 * Thêm user vào danh sách left_members khi rời nhóm
 * @param groupId ID của nhóm
 * @param userId ID của user
 * @param shouldLog Có nên log hành động này không (tránh duplicate log)
 * @param onSuccess Callback khi thêm thành công
 */
public void addUserToLeftMembers(String groupId, String userId, boolean shouldLog, Runnable onSuccess) {
    Map<String, Object> leftMemberData = new HashMap<>();
    leftMemberData.put("leftAt", FieldValue.serverTimestamp());
    
    db.collection("groups")
      .document(groupId)
      .collection("left_members")
      .document(userId)
      .set(leftMemberData)
      .addOnSuccessListener(aVoid -> {
          Log.d("PostRepository", "Đã thêm vào danh sách left_members");
          if (shouldLog) {
              LogRepository logRepo = new LogRepository(db);
              logRepo.logGroupAction(groupId, GroupLog.TYPE_MEMBER_REMOVED, "user", userId, "Đã rời nhóm", null);
          }
          if (onSuccess != null) {
              onSuccess.run();
          }
      })
      .addOnFailureListener(e -> 
          Log.e("PostRepository", "Lỗi khi thêm vào left_members", e));
}

/**
 * Thêm user vào danh sách left_members (tương thích ngược)
 */
public void addUserToLeftMembers(String groupId, String userId) {
    addUserToLeftMembers(groupId, userId, true, null);
}

/**
 * Xóa user khỏi danh sách left_members khi tham gia lại nhóm
 * @param groupId ID của nhóm
 * @param userId ID của user
 */
public void removeUserFromLeftMembers(String groupId, String userId) {
    db.collection("groups")
      .document(groupId)
      .collection("left_members")
      .document(userId)
      .delete()
      .addOnSuccessListener(aVoid -> {
          Log.d("PostRepository", "Đã xóa khỏi danh sách left_members");
          // Log khi user tham gia lại nhóm
          LogRepository logRepo = new LogRepository(db);
          logRepo.logGroupAction(groupId, GroupLog.TYPE_MEMBER_JOINED, "user", userId, "Đã tham gia lại nhóm", null);
      })
      .addOnFailureListener(e -> 
          Log.e("PostRepository", "Lỗi khi xóa khỏi left_members", e));
    }

    public void deletePost(String groupId, String postId, PostCallback callback) {
        // Validate input parameters
        if (groupId == null || groupId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Group ID cannot be null or empty"));
            return;
        }
        if (postId == null || postId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Post ID cannot be null or empty"));
            return;
        }
        
        // First delete all comments
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection("comments")
                .get()
                .continueWithTask(commentsTask -> {
                    if (!commentsTask.isSuccessful()) {
                        return com.google.android.gms.tasks.Tasks.forException(commentsTask.getException());
                    }
                    
                    // Delete all comments in batch
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot commentDoc : commentsTask.getResult()) {
                        batch.delete(commentDoc.getReference());
                    }
                    
                    return batch.commit();
                })
                .continueWithTask(commentsDeleteTask -> {
                    // Then delete all likes
                    return db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .collection(POSTS_COLLECTION)
                            .document(postId)
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
                            });
                })
                .continueWithTask(likesDeleteTask -> {
                    // Finally delete the post itself
                    return db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .collection(POSTS_COLLECTION)
                            .document(postId)
                            .delete();
                })
                .addOnSuccessListener(aVoid -> {
                    Post deletedPost = new Post();
                    deletedPost.postId = postId;
                    callback.onSuccess(deletedPost);
                    // Log post deletion
                    LogRepository logRepo = new LogRepository(db);
                    logRepo.logGroupAction(groupId, "post_deleted", "post", postId, null, null);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e);
                });
    }

    /**
     * Xóa nhiều ảnh trong users/{authorId}/images theo danh sách imageIds
     */
    public void deleteUserImages(String authorId, java.util.List<String> imageIds,
                                 com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
                                 com.google.android.gms.tasks.OnFailureListener onFailure) {
        if (imageIds == null || imageIds.isEmpty()) { onSuccess.onSuccess(null); return; }
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        for (String id : imageIds) {
            DocumentReference ref = db.collection("users").document(authorId)
                    .collection(IMAGES_COLLECTION).document(id);
            batch.delete(ref);
        }
        batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
    }

    // ---------- Likes API ----------
    public void likePost(String groupId, String postId, String userId,
                         com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
                         com.google.android.gms.tasks.OnFailureListener onFailure) {
        com.google.firebase.firestore.DocumentReference likeRef = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(LIKES_COLLECTION)
                .document(userId);

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        batch.set(likeRef, new java.util.HashMap<>() {{ put("createdAt", System.currentTimeMillis()); }});
        // tăng likeCount trên post
        com.google.firebase.firestore.DocumentReference postRef = db.collection(GROUPS_COLLECTION)
                .document(groupId).collection(POSTS_COLLECTION).document(postId);
        batch.update(postRef, "likeCount", com.google.firebase.firestore.FieldValue.increment(1));
        batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
    }

    public void unlikePost(String groupId, String postId, String userId,
                           com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
                           com.google.android.gms.tasks.OnFailureListener onFailure) {
        com.google.firebase.firestore.DocumentReference likeRef = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(LIKES_COLLECTION)
                .document(userId);

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        batch.delete(likeRef);
        // giảm likeCount trên post nhưng không để âm
        com.google.firebase.firestore.DocumentReference postRef = db.collection(GROUPS_COLLECTION)
                .document(groupId).collection(POSTS_COLLECTION).document(postId);
        batch.update(postRef, "likeCount", com.google.firebase.firestore.FieldValue.increment(-1));
        batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
    }

    public void isPostLiked(String groupId, String postId, String userId,
                            com.google.android.gms.tasks.OnSuccessListener<Boolean> onSuccess,
                            com.google.android.gms.tasks.OnFailureListener onFailure) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .document(postId)
                .collection(LIKES_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(snap -> onSuccess.onSuccess(snap.exists()))
                .addOnFailureListener(onFailure);
    }

    // ---------- Video Upload API ----------
    
    /**
     * Upload video to Firebase Storage
     * @param videoUri URI of the video file
     * @param groupId Group ID for organizing storage
     * @param postId Post ID for organizing storage
     * @param progressListener Progress listener for upload tracking
     * @param onSuccess Success callback with video URL
     * @param onFailure Failure callback
     */
    public void uploadVideoToStorage(Uri videoUri, String groupId, String postId,
                                   com.google.firebase.storage.OnProgressListener<UploadTask.TaskSnapshot> progressListener,
                                   com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        String storagePath = "videos/group_posts/" + groupId + "/" + postId + "/video.mp4";
        StorageReference videoRef = storage.getReference().child(storagePath);
        
        UploadTask uploadTask = videoRef.putFile(videoUri);
        
        if (progressListener != null) {
            uploadTask.addOnProgressListener(progressListener);
        }
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            videoRef.getDownloadUrl().addOnSuccessListener(uri -> {
                onSuccess.onSuccess(uri.toString());
            }).addOnFailureListener(onFailure);
        }).addOnFailureListener(onFailure);
    }

    /**
     * Upload video thumbnail to Firebase Storage
     * @param jpegBytes JPEG bytes of the thumbnail
     * @param groupId Group ID for organizing storage
     * @param postId Post ID for organizing storage
     * @param onSuccess Success callback with thumbnail URL
     * @param onFailure Failure callback
     */
    public void uploadVideoThumbnail(byte[] jpegBytes, String groupId, String postId,
                                   com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        String storagePath = "videos/group_posts/" + groupId + "/" + postId + "/thumb.jpg";
        StorageReference thumbRef = storage.getReference().child(storagePath);
        
        UploadTask uploadTask = thumbRef.putBytes(jpegBytes);
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            thumbRef.getDownloadUrl().addOnSuccessListener(uri -> {
                onSuccess.onSuccess(uri.toString());
            }).addOnFailureListener(onFailure);
        }).addOnFailureListener(onFailure);
    }

    /**
     * Generate video thumbnail using MediaMetadataRetriever
     * @param context Android context
     * @param videoUri URI of the video file
     * @return Bitmap thumbnail or null if failed
     */
    public static Bitmap generateVideoThumbnail(Context context, Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, videoUri);
            
            // Try to get frame at 1 second, fallback to first frame
            Bitmap thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (thumbnail == null) {
                thumbnail = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            
            // Resize thumbnail to max width 640px to reduce file size
            if (thumbnail != null) {
                int maxWidth = 640;
                int width = thumbnail.getWidth();
                int height = thumbnail.getHeight();
                
                if (width > maxWidth) {
                    float ratio = (float) height / width;
                    int newHeight = (int) (maxWidth * ratio);
                    thumbnail = Bitmap.createScaledBitmap(thumbnail, maxWidth, newHeight, true);
                }
            }
            
            return thumbnail;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get video metadata using MediaMetadataRetriever
     * @param context Android context
     * @param videoUri URI of the video file
     * @return VideoMetadata object with duration, width, height, fileSize
     */
    public static VideoMetadata getVideoMetadata(Context context, Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, videoUri);
            
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            
            long duration = durationStr != null ? Long.parseLong(durationStr) : 0;
            int width = widthStr != null ? Integer.parseInt(widthStr) : 0;
            int height = heightStr != null ? Integer.parseInt(heightStr) : 0;
            
            // Get file size
            long fileSize = 0;
            try {
                File file = new File(videoUri.getPath());
                if (file.exists()) {
                    fileSize = file.length();
                }
            } catch (Exception e) {
                // File size might not be available for content:// URIs
            }
            
            return new VideoMetadata(duration, width, height, fileSize);
        } catch (Exception e) {
            e.printStackTrace();
            return new VideoMetadata(0, 0, 0, 0);
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Compress bitmap to JPEG bytes
     * @param bitmap Bitmap to compress
     * @return JPEG bytes
     */
    public static byte[] compressBitmapToJpeg(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return baos.toByteArray();
    }

    /**
     * Video metadata class
     */
    public static class VideoMetadata {
        public long duration;
        public int width;
        public int height;
        public long fileSize;

        public VideoMetadata(long duration, int width, int height, long fileSize) {
            this.duration = duration;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
        }
    }

    /**
     * Tìm kiếm posts theo nội dung với pagination
     * @param groupId ID của group
     * @param query Từ khóa tìm kiếm
     * @param pageSize Số posts mỗi page
     * @param lastVisible Document cuối cùng của page trước
     * @param callback Callback trả về kết quả
     */
    public void searchPostsByContentPaged(String groupId, String query, int pageSize,
                                         @Nullable com.google.firebase.firestore.DocumentSnapshot lastVisible,
                                         PagedPostsCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Search query cannot be empty"));
            return;
        }

        // Load batch lớn để filter ở client-side (vì Firestore không hỗ trợ text search)
        int batchSize = Math.max(pageSize * 10, 100); // Load ít nhất 100 posts để filter
        
        com.google.firebase.firestore.Query base = db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(POSTS_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(batchSize);

        com.google.firebase.firestore.Query queryObj = lastVisible != null ? base.startAfter(lastVisible) : base;

        queryObj.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Post> allPosts = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            post.groupId = groupId;
                            allPosts.add(post);
                        }
                    }

                    // Filter posts by content (case-insensitive)
                    String lowerQuery = query.toLowerCase().trim();
                    List<Post> filteredPosts = new ArrayList<>();
                    for (Post post : allPosts) {
                        if (post.content != null && post.content.toLowerCase().contains(lowerQuery)) {
                            filteredPosts.add(post);
                            if (filteredPosts.size() >= pageSize) {
                                break; // Đủ số posts cần thiết
                            }
                        }
                    }

                    // Tìm lastVisible cho pagination tiếp theo
                    com.google.firebase.firestore.DocumentSnapshot newLastVisible = null;
                    if (!querySnapshot.isEmpty() && filteredPosts.size() == pageSize) {
                        // Nếu có đủ kết quả, dùng document cuối cùng của batch
                        newLastVisible = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    }

                    callback.onSuccess(filteredPosts, newLastVisible);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Tìm kiếm posts theo tác giả với pagination
     * @param groupId ID của group
     * @param authorName Tên tác giả
     * @param pageSize Số posts mỗi page
     * @param lastVisible Document cuối cùng của page trước
     * @param callback Callback trả về kết quả
     */
    public void searchPostsByAuthorPaged(String groupId, String authorName, int pageSize,
                                        @Nullable com.google.firebase.firestore.DocumentSnapshot lastVisible,
                                        PagedPostsCallback callback) {
        if (authorName == null || authorName.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Author name cannot be empty"));
            return;
        }

        // Bước 1: Tìm users có displayName chứa authorName
        String lowerAuthorName = authorName.toLowerCase().trim();
        db.collection("users")
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    List<String> matchingUserIds = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot userDoc : userSnapshot.getDocuments()) {
                        String displayName = userDoc.getString("displayName");
                        if (displayName != null && displayName.toLowerCase().contains(lowerAuthorName)) {
                            matchingUserIds.add(userDoc.getId());
                        }
                    }

                    if (matchingUserIds.isEmpty()) {
                        // Không tìm thấy user nào
                        callback.onSuccess(new ArrayList<>(), null);
                        return;
                    }

                    // Bước 2: Tìm posts của các users này
                    int batchSize = Math.max(pageSize * 10, 100);
                    com.google.firebase.firestore.Query base = db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .collection(POSTS_COLLECTION)
                            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(batchSize);

                    com.google.firebase.firestore.Query queryObj = lastVisible != null ? base.startAfter(lastVisible) : base;

                    queryObj.get()
                            .addOnSuccessListener(postSnapshot -> {
                                List<Post> allPosts = new ArrayList<>();
                                for (com.google.firebase.firestore.DocumentSnapshot doc : postSnapshot.getDocuments()) {
                                    Post post = doc.toObject(Post.class);
                                    if (post != null) {
                                        post.groupId = groupId;
                                        allPosts.add(post);
                                    }
                                }

                                // Filter posts by authorId
                                List<Post> filteredPosts = new ArrayList<>();
                                for (Post post : allPosts) {
                                    if (post.authorId != null && matchingUserIds.contains(post.authorId)) {
                                        filteredPosts.add(post);
                                        if (filteredPosts.size() >= pageSize) {
                                            break;
                                        }
                                    }
                                }

                                // Tìm lastVisible cho pagination tiếp theo
                                com.google.firebase.firestore.DocumentSnapshot newLastVisible = null;
                                if (!postSnapshot.isEmpty() && filteredPosts.size() == pageSize) {
                                    newLastVisible = postSnapshot.getDocuments().get(postSnapshot.size() - 1);
                                }

                                callback.onSuccess(filteredPosts, newLastVisible);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }
}
