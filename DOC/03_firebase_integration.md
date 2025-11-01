# Firebase Integration

## 1. Services được sử dụng
- [x] Authentication (Google Sign-In, Email/Password)
- [x] Firestore Database (NoSQL document database)
- [x] Storage (Image và file storage)
## 2. Cấu trúc Database

### 2.1 Firestore Collections
```
/ users/{userId }
├── userId: string
├── createdAt: timestamp
├── email: string
├── displayName: string
├── photoUrl: string (Google profile photo)
├── avatarImageId: string (custom avatar reference)
├── lastLoginAt: timestamp
├── status: string ("online" | "offline")
└── /images/{imageId} (subcollection)
    ├── imageId: string
    ├── createdAt: timestamp
    └── storageUrl: string

/ groups/{groupId }
├── groupId: string
├── name: string
├── description: string
├── code: string (join code)
├── createdAt: timestamp
├── createdBy: string (userId)
├── memberCount: number
├── avatarUrl: string
├── coverUrl: string
├── isPrivate: boolean
├── /members/{userId} (subcollection)
│   ├── userId: string
│   ├── role: string ("owner" | "admin" | "member")
│   ├── joinedAt: timestamp
│   └── status: string ("active" | "banned")
├── /posts/{postId} (subcollection)
│   ├── postId: string
│   ├── content: string
│   ├── imageUrls: array<string>
│   ├── createdAt: timestamp
│   ├── authorId: string
│   ├── authorName: string
│   ├── likeCount: number
│   ├── commentCount: number
│   ├── /comments/{commentId}
│   │   ├── commentId: string
│   │   ├── content: string
│   │   ├── authorId: string
│   │   ├── authorName: string
│   │   └── createdAt: timestamp
│   └── /likes/{userId}
│       ├── userId: string
│       └── createdAt: timestamp
└── /events/{eventId} (subcollection)
    ├── eventId: string
    ├── title: string
    ├── description: string
    ├── startTime: timestamp
    ├── endTime: timestamp
    ├── location: string
    ├── createdBy: string
    ├── status: string ("scheduled" | "canceled" | "ended")
    └── /rsvps/{userId}
        ├── userId: string
        ├── attendanceStatus: string ("attending" | "maybe" | "not_attending")
        ├── createdAt: timestamp
        └── updatedAt: timestamp

/ chats/{chatId }
├── chatId: string
├── type: string ("private" | "group")
├── memberIds: array<string>
├── groupId: string (if group chat)
├── lastMessage: string
├── lastMessageAt: timestamp
├── lastMessageBy: string
├── /members/{userId} (subcollection)
│   ├── userId: string
│   ├── role: string ("admin" | "member")
│   ├── lastRead: timestamp
│   └── joinedAt: timestamp
└── /messages/{messageId} (subcollection)
    │    ├── messageId: string
    │    ├── senderId: string
    │    ├── senderName: string
    │    ├── content: string
    │    ├── type: string ("text" | "image" | "file")
    │    ├── imageUrls: array<string>
    │    ├── fileUrls: array<string>
    │    ├── createdAt: timestamp
    │    ├── editedAt: timestamp
    │    └── isDeleted: boolean

### 2.2 Firebase Storage Structure
```
/ images /
├── /posts/
│   └── post_{timestamp}_{hash}.jpg
├── /group_images/
│   ├── /avatar/
│   │   └── img_{timestamp}.jpg
│   └── /cover/
│       └── img_{timestamp}.jpg
├── /user_images/
│   └── user_{userId}_{timestamp}.jpg
└── /chat_images/
    └── /{chatId}/
        └── {timestamp}_{hash}.jpg

/ files /
└── /chat_files/
    └── /{chatId}/
        └── {timestamp}_{filename}
```

## 3. Authentication Flow

### 3.1 Email/Password Authentication
```java
// AuthRepository.java - Email registration
public Task<AuthResult> registerWithEmail(String email, String password, String displayName) {
    return auth.createUserWithEmailAndPassword(email, password)
            .continueWithTask(task -> {
                if (!task.isSuccessful()) throw task.getException();
                FirebaseUser fUser = auth.getCurrentUser();
                
                // Create user document in Firestore
                Map<String, Object> userDoc = new HashMap<>();
                userDoc.put("userId", fUser.getUid());
                userDoc.put("email", email);
                userDoc.put("displayName", displayName);
                userDoc.put("createdAt", System.currentTimeMillis());
                userDoc.put("lastLoginAt", System.currentTimeMillis());
                userDoc.put("status", "online");
                
                return db.collection("users").document(fUser.getUid()).set(userDoc);
            });
}

