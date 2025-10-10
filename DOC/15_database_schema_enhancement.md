# Database Schema Enhancement

## 1. Current Schema Review

### 1.1 Existing Collections Analysis

| Collection | Current Structure | Issues Identified | Enhancement Needed |
|------------|------------------|-------------------|-------------------|
| users | Basic profile data | Missing settings, preferences | Add user_settings subcollection |
| groups | Group info + memberIds array | Large arrays, no pagination | Separate members subcollection |
| chats | Chat metadata | Missing typing indicators | Add real-time status fields |
| messages | Message content | No message status tracking | Add delivery/read status |
| posts | Post content + engagement | Missing moderation fields | Add content moderation |
| comments | Comment data | No nested replies support | Add reply threading |

### 1.2 Current Schema Strengths
- **Denormalization**: Good use of cached fields (memberCount, likeCount)
- **Hierarchical Structure**: Proper subcollection usage for related data
- **Firebase Integration**: Well-designed for Firestore capabilities
- **Real-time Support**: Structure supports snapshot listeners

### 1.3 Critical Gaps Identified
- **No notification system**: Missing push notification management
- **Limited user preferences**: No settings/privacy controls
- **No content moderation**: Missing reporting and moderation tools
- **Incomplete audit trail**: Missing comprehensive logging
- **No analytics tracking**: No user behavior data collection
- **Missing configuration**: No app-wide settings management

## 2. Missing Collections

### 2.1 notifications Collection
```javascript
/notifications/{notificationId}
├── userId: string                    // Target user
├── type: string                      // "post_like", "new_message", "group_invite", "mention"
├── title: string                     // Notification title
├── body: string                      // Notification content
├── data: map {                       // Additional payload
│   ├── postId?: string
│   ├── chatId?: string
│   ├── groupId?: string
│   ├── senderId?: string
│   └── actionUrl?: string
├── }
├── isRead: boolean                   // Read status
├── isPush: boolean                   // Sent as push notification
├── priority: string                  // "high", "normal", "low"
├── createdAt: timestamp
├── readAt?: timestamp
├── expiresAt: timestamp              // Auto-cleanup date
└── metadata: map {                   // System metadata
    ├── source: string                // "system", "user_action", "scheduled"
    ├── batchId?: string              // For bulk notifications
    └── version: number
}
```

**Indexes Required**:
```javascript
// User notifications query
Fields: userId (ASC), createdAt (DESC)
// Unread notifications count
Fields: userId (ASC), isRead (ASC), createdAt (DESC)
// Cleanup expired notifications
Fields: expiresAt (ASC)
```

### 2.2 user_settings Collection
```javascript
/user_settings/{userId}
├── privacy: map {
│   ├── profileVisibility: string     // "public", "friends", "private"
│   ├── allowGroupInvites: boolean
│   ├── allowDirectMessages: boolean
│   ├── showOnlineStatus: boolean
│   └── allowSearchByEmail: boolean
├── }
├── notifications: map {
│   ├── pushEnabled: boolean
│   ├── emailEnabled: boolean
│   ├── postLikes: boolean
│   ├── newMessages: boolean
│   ├── groupInvites: boolean
│   ├── mentions: boolean
│   └── quietHours: map {
│       ├── enabled: boolean
│       ├── startTime: string         // "22:00"
│       └── endTime: string           // "08:00"
│   }
├── }
├── appearance: map {
│   ├── theme: string                 // "light", "dark", "auto"
│   ├── language: string              // "vi", "en"
│   ├── fontSize: string              // "small", "medium", "large"
│   └── compactMode: boolean
├── }
├── security: map {
│   ├── pinEnabled: boolean
│   ├── biometricEnabled: boolean
│   ├── autoLockMinutes: number
│   └── lastSecurityUpdate: timestamp
├── }
├── createdAt: timestamp
├── updatedAt: timestamp
└── version: number                   // Schema version for migrations
```

