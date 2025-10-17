# Tổng quan Project NanaClu

## 1. Thông tin cơ bản
- **Tên project**: NanaClu
- **Package name**: com.example.nanaclu
- **Version**: 1.0 (versionCode: 1)
- **Target SDK**: 35 (Android 14)
- **Minimum SDK**: 31 (Android 12)
- **Ngôn ngữ**: Java 11
- **Kiến trúc**: MVVM (Model-View-ViewModel)

## 2. Mục đích ứng dụng
NanaClu là một **ứng dụng Android quản lý nhóm và cộng đồng** với đầy đủ tính năng mạng xã hội. Ứng dụng tập trung vào việc tạo và quản lý các nhóm, tương tác xã hội, và tổ chức sự kiện trong cộng đồng.

### Tính năng chính:

#### 🔐 **Xác thực (Authentication)**
- **Chức năng**: Đăng nhập/đăng ký người dùng
- **Công nghệ**: Firebase Auth với Google Sign-in và Email/Password
- **Cách hoạt động**:
  - Google Sign-in: Sử dụng Google Play Services Auth API
  - Email/Password: Firebase Auth với email verification
  - Auto-login: Lưu trạng thái đăng nhập trong SharedPreferences
  - User profile: Tự động tạo/cập nhật User document trong Firestore
- **Bảo mật**: Token-based authentication, secure session management

#### 👥 **Quản lý nhóm (Group Management)**
- **Chức năng**: Tạo, tham gia, quản lý nhóm và thành viên
- **Công nghệ**: Firebase Firestore với subcollections
- **Cách hoạt động**:
  - **Tạo nhóm**: Tự động tạo mã code 6 ký tự, thiết lập quyền owner
  - **Tham gia nhóm**: Quét mã code hoặc tìm kiếm, xử lý yêu cầu approval
  - **Phân quyền**: Owner > Admin > Member với các quyền khác nhau
  - **Quản lý thành viên**: Block/unblock, approve/reject pending users
  - **Transfer ownership**: Chuyển quyền sở hữu nhóm
- **Data structure**: `groups/{groupId}/members/{userId}` với role-based access

#### 📝 **Bài viết (Posts & Interactions)**
- **Chức năng**: Đăng bài, bình luận, like/unlike
- **Công nghệ**: Firebase Firestore + Firebase Storage + Glide
- **Cách hoạt động**:
  - **Đăng bài**: Upload ảnh lên Firebase Storage → lưu URL vào Post document
  - **Hiển thị**: Glide load ảnh với caching, pagination với `startAfter`
  - **Tương tác**: Real-time updates cho like count, comment count
  - **Bình luận**: Nested subcollection `posts/{postId}/comments/{commentId}`
  - **Like system**: Subcollection `posts/{postId}/likes/{userId}` với duplicate prevention
- **Performance**: Image compression, lazy loading, pull-to-refresh

#### 💬 **Chat (Messaging System)**
- **Chức năng**: Tin nhắn riêng tư và chat nhóm realtime
- **Công nghệ**: Firebase Firestore realtime listeners + Firebase Storage
- **Cách hoạt động**:
  - **Gửi tin nhắn**: Lưu Message document với `addSnapshotListener` cho realtime
  - **File đính kèm**: Upload file lên Firebase Storage → lưu FileAttachment metadata
  - **Hỗ trợ file**: PDF, DOC, images, audio, video với size limit 50MB
  - **Gallery**: PhotoGalleryActivity và FileGalleryActivity cho xem file
  - **Typing indicators**: Real-time status updates
  - **Message types**: text, image, file, mixed content
- **Data structure**: `chats/{chatId}/messages/{messageId}` với pagination

#### 📅 **Sự kiện (Event Management)**
- **Chức năng**: Tạo và quản lý sự kiện trong nhóm với RSVP
- **Công nghệ**: Firebase Firestore với complex queries
- **Cách hoạt động**:
  - **Tạo sự kiện**: Event document với startTime, endTime, location, imageUrl
  - **RSVP system**: EventRSVP subcollection với 3 trạng thái (attending, not_attending, maybe)
  - **Calendar view**: EventCalendarFragment hiển thị theo tháng
  - **Reminder system**: Cài đặt nhắc nhở trước sự kiện (30min, 1h, 1day)
  - **Event status**: active, cancelled, completed với auto-update
  - **Statistics**: Real-time count cho từng trạng thái RSVP
- **Data structure**: `groups/{groupId}/events/{eventId}/attendees/{userId}`

#### 🛡️ **Bảo mật (Security Features)**
- **Chức năng**: PIN protection, content reporting, user blocking
- **Công nghệ**: SharedPreferences + Firebase Firestore
- **Cách hoạt động**:
  - **PIN system**: 4-6 số PIN với hash storage, PinEntryActivity cho verification
  - **Content reporting**: ReportModel với reason classification và moderator workflow
  - **User blocking**: BlockedUsers subcollection với auto-removal from groups
  - **Admin controls**: Role-based access control với permission checking
