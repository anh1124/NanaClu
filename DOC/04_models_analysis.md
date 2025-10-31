# Models và Data Classes

## 1. Danh sách Models
| Model Name  | Purpose                             | Firebase Collection                            | Key Properties                       |
|-------------|-------------------------------------|------------------------------------------------|--------------------------------------|
| User        | User profile và authentication data | users                                          | userId, email, displayName, status   |
| UserImage   | User's custom avatar images         | users/{userId}/images                          | imageId, storageUrl, createdAt       |
| Group       | Group/community information         | groups                                         | groupId, name, code, memberCount     |
| Member      | Group membership data               | groups/{groupId}/members                       | userId, role, status, joinedAt       |
| Post        | Group posts/content                 | groups/{groupId}/posts                         | postId, content, imageUrls, authorId |
| Comment     | Post comments                       | groups/{groupId}/posts/{postId}/comments       | commentId, content, authorId         |
| Like        | Post likes                          | groups/{groupId}/posts/{postId}/likes          | userId, createdAt                    |
| Event       | Group events                        | groups/{groupId}/events                        | eventId, title, startTime, location  |
| EventRSVP   | Event attendance                    | groups/{groupId}/events/{eventId}/rsvps        | userId, attendanceStatus             |
| Participant | Event participants (legacy)         | groups/{groupId}/events/{eventId}/participants | userId, status                       |
| Chat        | Chat conversations                  | chats                                          | chatId, type, memberIds, lastMessage |
| ChatMember  | Chat membership                     | chats/{chatId}/members                         | userId, role, lastRead               |
| Message     | Chat messages                       | chats/{chatId}/messages                        | messageId, content, senderId, type   |
| FileAttachment | Files sent in chat                | chats/{chatId}/messages (fileUrls in message)  | fileUrls, fileName, size, mimeType   |

## 2. Chi tiết từng Model

### 2.1 User Model
```java
public class User {
    public String userId;
    public long createdAt;
    public String email;
    public String displayName;
    public String photoUrl;        // Google profile photo
    public String avatarImageId;   // Custom avatar reference
    public long lastLoginAt;
    public String status;          // "online" | "offline"
    
    public User() {} // Required for Firestore
    
    public User(String userId, String email, String displayName) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = System.currentTimeMillis();
        this.lastLoginAt = System.currentTimeMillis();
        this.status = "online";
    }
}
```
**Purpose**: Lưu trữ thông tin profile người dùng và trạng thái online/offline  
**Properties**:
- `userId`: Primary key, Firebase Auth UID
- `email`: Email đăng nhập
- `displayName`: Tên hiển thị
- `photoUrl`: URL ảnh từ Google (nếu login bằng Google)
- `avatarImageId`: Reference đến custom avatar trong subcollection
- `status`: Trạng thái online/offline cho chat features

### 2.2 UserImage Model
```java
public class UserImage {
    public String imageId;
    public long createdAt;
    public String storageUrl;
    
    public UserImage() {}
    
    public UserImage(String imageId, String storageUrl) {
        this.imageId = imageId;
        this.storageUrl = storageUrl;
        this.createdAt = System.currentTimeMillis();
    }
}
```
**Purpose**: Quản lý custom avatar images của user  
**Properties**:
- `imageId`: Unique identifier cho image
- `storageUrl`: Firebase Storage download URL
- `createdAt`: Timestamp tạo image

### 2.3 Group Model
```java
public class Group {
    public String groupId;
    public String name;
    public String description;
    public String code;           // Join code (6 chars)
    public long createdAt;
    public String createdBy;      // userId of creator
    public int memberCount;
    public String avatarUrl;
    public String coverUrl;
    public boolean isPrivate;
    
    public Group() {}
    
    public Group(String name, String description, String createdBy, boolean isPrivate) {
        this.groupId = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.isPrivate = isPrivate;
        this.createdAt = System.currentTimeMillis();
        this.memberCount = 1; // Creator is first member
    }
}
```
**Purpose**: Lưu trữ thông tin nhóm/cộng đồng  
**Properties**:
- `code`: Mã tham gia nhóm (6 ký tự unique)
- `memberCount`: Số lượng thành viên (denormalized cho performance)
- `isPrivate`: Nhóm riêng tư hay công khai

### 2.4 Member Model
```java
public class Member {
    public String userId;
    public String role;           // "owner" | "admin" | "member"
    public String status;         // "active" | "banned"
    public long joinedAt;
    public String userName;       // Denormalized for display
    
    public Member() {}
    
    public Member(String userId, String role) {
        this.userId = userId;
        this.role = role;
        this.status = "active";
        this.joinedAt = System.currentTimeMillis();
    }
}
```
**Purpose**: Quản lý thành viên nhóm và phân quyền  
**Properties**:
- `role`: Phân quyền trong nhóm (owner > admin > member)
- `status`: Trạng thái thành viên (active/banned)
- `userName`: Denormalized displayName để hiển thị nhanh