### 2.3 reports Collection
```javascript
/reports/{reportId}
├── reporterId: string                // User who reported
├── targetType: string                // "post", "comment", "user", "group"
├── targetId: string                  // ID of reported content
├── targetOwnerId: string             // Owner of reported content
├── reason: string                    // "spam", "harassment", "inappropriate", "other"
├── description?: string              // Additional details
├── status: string                    // "pending", "reviewing", "resolved", "dismissed"
├── priority: string                  // "low", "medium", "high", "critical"
├── assignedTo?: string               // Moderator ID
├── resolution?: map {
│   ├── action: string                // "no_action", "warning", "content_removed", "user_suspended"
│   ├── reason: string
│   ├── moderatorId: string
│   └── resolvedAt: timestamp
├── }
├── evidence: array [                 // Screenshots, additional proof
│   ├── {
│   │   ├── type: string              // "screenshot", "text", "url"
│   │   ├── content: string
│   │   └── uploadedAt: timestamp
│   └── }
├── ]
├── createdAt: timestamp
├── updatedAt: timestamp
└── metadata: map {
    ├── reporterIP?: string           // For spam detection
    ├── userAgent?: string
    └── appVersion: string
}
```

### 2.4 app_config Collection
```javascript
/app_config/{configId}
├── key: string                       // "maintenance_mode", "max_file_size", "feature_flags"
├── value: any                        // Configuration value
├── type: string                      // "boolean", "number", "string", "object"
├── description: string               // Human-readable description
├── environment: string               // "production", "staging", "development"
├── isActive: boolean
├── validFrom?: timestamp             // When config becomes active
├── validUntil?: timestamp            // When config expires
├── createdBy: string                 // Admin user ID
├── createdAt: timestamp
├── updatedAt: timestamp
└── metadata: map {
    ├── category: string              // "feature", "limit", "ui", "security"
    ├── requiresRestart: boolean      // App restart needed
    └── rollbackValue?: any           // Previous value for rollback
}

// Example configurations:
// maintenance_mode: { value: false, type: "boolean" }
// max_file_size_mb: { value: 10, type: "number" }
// feature_video_calls: { value: false, type: "boolean" }
// welcome_message: { value: "Chào mừng!", type: "string" }
```

### 2.5 analytics Collection
```javascript
/analytics/{eventId}
├── userId?: string                   // Anonymous if not logged in
├── sessionId: string                 // Session identifier
├── eventType: string                 // "screen_view", "button_click", "feature_usage"
├── eventName: string                 // "login_success", "post_created", "chat_opened"
├── properties: map {                 // Event-specific data
│   ├── screen?: string
│   ├── feature?: string
│   ├── duration?: number
│   ├── success?: boolean
│   └── errorCode?: string
├── }
├── deviceInfo: map {
│   ├── platform: string              // "android"
│   ├── osVersion: string
│   ├── appVersion: string
│   ├── deviceModel?: string
│   └── screenSize?: string
├── }
├── timestamp: timestamp
└── metadata: map {
    ├── batchId?: string              // For batch processing
    ├── processed: boolean            // Analytics processing status
    └── retentionDays: number         // Data retention period
}
```

### 2.6 feedback Collection
```javascript
/feedback/{feedbackId}
├── userId?: string                   // Optional for anonymous feedback
├── type: string                      // "bug_report", "feature_request", "general"
├── category: string                  // "ui", "performance", "functionality", "other"
├── title: string
├── description: string
├── severity?: string                 // "low", "medium", "high", "critical" (for bugs)
├── steps?: string                    // Reproduction steps
├── expectedBehavior?: string
├── actualBehavior?: string
├── attachments: array [              // Screenshots, logs
│   ├── {
│   │   ├── type: string              // "image", "log", "video"
│   │   ├── url: string               // Firebase Storage URL
│   │   ├── filename: string
│   │   └── size: number
│   └── }
├── ]
├── status: string                    // "new", "in_progress", "resolved", "closed"
├── priority: string                  // "low", "medium", "high"
├── assignedTo?: string               // Developer/support ID
├── response?: string                 // Official response
├── resolution?: map {
│   ├── type: string                  // "fixed", "wont_fix", "duplicate", "not_reproducible"
│   ├── description: string
│   ├── resolvedBy: string
│   └── resolvedAt: timestamp
├── }
├── deviceInfo: map {                 // Same as analytics
│   ├── platform: string
│   ├── osVersion: string
│   ├── appVersion: string
│   └── deviceModel?: string
├── }
├── createdAt: timestamp
├── updatedAt: timestamp
└── metadata: map {
    ├── source: string                // "in_app", "email", "support_chat"
    ├── userAgent?: string
    └── buildNumber?: string
}
```

