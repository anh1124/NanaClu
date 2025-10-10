# API Interaction Flows

## 1. Firebase Authentication API Flows

### 1.1 Google Sign-In Flow
```
Client App → Google OAuth → Firebase Auth → Custom Token → Firestore Access
     ↓             ↓             ↓             ↓             ↓
User Action   OAuth Dialog   ID Token      Auth State    Database Query
     ↓             ↓             ↓             ↓             ↓
Tap Button    Credentials   Verification   User Object   User Document
```

**API Sequence**:
```java
// 1. Initialize Google Sign-In
GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken(getString(R.string.default_web_client_id))
    .requestEmail()
    .build();

// 2. Launch sign-in intent
Intent signInIntent = googleSignInClient.getSignInIntent();
startActivityForResult(signInIntent, RC_SIGN_IN);

// 3. Handle result and authenticate with Firebase
GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult();
AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
FirebaseAuth.getInstance().signInWithCredential(credential);
```

**Response Handling**:
```java
// Success Response
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
String uid = user.getUid();
String email = user.getEmail();
String displayName = user.getDisplayName();

// Error Handling
.addOnFailureListener(exception -> {
    if (exception instanceof FirebaseAuthUserCollisionException) {
        // Account already exists with different credential
    } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
        // Invalid credential
    }
});
```

### 1.2 Email/Password Authentication
```
Email Input → Validation → Firebase Auth → Success/Error → User Creation/Login
     ↓            ↓             ↓             ↓             ↓
Form Data    Client Check   API Call      Response      Navigation
```

**Registration API Flow**:
```java
// Create user with email and password
FirebaseAuth.getInstance()
    .createUserWithEmailAndPassword(email, password)
    .addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
            FirebaseUser user = task.getResult().getUser();
            createUserProfile(user);
        } else {
            handleRegistrationError(task.getException());
        }
    });
```

## 2. Firestore Database API Flows

### 2.1 User Profile Management
```
User Action → Repository → Firestore API → Response → ViewModel → UI Update
     ↓            ↓             ↓             ↓            ↓            ↓
Create/Update  Validate      Document      Success      LiveData     Refresh
```

**Create User Profile**:
```java
// API Call Structure
DocumentReference userRef = db.collection("users").document(userId);

User newUser = new User(userId, email, displayName);
userRef.set(newUser)
    .addOnSuccessListener(aVoid -> {
        // Profile created successfully
        userViewModel.setCurrentUser(newUser);
    })
    .addOnFailureListener(e -> {
        // Handle creation error
        Log.e(TAG, "Error creating user profile", e);
    });
```

**Update User Profile**:
```java
// Partial update using Map
Map<String, Object> updates = new HashMap<>();
updates.put("displayName", newDisplayName);
updates.put("lastLoginAt", System.currentTimeMillis());

userRef.update(updates)
    .addOnSuccessListener(aVoid -> updateUI())
    .addOnFailureListener(e -> showError(e.getMessage()));
```

### 2.2 Group Operations API Flow

**Create Group with Members**:
```java
// Batch write for atomic operation
WriteBatch batch = db.batch();

// Create group document
DocumentReference groupRef = db.collection("groups").document();
Group newGroup = new Group(name, description, creatorId, isPrivate);
batch.set(groupRef, newGroup);

// Add creator as owner
DocumentReference memberRef = groupRef.collection("members").document(creatorId);
Member owner = new Member(creatorId, "owner");
batch.set(memberRef, owner);

// Execute batch
batch.commit()
    .addOnSuccessListener(aVoid -> {
        // Group created successfully
        navigateToGroup(groupRef.getId());
    })
    .addOnFailureListener(e -> {
        // Handle batch failure
        showError("Failed to create group: " + e.getMessage());
    });
```

**Join Group API Flow**:
```java
// 1. Find group by code
db.collection("groups")
    .whereEqualTo("code", groupCode)
    .limit(1)
    .get()
    .addOnSuccessListener(querySnapshot -> {
        if (!querySnapshot.isEmpty()) {
            DocumentSnapshot groupDoc = querySnapshot.getDocuments().get(0);
            Group group = groupDoc.toObject(Group.class);
            
            // 2. Check if group is private and user has permission
            if (group.isPrivate && !hasInvitation(userId, group.groupId)) {
                showError("This is a private group");
                return;
            }
            
            // 3. Add user as member
            addMemberToGroup(group.groupId, userId);
        } else {
            showError("Group not found");
        }
    });

// Add member function
private void addMemberToGroup(String groupId, String userId) {
    WriteBatch batch = db.batch();
    
    // Add member document
    DocumentReference memberRef = db.collection("groups")
        .document(groupId)
        .collection("members")
        .document(userId);
    Member newMember = new Member(userId, "member");
    batch.set(memberRef, newMember);
    
    // Increment member count
    DocumentReference groupRef = db.collection("groups").document(groupId);
    batch.update(groupRef, "memberCount", FieldValue.increment(1));
    
    batch.commit();
}
```

