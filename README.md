# Group Management App (Android + Firebase)

## 🎯 Mục tiêu dự án
Ứng dụng Android hỗ trợ **quản lý nhóm, sự kiện và tương tác xã hội**.  
Người dùng có thể tạo nhóm, tham gia nhóm, đăng bài viết, bình luận, chat riêng/tập thể và quản lý sự kiện.  
Dự án được thiết kế để mô phỏng một hệ thống mạng xã hội mini, với trọng tâm là **quản lý cộng đồng và nhóm**.

---

## ✨ Tính năng chính
- **Xác thực (Authentication)**: đăng nhập/đăng ký bằng Firebase Auth (Google hoặc Email/Password).  
- **Quản lý người dùng**: chỉnh sửa hồ sơ, ảnh đại diện.  
- **Nhóm (Groups)**  
  - Tạo nhóm (public/private).  
  - Quản lý thành viên (mời, chấp nhận, từ chối, phân quyền admin/owner).  
  - Xóa/đổi tên nhóm.  
- **Bài viết (Posts)**  
  - Tạo bài viết (văn bản + ảnh base64).  
  - Bình luận, like/unlike.  
  - Xóa hoặc chỉnh sửa bài viết.  
- **Chat**  
  - Chat riêng (private).  
  - Chat nhóm.  
- **Sự kiện (Events)**  
  - Tạo sự kiện trong nhóm.  
  - Tham gia/hủy tham gia.  
  - Quản lý trạng thái sự kiện (scheduled, canceled, end).  

---

## 🏗️ Công nghệ & Kiến trúc
- **Ngôn ngữ:** Java  
- **Kiến trúc:** MVVM (Model - View - ViewModel)  
- **Database:** Firebase Firestore (NoSQL)  
- **Authentication:** Firebase Auth (Google, Email/Password)  
  

📌 MVVM flow:
- **Model:** Các `POJO` class (User, Group, Post, Event, Chat, …) map trực tiếp với Firestore.  
- **Repository:** Chịu trách nhiệm đọc/ghi dữ liệu từ Firestore.  
- **ViewModel:** Xử lý logic, cung cấp `LiveData` cho UI.  
- **View (Activity/Fragment):** Quan sát dữ liệu từ ViewModel và hiển thị UI.  

---

## 📂 Cấu trúc dự án
app/
├── data/
│ ├── model/ # Các model Java (POJO)
│ │ ├── User.java
│ │ ├── UserImage.java
│ │ ├── Group.java
│ │ ├── Member.java
│ │ ├── Post.java
│ │ ├── Comment.java
│ │ ├── Like.java
│ │ ├── Event.java
│ │ ├── Participant.java
│ │ ├── Chat.java
│ │ ├── ChatMember.java
│ │ └── Message.java
│ ├── repository/ # Firestore repository (UserRepository, GroupRepository, ChatRepository, ...)
│
├── ui/
│ ├── auth/ # Login (Google, Email/Password) & Register
│ ├── home/ # Trang chủ, feed bài viết
│ ├── group/ # Group list, group detail
│ ├── chat/ # Chat list, chat room
│ ├── event/ # Event list, event detail
│ └── profile/ # User profile & settings
│
├── viewmodel/ # ViewModel cho từng module
│ ├── UserViewModel.java
│ ├── GroupViewModel.java
│ ├── PostViewModel.java
│ ├── EventViewModel.java
│ └── ChatViewModel.java
│
└── utils/ # Helper, constants (Date/time utils, paging helpers)

### 📦 Firestore schema (đề xuất)
- `users/{userId}` → User
  - `images/{imageId}` → UserImage
- `groups/{groupId}` → Group
  - `members/{userId}` → Member
  - `posts/{postId}` → Post
    - `comments/{commentId}` → Comment
    - `likes/{userId}` → Like
- `chats/{chatId}` → Chat
  - `members/{userId}` → ChatMember
  - `messages/{messageId}` → Message
- `events/{eventId}` → Event
  - `participants/{userId}` → Participant


---

## ✅ Quy tắc & Chuẩn cần tuân thủ
1. **MVVM chuẩn**: View không gọi trực tiếp Firestore, chỉ lấy dữ liệu qua ViewModel.  
2. **Model POJO**: Chỉ có field + constructor rỗng. Getter/Setter tuỳ bạn cài thêm.  
3. **Firestore Rule**:  
   - User chỉ được sửa thông tin của chính mình.  
   - Admin/Owner mới có quyền xóa/chỉnh sửa bài viết trong nhóm.  
   - Chỉ người tạo sự kiện mới có quyền huỷ sự kiện.  