## 3. Schema Improvements

### 3.1 Consistency Improvements

**1. Standardized Timestamp Fields**
```javascript
// Add to ALL collections
├── createdAt: timestamp              // Document creation time
├── updatedAt: timestamp              // Last modification time
├── deletedAt?: timestamp             // Soft delete timestamp
└── version: number                   // Document version for optimistic locking
```

**2. Audit Trail Fields**
```javascript
// Add to content collections (posts, comments, groups)
├── createdBy: string                 // User ID who created
├── updatedBy?: string                // User ID who last modified
├── moderatedBy?: string              // Moderator who took action
└── auditLog: array [                 // Change history
    ├── {
    │   ├── action: string            // "created", "updated", "deleted", "restored"
    │   ├── userId: string
    │   ├── timestamp: timestamp
    │   ├── changes?: map             // What changed
    │   └── reason?: string           // Why changed
    └── }
]
```

**3. Soft Delete Pattern**
```javascript
// Implement across all collections
├── isDeleted: boolean                // Soft delete flag
├── deletedAt?: timestamp             // When deleted
├── deletedBy?: string                // Who deleted
└── deletionReason?: string           // Why deleted
```

### 3.2 Performance Optimizations

**1. Enhanced Denormalization**
```javascript
// posts collection improvements
├── authorInfo: map {                 // Denormalized author data
│   ├── displayName: string
│   ├── avatarUrl?: string
│   └── isVerified: boolean
├── }
├── groupInfo: map {                  // Denormalized group data
│   ├── name: string
│   ├── isPrivate: boolean
│   └── memberCount: number
├── }
├── engagement: map {                 // Pre-calculated metrics
│   ├── likeCount: number
│   ├── commentCount: number
│   ├── shareCount: number
│   ├── viewCount: number
│   └── lastEngagementAt: timestamp
├── }
└── searchKeywords: array             // For text search optimization
```

**2. Improved Indexing Strategy**
```javascript
// messages collection optimization
├── chatId: string
├── senderId: string
├── content: string
├── type: string                      // "text", "image", "file", "system"
├── status: map {                     // Message delivery status
│   ├── sent: timestamp
│   ├── delivered?: timestamp
│   ├── read?: timestamp
│   └── readBy: array                 // For group chats
├── }
├── replyTo?: string                  // Parent message ID for threading
├── editHistory?: array [             // Message edit history
│   ├── {
│   │   ├── content: string
│   │   ├── editedAt: timestamp
│   │   └── reason?: string
│   └── }
├── ]
├── reactions: map {                  // Message reactions
│   ├── "👍": array [userId1, userId2]
│   ├── "❤️": array [userId3]
│   └── total: number
├── }
└── searchText: string                // Processed text for search
```

### 3.3 Data Integrity Enhancements

**1. Relationship Validation**
```javascript
// groups collection improvements
├── relationships: map {
│   ├── parentGroupId?: string        // For subgroups
│   ├── linkedGroups: array           // Related groups
│   └── dependencies: array           // Required groups
├── }
├── constraints: map {
│   ├── maxMembers: number
│   ├── minMembers: number
│   ├── allowSubgroups: boolean
│   └── requireApproval: boolean
├── }
└── validation: map {
    ├── lastValidated: timestamp
    ├── memberCountValid: boolean
    ├── permissionsValid: boolean
    └── dataIntegrityScore: number
}
```

