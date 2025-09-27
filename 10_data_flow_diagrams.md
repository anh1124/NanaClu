# Data Flow Diagrams

## 1. Authentication Flow

### 1.1 Login Process
```
User Input → LoginActivity → Firebase Auth → UserRepository → UserViewModel → HomeActivity
    ↓              ↓              ↓              ↓              ↓              ↓
Email/Pass    Validate      Auth Token     User Data      LiveData      UI Update
    ↓              ↓              ↓              ↓              ↓              ↓
Google OAuth  Form Check    ID Token       Firestore      Observer      Navigation
```

**Detailed Flow**:
1. **User Input**: Email/password or Google OAuth selection
2. **LoginActivity**: Form validation and Firebase Auth call
3. **Firebase Auth**: Authentication and token generation
4. **UserRepository**: Fetch/create user profile from Firestore
5. **UserViewModel**: Process data and update LiveData
6. **HomeActivity**: Observe changes and update UI

### 1.2 PIN Security Flow
```
App Launch → PinEntryActivity → SharedPreferences → Validation → HomeActivity/LoginActivity
     ↓              ↓                   ↓              ↓              ↓
Auto Login     PIN Input          Hash Check     Success/Fail    Navigation
     ↓              ↓                   ↓              ↓              ↓
Check Flag     Attempt Count      Compare Hash   Update Counter  Redirect
```

## 2. Group Management Flow

### 2.1 Group Creation
```
CreateGroupActivity → GroupViewModel → GroupRepository → Firestore → Real-time Updates
        ↓                  ↓              ↓              ↓              ↓
    Form Data         Validation      Create Doc     Group Created   UI Refresh
        ↓                  ↓              ↓              ↓              ↓
    Image Upload      Business Rules  Member Doc     Batch Write    Notification
```

**Data Transformation**:
```java
// Input Data
GroupFormData {
    name, description, isPrivate, coverImage
}
↓
// Processed Data
Group {
    groupId: UUID,
    name: validated,
    code: generated,
    createdBy: currentUserId,
    memberCount: 1
}
↓
// Firestore Documents
/groups/{groupId} + /groups/{groupId}/members/{userId}
```

### 2.2 Group Join Flow
```
JoinGroupActivity → Code Input → GroupRepository → Validation → Member Creation
        ↓              ↓              ↓              ↓              ↓
    Enter Code     Format Check   Find Group     Check Private   Add Member
        ↓              ↓              ↓              ↓              ↓
    Submit Form    Length/Chars   Query Firestore Permission    Batch Update
```

## 3. Post Creation & Interaction Flow

### 3.1 Post Creation
```
CreatePostActivity → Image Selection → Compression → Firebase Storage → Firestore
        ↓                 ↓              ↓              ↓              ↓
    Content Input     Gallery/Camera   Reduce Size    Upload Files   Create Doc
        ↓                 ↓              ↓              ↓              ↓
    Validation        Multiple Select  Background     Get URLs       Real-time
```

**Image Processing Pipeline**:
```
Original Image → Compression → Firebase Storage → URL Generation → Firestore Reference
     ↓              ↓              ↓                    ↓              ↓
  10MB+ File    Reduce to 2MB   Organized Path      Download URL    imageUrls[]
     ↓              ↓              ↓                    ↓              ↓
  High Quality   Maintain Ratio  /posts/{groupId}/   Permanent Link  Display Ready
```

### 3.2 Like/Comment Interaction
```
User Action → PostAdapter → PostViewModel → PostRepository → Firestore Update
     ↓            ↓              ↓              ↓              ↓
  Tap Like    Handle Click   Update State   Batch Write    Real-time Sync
     ↓            ↓              ↓              ↓              ↓
  UI Feedback  Optimistic UI  Business Logic Counter Update  All Clients
```

## 4. Chat System Flow

### 4.1 Private Chat Creation
```
UserListActivity → Select User → ChatRepository → Create/Find Chat → ChatRoomActivity
        ↓              ↓              ↓              ↓              ↓
    User Search    Tap Profile    Check Existing  Generate ID     Open Chat
        ↓              ↓              ↓              ↓              ↓
    Filter List    User Selection  Query by Pair   Create Doc     Load Messages
```

**Chat ID Generation**:
```java
// Private Chat
String pairKey = userId1.compareTo(userId2) < 0 ? 
    userId1 + "_" + userId2 : userId2 + "_" + userId1;

// Query existing or create new
Query existingChat = db.collection("chats")
    .whereEqualTo("pairKey", pairKey)
    .whereEqualTo("type", "private");
```

### 4.2 Message Flow
```
Message Input → ChatRoomActivity → MessageRepository → Firestore → Real-time Listeners
      ↓              ↓                   ↓              ↓              ↓
  Type Text      Send Button         Create Doc     Document Add   All Participants
      ↓              ↓                   ↓              ↓              ↓
  Image Select   Validation          Batch Write    Snapshot       UI Update
```

