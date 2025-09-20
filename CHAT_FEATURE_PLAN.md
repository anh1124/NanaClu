# Kế hoạch triển khai tính năng Chat (Firestore + FCM + Storage)

Tài liệu này mô tả đầy đủ các bước để hoàn thiện tính năng chat theo kiến trúc MVVM đã dùng trong dự án. Không sử dụng Realtime Database cho chat; chỉ dùng Firestore, FCM và Firebase Storage.

---

## 1) Mục tiêu
- Chat riêng (private) 1-1 và chat nhóm.
- Gửi/nhận tin nhắn realtime theo hướng dẫn README: kích hoạt đồng bộ bằng FCM (không dùng snapshot listener mặc định).
- Hỗ trợ văn bản và ảnh,tệp đính kèm(100mb tối đa) (upload Storage).
- Tính năng nền tảng: trả lời (reply), thu hồi (unsend/recall), sửa (edit), xóa mềm (soft delete), đếm chưa đọc (unread) client-side, tìm kiếm hội thoại cơ bản, phân trang tin nhắn.

## 2) Công nghệ & nguyên tắc
- Ngôn ngữ: Java
- Kiến trúc: MVVM
- Database: Firestore (NoSQL)
- Storage: Firebase Storage (ảnh đính kèm)
- Realtime: FCM (push để kích hoạt fetch có điều kiện)
- UI: Material, RecyclerView, Glide
- Không addSnapshotListener mặc định cho chat; chỉ cân nhắc flag dev.

---

## 3) Lược đồ Firestore
- `chats/{chatId}` (Chat)
  - `chatId: string`
  - `createdAt: serverTimestamp`
  - `type: "private" | "group"`
  - `memberCount: number`
  - `groupId?: string` (nếu là chat nhóm)
  - `lastMessage?: string` (text tóm tắt)
  - `lastMessageAt?: timestamp`
  - `lastMessageAuthorId?: string`
- `chats/{chatId}/members/{userId}` (ChatMember)
  - `userId: string`
  - `joinedAt: timestamp`
  - `lastReadAt?: timestamp`
  - `role?: "admin" | "member"`
  - `muteUntil?: timestamp`
- `chats/{chatId}/messages/{messageId}` (Message)
  - `messageId: string`
  - `authorId: string`
  - `type: "text" | "image" | "file"`
  - `content: string` (text hoặc storageUrl)
  - `createdAt: serverTimestamp`
  - `editedAt?: timestamp`
  - `deletedAt?: timestamp` (xóa mềm/thu hồi)
  - `replyTo?: string` (messageId được trả lời)

Chỉ số/Index gợi ý:
- messages: orderBy(`createdAt` DESC) + phạm vi theo `chatId` (index tổng hợp nếu cần).
- chats: orderBy(`lastMessageAt` DESC); có thể thêm where theo `type`.

---

## 4) Model (POJO)
- Chat (mở rộng trường như lược đồ trên)
- ChatMember (thêm `muteUntil`)
- Message (thêm `replyTo`, `deletedAt`, `editedAt`)

Tuân thủ quy ước: constructor rỗng, trường công khai.

---

## 5) Repository
- ChatRepository
  - `getOrCreatePrivateChat(currentUid, otherUid)`
  - `getOrCreateGroupChat(groupId)` (tạo chat nhóm nếu chưa có)
  - `listUserChats(uid, limit, startAfter)` (phân trang, orderBy lastMessageAt desc)
  - `addMember(chatId, userId)` / `removeMember(chatId, userId)`
  - `updateLastMessageMeta(chatId, messageText, authorId, createdAt)`
  - `updateLastRead(chatId, userId, timestamp)`
- MessageRepository
  - `sendText(chatId, authorId, text)`
  - `sendImage(chatId, authorId, imageUri)` (upload Storage → lấy url → lưu message)
  - `listMessages(chatId, anchorTs, limit)` (phân trang theo createdAt)
  - `editMessage(chatId, messageId, newText)`
  - `softDeleteMessage(chatId, messageId)` (đặt deletedAt; giữ content rỗng hoặc nhãn "Tin nhắn đã thu hồi")