### 2.5 Post Model
```java
public class Post {
    public String postId;
    public String groupId;
    public String content;
    public List<String> imageUrls;
    public long createdAt;
    public String authorId;
    public String authorName;     // Denormalized
    public int likeCount;         // Denormalized
    public int commentCount;      // Denormalized
    public boolean isDeleted;
    public long deletedAt;
    
    public Post() {}
    
    public Post(String groupId, String content, String authorId, String authorName) {
        this.groupId = groupId;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = System.currentTimeMillis();
        this.likeCount = 0;
        this.commentCount = 0;
        this.isDeleted = false;
    }
}
```
**Purpose**: Lưu trữ bài viết trong nhóm  
**Properties**:
- `imageUrls`: List URL ảnh đính kèm
- `likeCount`, `commentCount`: Denormalized counters
- `isDeleted`: Soft delete thay vì hard delete

### 2.6 Comment Model
```java
public class Comment {
    public String commentId;
    public String postId;
    public String content;
    public String authorId;
    public String authorName;     // Denormalized
    public long createdAt;
    public boolean isDeleted;
    
    public Comment() {}
    
    public Comment(String postId, String content, String authorId, String authorName) {
        this.postId = postId;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = System.currentTimeMillis();
        this.isDeleted = false;
    }
}
```
**Purpose**: Bình luận trên bài viết  
**Properties**:
- `authorName`: Denormalized để hiển thị nhanh
- `isDeleted`: Soft delete support

### 2.7 Like Model
```java
public class Like {
    public String userId;
    public long createdAt;
    
    public Like() {}
    
    public Like(String userId) {
        this.userId = userId;
        this.createdAt = System.currentTimeMillis();
    }
}
```
**Purpose**: Lưu trữ like trên bài viết  
**Properties**: Đơn giản chỉ có userId và timestamp

### 2.8 Event Model
```java
public class Event {
    public String eventId;
    public String groupId;
    public String title;
    public String description;
    public long startTime;
    public long endTime;
    public String location;
    public String createdBy;
    public String creatorName;    // Denormalized
    public String status;         // "active" | "cancelled" | "ended"
    public long createdAt;
    public int goingCount;        // Denormalized
    public int notGoingCount;     // Denormalized
    public int maybeCount;        // Denormalized
    
    public Event() {}
    
    public Event(String groupId, String title, String description, 
                 long startTime, String location, String createdBy, String creatorName) {
        this.groupId = groupId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.location = location;
        this.createdBy = createdBy;
        this.creatorName = creatorName;
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
        this.goingCount = 0;
        this.notGoingCount = 0;
        this.maybeCount = 0;
    }
}
```
**Purpose**: Quản lý sự kiện trong nhóm  
**Properties**:
- `status`: Trạng thái sự kiện (active/cancelled/ended)
- `goingCount`, `notGoingCount`, `maybeCount`: Denormalized attendance counters

### 2.9 EventRSVP Model
```java
public class EventRSVP {
    public String userId;
    public String eventId;
    public String attendanceStatus; // "attending" | "not_attending" | "maybe"
    public String status;           // Backward compatibility
    public long createdAt;
    public long updatedAt;
    
    public EventRSVP() {}
    
    public EventRSVP(String userId, String eventId, String attendanceStatus) {
        this.userId = userId;
        this.eventId = eventId;
        this.attendanceStatus = attendanceStatus;
        this.status = attendanceStatus; // Backward compatibility
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}
```
**Purpose**: Lưu trữ phản hồi tham gia sự kiện  
**Properties**:
- `attendanceStatus`: Trạng thái tham gia mới
- `status`: Backward compatibility với version cũ

### 2.10 Chat Model
```java
public class Chat {
    public String chatId;
    public String type;           // "private" | "group"
    public List<String> participants; // For private chats
    public List<String> memberIds;    // For group chats
    public String groupId;        // If group chat
    public String pairKey;        // For private chats: "userId1_userId2"
    public String lastMessage;
    public long lastMessageAt;
    public String lastMessageBy;
    public long createdAt;
    
    public Chat() {}
    
    public Chat(String type, List<String> memberIds) {
        this.chatId = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.memberIds = memberIds;
        this.participants = memberIds; // Backward compatibility
        this.createdAt = System.currentTimeMillis();
        this.lastMessageAt = System.currentTimeMillis();
    }
}
```
**Purpose**: Quản lý chat conversations  
**Properties**:
- `type`: Phân biệt private chat và group chat
- `pairKey`: Unique key cho private chat (userId1_userId2)
- `participants`, `memberIds`: Backward compatibility