### 2.3 Post Creation with Images
```
Image Selection → Compression → Storage Upload → URL Generation → Firestore Document
       ↓              ↓              ↓              ↓              ↓
   File Picker    Reduce Size    Firebase API   Download URLs   Post Creation
```

**Complete Post Creation Flow**:
```java
// 1. Upload images to Firebase Storage
private void uploadImagesAndCreatePost(List<Uri> imageUris, String content) {
    List<String> imageUrls = new ArrayList<>();
    AtomicInteger uploadCount = new AtomicInteger(0);
    
    for (Uri imageUri : imageUris) {
        String fileName = "posts/" + groupId + "/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storage.getReference().child(fileName);
        
        imageRef.putFile(imageUri)
            .addOnSuccessListener(taskSnapshot -> {
                imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    imageUrls.add(downloadUri.toString());
                    
                    if (uploadCount.incrementAndGet() == imageUris.size()) {
                        // All images uploaded, create post
                        createPostDocument(content, imageUrls);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Image upload failed", e);
                showError("Failed to upload image");
            });
    }
}

// 2. Create Firestore document
private void createPostDocument(String content, List<String> imageUrls) {
    DocumentReference postRef = db.collection("groups")
        .document(groupId)
        .collection("posts")
        .document();
    
    Post newPost = new Post(groupId, content, currentUserId, currentUserName);
    newPost.imageUrls = imageUrls;
    
    postRef.set(newPost)
        .addOnSuccessListener(aVoid -> {
            // Post created successfully
            finish();
        })
        .addOnFailureListener(e -> {
            Log.e(TAG, "Failed to create post", e);
            showError("Failed to create post");
        });
}
```

## 3. Real-time Data Synchronization

### 3.1 Chat Message Listeners
```
Firestore Change → Snapshot Listener → Data Parsing → ViewModel Update → UI Refresh
       ↓                 ↓                 ↓              ↓              ↓
   New Message        onEvent()         Message Obj     LiveData       RecyclerView
```

**Real-time Message Listener**:
```java
// Setup listener for chat messages
private ListenerRegistration messagesListener;

private void setupMessageListener(String chatId) {
    messagesListener = db.collection("chats")
        .document(chatId)
        .collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed", error);
                return;
            }
            
            List<Message> messages = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Message message = doc.toObject(Message.class);
                messages.add(message);
            }
            
            // Update UI on main thread
            runOnUiThread(() -> {
                messageAdapter.updateMessages(messages);
                scrollToBottom();
            });
        });
}

// Cleanup listener
@Override
protected void onDestroy() {
    super.onDestroy();
    if (messagesListener != null) {
        messagesListener.remove();
    }
}
```

### 3.2 Group Feed Real-time Updates
```java
// Listen for new posts in group
private void setupPostListener(String groupId) {
    db.collection("groups")
        .document(groupId)
        .collection("posts")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(20)
        .addSnapshotListener((querySnapshot, error) -> {
            if (error != null) return;
            
            for (DocumentChange change : querySnapshot.getDocumentChanges()) {
                Post post = change.getDocument().toObject(Post.class);
                
                switch (change.getType()) {
                    case ADDED:
                        postAdapter.addPost(post);
                        break;
                    case MODIFIED:
                        postAdapter.updatePost(post);
                        break;
                    case REMOVED:
                        postAdapter.removePost(post.postId);
                        break;
                }
            }
        });
}
```

## 4. Error Handling and Retry Logic

### 4.1 Network Error Handling
```java
// Retry mechanism with exponential backoff
private void retryOperation(Runnable operation, int maxRetries) {
    AtomicInteger retryCount = new AtomicInteger(0);
    
    Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                operation.run();
            } catch (Exception e) {
                int currentRetry = retryCount.incrementAndGet();
                if (currentRetry < maxRetries) {
                    long delay = (long) Math.pow(2, currentRetry) * 1000; // Exponential backoff
                    new Handler().postDelayed(this, delay);
                } else {
                    showError("Operation failed after " + maxRetries + " attempts");
                }
            }
        }
    };
    
    retryRunnable.run();
}
```