- **Security**: PIN hashing, secure storage, audit trails

#### 👨‍💼 **Admin Dashboard**
- **Chức năng**: Quản trị hệ thống và xuất dữ liệu
- **Công nghệ**: Firebase Firestore + JSON export
- **Cách hoạt động**:
  - **Data export**: AdminRepository xuất toàn bộ dữ liệu ra JSON file
  - **Database cleanup**: Xóa dữ liệu cũ, orphaned documents
  - **User management**: Xem thống kê users, groups, posts
  - **Content moderation**: Xử lý reports, ban users
- **File export**: Lưu vào Downloads folder với timestamp

## 3. Công nghệ sử dụng

### Core Technologies:
- **Android SDK**: API 35 (compileSdk), minimum API 31
- **Java**: Java 11 compatibility
- **Firebase Services**:
  - Firebase Auth (Google Sign-in, Email/Password)
  - Firebase Firestore (NoSQL database)
  - Firebase Storage (image & file storage)

### Libraries & Dependencies:
- **AndroidX Lifecycle**: LiveData, ViewModel
- **Material Design Components**: UI components
- **Glide**: Image loading và caching
- **SwipeRefreshLayout**: Pull-to-refresh functionality
- **Preference library**: Settings management

## 4. Cấu trúc thư mục chi tiết

```
app/src/main/java/com/example/nanaclu/
├── data/
│   ├── model/                    # Data models (POJO classes)
│   │   ├── User.java            # User profile & authentication
│   │   ├── Group.java           # Group information
│   │   ├── Member.java          # Group membership
│   │   ├── Post.java            # Posts with image support
│   │   ├── Comment.java         # Post comments
│   │   ├── Like.java            # Post likes
│   │   ├── Event.java           # Group events
│   │   ├── EventRSVP.java       # Event attendance
│   │   ├── Chat.java            # Chat rooms
│   │   ├── ChatMember.java      # Chat membership
│   │   ├── Message.java         # Chat messages
│   │   ├── FileAttachment.java  # File attachments
│   │   ├── ReportModel.java     # Content reporting
│   │   └── UserImage.java       # User images
│   └── repository/              # Data access layer
│       ├── UserRepository.java
│       ├── GroupRepository.java
│       ├── PostRepository.java
│       ├── EventRepository.java
│       ├── ChatRepository.java
│       ├── MessageRepository.java
│       ├── FileRepository.java
│       ├── ReportRepository.java
│       └── AdminRepository.java
├── ui/
│   ├── auth/                    # Authentication
│   │   ├── LoginActivity.java
│   │   └── RegisterActivity.java
│   ├── home/                    # Home & Feed
│   │   └── FeedFragment.java
│   ├── group/                   # Group management
│   │   ├── GroupsFragment.java
│   │   ├── GroupDetailActivity.java
│   │   ├── GroupSettingsActivity.java
│   │   ├── GroupMembersActivity.java
│   │   ├── EditGroupInfoActivity.java
│   │   ├── TransferOwnershipActivity.java
│   │   ├── GroupBlockedUsersActivity.java
│   │   ├── GroupPendingMembersActivity.java
│   │   └── GroupNotificationsActivity.java
│   ├── post/                    # Post management
│   │   ├── CreatePostActivity.java
│   │   ├── PostDetailActivity.java
│   │   └── ImageViewerActivity.java
│   ├── chat/                    # Chat functionality
│   │   ├── ChatFragment.java
│   │   ├── ChatsFragment.java
│   │   ├── ChatRoomActivity.java
│   │   ├── MembersActivity.java
│   │   ├── PhotoGalleryActivity.java
│   │   └── FileGalleryActivity.java
│   ├── event/                   # Event management
│   │   ├── GroupEventActivity.java
│   │   ├── CreateEventActivity.java
│   │   ├── EventDetailActivity.java
│   │   ├── EventListFragment.java
│   │   ├── EventCalendarFragment.java
│   │   ├── EventDiscussionFragment.java
│   │   └── RSVPListFragment.java
│   ├── profile/                 # User profile
│   │   ├── ProfileActivity.java
│   │   └── ProfileFragment.java
│   ├── security/                # Security features
│   │   ├── SecurityActivity.java
│   │   ├── PinEntryActivity.java
│   │   ├── PinSetupDialog.java
│   │   └── PinVerifyDialog.java
│   ├── report/                  # Content reporting
│   │   ├── GroupReportActivity.java
│   │   ├── ReportBottomSheetDialogFragment.java
│   │   ├── ReportDetailFragment.java
│   │   └── ActiveGroupReportDashboardFragment.java
│   ├── admin/                   # Admin features
│   │   ├── AdminDashboardActivity.java
│   │   └── DatabaseCleanupActivity.java
│   ├── adapter/                 # RecyclerView adapters
│   ├── common/                  # Shared components
│   └── components/              # Reusable UI components
├── viewmodel/                   # ViewModels for MVVM
│   ├── AuthViewModel.java
│   ├── GroupViewModel.java
│   ├── ChatListViewModel.java
│   └── ChatRoomViewModel.java
├── utils/                       # Utility classes
│   ├── ThemeUtils.java
│   ├── FileActionsUtil.java
│   └── EventAttendeesCleanup.java
├── MainActivity.java            # App entry point
└── HomeActivity.java            # Main navigation
```

