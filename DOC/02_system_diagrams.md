## Danh mục sơ đồ hệ thống (ASCII Markdown)

Lưu ý chung:
- Tất cả sơ đồ dùng khoảng trắng (space), không dùng tab để tránh lỗi hiển thị.
- Các sơ đồ mang tính minh họa, dễ đọc trong code viewer và thuận tiện để vẽ lại bằng draw.io.
- Ký hiệu đơn giản: hình chữ nhật cho tác nhân/thành phần, mũi tên bằng ký tự '-', '>' cho luồng, nhóm bằng khung "+----".


### 1) Use Case Diagram (Tổng quan chức năng chính)
Mô tả ngắn: Các tác nhân chính và chức năng tương tác trong ứng dụng MXH thu nhỏ.

Mô tả chi tiết:
- Người dùng (User) có thể đăng ký/đăng nhập, quản lý hồ sơ, tham gia nhóm, tạo bài viết, bình luận, thả tim, chat, RSVP sự kiện.
- Quản trị (Admin/Owner) có thể duyệt bài, quản lý thành viên, chặn/kick, xem thống kê, nhật ký hoạt động.
- Hệ thống FCM gửi thông báo đẩy các sự kiện quan trọng (tin nhắn mới, mention, yêu cầu kết bạn, phê duyệt...).
- Firebase (Auth, Firestore, Storage) cung cấp nền tảng xác thực, dữ liệu realtime, lưu trữ tệp.

```
      +-------------------+                 +----------------------+
      |      Người dùng   |                 |        Quản trị      |
      +---------+---------+                 +-----------+----------+
                |                                       |
                |                                       |
                v                                       v
      +-------------------+                    +-------------------+
      |  Đăng ký/Đăng nhập|<------------------>| Quản lý người dùng|
      +-------------------+                    +-------------------+
                |
                v
      +-------------------+         +-------------------+         +------------------+
      |   Quản lý hồ sơ   |<------->|  Tìm kiếm/Tham gia|<------->|    Quản lý nhóm  |
      +-------------------+         |      nhóm        |         +------------------+
                |                    +--------+----------+                  |
                v                             |                             v
      +-------------------+                   |                    +-------------------+
      |  Tạo bài/ Bình luận|<-----------------+------------------->| Duyệt bài/ Nhật ký|
      |  Thả tim/ Báo cáo  |                                       |  Thống kê         |
      +--------------------+                                       +-------------------+
                |
                v
      +-------------------+           +-------------------+
      |  Chat nhóm/ File  |<--------->|   Sự kiện/ RSVP   |
      +-------------------+           +-------------------+
                |
                v
      +--------------------------------------------------+
      |              Thông báo đẩy (FCM)                 |
      +--------------------------------------------------+
```


### 2) System Context Diagram (Tổng quan hệ thống)
Mô tả ngắn: Bối cảnh ứng dụng Android và các dịch vụ Firebase liên quan.

Mô tả chi tiết:
- Ứng dụng Android giao tiếp với Firebase Auth (đăng nhập), Firestore (dữ liệu), Storage (tệp), FCM (push), và có thể mở Deep Link.
- Tập tin Firestore Rules và Storage Rules kiểm soát truy cập; Admin console hỗ trợ cấu hình.
- Người dùng tương tác qua mạng di động/Wi‑Fi; CDN Firebase phân phối nội dung nhanh.

```
+-------------------------+           Internet            +-------------------------+
|     Thiết bị Android    |<----------------------------->|      Firebase Services   |
|  - App (UI/VM/Repo)     |                               |  - Auth                  |
|  - Cache/Media/Intents  |                               |  - Firestore             |
+------------+------------+                               |  - Storage               |
             |                                            |  - FCM (Messaging)       |
             | Deep Link / Intent                         |  - Hosting/CDN (tuỳ chọn)|
             v                                            +------------+------------+
       +-----+-----+                                                      |
       |  User     |                                                      |
       +-----------+                                                      |
                                                                         v
                                                              +----------------------+
                                                              |  Rules & Admin Console|
                                                              +----------------------+
```


### 3) Activity Flow (Đăng bài trong nhóm)
Mô tả ngắn: Luồng hoạt động khi người dùng đăng bài vào một nhóm.

Mô tả chi tiết:
- Người dùng mở màn tạo bài, nhập nội dung, chọn ảnh/video.
- Ứng dụng upload tệp lên Storage, lưu metadata bài viết vào Firestore.
- Nếu nhóm bật duyệt bài, tạo trạng thái "pending" cho Admin/Owner duyệt.
- Sau khi đăng/bật duyệt: cập nhật feed, gửi thông báo (in‑app/FCM) đến thành viên quan trọng.