### 4.2 Firestore Error Codes
```java
private void handleFirestoreError(Exception e) {
    if (e instanceof FirebaseFirestoreException) {
        FirebaseFirestoreException ffe = (FirebaseFirestoreException) e;
        
        switch (ffe.getCode()) {
            case PERMISSION_DENIED:
                showError("You don't have permission to perform this action");
                break;
            case UNAVAILABLE:
                showError("Service temporarily unavailable. Please try again.");
                retryLastOperation();
                break;
            case DEADLINE_EXCEEDED:
                showError("Request timed out. Please check your connection.");
                break;
            case RESOURCE_EXHAUSTED:
                showError("Too many requests. Please wait a moment.");
                break;
            default:
                showError("An error occurred: " + ffe.getMessage());
        }
    }
}
```

## 5. Offline Data Management

### 5.1 Offline Persistence Configuration
```java
// Enable offline persistence
FirebaseFirestore db = FirebaseFirestore.getInstance();
FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true)
    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
    .build();
db.setFirestoreSettings(settings);
```

### 5.2 Offline Write Operations
```java
// Queue operations for offline execution
private void createPostOffline(Post post) {
    // Add to local queue
    OfflineOperationQueue.getInstance().addOperation(
        new CreatePostOperation(post)
    );
    
    // Show optimistic UI
    postAdapter.addPost(post);
    
    // Attempt online sync
    if (NetworkUtils.isConnected()) {
        syncPendingOperations();
    }
}

// Sync when connection restored
private void syncPendingOperations() {
    List<OfflineOperation> pendingOps = OfflineOperationQueue.getInstance().getPendingOperations();
    
    for (OfflineOperation op : pendingOps) {
        op.execute()
            .addOnSuccessListener(result -> {
                OfflineOperationQueue.getInstance().removeOperation(op);
            })
            .addOnFailureListener(e -> {
                // Keep in queue for next sync attempt
                Log.e(TAG, "Failed to sync operation", e);
            });
    }
}
```

## 6. Performance Optimization APIs

### 6.1 Pagination Implementation
```java
// Implement pagination for large datasets
private DocumentSnapshot lastVisible;
private boolean isLoading = false;

private void loadMorePosts() {
    if (isLoading) return;
    isLoading = true;
    
    Query query = db.collection("groups")
        .document(groupId)
        .collection("posts")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(10);
    
    if (lastVisible != null) {
        query = query.startAfter(lastVisible);
    }
    
    query.get().addOnSuccessListener(querySnapshot -> {
        List<Post> newPosts = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            newPosts.add(doc.toObject(Post.class));
        }
        
        if (!newPosts.isEmpty()) {
            lastVisible = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            postAdapter.addPosts(newPosts);
        }
        
        isLoading = false;
    });
}
```

### 6.2 Batch Operations for Performance
```java
// Batch multiple operations for better performance
private void updatePostEngagement(String postId, boolean isLike) {
    WriteBatch batch = db.batch();
    
    DocumentReference postRef = db.collection("groups")
        .document(groupId)
        .collection("posts")
        .document(postId);
    
    if (isLike) {
        // Add like document
        DocumentReference likeRef = postRef.collection("likes").document(currentUserId);
        Like like = new Like(currentUserId);
        batch.set(likeRef, like);
        
        // Increment like count
        batch.update(postRef, "likeCount", FieldValue.increment(1));
    } else {
        // Remove like document
        DocumentReference likeRef = postRef.collection("likes").document(currentUserId);
        batch.delete(likeRef);
        
        // Decrement like count
        batch.update(postRef, "likeCount", FieldValue.increment(-1));
    }
    
    batch.commit()
        .addOnSuccessListener(aVoid -> updateUI(isLike))
        .addOnFailureListener(e -> revertOptimisticUpdate());
}
```

## 7. Security and Validation

### 7.1 Client-side Validation
```java
// Validate data before API calls
private boolean validatePostData(String content, List<Uri> images) {
    if (content.trim().isEmpty() && images.isEmpty()) {
        showError("Post cannot be empty");
        return false;
    }
    
    if (content.length() > MAX_POST_LENGTH) {
        showError("Post is too long (max " + MAX_POST_LENGTH + " characters)");
        return false;
    }
    
    if (images.size() > MAX_IMAGES_PER_POST) {
        showError("Too many images (max " + MAX_IMAGES_PER_POST + ")");
        return false;
    }
    
    return true;
}
```

### 7.2 Server-side Security Rules
```javascript
// Firestore security rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only access their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Group access control
    match /groups/{groupId} {
      allow read: if request.auth != null && 
        request.auth.uid in resource.data.memberIds;
      
      allow write: if request.auth != null && 
        get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner', 'admin'];
      
      // Posts within groups
      match /posts/{postId} {
        allow read: if request.auth != null && 
          request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds;
        
        allow create: if request.auth != null && 
          request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds &&
          request.auth.uid == resource.data.authorId;
        
        allow update, delete: if request.auth != null && 
          (request.auth.uid == resource.data.authorId ||
           get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner', 'admin']);
      }
    }
  }
}
```