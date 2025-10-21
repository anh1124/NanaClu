## Tổng quan dự án NanaClu

### Thông tin cơ bản
- **Tên dự án**: NanaClu
- **Package**: `com.example.nanaclu`
- **Phiên bản**: 1.0 (versionCode: 1)
- **Target SDK**: 35 (Android 14)
- **Min SDK**: 31 (Android 12)
- **Ngôn ngữ**: Java 11
- **Kiến trúc**: MVVM (Model — Repository — ViewModel — View)

### Mục tiêu ứng dụng
Ứng dụng mạng xã hội định hướng nhóm/cộng đồng: người dùng tham gia nhóm, đăng bài, bình luận/like, nhắn tin riêng hoặc nhóm, tạo sự kiện và quản trị nội dung.

## Nền tảng & công nghệ
- **Firebase**: Auth, Firestore (database realtime), Storage (media/files)
- **AndroidX**: Lifecycle (LiveData, ViewModel), RecyclerView, Navigation cơ bản
- **UI**: Material Components, Glide (ảnh)
- **Realtime**: Firestore snapshot listeners cho chat, comments, feed và (đang bổ sung) thông báo trong app

## Tính năng chính theo codebase
### Xác thực
- Đăng nhập Google và Email/Password bằng Firebase Auth
- Tự động tạo/ghép `users/{uid}` khi đăng nhập; cache thông tin người dùng vào SharedPreferences

### Nhóm (Groups)
- Tạo/ tham gia/ quản lý nhóm; phân quyền `owner | admin | member`
- Thành viên ở `groups/{groupId}/members/{userId}`; thống kê đếm cache (`memberCount`, `postCount`)

### Bài viết (Posts)
- Lưu ở `groups/{groupId}/posts/{postId}`; hỗ trợ nhiều ảnh (URL Storage)
- Pagination feed đa‑group trong `FeedFragment` (hợp nhất theo `createdAt`), pull‑to‑refresh
- Tương tác: like/unlike (`likes` subcollection), bình luận (`comments` subcollection) realtime

### Chat
- Chat riêng (global `chats/{chatId}`) và chat nhóm (`groups/{groupId}/chats/{chatId}`)
- Tin nhắn tại `.../messages/{messageId}`; realtime bằng `addSnapshotListener`
- Bộ sưu tập ảnh/file: `PhotoGalleryActivity`, `FileGalleryActivity`
- Logic ẩn hội thoại một phía dùng trường membership (`hidden`) đã có; lộ trình cải tiến: thêm `clearedAt` để ẩn lịch sử cũ và tự unhide khi có tin nhắn mới

### Sự kiện (Events)
- Tạo sự kiện trong nhóm, RSVP (`attendees`) và lịch tháng; đếm realtime theo trạng thái

### Báo cáo & Quản trị
- Báo cáo nội dung, dashboard quản trị, export JSON (AdminRepository)

### Bảo mật người dùng
- Mã PIN mở khóa ứng dụng (`PinEntryActivity`), chặn người dùng, quản trị theo vai trò

## Kiến trúc & cấu trúc mã
### MVVM
- **Model**: POJO ánh xạ tài liệu Firestore (`Post`, `Comment`, `Chat`, `Message`, `Group`, `Member`, `Event`, `FileAttachment`, ...)
- **Repository**: gói `data/repository` (Firestore/Storage); ví dụ `PostRepository`, `CommentRepository`, `ChatRepository`, `MessageRepository`, ...
- **ViewModel**: điều phối luồng dữ liệu cho UI (ví dụ `ChatListViewModel`, `ChatRoomViewModel`)
- **View**: `Activity`/`Fragment` quan sát `LiveData` (ví dụ `HomeActivity`, `FeedFragment`, `ChatFragment`, `PostDetailActivity`)