**2. Content Moderation Fields**
```javascript
// Add to posts and comments
├── moderation: map {
│   ├── status: string                // "approved", "pending", "flagged", "removed"
│   ├── autoFlags: array              // Automated detection flags
│   ├── manualReviews: array [
│   │   ├── {
│   │   │   ├── reviewerId: string
│   │   │   ├── decision: string
│   │   │   ├── reason: string
│   │   │   └── reviewedAt: timestamp
│   │   └── }
│   ├── ]
│   ├── contentScore: number          // AI content safety score
│   └── lastModerated: timestamp
├── }
└── visibility: map {
    ├── isPublic: boolean
    ├── restrictedTo: array           // User IDs with access
    ├── hiddenFrom: array             // Blocked users
    └── geofencing?: map              // Location-based visibility
}
```

## 4. Migration Strategy

### 4.1 Backward Compatibility Plan

**Phase 1: Additive Changes (Week 1-2)**
```javascript
// Add new fields without breaking existing code
// All new fields are optional with default values

// Example: Add to existing users collection
{
  // Existing fields remain unchanged
  "userId": "existing_value",
  "email": "existing_value",
  
  // New optional fields
  "createdAt": "2024-01-01T00:00:00Z",  // Backfill with account creation
  "updatedAt": "2024-01-01T00:00:00Z",  // Set to current time
  "version": 1,                         // Start with version 1
  "isDeleted": false                    // Default to not deleted
}
```

**Phase 2: New Collections (Week 3-4)**
```javascript
// Create new collections without affecting existing ones
// Populate with default data for existing users

// user_settings creation script
function createDefaultUserSettings(userId) {
  return {
    privacy: {
      profileVisibility: "public",
      allowGroupInvites: true,
      allowDirectMessages: true,
      showOnlineStatus: true,
      allowSearchByEmail: true
    },
    notifications: {
      pushEnabled: true,
      emailEnabled: false,
      postLikes: true,
      newMessages: true,
      groupInvites: true,
      mentions: true,
      quietHours: {
        enabled: false,
        startTime: "22:00",
        endTime: "08:00"
      }
    },
    appearance: {
      theme: "auto",
      language: "vi",
      fontSize: "medium",
      compactMode: false
    },
    security: {
      pinEnabled: false,
      biometricEnabled: false,
      autoLockMinutes: 5,
      lastSecurityUpdate: new Date()
    },
    createdAt: new Date(),
    updatedAt: new Date(),
    version: 1
  };
}
```

**Phase 3: Data Enhancement (Week 5-6)**
```javascript
// Enhance existing documents with new fields
// Use Cloud Functions for batch processing

// Example: Enhance posts with new fields
function enhancePostDocument(postDoc) {
  const enhanced = {
    ...postDoc.data(),
    
    // Add audit trail
    createdBy: postDoc.data().authorId,
    auditLog: [{
      action: "created",
      userId: postDoc.data().authorId,
      timestamp: postDoc.data().createdAt || new Date(),
      reason: "Initial creation"
    }],
    
    // Add moderation
    moderation: {
      status: "approved",  // Assume existing content is approved
      autoFlags: [],
      manualReviews: [],
      contentScore: 0.9,   // High score for existing content
      lastModerated: new Date()
    },
    
    // Add visibility
    visibility: {
      isPublic: true,
      restrictedTo: [],
      hiddenFrom: []
    },
    
    // Update timestamps
    updatedAt: new Date(),
    version: 1
  };
  
  return enhanced;
}
```

### 4.2 Data Migration Scripts