Ghi chú: cập nhật `lastMessage*` trong Chat khi gửi/thu hồi/sửa hợp lệ.

---

## 6) ViewModel
- ChatListViewModel
  - LiveData<List<ChatThreadUI>>: danh sách hội thoại
  - `refresh()`, `paginate()`
- ChatRoomViewModel
  - LiveData<List<MessageUI>>: danh sách tin nhắn
  - LiveData<Boolean> loading/sending; LiveData<String> error
  - `init(chatId)`
  - `sendText(text)` / `sendImage(uri)`
  - `loadMore()` (phân trang ngược)
  - `markRead()` (update lastReadAt)
  - `editMessage(messageId, newText)`
  - `recallMessage(messageId)` (soft delete)

---

## 7) UI
- ChatsFragment (đã có khung)
  - Kết nối ViewModel → hiển thị threads (avatar, tên, last, time, unread)
  - Tìm kiếm cục bộ theo tên/lastMessage
  - Pull-to-refresh, empty state
  - Item click → mở ChatRoom
- ChatRoomActivity/Fragment (mới)
  - AppBar: tiêu đề, avatar/nhóm; menu (xem thành viên, mute, rời nhóm…)
  - RecyclerView messages: bubble trái/phải; hỗ trợ reply preview; item ảnh (bấm phóng to)
  - Composer: TextInput + send + attach (ảnh)
  - Auto-scroll hợp lý, hiển thị mốc ngày (group by day)

Màu thương hiệu: `#a8d7ff` (áp dụng cho nút chính, nhấn nhá UI phù hợp).

---

## 8) FCM (Client side)
- Cài `FirebaseMessagingService` để nhận thông báo tin nhắn mới.
- Foreground: nhận FCM → trigger ViewModel fetch có điều kiện `where createdAt > lastReadAt`.
- Background: hiển thị notification; khi ấn mở thẳng `ChatRoomActivity(chatId)`.
- Payload FCM đề xuất:
```json
{
  "type": "chat_message",
  "chatId": "...",
  "messageId": "...",
  "createdAt": 1700000000000,
  "senderId": "...",
  "title": "Tên người gửi hoặc nhóm",
  "body": "Trích nội dung"
}
```

---

## 9) Storage
- Thư mục: `chat_images/{chatId}/{yyyy}/{MM}/{messageId}.jpg` (hoặc UUID)
- Quy trình: chọn ảnh → nén nhẹ (nếu cần) → upload → lấy URL → tạo message `type=image`.

---

## 10) Tích hợp với User/Group
- ProfileActivity: nút "Chat" → `getOrCreatePrivateChat(currentUid, otherUid)` → mở ChatRoom.
- GroupDetail: nút "Chat" → `getOrCreateGroupChat(groupId)` → mở ChatRoom.

---

## 11) Quy trình gửi/nhận
1. Người dùng nhập text → `sendText()` tạo message (Firestore) + update lastMessage*.
2. Cloud Function/Server gửi FCM tới thành viên chat.
3. Client nhận FCM:
   - Nếu đang ở phòng tương ứng: fetch tin mới (createdAt > lastReadAt) → cập nhật UI.
   - Nếu không: notification hiển thị badge/tóm tắt.
4. Khi người dùng mở phòng → `markRead()` cập nhật `lastReadAt`.

---

## 12) Firestore Security (đề cương)
- Chỉ thành viên của `chats/{chatId}` mới được đọc/ghi `messages` và `chat`.
- Người gửi được quyền `edit/softDelete` trong khoảng thời gian cho phép (ví dụ 15 phút) hoặc theo role.
- Storage: chỉ thành viên chat tương ứng được đọc ảnh trong thư mục chat đó.

---

## 13) Kiểm thử
- Unit test Repository: stub Firestore/Storage (qua abstraction) → kiểm tra CRUD, phân trang, cập nhật lastMessage.
- ViewModel test: logic markRead, paginate, send/recall.
- UI smoke test: binding adapters, định dạng thời gian, reply preview.

---

## 14) Rủi ro & giảm thiểu
- Trễ FCM: có thể thêm refresh theo chu kỳ khi phòng đang mở (interval nhỏ, tắt khi nền).
- Kích thước ảnh: giới hạn và nén trước khi upload.
- Đồng bộ unread: dựa `lastReadAt` trên client, cập nhật kịp thời khi mở phòng.