```
User -> Màn "Tạo bài" -> Nhập nội dung/đính kèm
      -> Nhấn "Đăng"
      -> [Upload] Ảnh/Video -> Firebase Storage (trả về URL)
      -> [Ghi] Document Post -> Firestore (status: published|pending)
      -> Nếu pending: tạo Notice cho Admin/Owner
      -> Nếu published: cập nhật lastPost, push FCM (tuỳ chọn)
      -> Hiển thị bài trong Feed người dùng
```


### 4) Activity Flow (Chat nhóm gửi ảnh/tệp)
Mô tả ngắn: Luồng gửi ảnh/tệp trong phòng chat nhóm.

Mô tả chi tiết:
- Người dùng chọn ảnh/tệp, app nén/resize ảnh nếu cần.
- Upload lên Storage theo cấu trúc thư mục chat; ghi message vào Firestore kèm downloadUrl.
- Lắng nghe realtime để hiển thị ngay trên tất cả thiết bị; gửi FCM wakeup.

```
User -> Chọn ảnh/tệp
      -> [Upload] Storage: /chats/{chatId}/(images|files)/...
      -> [Ghi] Firestore: messages (type=image|file, downloadUrl)
      -> Realtime listener nhận message mới -> cập nhật UI
      -> FCM (tuỳ chọn): thông báo tin nhắn mới
```


### 5) Class Diagram (Rút gọn)
Mô tả ngắn: Các lớp dữ liệu và repository chủ đạo.

Mô tả chi tiết:
- Model: User, Group, Member, Post, Comment, Like, Chat, Message, Event, Notice.
- Repository: AuthRepository, UserRepository, GroupRepository, PostRepository, CommentRepository, ChatRepository, MessageRepository, EventRepository, NoticeRepository.
- Quan hệ: User‑Group (Member), Group‑Post, Post‑Comment/Like, Group‑Chat‑Message, Group‑Event‑RSVP, Notice liên kết thực thể.

```
+--------+     +---------+     +-------+
|  User  |<--> | Member  |<--> | Group |
+--------+     +---------+     +-------+
    |               |              |
    |               v              v
    |          +---------+    +--------+
    |          |  Chat   |<-->|  Post  |<--+-- Like
    |          +---------+    +--------+   +-- Comment
    |               |
    v               v
+---------+     +--------+
|  Notice |     | Message|
+---------+     +--------+

+--------+     +-----------+
| Event  |<--->|   RSVP    |
+--------+     +-----------+
```


### 6) Sequence Diagram (Đăng nhập Google)
Mô tả ngắn: Trình tự đăng nhập bằng Google ID Token.

Mô tả chi tiết:
- App gọi Google Sign‑In để lấy `idToken`.
- Gửi `idToken` cho Firebase Auth để nhận `FirebaseUser`.
- Ghi/merge hồ sơ người dùng vào Firestore và đặt trạng thái online.

```
User    App(UI)      AuthRepository      Google     FirebaseAuth     Firestore
 |        |               |               |             |               |
 |  Tap Google Login      |               |             |               |
 |------>|  requestIdToken|-------------->|             |               |
 |        |               |<---idToken----|             |               |
 |        |  signInWithCredential(idToken)|------------>|               |
 |        |               |               |   FirebaseUser            |
 |        |               |<--------------|             |               |
 |        |  upsert user document ------------------------------>      |
 |        |               |               |             |     user set |
 |        |               |               |             |<--------------|
 |<-------|  success      |               |             |               |
```


### 7) ERD (Entity Relationship Diagram rút gọn)
Mô tả ngắn: Thực thể chính và mối quan hệ trong Firestore.

Mô tả chi tiết:
- `users` 1‑n `members` n‑1 `groups`.
- `groups` 1‑n `posts` 1‑n `comments`, `likes`.
- `groups` 1‑1 `chats` 1‑n `messages`.
- `groups` 1‑n `events` 1‑n `rsvps`.
- `notices` liên kết động với post/chat/event cho thông báo.

```
[users] (userId PK)
    |
    | 1..n
    v
[members] (groupId+userId PK) ---- n..1 ----> [groups] (groupId PK)
                                           |
                                           | 1..n
                                           v
                                        [posts] (postId PK)
                                           | 1..n           \ 1..n
                                           v                  v
                                       [comments]           [likes]

[groups] 1..1 [chats] 1..n [messages]
[groups] 1..n [events] 1..n [rsvps]
[notices] -- ref --> (post|comment|message|event)
```


### 8) Deployment Diagram (Rút gọn)
Mô tả ngắn: Thành phần triển khai logic.

Mô tả chi tiết:
- Ứng dụng chạy trên thiết bị Android; Firebase là backend‑as‑a‑service.
- CDN/Edge tăng tốc nội dung tĩnh; Play Services hỗ trợ Sign‑In.