**1. User Settings Migration**
```java
// Android migration helper
public class UserSettingsMigration {
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    public Task<Void> migrateUserSettings(String userId) {
        DocumentReference settingsRef = db.collection("user_settings").document(userId);
        
        return settingsRef.get().continueWithTask(task -> {
            if (!task.getResult().exists()) {
                // Create default settings
                Map<String, Object> defaultSettings = createDefaultSettings();
                return settingsRef.set(defaultSettings);
            }
            return Tasks.forResult(null);
        });
    }
    
    private Map<String, Object> createDefaultSettings() {
        Map<String, Object> settings = new HashMap<>();
        
        // Privacy defaults
        Map<String, Object> privacy = new HashMap<>();
        privacy.put("profileVisibility", "public");
        privacy.put("allowGroupInvites", true);
        privacy.put("allowDirectMessages", true);
        privacy.put("showOnlineStatus", true);
        privacy.put("allowSearchByEmail", true);
        settings.put("privacy", privacy);
        
        // Notification defaults
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("pushEnabled", true);
        notifications.put("emailEnabled", false);
        notifications.put("postLikes", true);
        notifications.put("newMessages", true);
        notifications.put("groupInvites", true);
        notifications.put("mentions", true);
        
        Map<String, Object> quietHours = new HashMap<>();
        quietHours.put("enabled", false);
        quietHours.put("startTime", "22:00");
        quietHours.put("endTime", "08:00");
        notifications.put("quietHours", quietHours);
        settings.put("notifications", notifications);
        
        // Appearance defaults
        Map<String, Object> appearance = new HashMap<>();
        appearance.put("theme", "auto");
        appearance.put("language", "vi");
        appearance.put("fontSize", "medium");
        appearance.put("compactMode", false);
        settings.put("appearance", appearance);
        
        // Security defaults
        Map<String, Object> security = new HashMap<>();
        security.put("pinEnabled", false);
        security.put("biometricEnabled", false);
        security.put("autoLockMinutes", 5);
        security.put("lastSecurityUpdate", FieldValue.serverTimestamp());
        settings.put("security", security);
        
        // Metadata
        settings.put("createdAt", FieldValue.serverTimestamp());
        settings.put("updatedAt", FieldValue.serverTimestamp());
        settings.put("version", 1);
        
        return settings;
    }
}
```

**2. Batch Migration Cloud Function**
```javascript
// Cloud Function for batch migration
const functions = require('firebase-functions');
const admin = require('firebase-admin');

exports.migrateUserData = functions.https.onCall(async (data, context) => {
  // Verify admin access
  if (!context.auth || !context.auth.token.admin) {
    throw new functions.https.HttpsError('permission-denied', 'Admin access required');
  }
  
  const db = admin.firestore();
  const batch = db.batch();
  let processedCount = 0;
  
  try {
    // Get all users
    const usersSnapshot = await db.collection('users').get();
    
    for (const userDoc of usersSnapshot.docs) {
      const userId = userDoc.id;
      const userData = userDoc.data();
      
      // Migrate user document
      const userRef = db.collection('users').doc(userId);
      batch.update(userRef, {
        createdAt: userData.createdAt || admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        version: 1,
        isDeleted: false
      });
      
      // Create user settings
      const settingsRef = db.collection('user_settings').doc(userId);
      batch.set(settingsRef, createDefaultUserSettings());
      
      processedCount++;
      
      // Commit batch every 500 operations (Firestore limit)
      if (processedCount % 500 === 0) {
        await batch.commit();
        batch = db.batch();
      }
    }
    
    // Commit remaining operations
    if (processedCount % 500 !== 0) {
      await batch.commit();
    }
    
    return { success: true, processedCount };
    
  } catch (error) {
    console.error('Migration error:', error);
    throw new functions.https.HttpsError('internal', 'Migration failed');
  }
});

function createDefaultUserSettings() {
  return {
    privacy: {
      profileVisibility: 'public',
      allowGroupInvites: true,
      allowDirectMessages: true,
      showOnlineStatus: true,
      allowSearchByEmail: true
    },
    notifications: {
      pushEnabled: true,
      emailEnabled: false,
      postLikes: true,
      newMessages: true,
      groupInvites: true,
      mentions: true,
      quietHours: {
        enabled: false,
        startTime: '22:00',
        endTime: '08:00'
      }
    },
    appearance: {
      theme: 'auto',
      language: 'vi',
      fontSize: 'medium',
      compactMode: false
    },
    security: {
      pinEnabled: false,
      biometricEnabled: false,
      autoLockMinutes: 5,
      lastSecurityUpdate: admin.firestore.FieldValue.serverTimestamp()
    },
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    version: 1
  };
}
```