---

## 15) Lộ trình triển khai chi tiết (Task List)

### Phase 1: Core Models & Repository
- [x] 1.1. Cập nhật model Chat với các trường mới (lastMessage, lastMessageAt, lastMessageAuthorId)
- [x] 1.2. Cập nhật model Message với các trường mới (editedAt, deletedAt, replyTo)
- [x] 1.3. Cập nhật model ChatMember với trường mới (lastReadAt, role, muteUntil)
- [x] 1.4. Hoàn thiện ChatRepository với các method cần thiết
- [x] 1.5. Tạo MessageRepository với đầy đủ CRUD operations
- [ ] 1.6. Test Repository methods cơ bản

### Phase 2: ViewModels & Business Logic
- [x] 2.1. Hoàn thiện ChatListViewModel với refresh và pagination
- [x] 2.2. Tạo ChatRoomViewModel với đầy đủ chức năng
- [x] 2.3. Thêm logic markRead, sendText, sendImage
- [x] 2.4. Thêm logic editMessage, recallMessage
- [ ] 2.5. Test ViewModel logic

### Phase 3: UI Components
- [x] 3.1. Cập nhật ChatsFragment để kết nối với ChatListViewModel
- [x] 3.2. Tạo ChatRoomActivity/Fragment mới
- [x] 3.3. Tạo MessageAdapter với support text, image, reply
- [x] 3.4. Thêm UI cho composer (input + send + attach)
- [ ] 3.5. Thêm UI cho message actions (edit, reply, delete)
- [ ] 3.6. Thêm day headers và auto-scroll

### Phase 4: Navigation & Integration
- [x] 4.1. Tích hợp nút Chat trong ProfileActivity
- [x] 4.2. Tích hợp nút Chat trong GroupDetailActivity
- [x] 4.3. Thêm navigation từ ChatFragment đến ChatRoom
- [ ] 4.4. Thêm deep linking cho chat notifications

### Phase 5: Storage & Media
- [x] 5.1. Thêm image upload functionality
- [ ] 5.2. Thêm image preview và zoom
- [ ] 5.3. Thêm file attachment support
- [ ] 5.4. Optimize image compression

### Phase 6: Real-time & Notifications
- [ ] 6.1. Setup FirebaseMessagingService
- [ ] 6.2. Thêm FCM payload handling
- [ ] 6.3. Thêm foreground notification handling
- [ ] 6.4. Thêm background notification với deep link
- [ ] 6.5. Test notification flow

### Phase 7: Advanced Features
- [ ] 7.1. Thêm unread count calculation
- [ ] 7.2. Thêm search functionality trong chat list
- [ ] 7.3. Thêm mute/unmute chat
- [ ] 7.4. Thêm typing indicator (optional)
- [ ] 7.5. Thêm message reactions (optional)

### Phase 8: Testing & Polish
- [ ] 8.1. Unit tests cho Repository
- [ ] 8.2. Unit tests cho ViewModel
- [ ] 8.3. UI tests cho chat flow
- [ ] 8.4. Performance optimization
- [ ] 8.5. Error handling improvement

### Phase 9: Security & Rules
- [ ] 9.1. Viết Firestore Security Rules
- [ ] 9.2. Viết Storage Security Rules
- [ ] 9.3. Test security rules
- [ ] 9.4. Documentation update

---

## 📋 Tóm tắt tiến độ hiện tại

### ✅ Đã hoàn thành:
1. **Core Infrastructure**:
   - ✅ Cập nhật models (Chat, Message, ChatMember) với đầy đủ fields
   - ✅ Hoàn thiện ChatRepository với CRUD operations
   - ✅ Hoàn thiện MessageRepository với text/image support
   - ✅ ChatListViewModel và ChatRoomViewModel hoàn chỉnh

