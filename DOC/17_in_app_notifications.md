## Thiết kế Tính năng Thông báo trong Ứng dụng (In‑App Notifications)

### 1. Mục tiêu và phạm vi
- Cung cấp cơ chế thông báo nội bộ (không cần FCM) cho các sự kiện quan trọng: bài viết mới, bình luận/like, tin nhắn mới (khi user không ở phòng chat), mời nhóm, phê duyệt nội dung.
- Hiển thị badge trên toolbar và danh sách thông báo; đồng bộ realtime.

### 2. Kiến trúc tổng quan (MVVM)
- View (UI): `NoticeCenter` (banner + badge), `NotificationsActivity`/`Fragment` để liệt kê thông báo.
- ViewModel: `NoticeViewModel` cung cấp danh sách thông báo chưa đọc/đã đọc, xử lý đánh dấu đã đọc.
- Repository: `NoticeRepository` subscribe Firestore, ghi/đọc notice và trạng thái.
- Data: Model `Notice` mô tả loại sự kiện, tham chiếu đến thực thể (post/comment/message/event).

Sơ đồ luồng MVVM:
```
UI (Badge/List) -> NoticeViewModel -> NoticeRepository -> Firestore (users/{uid}/notices)
                                     ^
                          snapshot listener (realtime)
```

### 3. Lược đồ dữ liệu (Firestore)
Thiết kế theo per-user subcollection để tách riêng thông báo cho từng người dùng và dễ quản lý quyền.
```
/users/{userId}/notices/{noticeId}
  noticeId: string
  type: "post" | "comment" | "like" | "message" | "invite" | "event"
  refId: string              // id của thực thể liên quan (postId, messageId, eventId...)
  refGroupId: string | null  // nếu liên quan group/chat của group
  title: string              // tiêu đề ngắn gọn
  body: string               // mô tả
  createdAt: timestamp
  readAt: timestamp | null
  fromUserId: string | null  // ai đã tạo sự kiện (tác giả, người gửi)
  metadata: map              // tuỳ chọn: deep link, preview, etc.
```

Index gợi ý:
- `users/{uid}/notices orderBy(createdAt desc)`
- Composite index cho truy vấn có điều kiện `readAt == null` nếu cần lọc chưa đọc.

### 4. Nguồn phát sinh thông báo
- Bài viết mới trong nhóm: tạo notice cho thành viên key (ví dụ: owner/admin hoặc tất cả thành viên nếu cần).
- Bình luận/like trên bài viết của user: tạo notice cho tác giả bài viết.
- Tin nhắn mới trong chat khi user không ở phòng: tạo notice cho thành viên còn lại.
- Mời nhóm/Phê duyệt nội dung: tạo notice cho người được mời hoặc tác giả bài pending.

Lưu ý: Ở bản hiện tại, client tạo notice khi thực hiện hành động; có thể thay bằng Cloud Functions sau này để an toàn/đồng nhất hơn.

### 5. Luồng dữ liệu chính

#### 5.1. Hiển thị badge số lượng chưa đọc
```
App start -> NoticeRepository.listenUnreadCount()
 -> Firestore: query users/{uid}/notices where readAt == null (hoặc lắng nghe toàn bộ và đếm client)
 -> onSnapshot -> ViewModel.update(unreadCount) -> UI hiển thị badge
```

Phương án tiết kiệm chi phí: lắng nghe `orderBy createdAt desc limit N` rồi đếm readAt==null trong N bản ghi gần nhất; hoặc lưu một document aggregate `/users/{uid}/notice_counters` và cập nhật bằng Cloud Functions trong tương lai.

#### 5.2. Danh sách thông báo (mới nhất ở trên)
```
UI(NotificationsActivity) -> NoticeViewModel.listNotices(limit, cursor)
 -> NoticeRepository.queryNotices(orderBy createdAt desc, startAfter cursor)
 -> Firestore.get() hoặc addSnapshotListener để realtime
 -> map -> ViewModel LiveData<List<Notice>> -> UI render
```

#### 5.3. Đánh dấu đã đọc / mở chi tiết
```
UI tap notice -> open detail via deep link (post/comment/chat/event)
 -> NoticeViewModel.markAsRead(noticeId)
 -> NoticeRepository.update(readAt = serverTimestamp())
 -> badge giảm theo realtime listener
```

### 6. Tích hợp với các module khác
- Post/Comment: tạo notice khi tạo post mới (tuỳ chọn phạm vi), khi có comment/like vào post của user.
- Chat: khi nhận tin nhắn mới và user không ở phòng chat (UI không foreground), app tạo notice cho user.
- Events: khi được mời sự kiện mới, khi sự kiện sắp diễn ra (local notification có thể bổ sung).

### 7. UX & hiệu năng
- Danh sách có phân trang (cursor `startAfter(createdAt)`), hiển thị preview nội dung.
- Swipe to mark as read; long‑press để xóa.
- Debounce cập nhật badge để giảm re-render.
- Chỉ bật listener khi app active hoặc màn hình thông báo đang mở.

### 8. Bảo mật (Firestore Rules)
Nguyên tắc: mỗi user chỉ đọc/ghi notices của chính họ.
```javascript
match /users/{userId}/notices/{noticeId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

### 9. Ví dụ API (Repository, phác thảo)
```java
public ListenerRegistration listenUnreadCount(String userId, EventListener<QuerySnapshot> l) {
  return db.collection("users").document(userId)
    .collection("notices")
    .whereEqualTo("readAt", null)
    .addSnapshotListener(l);
}

public Task<QuerySnapshot> listNotices(String userId, int limit, @Nullable Timestamp cursor) {
  Query q = db.collection("users").document(userId)
    .collection("notices")
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(limit);
  if (cursor != null) q = q.startAfter(cursor);
  return q.get();
}

public Task<Void> markAsRead(String userId, String noticeId) {
  return db.collection("users").document(userId)
    .collection("notices").document(noticeId)
    .update("readAt", FieldValue.serverTimestamp());
}
```

### 10. Kiểm thử
- Unit: logic tính unread, mapper deep link từ Notice -> màn chi tiết.
- Integration: listener badge + list + markAsRead cập nhật realtime.
- Manual: stress test khi nhiều notice đến cùng lúc; kiểm tra memory/leak ở màn hình list.


