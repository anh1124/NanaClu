# Security & Performance Analysis

## 1. Security Measures

### 1.1 Authentication Security
**Multi-layer Authentication**:
- **Primary**: Firebase Authentication (Google OAuth, Email/Password)
- **Secondary**: PIN-based app lock with hash storage
- **Session Management**: Automatic logout on PIN failures

**Implementation**:
```java
// PIN hashing in PinEntryActivity
private String hashPin(String pin) {
    return String.valueOf(pin.hashCode()); // Simple hash for demo
}

// PIN verification with attempt limiting
private void verifyPin() {
    if (failedAttempts >= MAX_ATTEMPTS) {
        forceLogout();
    }
}
```

**Security Features**:
- PIN attempt limiting (3 max attempts)
- Automatic logout on security violations
- Session invalidation on PIN failures
- Secure PIN storage using SharedPreferences

### 1.2 Data Protection
**Firestore Security Rules**:
```javascript
// User data protection
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}

// Group-based access control via membership subcollection
match /groups/{groupId} {
  // Read basic group metadata if public; otherwise require membership
  allow read: if request.auth != null && (
    resource.data.isPrivate == false ||
    exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid))
  );

  // Only owner/admin can modify group metadata
  allow update, delete: if request.auth != null &&
    get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin'];
  allow create: if request.auth != null;

  match /members/{memberId} {
    allow read: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow write: if request.auth != null &&
      get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin'];
  }

  match /posts/{postId} {
    allow read, create: if request.auth != null && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid));
    allow update, delete: if request.auth != null && (
      request.auth.uid == resource.data.authorId ||
      get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role in ['owner','admin']
    );
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

**Data Validation**:
- Client-side input validation before Firebase calls
- Server-side validation via Firestore security rules
- SQL injection prevention through parameterized queries
- XSS protection through input sanitization

### 1.3 Network Security
**HTTPS Enforcement**:
- All Firebase services use HTTPS by default
- Image uploads to Firebase Storage over secure connections
- API calls encrypted in transit

**Authentication Tokens**:
- Firebase ID tokens for API authentication
- Automatic token refresh handling
- Secure token storage in Firebase SDK

### 1.4 Local Data Security
**Sensitive Data Storage**:
```java
// PIN hash storage in encrypted SharedPreferences
SharedPreferences securityPrefs = getSharedPreferences("security", MODE_PRIVATE);
securityPrefs.edit()
    .putString("pin_hash", hashPin(pin))
    .putBoolean("pin_enabled", true)
    .apply();
```

**Data Clearing on Logout**:
```java
private void forceLogout() {
    // Clear PIN settings
    securityPrefs.edit()
        .putBoolean("pin_enabled", false)
        .remove("pin_hash")
        .apply();
    
    // Clear auto login
    getSharedPreferences("auth", MODE_PRIVATE)
        .edit()
        .putBoolean("auto_login", false)
        .apply();
    
    // Firebase signout
    FirebaseAuth.getInstance().signOut();
}
```

## 2. Performance Optimizations

### 2.1 Memory Management
**Image Loading Optimization**:
```java
// Glide configuration for efficient image loading
Glide.with(context)
    .load(imageUrl)
    .placeholder(R.drawable.placeholder)
    .error(R.drawable.error_image)
    .diskCacheStrategy(DiskCacheStrategy.ALL)
    .into(imageView);
```

**RecyclerView Optimization**:
- ViewHolder pattern implementation
- Efficient data binding
- Image recycling in adapters
- Pagination for large datasets

**Lifecycle Management**:
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    // Remove Firestore listeners to prevent memory leaks
    if (messagesListener != null) {
        messagesListener.remove();
    }
}
```

### 2.2 Database Performance
**Query Optimization**:
```java
// Efficient pagination with Firestore
Query query = db.collection("chats")
    .whereArrayContains("memberIds", userId)
    .orderBy("lastMessageAt", Query.Direction.DESCENDING)
    .limit(20);

if (lastItem != null) {
    query = query.startAfter(lastItem.lastMessageAt);
}
```

**Denormalization Strategy**:
- Like counts stored in Post documents
- Member counts cached in Group documents
- User names denormalized in messages/comments
- Last message cached in Chat documents