```
+-------------------+             +--------------------------+
|  Android Device   |             |   Firebase Cloud (BaaS)  |
|  - App APK        |<----------->| - Auth / Firestore       |
|  - Media3/Glide   |   HTTPS     | - Storage / FCM          |
+-------------------+             +--------------------------+
```


### 9) State Machine (Post)
Mô tả ngắn: Trạng thái bài viết.

Mô tả chi tiết:
- Draft (tuỳ chọn) -> Pending (nếu bật duyệt) -> Published -> Deleted.
- Chuyển trạng thái do Author/Admin/Owner quyết định; rules kiểm soát quyền.

```
[DRAFT] -> [PENDING] -> [PUBLISHED] -> [DELETED]
    ^          |             |
    |          v             v
   edit     approve        remove
```


### 10) Notification Flow (FCM + In‑App)
Mô tả ngắn: Cách thông báo được phát sinh và hiển thị.

Mô tả chi tiết:
- Sự kiện (message mới, mention, phê duyệt, mời nhóm) tạo notice trong Firestore.
- Serverless/Cloud Function (tuỳ chọn) hoặc client trigger gửi FCM đến người nhận.
- Ứng dụng nhận FCM hiển thị notification và điều hướng deep link.

```
Event -> Notice doc -> (Cloud Function/Client) -> FCM Topic/Token -> Thiết bị
                                                  -> Hiển thị Notification
                                                  -> Mở màn hình liên quan
```


### 11) Sequence Diagram (Phê duyệt bài)
Mô tả ngắn: Duyệt bài trong nhóm có kiểm duyệt.

Mô tả chi tiết:
- Tác giả đăng bài (pending) -> Admin/Owner mở dashboard pending.
- Admin/Owner approve/reject -> cập nhật post.status, log thay đổi, gửi thông báo.

```
Author   App      Firestore      Admin/Owner
  |       |           |              |
  |--- create post(pending) -------> |
  |       |           |              |
  |       |<-- notice pending ------ |
  |       |           |              |
          |           | <--- open pending list ---|
          |           |                            |
          |           |--- approve/reject -------->|
          |           |                            |
          |<-- status updated + notice ----------- |
```


### 12) Data Flow (Upload media)
Mô tả ngắn: Dòng dữ liệu khi upload ảnh/video.

Mô tả chi tiết:
- App đọc ảnh/video, nén/resize thumbnail (nếu có), upload Storage.
- Nhận downloadURL, ghi vào doc bài viết/tin nhắn; hiển thị qua Glide/Media3.

```
Camera/Gallery -> App (optional compress) -> Storage.putFile -> downloadURL
                                           -> Firestore document (media fields)
                                           -> UI render (Glide/Media3)
```


### 13) Sequence Diagram (Khôi phục mật khẩu)
Mô tả ngắn: Gửi email reset mật khẩu.

Mô tả chi tiết:
- Người dùng nhập email -> gọi `sendPasswordResetEmail` -> email có link reset.
- Sau khi reset, đăng nhập lại và cập nhật `lastLoginAt`.

```
User -> App -> FirebaseAuth.sendPasswordResetEmail -> Email
     <-     <-                success/failure      <-
```


### 14) Permission & Rules Overview
Mô tả ngắn: Nguyên tắc cấp quyền và ràng buộc dữ liệu.

Mô tả chi tiết:
- Chỉ thành viên group được đọc/ghi post, comment, message trong group đó.
- Chỉ tác giả hoặc admin/owner được sửa/xoá bài viết; validate kích thước tệp.
- Chỉ thành viên chat được đọc tin nhắn; token FCM gắn theo user.

```
[Rules]
- groups/{groupId}/members contains request.auth.uid -> allow read posts/comments/messages in group
- posts.authorId == request.auth.uid OR role in [admin, owner] -> allow update/delete
- storage: limit size/contentType per path (images/videos/chats)
```


### 15) Global Search (Phác thảo)
Mô tả ngắn: Tìm kiếm gộp người dùng/nhóm/bài.

Mô tả chi tiết:
- Input một ô tìm kiếm -> truy vấn song song: users, groups, posts (title/body/tag).
- Gợi ý gần đây, bộ lọc theo loại; điều hướng đến trang chi tiết.

```
Query -> search(users | groups | posts) -> merge rank -> list results
      -> filter chips -> navigate to detail
```


### 16) Optional: Feature “Đột phá” (phác thảo)
Mô tả ngắn: Ví dụ Semantic Search/AI Toxicity Guard.

Mô tả chi tiết:
- Embedding on‑device (TFLite) cho gợi ý; hoặc từ điển + heuristic lọc độc hại.
- Không thay đổi schema nhiều; thêm field score/flag, hiển thị cảnh báo.

```
Text -> (Tokenizer/Embedding) -> Score -> store to Firestore (post.score)
     -> Rank feed/search -> UI badge/warning
```