### 2.11 Message Model
```java
public class Message {
    public String messageId;
    public String chatId;
    public String senderId;
    public String senderName;     // Denormalized
    public String content;
    public String type;           // "text" | "image" | "file"
    public List<String> imageUrls;
    public List<String> fileUrls;
    public long createdAt;
    public long editedAt;
    public boolean isDeleted;
    
    public Message() {}
    
    public Message(String chatId, String senderId, String senderName, 
                   String content, String type) {
        this.chatId = chatId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.isDeleted = false;
    }
}
```
**Purpose**: Lưu trữ tin nhắn chat  
**Properties**:
- `type`: Phân biệt text/image/file message
- `imageUrls`: List URL ảnh đính kèm
- `fileUrls`: List URL tệp đính kèm
- `editedAt`: Support edit message feature

### 2.12 FileAttachment Model
```java
public class FileAttachment {
    public String fileName;
    public String mimeType;
    public long size;
    public String url; // Firebase Storage download URL

    public FileAttachment() {}

    public FileAttachment(String fileName, String mimeType, long size, String url) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.size = size;
        this.url = url;
    }
}
```
**Purpose**: Mô tả metadata của tệp đính kèm trong tin nhắn  
**Properties**:
- `fileName`, `mimeType`, `size`, `url`

## 3. Sơ đồ quan hệ Models

```
User (1) ──────────── (N) UserImage
  │
  │ (1:N)
  ├─── Member ──────── (N:1) Group
  │                      │
  │                      │ (1:N)
  │                      ├─── Post ──┬─── (1:N) Comment
  │                      │           └─── (1:N) Like
  │                      │
  │                      └─── Event ──── (1:N) EventRSVP
  │
  │ (N:M via ChatMember)
  └─── Chat ──┬─── (1:N) Message
              └─── (1:N) ChatMember

Relationships:
- User → UserImage: 1:N (user sẽ lưu những image mà họ đăng lên vào subcollection của họ)
- User → Member: 1:N (user có thể join nhiều groups)
- Group → Member: 1:N (group có nhiều members)
- Group → Post: 1:N (group có nhiều posts)
- Group → Event: 1:N (group có nhiều events)
- Post → Comment: 1:N (post có nhiều comments)
- Post → Like: 1:N (post có nhiều likes)
- Event → EventRSVP: 1:N (event có nhiều RSVPs)
- Chat → Message: 1:N (chat có nhiều messages)
- Chat → ChatMember: 1:N (chat có nhiều members)
- User → Chat: N:M (user có thể tham gia nhiều chats)
```

## 4. Design Patterns trong Models

### 4.1 Denormalization Pattern
Nhiều models sử dụng denormalized data để cải thiện performance:
- `authorName` trong Post, Comment, Message
- `creatorName` trong Event
- `userName` trong Member
- `likeCount`, `commentCount` trong Post
- `goingCount`, `notGoingCount`, `maybeCount` trong Event

### 4.2 Soft Delete Pattern
Các models quan trọng sử dụng soft delete:
- Post: `isDeleted`, `deletedAt`
- Comment: `isDeleted`
- Message: `isDeleted`

### 4.3 Timestamp Pattern
Tất cả models đều có timestamp tracking:
- `createdAt`: Thời gian tạo
- `updatedAt`: Thời gian cập nhật (một số models)
- `editedAt`: Thời gian chỉnh sửa (Message)

### 4.4 Status Enum Pattern
Sử dụng string constants cho status:
- User status: "online" | "offline"
- Member status: "active" | "banned"
- Event status: "active" | "cancelled" | "ended"
- RSVP status: "attending" | "not_attending" | "maybe"

## 5. Validation và Business Rules

### 5.1 Group Code Generation
- Group code phải unique (6 ký tự)
- Retry mechanism nếu code bị trùng

### 5.2 Member Role Hierarchy
- owner > admin > member
- Chỉ owner có thể transfer ownership
- Admin có thể manage members nhưng không thể remove owner

### 5.3 Event Time Validation
- `startTime` phải < `endTime`
- Không thể tạo event trong quá khứ
- Event status tự động update dựa trên thời gian

### 5.4 Chat Type Rules
- Private chat: chỉ 2 participants
- Group chat: có thể có nhiều members
- pairKey format: "smallerUserId_largerUserId"