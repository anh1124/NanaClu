## Thiết kế Tính năng Chat (Realtime)

### 1. Mục tiêu và phạm vi
- Chat riêng (private) giữa 2 người dùng và chat nhóm gắn với group.
- Gửi/nhận tin nhắn theo thời gian thực, hỗ trợ văn bản và ảnh/file (mở rộng).
- Phân trang lịch sử, ẩn hội thoại một phía, đếm chưa đọc client‑side.

### 2. Kiến trúc tổng quan (MVVM)
- View (UI): `ChatFragment`, `ChatRoomActivity` hiển thị danh sách chat và phòng chat.
- ViewModel: `ChatListViewModel`, `ChatRoomViewModel` điều phối dữ liệu, state UI.
- Repository: `ChatRepository`, `MessageRepository` làm việc với Firestore/Storage.
- Data: Model `Chat`, `ChatMember`, `Message` ánh xạ trực tiếp Firestore docs.

Sơ đồ luồng MVVM (khái quát):
```
UI -> ViewModel -> Repository -> Firestore/Storage
                       ^               |
                       |---- Listener --|
          (LiveData/StateFlow) <- ViewModel <- Repository
```

### 3. Lược đồ dữ liệu (Firestore)
```
/chats/{chatId}
  chatId: string
  type: "private" | "group"
  memberIds: array<string>         // hỗ trợ truy vấn nhanh danh sách chat của user
  groupId: string | null           // nếu chat nhóm
  lastMessage: string              // denormalized
  lastMessageAt: timestamp         // denormalized
  lastMessageBy: string            // denormalized (userId)
  createdAt: timestamp
  /members/{userId}
    userId: string
    role: "admin" | "member" (optional)
    lastRead: timestamp | null     // hỗ trợ tính unread client‑side
    joinedAt: timestamp
  /messages/{messageId}
    messageId: string
    senderId: string
    senderName: string (denormalized)
    content: string
    type: "text" | "image" | "file"
    imageUrls: array<string> | null
    createdAt: timestamp
    editedAt: timestamp | null
    isDeleted: boolean
```

Storage (ảnh/chat):
```
/chat_images/{chatId}/{timestamp}_{hash}.jpg
```

### 4. Các ca sử dụng chính và luồng dữ liệu

#### 4.1. Liệt kê danh sách hội thoại (Chat list)
Mục tiêu: Hiển thị các chat có `memberIds` chứa `currentUserId`, sắp xếp theo `lastMessageAt` giảm dần, phân trang.

Luồng dữ liệu:
```
UI(ChatFragment) -> ChatListViewModel.listUserChats(limit, cursor)
 -> ChatRepository.queryChats(memberIds contains currentUserId, orderBy lastMessageAt desc, limit)
 -> Firestore.get() (hoặc listener nếu muốn realtime list)
 -> Repository map -> ViewModel set LiveData<List<Chat>> -> UI render
```

Pseudo (Repository):
```java
Query q = db.collection("chats")
  .whereArrayContains("memberIds", userId)
  .orderBy("lastMessageAt", Query.Direction.DESCENDING)
  .limit(limit);
if (cursor != null) q = q.startAfter(cursor.lastMessageAt);
```

Phân trang: dùng `startAfter(lastMessageAt)` làm cursor.

#### 4.2. Mở phòng chat và lắng nghe tin nhắn realtime
Mục tiêu: Lắng nghe `messages` theo `createdAt` tăng dần, tải phân trang ngược.

Luồng dữ liệu (realtime):
```
UI(ChatRoomActivity) -> ChatRoomViewModel.listenMessages(chatId)
 -> MessageRepository.addSnapshotListener(chats/{chatId}/messages orderBy createdAt asc)
 -> onSnapshot -> diff -> ViewModel update LiveData<List<Message>> -> UI render
```

Phân trang lịch sử: truy vấn `orderBy createdAt desc limit N startAfter(lastSeen)` và prepend vào danh sách.

#### 4.3. Gửi tin nhắn (text/ảnh)
Mục tiêu: Tạo `messages/{messageId}` và cập nhật denormalized fields ở `chats/{chatId}`.

Luồng dữ liệu:
```
UI -> ViewModel.sendMessage(chatId, message)
 -> Repository (nếu có ảnh): upload Storage -> nhận downloadUrl(s)
 -> Repository: create messages doc + batch update chat.lastMessage*
 -> onSuccess -> UI scroll bottom
```

Batch cập nhật (phác thảo):
```java
WriteBatch b = db.batch();
DocumentReference msgRef = chats(docId).collection("messages").document();
b.set(msgRef, message);
b.update(chats(docId),
  "lastMessage", summary,
  "lastMessageAt", FieldValue.serverTimestamp(),
  "lastMessageBy", currentUserId);
b.commit();
```

#### 4.4. Đánh dấu đã đọc (lastRead) và tính unread
- Khi mở phòng: cập nhật `members/{uid}.lastRead = now()`.
- Unread = count(messages.createdAt > lastRead) tính phía client với một ngưỡng cắt (hoặc tính gần đúng bằng so sánh `lastMessageAt > lastRead`).

#### 4.5. Ẩn hội thoại một phía
- Trường `members/{uid}.hidden = true` (đề xuất) hoặc dùng `clearedAt` để ẩn lịch sử cũ.
- Khi có tin nhắn mới sau `clearedAt`, UI tự hiện lại hội thoại.

### 5. Trạng thái offline, lỗi và phục hồi
- Bật Firestore offline persistence: UI vẫn hiển thị cache khi mất mạng.
- Gửi tin nhắn xếp hàng: Firestore sẽ đồng bộ khi kết nối lại (hiển thị trạng thái gửi: sending/sent/failed ở UI nếu cần).
- Retry upload ảnh với backoff; dọn tệp tạm khi thất bại.

### 6. Bảo mật và quyền truy cập
Nguyên tắc chính (rút gọn):
```
match /chats/{chatId} {
  allow read: if request.auth != null &&
    request.auth.uid in resource.data.memberIds;
  allow update: if request.auth != null &&
    request.auth.uid in resource.data.memberIds; // hoặc kiểm tra role

  match /members/{userId} {
    allow read, update: if request.auth != null && request.auth.uid == userId;
  }

  match /messages/{messageId} {
    allow read, create: if request.auth != null &&
      request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.memberIds;
    allow update, delete: if request.auth != null && request.auth.uid == resource.data.senderId;
  }
}
```

Lưu ý: Nếu chat nhóm gắn `groupId`, có thể kiểm tra membership qua `groups/{groupId}/members/{uid}` thay cho `memberIds` toàn cục.

### 7. Trải nghiệm người dùng (UX) và tối ưu hiệu năng
- Danh sách tin nhắn dùng RecyclerView với diff util; tránh rebind toàn bộ khi onSnapshot.
- Hình ảnh: nén trước khi upload, dùng Glide cache; lazy loading.
- Phân trang + prefetch: tải trước một trang kế tiếp khi gần cuối.
- Giảm chi phí Firestore: chỉ bật listener ở màn hình đang hiển thị; gỡ listener onStop.

### 8. Kiểm thử
- Unit test: formatter tin nhắn, summary `lastMessage`.
- Integration test: listener -> ViewModel -> UI updates.
- Manual test: phân trang, gửi ảnh lớn, mất mạng/khôi phục.


