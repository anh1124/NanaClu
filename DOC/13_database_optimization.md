# Database Query Optimization

## 1. Query Analysis

### 1.1 High-Frequency Queries

| Query | Collection | Frequency | Performance | Index Needed |
|-------|------------|-----------|-------------|--------------|
| listUserChats (collectionGroup) | members | High | 6/10 | Composite: userId + hidden |
| listenMessages (orderBy createdAt) | messages | High | 8/10 | Single: createdAt ASC |
| listMessages with pagination | messages | High | 7/10 | Single: createdAt ASC |
| whereArrayContains memberIds | chats | Medium | 5/10 | Array-contains: memberIds |
| findGroupByCode | groups | Medium | 8/10 | Single: code |
| getUserGroups via members | groups/{id}/members | High | 6/10 | Single: userId |
| getGroupPosts orderBy createdAt | posts | High | 7/10 | Single: createdAt DESC |
| getUserProfile | users | High | 9/10 | Document read (optimal) |

### 1.2 Expensive Queries

**1. Chat List Query (ChatRepository.listUserChats)**
```java
// Current implementation - EXPENSIVE
db.collectionGroup("members")
    .whereEqualTo("userId", uid)
    .get()
```
**Performance Issues**:
- Collection group query across all groups
- No pagination implemented
- Requires secondary queries to fetch chat documents
- Cost: ~N reads where N = total group memberships across all users

**2. Message History Loading**
```java
// MessageRepository.listMessages
Query q = db.collection(CHATS).document(chatId).collection(MESSAGES)
    .orderBy("createdAt", Query.Direction.ASCENDING);
```
**Performance Issues**:
- Loads all messages without proper pagination
- No limit on initial load
- Can cause memory issues with large chat histories

**3. Group Member Validation**
```java
// Implicit queries for permission checking
get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid))
```
**Performance Issues**:
- Security rule triggers additional reads
- Not cached, executed on every operation
- Multiplies read costs for group operations

### 1.3 Missing Indexes

**Required Composite Indexes**:
```javascript
// 1. Chat members with hidden filter
Collection: groups/{groupId}/members
Fields: userId (Ascending), hidden (Ascending)

// 2. Messages with chat type routing
Collection: groups/{groupId}/chats/{chatId}/messages  
Fields: createdAt (Ascending)

// 3. Posts with group filtering
Collection: groups/{groupId}/posts
Fields: createdAt (Descending), isDeleted (Ascending)

// 4. Events with status and time
Collection: groups/{groupId}/events
Fields: status (Ascending), startTime (Ascending)

// 5. Group members by role
Collection: groups/{groupId}/members
Fields: role (Ascending), joinedAt (Descending)
```

**Array-Contains Indexes**:
```javascript
// Chat membership queries
Collection: chats
Fields: memberIds (Array-contains), lastMessageAt (Descending)

// Group membership (if using array approach)
Collection: groups  
Fields: memberIds (Array-contains), lastActivityAt (Descending)
```

## 2. Critical Performance Issues

### 2.1 N+1 Query Problems

**Issue 1: Chat List Loading**
```java
// Current: 1 query + N queries for chat documents
db.collectionGroup("members").whereEqualTo("userId", uid).get()
// Then for each member document:
db.collection("chats").document(chatId).get()
```

**Solution**:
```java
// Optimized: Single query with denormalized data
db.collection("chats")
    .whereArrayContains("memberIds", userId)
    .orderBy("lastMessageAt", Query.Direction.DESCENDING)
    .limit(20)
```

**Issue 2: Group Posts with Author Names**
```java
// Current: Denormalized authorName in posts (GOOD)
// But missing for comments - causes additional user lookups
```

### 2.2 Large Collection Scans

**Issue: Message History Without Pagination**
```java
// Current implementation loads all messages
Query q = db.collection(CHATS).document(chatId).collection(MESSAGES)
    .orderBy("createdAt", Query.Direction.ASCENDING);
```

**Impact**: 
- Memory issues with large chat histories (1000+ messages)
- Slow initial load times
- Unnecessary bandwidth usage