4. **Ảnh**: Dùng `base64` thay cho Firebase Storage (theo yêu cầu đề tài).  
5. **Cập nhật dữ liệu chat với FCM**: Không sử dụng `addSnapshotListener`. Dùng **Firebase Cloud Messaging (FCM)** để nhận thông báo khi có tin nhắn/sự kiện mới và kích hoạt đồng bộ có điều kiện (fetch theo `createdAt > lastReadAt`). Hạn chế/không sử dụng polling định kỳ.  
6. **Code style**:  
   - Tên class PascalCase (`UserViewModel`)  
   - Tên biến camelCase (`createdAt`, `authorId`)  
   - Comment code rõ ràng cho Repository & ViewModel.  

---


---

## 🔁 Chiến lược cập nhật dữ liệu với FCM
- **Chat (ưu tiên FCM)**: Khi có tin nhắn mới, server hoặc Cloud Function gửi FCM tới các thiết bị liên quan. Ứng dụng nhận thông báo (foreground/background) và đồng bộ có điều kiện: `orderBy(createdAt)` + `where(createdAt > lastReadAt)` + `limit N`. Có thể kết hợp `WorkManager` để đảm bảo đồng bộ nền đáng tin cậy.  
- **Feed bài viết & bình luận**: Kéo để làm mới (pull-to-refresh) và phân trang `limit/offset` hoặc `startAfter`. Có thể cân nhắc FCM cho sự kiện quan trọng (ví dụ: có bình luận mới) để kích hoạt refresh tinh gọn.  
- **Badge/đếm số**: Tính toán phía client sau mỗi lần fetch; tránh đếm động tốn chi phí.  
- **Giảm chi phí & hạn chế**: Cache trong `ViewModel`, chỉ gọi lại khi màn hình active; tránh gọi khi app nền; thêm chỉ số/điều kiện truy vấn phù hợp (index, `whereEqualTo`, `orderBy`).  

---
// ---------------- Image (subcollection of User) ----------------
public class UserImage {
    public String imageId;
    public long createdAt;
    public String base64Code;

    public UserImage() {}
}
// ---------------- User ----------------
// user có thể sử dụng Google Auth hoặc Email/Password
public class User {
    public String userId;
    public long createdAt;
    public String email; // với Google: email từ provider; với Email/Password: email đăng ký
    public String displayName;
    public String avatarImageId; // trỏ tới ảnh đại diện trong subcollection images
    public long lastLoginAt; // lần đăng nhập gần nhất (server time millis)
    public String status; // "online" | "offline"

    public User() {}
}
// ---------------- Chat ----------------
public class Chat {
    public String chatId;
    public long createdAt;
    public String type; // "private" | "group"
    public int memberCount; // dữ liệu thành viên ở subcollection ChatMember

    public Chat()  {}
}

// Subcollection: chats/{chatId}/members/{userId}
public class ChatMember {
    public String userId;
    public long joinedAt; 
    public Long lastReadAt; // hỗ trợ tính unread client-side
    public String role; // optional: "admin" | "member"

    public ChatMember() {}
}
public class Message {
    public String messageId;
    public String authorId;
    public String type; // "text" | "image" | "file"
    public String content; // nếu type = "image" => chứa imageId
    public long createdAt;

    public Message() {}
}
// ---------------- Group ----------------
public class Group {
    public String groupId;
    public String name;
    public String avatarImageId;   // thay cho base64
    public String coverImageId;    // thay cho coverUrl
    public String description;
    public String createdBy;
    public long createdAt;
    public boolean isPublic;
    public int memberCount;
    public int postCount;

    public Group() {}
}

public class Member {
    public String userId;
    public String role;   // "admin" | "member" | "owner"
    public long joinedAt;
    public String status; // "active" | "pending" | "banned"

    public Member() {}
}
// ---------------- Post ----------------
public class Post {
    public String postId;
    public String authorId;
    public String content;
    public String imageId; // thay cho img base64
    public long createdAt;
    public Long deletedAt;
    public Long editedAt;
    public int likeCount;
    public int commentCount;

    public Post() {}
}

public class Comment {
    public String commentId;
    public String authorId;
    public String content;
    public int likeCount;
    public long createdAt;

    public Comment() {}
}

public class Like {
    public String userId;
    public long createdAt;

    public Like() {}
}
// ---------------- Event ----------------
public class Event {
    public String eventId;
    public String title;
    public String description;
    public long startAt;
    public long endAt;
    public String imageId;   // thay cho imgBase64
    public String createdBy;
    public String status;    // "scheduled" | "canceled" | "end"
    public int maxParticipants; // giới hạn số người tham gia

    public Event() {}
}

public class Participant {
    public String userId;
    public long joinedAt;
    public String status; // "joined" | "canceled" | "pending"

    public Participant() {}
}