// Email login
public Task<AuthResult> loginWithEmail(String email, String password) {
    return auth.signInWithEmailAndPassword(email, password)
            .continueWithTask(task -> {
                FirebaseUser fUser = auth.getCurrentUser();
                return db.collection("users").document(fUser.getUid())
                        .update("lastLoginAt", System.currentTimeMillis(), "status", "online");
            });
}
```

### 3.2 Google Sign-In Authentication
```java
// AuthRepository.java - Google authentication
public Task<AuthResult> loginWithGoogleIdToken(String idToken) {
    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
    return auth.signInWithCredential(credential)
            .continueWithTask(task -> {
                FirebaseUser fUser = auth.getCurrentUser();
                
                // Merge user data (create if not exists)
                Map<String, Object> userDoc = new HashMap<>();
                userDoc.put("userId", fUser.getUid());
                userDoc.put("email", fUser.getEmail());
                userDoc.put("displayName", fUser.getDisplayName());
                userDoc.put("photoUrl", fUser.getPhotoUrl().toString());
                userDoc.put("lastLoginAt", System.currentTimeMillis());
                userDoc.put("status", "online");
                
                return db.collection("users").document(fUser.getUid())
                        .set(userDoc, SetOptions.merge());
            });
}
```

## 4. Data Operations

### 4.1 Create Operations
```java
// PostRepository.java - Create post with images
public void createPost(Post post, PostCallback callback) {
    String postId = db.collection("groups").document(post.groupId)
            .collection("posts").document().getId();
    post.postId = postId;
    
    db.collection("groups").document(post.groupId)
            .collection("posts").document(postId)
            .set(post)
            .addOnSuccessListener(v -> callback.onSuccess(post))
            .addOnFailureListener(callback::onError);
}

// GroupRepository.java - Create group
public Task<String> createGroup(Group group) {
    String groupId = db.collection("groups").document().getId();
    group.groupId = groupId;
    
    return db.collection("groups").document(groupId).set(group)
            .continueWith(task -> groupId);
}
```

### 4.2 Read Operations
```java
// ChatRepository.java - Read with pagination
public Task<List<Chat>> listUserChats(String userId, int limit, Chat lastItem) {
    Query query = db.collection("chats")
            .whereArrayContains("memberIds", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(limit);
            
    if (lastItem != null) {
        query = query.startAfter(lastItem.lastMessageAt);
    }
    
    return query.get().continueWith(task -> {
        List<Chat> chats = new ArrayList<>();
        for (DocumentSnapshot doc : task.getResult()) {
            Chat chat = doc.toObject(Chat.class);
            if (chat != null) chats.add(chat);
        }
        return chats;
    });
}

// Real-time read with listeners
public ListenerRegistration listenMessages(String chatId, EventListener<QuerySnapshot> listener) {
    return db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener(listener);
}
```

### 4.3 Update Operations
```java
// UserRepository.java - Update user profile
public Task<Void> updateUserProfile(String userId, String displayName, String avatarImageId) {
    Map<String, Object> updates = new HashMap<>();
    updates.put("displayName", displayName);
    if (avatarImageId != null) {
        updates.put("avatarImageId", avatarImageId);
    }
    
    return db.collection("users").document(userId).update(updates);
}

// GroupRepository.java - Update member role
public Task<Void> updateMemberRole(String groupId, String userId, String newRole) {
    return db.collection("groups").document(groupId)
            .collection("members").document(userId)
            .update("role", newRole);
}
```

### 4.4 Delete Operations
```java
// PostRepository.java - Soft delete post
public Task<Void> deletePost(String groupId, String postId) {
    return db.collection("groups").document(groupId)
            .collection("posts").document(postId)
            .update("isDeleted", true, "deletedAt", System.currentTimeMillis());
}

// GroupRepository.java - Remove member
public Task<Void> removeMember(String groupId, String userId) {
    return db.collection("groups").document(groupId)
            .collection("members").document(userId)
            .delete();
}
```

## 5. Firebase Storage Operations

### 5.1 Image Upload
```java
// PostRepository.java - Upload multiple images
public void uploadMultipleImages(List<byte[]> imageDataList, 
        OnSuccessListener<List<String>> onSuccess) {
    List<Task<String>> uploadTasks = new ArrayList<>();
    
    for (byte[] imageData : imageDataList) {
        String fileName = "post_" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = storage.getReference()
                .child("post_images").child(fileName);
                
        Task<String> uploadTask = ref.putBytes(imageData)
                .continueWithTask(task -> ref.getDownloadUrl())
                .continueWith(task -> task.getResult().toString());
                
        uploadTasks.add(uploadTask);
    }
    
    Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(onSuccess);
}
```

### 5.2 Group Image Upload
```java
// GroupRepository.java - Upload group avatar/cover
public void uploadGroupImage(byte[] imageData, String type,
        OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
    StorageReference ref = storage.getReference()
            .child("group_images").child(type)
            .child("img_" + System.currentTimeMillis() + ".jpg");
            
    ref.putBytes(imageData)
            .addOnSuccessListener(taskSnapshot -> 
                ref.getDownloadUrl().addOnSuccessListener(uri -> 
                    onSuccess.onSuccess(uri.toString())))
            .addOnFailureListener(onFailure);
}
```

## 6. Security Rules (Firestore)

### 6.1 User Collection Rules
```javascript
// Users can only read/write their own data
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
  
  match /images/{imageId} {
    allow read, write: if request.auth != null && request.auth.uid == userId;
  }
}
```

### 6.2 Group Collection Rules
```javascript
// Group access based on membership (checked via subcollection)
match /groups/{groupId} {
  // Anyone authenticated can read basic group metadata if public; otherwise require membership
  allow read: if request.auth != null && (
    resource.data.isPrivate == false ||
    exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid))
  );

  // Only owner/admin can update group metadata
  allow update, delete: if request.auth != null &&
    get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin'];
  allow create: if request.auth != null; // creating a group is allowed for signed-in users
  
  match /members/{memberId} {
    allow read: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow write: if request.auth != null &&
      get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin'];
  }
  
  match /posts/{postId} {
    allow read: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow create: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow update, delete: if request.auth != null && (
      request.auth.uid == resource.data.authorId ||
      get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin']
    );
    
    match /comments/{commentId} {
      allow read, create: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
      allow update, delete: if request.auth != null && (
        request.auth.uid == resource.data.authorId ||
        get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin']
      );
    }
    
    match /likes/{userId} {
      allow read, write: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    }
  }
  
  match /events/{eventId} {
    allow read: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow create, update, delete: if request.auth != null &&
      get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin'];
    
    match /rsvps/{userId} {
      allow read, write: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    }
  }
}
```

## 7. Offline/Online State Handling

### 7.1 Connection State Monitoring
```java
// ChatRoomViewModel.java - Recommended offline handling (Firestore Android has
// persistence enabled by default; toggle network only when needed)
public class ChatRoomViewModel extends ViewModel {
    private final MutableLiveData<String> _connectionState = new MutableLiveData<>("online");