### Thư mục chính
```
app/src/main/java/com/example/nanaclu/
├── data/
│   ├── model/ (Post, Comment, Chat, Message, Group, Member, Event, ...)
│   └── repository/ (UserRepository, GroupRepository, PostRepository, CommentRepository,
│                    ChatRepository, MessageRepository, AdminRepository, ...)
├── ui/
│   ├── home/ (FeedFragment)
│   ├── group/ (GroupsFragment, Group* screens, PostAdapter, ...)
│   ├── post/ (CreatePostActivity, PostDetailActivity, ImageViewerActivity)
│   ├── chat/ (ChatFragment, ChatRoomActivity, *GalleryActivity)
│   ├── event/ (Event* screens)
│   ├── profile/ (ProfileActivity, ProfileFragment)
│   └── admin/, report/, security/ (PIN)
├── viewmodel/ (ChatListViewModel, ChatRoomViewModel, ...)
├── utils/ (ThemeUtils, Cache/cleanup helpers)
└── HomeActivity, MainActivity
```

## Dữ liệu & lược đồ (Firestore)
- Người dùng: `users/{userId}` (+ `images` subcollection)
- Nhóm: `groups/{groupId}` với `members`, `posts` (con `comments`, `likes`), `events` (con `attendees`), `chats` (con `members`)
- Chat riêng: `chats/{chatId}` + `members`, `messages`
- Báo cáo: `reports/{reportId}`
Thiết kế tối ưu realtime: subcollections, đếm cache, soft‑delete, timestamp nhất quán.

## Luồng dữ liệu trọng yếu
### Đăng bài (ảnh)
1) Ảnh nén → upload Firebase Storage → nhận URL
2) Tạo `Post` dưới `groups/{groupId}/posts/{postId}` kèm `imageUrls`
3) Feed đa‑group hợp nhất, sort theo `createdAt`

### Bình luận & like
- Ghi vào `comments`/`likes` subcollection và cập nhật đếm (FieldValue.increment)
- UI nhận thay đổi realtime qua listeners

### Nhắn tin
1) Gửi `Message` → cập nhật `chats.{lastMessage, lastMessageAt, ...}`
2) Người tham gia nhận realtime; phòng chat tải phân trang; gallery lấy từ Storage
3) Xóa một phía: đặt `hidden=true` (lộ trình: thêm `clearedAt` để ẩn lịch sử cũ và tự hiện lại khi có tin mới)

## Thông báo trong ứng dụng (in‑app)
- Định hướng triển khai: subcollection per‑user `users/{uid}/notices/{noticeId}`; đồng bộ realtime, hiển thị chỉ trong app (không dùng FCM)
- Nguồn sự kiện: duyệt bài, tin nhắn mới (khi người nhận không ở phòng), comment/like bài viết, sự kiện nhóm
- Thành phần (đề xuất): `NoticeRepository`, `NoticeCenter` (listener + banner + badge), `NotificationsActivity`, `NoticeAdapter`
- Menu `action_notice` trên toolbar sẽ mở danh sách thông báo và hiển thị badge chưa đọc

## Media & hiệu năng
- Ảnh: Glide + nén client; Storage URLs; caching
- Video (lộ trình demo 5–10MB): upload Storage (`videos/group_posts/...`), tạo thumbnail JPEG; phát bằng ExoPlayer (Media3) trong `VideoPlayerActivity`; không autoplay trong feed
- Phân trang/listeners chọn lọc để tối ưu chi phí Firestore

## Quyền & bảo mật
### Quyền Android
- Đọc ảnh: `READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32)
- (Lộ trình video) `READ_MEDIA_VIDEO` (API 33+)

### Nguyên tắc bảo mật
- Chỉ người đăng nhập được đọc/ghi nội dung
- Rule Firestore theo membership/role cho groups/chats; soft delete
- Không sử dụng push OS; thông báo chỉ trong app qua Firestore

## Lộ trình ngắn hạn
- Thêm hệ thống thông báo in‑app (repo + UI + badge)
- Cải tiến nhắn tin: `clearedAt`/auto‑unhide trên tin nhắn mới
- Hỗ trợ post video demo với ExoPlayer

## Điều hướng
- Bottom navigation trong `HomeActivity`: Home (Feed) — Groups — Chat — Profile
- Toolbar action: Search, Notifications (sẽ mở danh sách thông báo)

## Kiểm thử & tối ưu
- Phân trang `startAfter`, offline persistence của Firestore, batch writes
- Log có cấu trúc, thông báo lỗi thân thiện, đo P95 cho feed/chat