## 5. Permissions & Security

### Android Permissions:
- **CAMERA**: Chụp ảnh để đăng bài hoặc cập nhật avatar
- **READ_EXTERNAL_STORAGE**: Đọc ảnh từ bộ nhớ (Android 12 và thấp hơn)
- **READ_MEDIA_IMAGES**: Đọc ảnh từ bộ nhớ (Android 13+)
- **WRITE_EXTERNAL_STORAGE**: Ghi file (Android 9 và thấp hơn)
- **Hardware camera**: Tùy chọn, không bắt buộc

### Security Features:
- **PIN Protection**: Hệ thống PIN 4-6 số để bảo vệ ứng dụng
- **Content Reporting**: Báo cáo nội dung không phù hợp
- **User Blocking**: Chặn người dùng trong nhóm
- **Admin Controls**: Quản lý và kiểm duyệt nội dung

## 6. Database Schema (Firestore)

### Collections Structure:
```
users/{userId} → User
├── images/{imageId} → UserImage

groups/{groupId} → Group
├── members/{userId} → Member
├── posts/{postId} → Post
│   ├── comments/{commentId} → Comment
│   └── likes/{userId} → Like
├── events/{eventId} → Event
│   └── attendees/{userId} → EventRSVP
├── chats/{chatId} → Chat
│   └── members/{userId} → ChatMember
├── blockedUsers/{userId} → BlockedUser
└── pendingUsers/{userId} → PendingUser

chats/{chatId} → Chat
├── members/{userId} → ChatMember
└── messages/{messageId} → Message

reports/{reportId} → ReportModel
```

## 7. Đặc điểm kiến trúc

### MVVM Pattern:
- **Model**: POJO classes map trực tiếp với Firestore documents
- **Repository**: Tách biệt logic truy cập dữ liệu, xử lý Firebase operations
- **ViewModel**: Xử lý business logic, cung cấp LiveData cho UI
- **View**: Activities/Fragments quan sát ViewModel và hiển thị UI

### Key Features:
- **Realtime Updates**: Firestore snapshot listeners cho chat và comments
- **Image Storage**: Firebase Storage thay vì base64 để tối ưu hiệu suất
- **File Attachments**: Hỗ trợ đính kèm file trong chat (PDF, DOC, images, etc.)
- **Offline Support**: Firestore offline persistence
- **Push Notifications**: FCM cho thông báo hệ thống
- **Data Export**: Admin có thể xuất dữ liệu JSON

## 8. Navigation & User Flow

### Main Navigation (Bottom Navigation):
1. **Home**: Feed bài viết từ các nhóm đã tham gia
2. **Groups**: Danh sách nhóm, tạo nhóm mới
3. **Chat**: Danh sách cuộc trò chuyện
4. **Profile**: Thông tin cá nhân và cài đặt

### Key User Journeys:
- **Join Group**: Tìm nhóm bằng mã code → Gửi yêu cầu → Được chấp nhận
- **Create Post**: Chọn nhóm → Viết nội dung → Thêm ảnh → Đăng bài
- **Event Management**: Tạo sự kiện → Mời thành viên → Theo dõi RSVP
- **Chat**: Bắt đầu cuộc trò chuyện → Gửi tin nhắn/file → Xem lịch sử

## 9. Performance & Optimization

### Data Management:
- **Pagination**: Sử dụng `startAfter` cho danh sách dài
- **Caching**: ViewModel cache để giảm network calls
- **Image Optimization**: Glide với caching và compression
- **Batch Operations**: Sử dụng Firestore batch writes

### Cost Optimization:
- **Selective Listeners**: Chỉ lắng nghe khi cần thiết
- **Indexed Queries**: Tối ưu Firestore queries với proper indexing
- **Data Structure**: Efficient data modeling để giảm reads

## 10. Development & Maintenance

### Code Quality:
- **Consistent Naming**: PascalCase cho classes, camelCase cho variables
- **Error Handling**: Comprehensive error handling với user-friendly messages
- **Logging**: Structured logging cho debugging
- **Documentation**: Inline comments cho complex logic

### Testing Strategy:
- **Unit Tests**: Repository và ViewModel testing
- **Integration Tests**: Firebase integration testing
- **UI Tests**: Critical user flows testing

---

*Tài liệu này được cập nhật dựa trên phân tích toàn bộ codebase hiện tại của project NanaClu.*