    private void setupOfflineHandling(FirebaseFirestore db) {
        // Optionally force enable persistence (usually on by default on Android)
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        // Listen to real-time updates; if listener errors with UNAVAILABLE, treat as offline
        ListenerRegistration reg = db.collection("__health__")
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null && e.getCause() instanceof java.net.UnknownHostException) {
                        _connectionState.postValue("offline");
                    } else {
                        _connectionState.postValue("online");
                    }
                });
    }
}
```

### 7.2 User Status Management
```java
// AuthRepository.java - Update user status
public Task<Void> updateUserStatus(String status) {
    FirebaseUser user = auth.getCurrentUser();
    if (user != null) {
        return db.collection("users").document(user.getUid())
                .update("status", status, "lastLoginAt", System.currentTimeMillis());
    }
    return Tasks.forResult(null);
}

// Set offline when app goes to background
public Task<Void> logout() {
    FirebaseUser fUser = auth.getCurrentUser();
    if (fUser != null) {
        db.collection("users").document(fUser.getUid())
                .update("status", "offline");
    }
    auth.signOut();
    return Tasks.forResult(null);
}
```

## 8. Performance Optimizations

### 8.1 Firestore Indexing
- Composite indexes cho complex queries
- Single field indexes cho sorting
- Array-contains queries cho memberIds

### 8.2 Caching Strategy
- Firestore offline persistence enabled
- Image caching với Glide
- User profile caching trong SharedPreferences

### 8.3 Pagination Implementation
- `startAfter()` cho cursor-based pagination
- Limit queries để giảm bandwidth
- Load more on scroll cho infinite lists



## Phụ lục: Tổng kết Security Rules, Denormalization, Indexing

### A. Security Rules (tóm tắt)
- Users: chỉ cho phép người dùng đọc/ghi tài liệu của chính họ `users/{userId}`.
- Groups: quyền đọc phụ thuộc membership (nếu `isPrivate == false` thì cho phép đọc metadata; ngược lại cần membership).
- Members: chỉ owner/admin được ghi; thành viên được đọc khi là member của group.
- Posts/Comments/Likes: chỉ member group được đọc/ghi; update/delete bởi tác giả hoặc owner/admin.
- Events/RSVPs: chỉ member đọc; tạo/cập nhật/xóa bởi owner/admin; `events/{eventId}/rsvps/{userId}` cho trạng thái tham dự.

### B. Denormalization (khuyến nghị)
- `groups.memberCount` để hiển thị nhanh số thành viên.
- `posts.likeCount`, `posts.commentCount` để giảm chi phí tổng hợp.
- `chats.lastMessage`, `chats.lastMessageAt`, `chats.lastMessageBy` để render danh sách chat hiệu quả.
- Các trường hiển thị nhanh: `authorName`, `creatorName`, `senderName` trong Post/Comment/Event/Message.

Lưu ý: Đồng bộ các bộ đếm/field denormalized bằng transaction hoặc batched writes khi có thay đổi.

### C. Indexing (gợi ý cấu hình)
- Chats: composite index cho `whereArrayContains(memberIds, userId)` + `orderBy(lastMessageAt DESC)`.
- Posts Feed: index `groups/{groupId}/posts` theo `createdAt DESC` (và theo authorId nếu lọc theo tác giả).
- Events: index theo `startTime`/`status` để lọc sự kiện sắp tới.
- Comments: index theo `createdAt ASC` để phân trang.

Tham khảo console Firebase để tạo composite indexes khi gặp lỗi yêu cầu index.