### 4.3 Rollback Strategy

**1. Version-based Rollback**
```java
// Rollback mechanism
public class SchemaRollback {
    public Task<Void> rollbackToVersion(String collection, String documentId, int targetVersion) {
        DocumentReference docRef = db.collection(collection).document(documentId);
        
        return docRef.get().continueWithTask(task -> {
            DocumentSnapshot doc = task.getResult();
            int currentVersion = doc.getLong("version").intValue();
            
            if (currentVersion <= targetVersion) {
                return Tasks.forResult(null); // No rollback needed
            }
            
            // Get rollback data from audit log
            List<Map<String, Object>> auditLog = (List<Map<String, Object>>) doc.get("auditLog");
            Map<String, Object> rollbackData = findVersionData(auditLog, targetVersion);
            
            if (rollbackData != null) {
                return docRef.update(rollbackData);
            }
            
            throw new RuntimeException("Cannot rollback to version " + targetVersion);
        });
    }
}
```

**2. Backup Strategy**
```javascript
// Automated backup before migration
exports.createMigrationBackup = functions.https.onCall(async (data, context) => {
  const db = admin.firestore();
  const backupCollection = `backup_${Date.now()}`;
  
  // Backup all collections
  const collections = ['users', 'groups', 'chats', 'messages', 'posts', 'comments'];
  
  for (const collectionName of collections) {
    const snapshot = await db.collection(collectionName).get();
    const batch = db.batch();
    
    snapshot.docs.forEach(doc => {
      const backupRef = db.collection(backupCollection).doc(`${collectionName}_${doc.id}`);
      batch.set(backupRef, {
        originalCollection: collectionName,
        originalId: doc.id,
        data: doc.data(),
        backedUpAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });
    
    await batch.commit();
  }
  
  return { backupCollection, status: 'completed' };
});
```

## 5. Security Rules Updates

### 5.1 New Collection Rules

**1. notifications Collection**
```javascript
// Users can only read their own notifications
match /notifications/{notificationId} {
  allow read, write: if request.auth != null && 
    request.auth.uid == resource.data.userId;
  
  // System can create notifications
  allow create: if request.auth != null && 
    request.auth.token.admin == true;
}
```

**2. user_settings Collection**
```javascript
// Users can only access their own settings
match /user_settings/{userId} {
  allow read, write: if request.auth != null && 
    request.auth.uid == userId;
  
  // Validate settings structure
  allow write: if validateUserSettings(request.resource.data);
}

function validateUserSettings(data) {
  return data.keys().hasAll(['privacy', 'notifications', 'appearance', 'security']) &&
         data.privacy.keys().hasAll(['profileVisibility', 'allowGroupInvites']) &&
         data.notifications.keys().hasAll(['pushEnabled', 'emailEnabled']) &&
         data.appearance.keys().hasAll(['theme', 'language']) &&
         data.security.keys().hasAll(['pinEnabled', 'autoLockMinutes']);
}
```

**3. reports Collection**
```javascript
// Users can create reports and read their own
match /reports/{reportId} {
  allow create: if request.auth != null && 
    request.auth.uid == request.resource.data.reporterId;
  
  allow read: if request.auth != null && 
    (request.auth.uid == resource.data.reporterId || 
     request.auth.token.moderator == true);
  
  // Only moderators can update reports
  allow update: if request.auth != null && 
    request.auth.token.moderator == true;
}
```

**4. app_config Collection**
```javascript
// Only admins can manage app configuration
match /app_config/{configId} {
  allow read: if request.auth != null;
  allow write: if request.auth != null && 
    request.auth.token.admin == true;
}
```