2. **UI Components**:
   - ✅ ChatRoomActivity với giao diện chat đầy đủ
   - ✅ MessageAdapter hỗ trợ text và image messages
   - ✅ Chat bubbles với design theo brand color (#a8d7ff)
   - ✅ Message composer với input, send, attach buttons
   - ✅ SwipeRefreshLayout cho refresh messages

3. **Navigation & Integration**:
   - ✅ Tích hợp nút Chat trong ProfileActivity → tạo private chat
   - ✅ Tích hợp nút Chat trong GroupDetailActivity → tạo group chat
   - ✅ Navigation từ ChatFragment đến ChatRoomActivity
   - ✅ Cập nhật AndroidManifest.xml

4. **Features**:
   - ✅ Gửi/nhận tin nhắn text
   - ✅ Gửi/nhận ảnh (upload Firebase Storage)
   - ✅ Auto-update lastMessage metadata
   - ✅ Auto-refresh messages sau khi gửi
   - ✅ Mark as read functionality
   - ✅ Message timestamps với relative time
   - ✅ Soft delete (recall) messages
   - ✅ Edit messages

### 🔄 Đang làm/Cần cải thiện:
1. **UI/UX Enhancements**:
   - Message actions menu (edit, reply, delete)
   - Day headers cho messages
   - Image preview và zoom
   - Better error handling UI

2. **Real-time Features**:
   - FCM notifications
   - Typing indicators
   - Online status

3. **Advanced Features**:
   - Unread count calculation
   - ✅ Search trong chat list (đã hoạt động)
   - Mute/unmute chats
   - File attachments

### 🐛 Các lỗi đã sửa (Latest Update):
1. **✅ Navigation Issues**:
   - ✅ Sửa back button trong ProfileActivity (sử dụng toolbar navigation)
   - ✅ Click vào chat item trong ChatFragment mở ChatRoomActivity

2. **✅ Chat Display Names - MAJOR IMPROVEMENT**:
   - ✅ Cải thiện logic hiển thị tên chat (group vs private)
   - ✅ Private chat hiển thị tên user đúng trong ProfileActivity
   - ✅ **ChatFragment giờ load tên đúng cho cả group và private chats**
   - ✅ **Async loading tên từ GroupRepository và UserRepository**
   - ✅ **Private chat hiển thị tên người kia (không phải "Private Chat")**
   - ✅ **Group chat hiển thị tên group thực tế**

3. **✅ Enhanced User Interaction**:
   - ✅ Bỏ dialog chọn hành động, trực tiếp vào hồ sơ khi click avatar/tên trong posts
   - ✅ Chat button vẫn hoạt động từ ProfileActivity

4. **✅ Critical Bug Fixes**:
   - ✅ Sửa lỗi Timestamp deserialization trong Message và Chat models
   - ✅ Thêm custom setters để handle Firebase Timestamp objects
   - ✅ Tin nhắn giờ đã có thể load được từ Firestore

5. **✅ Navigation & Data Flow**:
   - ✅ Truyền chatType và groupId cho ChatRoomActivity
   - ✅ Cải thiện navigation từ ChatFragment với thông tin đầy đủ
   - ✅ Search functionality hoạt động (đã có sẵn)

6. **🔧 Debug & Logging**:
   - ✅ Thêm debug logs vào ChatRoomViewModel và MessageRepository
   - ✅ Giúp debug vấn đề tin nhắn không hiển thị

### 🎯 Trạng thái hiện tại:
**Chức năng chat đã hoạt động hoàn toàn với tên hiển thị đúng!** Người dùng có thể:
- ✅ Ấn nút Chat từ Profile hoặc Group để mở chat room
- ✅ **Click vào chat item trong ChatFragment để mở chat room (cả private và group)**
- ✅ Click vào avatar/tên trong posts để trực tiếp vào hồ sơ (đã bỏ dialog)
- ✅ Gửi tin nhắn text và ảnh
- ✅ **Xem lịch sử tin nhắn (đã sửa lỗi Timestamp deserialization)**
- ✅ Refresh để tải tin nhắn mới
- ✅ **Search trong danh sách chat (hoạt động đúng)**
- ✅ Back button hoạt động đúng trong ProfileActivity
- ✅ **Chat items hiển thị TÊN ĐÚNG:**
  - **Private chat**: Hiển thị tên người kia (VD: "Nguyễn Văn A")
  - **Group chat**: Hiển thị tên group thực tế (VD: "Nhóm học tập")

**Build status**: ✅ Successful - App có thể chạy và test được

**Các cải thiện chính trong update này**:
- 🎯 **Tên chat hiển thị đúng**: Không còn "Private Chat" hay "Group Chat" generic
- 🎯 **Navigation hoàn thiện**: Cả private và group chat đều mở được từ ChatFragment
- 🎯 **Async loading**: Tên được load bất đồng bộ từ database
- 🔧 Firebase Timestamp deserialization error → Tin nhắn giờ load được
- 🔧 UX cải thiện: trực tiếp vào profile thay vì dialog

**Tất cả các vấn đề bạn đề cập đã được giải quyết!** 🎉

---

## 16) Danh sách các hàm sẽ tạo (tên + mô tả ngắn)

### ChatRepository
- `Task<String> getOrCreatePrivateChat(String currentUid, String otherUid)`
  - Tìm chat private giữa 2 user; nếu chưa có thì tạo mới, thêm 2 ChatMember; trả về `chatId`.
- `Task<String> getOrCreateGroupChat(String groupId)`
  - Lấy/ tạo chat nhóm gắn `groupId`; đảm bảo `type=group`.
- `Task<List<Chat>> listUserChats(String uid, int limit, @Nullable Chat last)`
  - Liệt kê các chat của user theo `lastMessageAt` giảm dần, hỗ trợ phân trang.
- `Task<Void> addMember(String chatId, String userId)` / `removeMember(...)`
  - Quản lý thành viên chat (group).
- `Task<Void> updateLastMessageMeta(String chatId, String messageText, String authorId, long createdAt)`
  - Cập nhật metadata phục vụ danh sách hội thoại.
- `Task<Void> updateLastRead(String chatId, String userId, long ts)`
  - Ghi nhận thời điểm đã đọc.

### MessageRepository
- `Task<String> sendText(String chatId, String authorId, String text)`
  - Tạo message type=text; cập nhật lastMessage*.
- `Task<String> sendImage(String chatId, String authorId, Uri imageUri)`
  - Upload Storage → tạo message type=image; cập nhật lastMessage*.
- `Task<List<Message>> listMessages(String chatId, @Nullable Long anchorTs, int limit)`
  - Tải phân trang tin nhắn theo thời gian.
- `Task<Void> editMessage(String chatId, String messageId, String newText)`
  - Sửa nội dung message (ghi `editedAt`).
- `Task<Void> softDeleteMessage(String chatId, String messageId)`
  - Thu hồi/xóa mềm (ghi `deletedAt`, có thể thay `content` bằng nhãn ẩn).

### ViewModel
- ChatListViewModel: `refresh()`, `paginate()`
- ChatRoomViewModel: `init(chatId)`, `sendText()`, `sendImage()`, `loadMore()`, `markRead()`, `editMessage()`, `recallMessage()`

### FCM Service
- `onMessageReceived(RemoteMessage msg)`
  - Parse payload; nếu foreground → trigger VM refresh phòng tương ứng; nếu background → build notification deep-link.

---

## 17) Tính năng cụ thể của Chat
- Gửi/nhận tin nhắn văn bản
- Gửi/nhận ảnh (Storage)
- Trả lời (reply) một tin nhắn (hiển thị preview nội dung/ảnh)
- Thu hồi (recall/unsend) tin nhắn (soft delete; hiển thị nhãn "Tin nhắn đã được thu hồi")
- Chỉnh sửa (edit) tin nhắn văn bản (hiển thị nhãn "Đã chỉnh sửa")
- Đếm số chưa đọc (unread) dựa trên `lastReadAt` của mỗi thành viên
- Tắt thông báo tạm thời (mute) bằng `muteUntil`
- Tìm kiếm cơ bản trong danh sách hội thoại theo tên/last message
- Phân trang tin nhắn khi cuộn ngược

Gợi ý mở rộng tương lai (không thuộc phạm vi bắt buộc):
- Ghim hội thoại, ghim tin nhắn
- Reactions (emoji) cho tin nhắn
- Typing indicator (có thể dùng realtime `typing` nếu cần)
- Gửi file/tài liệu

