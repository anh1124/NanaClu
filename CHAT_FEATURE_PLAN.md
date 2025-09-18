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

## 15) Lộ trình triển khai (bước thực hiện)
1. Chốt lược đồ & index (Firestore) theo tài liệu này.
2. Tạo `ChatRepository`, `MessageRepository` (CRUD cốt lõi, cập nhật lastMessage*).
3. Tạo `ChatListViewModel`, `ChatRoomViewModel` (LiveData, phân trang, markRead, gửi text/ảnh, edit, recall).
4. Hoàn thiện `ChatsFragment` (kết nối repo/vm, adapter, điều hướng).
5. Tạo `ChatRoomActivity/Fragment` + `MessageAdapter` (text, image, reply preview, day headers).
6. Tích hợp Profile/Group → tạo hoặc mở chat tương ứng.
7. Thêm `FirebaseMessagingService` (nhận FCM, điều hướng, refresh có điều kiện).
8. Viết tài liệu tóm tắt Firestore Rules/Storage Rules cần thiết.
9. Kiểm thử và tối ưu (pagination, overlay, retry, lỗi mạng).

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