### 2.3 Inefficient Real-time Listeners

**Issue: Broad Snapshot Listeners**
```java
// Listening to all messages without limits
.addSnapshotListener((querySnapshot, error) -> {
    // Processes all messages on every change
});
```

**Impact**:
- High bandwidth usage
- Battery drain
- Unnecessary UI updates

## 3. Performance Recommendations

### 3.1 Immediate Actions (Week 1-2)

**1. Implement Proper Pagination**
```java
// MessageRepository optimization
public Task<List<Message>> listMessagesWithPagination(String chatId, int limit, Long lastTimestamp) {
    Query q = db.collection(CHATS).document(chatId).collection(MESSAGES)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(limit);
    
    if (lastTimestamp != null) {
        q = q.startAfter(lastTimestamp);
    }
    
    return q.get().continueWith(task -> {
        // Process results
    });
}
```

**2. Add Query Limits**
```java
// Default limits for all list operations
private static final int DEFAULT_PAGE_SIZE = 20;
private static final int MAX_PAGE_SIZE = 50;

// Apply to all queries
.limit(Math.min(requestedLimit, MAX_PAGE_SIZE))
```

**3. Optimize Chat List Query**
```java
// Replace collection group with direct chat query
public Task<List<Chat>> listUserChats(String userId, int limit, Chat lastItem) {
    Query query = db.collection("chats")
        .whereArrayContains("memberIds", userId)
        .orderBy("lastMessageAt", Query.Direction.DESCENDING)
        .limit(limit);
        
    if (lastItem != null) {
        query = query.startAfter(lastItem.lastMessageAt);
    }
    
    return query.get().continueWith(task -> {
        // Process and filter hidden chats on client
    });
}
```

### 3.2 Medium-term Improvements (Month 1-2)

**1. Implement Proper Caching**
```java
// Repository-level caching
public class CachedChatRepository {
    private final LruCache<String, List<Message>> messageCache;
    private final Map<String, Long> cacheTimestamps;
    
    public Task<List<Message>> getMessages(String chatId, boolean forceRefresh) {
        if (!forceRefresh && isCacheValid(chatId)) {
            return Tasks.forResult(messageCache.get(chatId));
        }
        
        return fetchFromFirestore(chatId).continueWith(task -> {
            if (task.isSuccessful()) {
                cacheMessages(chatId, task.getResult());
            }
            return task.getResult();
        });
    }
}
```

**2. Denormalization Improvements**
```java
// Add more denormalized fields to reduce joins
public class Post {
    // Existing fields...
    
    // Add for optimization
    public String groupName;        // Avoid group lookup
    public String authorAvatarUrl;  // Avoid user lookup
    public int totalEngagement;     // Pre-calculated likes + comments
}
```

**3. Background Sync Strategy**
```java
// Implement background sync for better offline experience
public class BackgroundSyncService {
    public void syncPendingOperations() {
        // Batch pending writes
        WriteBatch batch = db.batch();
        
        List<PendingOperation> pending = getPendingOperations();
        for (PendingOperation op : pending) {
            op.addToBatch(batch);
        }
        
        batch.commit().addOnSuccessListener(result -> {
            clearPendingOperations();
        });
    }
}
```

### 3.3 Long-term Optimizations (Month 3-6)

**1. Sharding Strategy for Large Collections**
```javascript
// Shard large collections by time
/chats/{chatId}/messages_2024_01/{messageId}
/chats/{chatId}/messages_2024_02/{messageId}

// Query current month + previous month for recent messages
```

**2. Aggregation Collections**
```javascript
// Pre-calculated aggregations
/groups/{groupId}/stats {
    memberCount: 150,
    postCount: 1250,
    activeMembers: 45,
    lastUpdated: timestamp
}

// Update via Cloud Functions on data changes
```