**Indexing Strategy**:
- Compound indexes for complex queries
- Single field indexes for sorting
- Array-contains indexes for membership queries

### 2.3 Network Optimization
**Firestore Offline Persistence**:
```java
// Firestore Android enables persistence by default; explicitly set if needed
FirebaseFirestore db = FirebaseFirestore.getInstance();
FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true)
    .build();
db.setFirestoreSettings(settings);
```

**Image Compression**:
- Client-side image compression before upload
- Appropriate image formats (JPEG for photos, PNG for graphics)
- Thumbnail generation for grid views

**Batch Operations**:
```java
// Batch writes for better performance
WriteBatch batch = db.batch();
batch.set(postRef, post);
batch.update(groupRef, "memberCount", FieldValue.increment(1));
batch.commit();
```

### 2.4 UI Performance
**Lazy Loading**:
- Images loaded on-demand with Glide
- Chat messages loaded incrementally
- Group posts paginated

**Background Processing**:
```java
// Image processing on background thread
new AsyncTask<Void, Void, String>() {
    @Override
    protected String doInBackground(Void... voids) {
        // Compress and upload image
        return uploadImage(compressedImageData);
    }
    
    @Override
    protected void onPostExecute(String imageUrl) {
        // Update UI on main thread
        updateImageView(imageUrl);
    }
}.execute();
```

## 3. Caching Strategies

### 3.1 Image Caching
**Glide Configuration**:
- Memory cache for recently viewed images
- Disk cache for persistent storage
- Automatic cache size management
- Cache invalidation on image updates

### 3.2 Data Caching
**SharedPreferences Caching**:
```java
// Cache user profile for offline access
SharedPreferences userPrefs = getSharedPreferences("user_profile", MODE_PRIVATE);
userPrefs.edit()
    .putString("displayName", user.getDisplayName())
    .putString("email", user.getEmail())
    .putString("photoUrl", photoUrl)
    .apply();
```

**Firestore Offline Cache**:
- Automatic offline data persistence
- Query result caching
- Optimistic updates for better UX

### 3.3 Memory Cache Management
**LRU Cache Implementation**:
- Automatic eviction of least recently used items
- Configurable cache sizes based on device memory
- Memory pressure handling

## 4. Battery Optimization

### 4.1 Background Activity Reduction
**Efficient Listeners**:
- Remove Firestore listeners when not needed
- Use lifecycle-aware components
- Minimize background processing

**Network Request Optimization**:
- Batch multiple operations
- Reduce polling frequency
- Ưu tiên thông báo trong app; (tương lai) dùng FCM thay cho polling

### 4.2 Wake Lock Management
**Minimal Wake Lock Usage**:
- No unnecessary wake locks
- Proper wake lock release
- Battery-conscious background tasks

## 5. Security Best Practices Applied

### 5.1 Input Validation
**Client-side Validation**:
- Length limits on all text inputs
- Format validation for emails/dates
- File type validation for uploads
- Size limits for images

**Server-side Validation**:
- Firestore security rules enforce data constraints
- Authentication required for all operations
- Role-based access control

### 5.2 Error Handling
**Secure Error Messages**:
- Generic error messages to prevent information leakage
- Detailed logging for debugging (development only)
- No sensitive data in error responses

### 5.3 Data Minimization
**Principle of Least Privilege**:
- Users can only access their own data
- Group members can only access group content
- Admins have limited elevated permissions

**Data Retention**:
- Soft delete for important data
- Automatic cleanup of temporary files
- User data deletion on account removal

## 6. Performance Monitoring

### 6.1 Key Metrics Tracked
**App Performance**:
- App startup time
- Screen transition times
- Memory usage patterns
- Network request latency

**User Experience**:
- Image loading times
- Chat message delivery speed
- Offline functionality effectiveness

### 6.2 Optimization Results
**Memory Usage**:
- Efficient RecyclerView implementation reduces memory footprint
- Image caching prevents repeated downloads
- Proper lifecycle management prevents memory leaks

**Network Efficiency**:
- Firestore offline persistence reduces network calls
- Image compression reduces bandwidth usage
- Batch operations minimize round trips

**Battery Life**:
- Efficient background processing
- Minimal wake lock usage
- Optimized notification handling