**Real-time Message Sync**:
```
Firestore Change → Snapshot Listener → Repository Callback → ViewModel → RecyclerView
       ↓                  ↓                    ↓              ↓              ↓
   New Message        onEvent()           Parse Data      LiveData      Adapter
       ↓                  ↓                    ↓              ↓              ↓
   Document Add       Real-time           Message Object   Observer      Insert Item
```

## 5. Event Management Flow

### 5.1 Event Creation
```
CreateEventActivity → EventViewModel → EventRepository → Firestore → Group Members
        ↓                  ↓              ↓              ↓              ↓
    Form Input         Validation      Create Event    Document       Notification
        ↓                  ↓              ↓              ↓              ↓
    Date/Time         Business Rules   Generate ID     Batch Write    Real-time
```

**Date Validation Flow**:
```java
// Input Validation
startTime > currentTime && endTime > startTime
↓
// Business Logic
if (startTime < System.currentTimeMillis()) {
    return ValidationResult.error("Event cannot be in the past");
}
↓
// Firestore Document
Event {
    startTime: validated_timestamp,
    endTime: validated_timestamp,
    status: "active"
}
```

### 5.2 RSVP Flow
```
EventDetailActivity → RSVP Selection → EventRepository → Counter Update → Real-time Sync
        ↓                  ↓              ↓              ↓              ↓
    View Event         Tap Button      Create RSVP     Increment      All Viewers
        ↓                  ↓              ↓              ↓              ↓
    Load Details       User Choice     Batch Write     Denormalized   UI Refresh
```

## 6. Offline Data Flow

### 6.1 Offline Write Operations
```
User Action → Local Cache → Queue Operation → Network Available → Firestore Sync
     ↓            ↓              ↓              ↓              ↓
  Create Post   Store Local    Add to Queue   Connection     Upload Data
     ↓            ↓              ↓              ↓              ↓
  UI Update     Optimistic     Retry Logic    Auto Detect    Real-time
```

### 6.2 Offline Read Operations
```
Data Request → Check Cache → Return Cached → Background Sync → Update UI
      ↓            ↓              ↓              ↓              ↓
  Load Posts   Local Storage   Display Data   Fetch Latest   Merge Changes
      ↓            ↓              ↓              ↓              ↓
  User Action  Firestore Cache Immediate UX   When Online    Seamless
```

## 7. Image Upload Flow

### 7.1 Complete Image Pipeline
```
Image Selection → Compression → Firebase Storage → URL Generation → Firestore Update
       ↓              ↓              ↓                    ↓              ↓
   Gallery/Camera  Reduce Size    Organized Path      Download URL    Reference
       ↓              ↓              ↓                    ↓              ↓
   Multiple Files  Background     /images/{type}/     Permanent Link  Display
       ↓              ↓              ↓                    ↓              ↓
   User Choice    Progress UI     Unique Names        Success/Error   UI Update
```

**Storage Path Organization**:
```
/images/
├── users/{userId}/
│   ├── avatar_{timestamp}.jpg
│   └── profile_{timestamp}.jpg
├── groups/{groupId}/
│   ├── cover_{timestamp}.jpg
│   └── avatar_{timestamp}.jpg
└── posts/{groupId}/
    ├── {postId}_1_{timestamp}.jpg
    └── {postId}_2_{timestamp}.jpg
```

## 8. Error Handling Flow

### 8.1 Network Error Recovery
```
Operation Failure → Error Detection → Retry Logic → User Notification → Manual Retry
       ↓                 ↓              ↓              ↓              ↓
   Network Timeout    Catch Exception  Exponential    Show Message   User Action
       ↓                 ↓              ↓              ↓              ↓
   Firebase Error     Log Error       Backoff Delay  Retry Button   Re-attempt
```

### 8.2 Validation Error Flow
```
User Input → Client Validation → Server Rules → Error Response → UI Feedback
     ↓              ↓              ↓              ↓              ↓
  Form Data      Check Rules     Firestore      Permission     Error Message
     ↓              ↓              ↓              ↓              ↓
  Submit Form    Real-time       Security       Denied         Field Highlight
```

## 9. Performance Optimization Flow

### 9.1 Data Loading Strategy
```
Screen Load → Check Cache → Load Cached → Background Fetch → Update UI
     ↓            ↓              ↓              ↓              ↓
  User Navigate Local Storage  Immediate      Fresh Data     Merge/Replace
     ↓            ↓              ↓              ↓              ↓
  Fast Response  Offline Data   Good UX        Latest Info    Seamless
```

### 9.2 Image Loading Optimization
```
Image Request → Glide Cache → Memory/Disk → Network Load → Cache Store
      ↓            ↓              ↓              ↓              ↓
  Display Need   Check Cache    Return Fast    Download       Store Local
      ↓            ↓              ↓              ↓              ↓
  RecyclerView   LRU Memory     Immediate      Background     Future Use
```