**3. Advanced Caching with TTL**
```java
// Implement TTL-based caching
public class TTLCache<T> {
    private final Map<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();
    
    public void put(String key, T value, long ttlMillis) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
    }
    
    public T get(String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry != null && entry.isValid()) {
            return entry.value;
        }
        cache.remove(key);
        return null;
    }
}
```

## 4. Index Configuration Plan

### 4.1 Required Firestore Indexes

**Create these indexes in Firebase Console:**

```javascript
// 1. Chat queries
{
  "collectionGroup": "chats",
  "fields": [
    {"fieldPath": "memberIds", "arrayConfig": "CONTAINS"},
    {"fieldPath": "lastMessageAt", "order": "DESCENDING"}
  ]
}

// 2. Message pagination
{
  "collectionGroup": "messages", 
  "fields": [
    {"fieldPath": "createdAt", "order": "ASCENDING"}
  ]
}

// 3. Group posts
{
  "collectionGroup": "posts",
  "fields": [
    {"fieldPath": "createdAt", "order": "DESCENDING"},
    {"fieldPath": "isDeleted", "order": "ASCENDING"}
  ]
}

// 4. Group members by role
{
  "collectionGroup": "members",
  "fields": [
    {"fieldPath": "role", "order": "ASCENDING"},
    {"fieldPath": "joinedAt", "order": "DESCENDING"}
  ]
}

// 5. Events by status and time
{
  "collectionGroup": "events",
  "fields": [
    {"fieldPath": "status", "order": "ASCENDING"},
    {"fieldPath": "startTime", "order": "ASCENDING"}
  ]
}
```

### 4.2 Security Rule Optimizations

**Current expensive rules:**
```javascript
// Expensive: Requires additional read
allow read: if request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.memberIds;
```

**Optimized rules:**
```javascript
// Better: Use resource data when possible
allow read: if request.auth != null && 
  request.auth.uid in resource.data.memberIds;

// For writes, cache membership in user claims (via Cloud Functions)
allow write: if request.auth != null && 
  request.auth.token.groupMemberships[groupId] != null;
```

## 5. Implementation Plan

### 5.1 Phase 1: Critical Fixes (Week 1)
1. **Add pagination to all list queries**
   - MessageRepository.listMessages
   - ChatRepository.listUserChats  
   - PostRepository.getGroupPosts

2. **Implement query limits**
   - Default 20 items per page
   - Maximum 50 items per request

3. **Create essential indexes**
   - messages.createdAt
   - chats.memberIds + lastMessageAt

### 5.2 Phase 2: Performance Improvements (Week 2-4)
1. **Optimize chat list query**
   - Replace collection group with direct query
   - Implement client-side filtering for hidden chats

2. **Add repository caching**
   - LRU cache for frequently accessed data
   - TTL-based cache invalidation

3. **Improve real-time listeners**
   - Add limits to snapshot listeners
   - Implement listener cleanup

### 5.3 Phase 3: Advanced Optimizations (Month 2-3)
1. **Denormalization improvements**
   - Add more pre-calculated fields
   - Implement background aggregation

2. **Background sync**
   - Batch operations for better performance
   - Offline operation queuing

3. **Advanced caching strategies**
   - Multi-level caching
   - Predictive data loading

## 6. Monitoring and Metrics

### 6.1 Performance Metrics to Track
- **Query latency**: Average response time per query type
- **Read operations**: Daily read count by collection
- **Cache hit ratio**: Percentage of requests served from cache
- **Offline sync**: Number of pending operations

### 6.2 Alerting Thresholds
- Query latency > 2 seconds
- Daily reads > 100,000 (cost management)
- Cache hit ratio < 70%
- Pending operations > 50

### 6.3 Cost Optimization
**Current estimated costs** (based on query analysis):
- Chat list: ~50 reads per user per day
- Messages: ~200 reads per active chat per day
- Posts: ~100 reads per group per day

**Optimized costs** (after implementation):
- Chat list: ~10 reads per user per day (-80%)
- Messages: ~50 reads per active chat per day (-75%)
- Posts: ~30 reads per group per day (-70%)

**Total estimated savings**: 70-80% reduction in Firestore read operations