**5. analytics Collection**
```javascript
// Write-only for users, read for admins
match /analytics/{eventId} {
  allow create: if request.auth != null;
  allow read: if request.auth != null && 
    request.auth.token.admin == true;
}
```

**6. feedback Collection**
```javascript
// Users can create and read their own feedback
match /feedback/{feedbackId} {
  allow create: if request.auth != null;
  
  allow read: if request.auth != null && 
    (request.auth.uid == resource.data.userId || 
     request.auth.token.admin == true);
  
  // Admins and support can update
  allow update: if request.auth != null && 
    (request.auth.token.admin == true || 
     request.auth.token.support == true);
}
```

### 5.2 Enhanced Existing Rules

**1. Improved User Rules**
```javascript
match /users/{userId} {
  allow read: if request.auth != null && 
    (request.auth.uid == userId || 
     isUserVisible(userId, request.auth.uid));
  
  allow write: if request.auth != null && 
    request.auth.uid == userId &&
    validateUserUpdate(request.resource.data);
}

function isUserVisible(targetUserId, requesterId) {
  let settings = get(/databases/$(database)/documents/user_settings/$(targetUserId));
  return settings.data.privacy.profileVisibility == 'public' ||
         (settings.data.privacy.profileVisibility == 'friends' && 
          isFriend(targetUserId, requesterId));
}

function validateUserUpdate(data) {
  return data.keys().hasAll(['userId', 'email', 'displayName']) &&
         data.userId is string &&
         data.email is string &&
         data.displayName is string &&
         data.updatedAt == request.time;
}
```

**2. Enhanced Group Rules**
```javascript
match /groups/{groupId} {
  allow read: if request.auth != null && 
    (request.auth.uid in resource.data.memberIds ||
     resource.data.visibility.isPublic == true);
  
  allow create: if request.auth != null &&
    validateGroupCreation(request.resource.data);
  
  allow update: if request.auth != null && 
    (isMemberWithRole(groupId, request.auth.uid, ['owner', 'admin']) ||
     isValidMemberUpdate(request.resource.data, resource.data));
}

function validateGroupCreation(data) {
  return data.keys().hasAll(['name', 'description', 'createdBy', 'memberIds']) &&
         data.createdBy == request.auth.uid &&
         request.auth.uid in data.memberIds &&
         data.createdAt == request.time &&
         data.updatedAt == request.time;
}
```

## 6. Implementation Timeline

### 6.1 Phase 1: Foundation (Week 1-2)
- **Week 1**: Create new collections with basic structure
- **Week 2**: Implement user_settings and notifications collections
- **Deliverables**: New collections created, basic security rules implemented

### 6.2 Phase 2: Migration (Week 3-4)
- **Week 3**: Develop migration scripts and backup procedures
- **Week 4**: Execute migration for existing data
- **Deliverables**: All existing data migrated, backward compatibility maintained

### 6.3 Phase 3: Enhancement (Week 5-6)
- **Week 5**: Implement advanced features (reports, analytics, feedback)
- **Week 6**: Performance optimization and testing
- **Deliverables**: Full schema enhancement completed, performance validated

### 6.4 Phase 4: Validation (Week 7-8)
- **Week 7**: Comprehensive testing and security validation
- **Week 8**: Production deployment and monitoring
- **Deliverables**: Production-ready enhanced schema

## 7. Success Metrics

### 7.1 Performance Metrics
- **Query Performance**: 20% improvement in average query time
- **Storage Efficiency**: 15% reduction in redundant data
- **Index Utilization**: 95% of queries using optimal indexes

### 7.2 Functionality Metrics
- **Feature Coverage**: 100% of planned features supported
- **Data Integrity**: 99.9% data consistency across collections
- **Migration Success**: 100% data migration without loss

### 7.3 Security Metrics
- **Rule Coverage**: 100% of collections have appropriate security rules
- **Access Control**: Proper role-based access implemented
- **Audit Trail**: Complete change tracking for all critical operations

**Final Validation**: All enhancements must maintain backward compatibility and pass comprehensive testing before production deployment.