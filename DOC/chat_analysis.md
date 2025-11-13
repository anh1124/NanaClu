=== BẮT ĐẦU PHÂN TÍCH CHI TIẾT CHỨC NĂNG CHAT TRONG NANACLU ===

1. CẤU TRÚC DỮ LIỆU FIRESTORE
   - Chat riêng (1-1): 
     - Đường dẫn: `chats/{chatId}`
     - Field chính: 
       - `type`: "private"
       - `pairKey`: Chuỗi sắp xếp uid1_uid2 để tìm kiếm nhanh
       - `memberCount`: Số thành viên (luôn là 2)
       - `createdAt`: Timestamp tạo chat
       - `lastMessage`: Nội dung tin nhắn cuối
       - `lastMessageAt`: Thời gian tin nhắn cuối
       - `lastMessageAuthorId`: ID người gửi tin cuối
     - Subcollection: 
       - `members/{userId}`: Thông tin thành viên (ChatMember)
       - `messages/{messageId}`: Lịch sử tin nhắn

   - Chat nhóm:
     - Đường dẫn: `groups/{groupId}/chats/{chatId}`
     - Khác biệt so với chat riêng:
       - `type`: "group"
       - `groupId`: ID của nhóm chứa chat
       - `memberCount`: Số lượng thành viên trong nhóm

   - Tin nhắn (Message):
     - Đường dẫn: `.../messages/{messageId}`
     - Các loại tin nhắn: 
       - `text`: Văn bản thông thường
       - `image`: Ảnh (lưu URL trong `fileUrl`)
       - `file`: File đính kèm (lưu URL trong `fileUrl`)
     - Các trường bắt buộc:
       - `senderId`: ID người gửi
       - `content`: Nội dung tin nhắn
       - `type`: Loại tin nhắn
       - `timestamp`: Thời gian gửi
       - `status`: Trạng thái (sending/sent/delivered/read)

   - Thành viên chat (ChatMember):
     - `userId`: ID người dùng
     - `joinedAt`: Thời gian tham gia
     - `lastRead`: Thời điểm đọc cuối cùng
     - `hidden`: Ẩn cuộc trò chuyện
     - `clearedAt`: Thời điểm xóa lịch sử (nếu có)

2. PHƯƠNG PHÁP CẬP NHẬT REALTIME
   - Sử dụng `addSnapshotListener` trong `MessageRepository` để lắng nghe thay đổi
   - Query cơ bản trong `MessageRepository.getMessages()`:
     ```java
     return chatRef.collection("messages")
             .orderBy("timestamp", Query.Direction.DESCENDING)
             .limit(50);
     ```
   - Pagination:
     - Sử dụng `startAfter` với document snapshot của tin nhắn cũ nhất
     - Kết hợp với `SwipeRefreshLayout` để load thêm khi kéo xuống
   - Xử lý lifecycle:
     - Đăng ký listener trong `onStart()`
     - Hủy đăng ký trong `onStop()` bằng cách gọi `listenerRegistration.remove()`
   - Cập nhật UI:
     - Sử dụng `ListAdapter` với `DiffUtil.ItemCallback` để cập nhật hiệu quả
     - Tự động scroll khi có tin nhắn mới nếu đang ở cuối danh sách

3. GỬI TIN NHẮN
   - Sử dụng `WriteBatch` để đảm bảo tính atomic
   - Các thao tác trong `MessageRepository.sendMessage()`:
     1. Tạo tin nhắn mới với status = "sending"
     2. Thêm vào `messages` collection
     3. Cập nhật thông tin mới nhất của chat:
        - `lastMessage`: Nội dung rút gọn
        - `lastMessageAt`: Thời gian gửi
        - `lastMessageAuthorId`: ID người gửi
        - `updatedAt`: Thời gian cập nhật
   - Xử lý offline:
     - Firestore tự động queue và đồng bộ khi có mạng
     - UI hiển thị trạng thái "Đang gửi..."
     - Khi gửi thành công, cập nhật status = "sent"
   - Đánh dấu đã đọc:
     - Gọi `markAsRead()` khi người dùng mở chat
     - Cập nhật `lastRead` của user trong `members` subcollection

4. HIỂN THỊ & TƯƠNG TÁC UI
   - `ChatRoomActivity`:
     - Sử dụng `ChatRoomViewModel` để quản lý dữ liệu
     - `MessagesAdapter` extends `ListAdapter` với `DiffUtil` để cập nhật hiệu quả
     - Sử dụng `LinearLayoutManager` với `stackFromEnd = false` để tin mới ở dưới
   - Auto-scroll:
     - Theo dõi vị trí cuộn qua `OnScrollListener`
     - Tự động scroll khi:
       - Có tin nhắn mới từ chính mình
       - Đang ở gần cuối danh sách
     - Hiển thị nút "Cuộn xuống" khi có tin mới và không ở cuối
   - Load more:
     - Kéo xuống để tải thêm tin cũ
     - Sử dụng `SwipeRefreshLayout` với `setOnRefreshListener`
     - Query với `startAfter` và `orderBy("timestamp").limit(20)`
   - Xử lý `clearedAt`:
     - Ẩn các tin nhắn cũ hơn `clearedAt`
     - Tự động hiện lại khi có tin nhắn mới
     - Cập nhật `clearedAt = null` khi có tin nhắn mới

5. ĐẾM TIN NHẮN CHƯA ĐỌC & MARK READ
   - Lưu trữ:
     - `lastRead` trong `chats/{chatId}/members/{userId}`
     - `unreadCount` được tính toán client-side
   - Cách tính unread:
     ```java
     // Trong ChatListViewModel
     long lastRead = member.lastRead != null ? member.lastRead : 0;
     int unread = 0;
     if (chat.lastMessageAt > lastRead) {
         unread = (int) db.collection("chats").document(chatId)
                        .collection("messages")
                        .whereGreaterThan("timestamp", lastRead)
                        .count()
                        .get()
                        .getResult()
                        .getCount();
     }
     ```
   - `markAsRead()`:
     - Cập nhật `lastRead = System.currentTimeMillis()`
     - Cập nhật local unreadCount = 0
     - Gửi yêu cầu cập nhật lên server

6. GALLERY ẢNH & TỆP
   - Lưu trữ file:
     - Ảnh: `chats/{chatId}/images/{timestamp}_{random}.jpg`
     - File: `chats/{chatId}/files/{filename}`
   - Các màn hình:
     - `PhotoGalleryActivity`: Hiển thị grid ảnh
     - `FileGalleryActivity`: Danh sách file đã gửi
   - Lấy danh sách:
     ```java
     // Lấy ảnh
     return chatRef.collection("messages")
             .whereEqualTo("type", "image")
             .orderBy("timestamp", Query.Direction.DESCENDING)
             .get();
             
     // Lấy file
     return chatRef.collection("messages")
             .whereEqualTo("type", "file")
             .orderBy("timestamp", Query.Direction.DESCENDING)
             .get();
     ```
   - Xem chi tiết:
     - Ảnh: Mở fullscreen với zoom
     - File: Tải về hoặc mở bằng app tương ứng

7. THÔNG BÁO IN-APP
   - Kích hoạt khi:
     - Có tin nhắn mới từ chat
     - Người dùng không đang ở màn hình chat đó
     - Tin nhắn không phải do chính mình gửi
   - Cập nhật badge:
     ```java
     // Trong NoticeCenter
     public void incrementUnreadCount() {
         int current = sharedPrefs.getInt(KEY_UNREAD_COUNT, 0);
         sharedPrefs.edit().putInt(KEY_UNREAD_COUNT, current + 1).apply();
         updateNotificationBadge();
     }
     ```
   - Hiển thị:
     - Icon thông báo trên Toolbar
     - Badge số với tổng tin chưa đọc
     - Notification khi app ở background

8. OFFLINE PERSISTENCE & ĐỒNG BỘ
   - Cấu hình trong `Application` class:
     ```java
     FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
             .setPersistenceEnabled(true)
             .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
             .build();
     FirebaseFirestore.getInstance().setFirestoreSettings(settings);
     ```
   - Các thao tác được queue khi offline:
     - Gửi tin nhắn mới
     - Đánh dấu đã đọc
     - Tải lên file đính kèm
   - Xử lý đồng bộ:
     - Tự động đồng bộ khi có mạng
     - Giữ trạng thái "đang gửi" cho đến khi thành công
     - Thông báo lỗi nếu không thể đồng bộ sau nhiều lần thử
   - Cache:
     - Lưu trữ tin nhắn gần đây
     - Tự động xóa cache cũ khi đạt giới hạn

9. CODE MẪU CHI TIẾT

```java
// 1. Lấy hoặc tạo chat riêng (ChatRepository.java)
public Task<String> getOrCreatePrivateChat(String currentUid, String otherUid) {
    String a = currentUid.compareTo(otherUid) < 0 ? currentUid : otherUid;
    String b = currentUid.compareTo(otherUid) < 0 ? otherUid : currentUid;
    String pairKey = a + "_" + b;
    
    return db.collection(CHATS)
            .whereEqualTo("type", "private")
            .whereEqualTo("pairKey", pairKey)
            .limit(1)
            .get()
            .continueWithTask(/* ... tạo mới nếu chưa tồn tại ... */);
}

// 2. Gửi tin nhắn (MessageRepository.java)
public Task<Void> sendMessage(String chatId, Message message) {
    WriteBatch batch = db.batch();
    DocumentReference msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document();
    
    // Thêm tin nhắn mới
    batch.set(msgRef, message.toMap());
    
    // Cập nhật thông tin chat
    DocumentReference chatRef = db.collection("chats").document(chatId);
    Map<String, Object> updates = new HashMap<>();
    updates.put("lastMessage", message.getPreviewText());
    updates.put("lastMessageAt", FieldValue.serverTimestamp());
    updates.put("lastMessageAuthorId", message.getSenderId());
    updates.put("updatedAt", FieldValue.serverTimestamp());
    batch.update(chatRef, updates);
    
    return batch.commit();
}

// 3. Lắng nghe tin nhắn (ChatRoomViewModel.java)
private void setupMessageListener() {
    messageListener = messageRepository.getMessages(chatId)
        .addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                // Xử lý lỗi
                return;
            }
            
            List<Message> messages = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Message msg = doc.toObject(Message.class);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            
            // Sắp xếp từ cũ đến mới
            Collections.sort(messages, (m1, m2) -> 
                Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                
            // Cập nhật LiveData
            messagesLiveData.postValue(messages);
            
            // Đánh dấu đã đọc nếu cần
            if (shouldMarkAsRead) {
                markAsRead();
            }
        });
}

// 4. Xử lý cuộn và tải thêm (ChatRoomActivity.java)
private void setupRecyclerView() {
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    layoutManager.setStackFromEnd(false);
    recyclerView.setLayoutManager(layoutManager);
    
    // Xử lý cuộn
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            
            // Kiểm tra đang ở cuối danh sách
            isAtBottom = !recyclerView.canScrollVertically(1);
            
            // Tự động ẩn nút "Cuộn xuống" khi đã ở gần cuối
            if (isAtBottom && newMessageButton.getVisibility() == View.VISIBLE) {
                newMessageButton.setVisibility(View.GONE);
            }
            
            // Tự động tải thêm khi cuộn lên đầu
            if (!isLoading && layoutManager.findFirstVisibleItemPosition() < 5) {
                loadMoreMessages();
            }
        }
    });
}

// 5. Đánh dấu đã đọc (ChatRepository.java)
public Task<Void> markAsRead(String chatId, String userId) {
    return db.collection("chats")
            .document(chatId)
            .collection("members")
            .document(userId)
            .update("lastRead", FieldValue.serverTimestamp());
}
```

10. LỘ TRÌNH PHÁT TRIỂN

### Đang phát triển:
- **Hệ thống thông báo in-app**
  - Tích hợp Firebase Cloud Messaging (FCM)
  - Đồng bộ thông báo đa thiết bị
  - Badge counter trên icon app

- **Cải tiến tính năng xóa tin nhắn**
  - Ẩn lịch sử với `clearedAt`
  - Tự động hiện lại khi có tin nhắn mới
  - Xóa khỏi lịch sử hai phía

### Kế hoạch ngắn hạn:
1. **Tối ưu hiệu năng**
   - Phân trang tin nhắn hiệu quả hơn
   - Giảm số lần đọc từ Firestore
   - Tối ưu bộ nhớ cache

2. **Nâng cao bảo mật**
   - Mã hóa đầu cuối cho tin nhắn
   - Xác thực 2 yếu tố
   - Quyền riêng tư chi tiết hơn

3. **Tích hợp đa phương tiện**
   - Gọi thoại/video
   - Gửi nhiều ảnh cùng lúc
   - Hỗ trợ file đa dạng hơn

4. **Cải thiện trải nghiệm**
   - Tìm kiếm tin nhắn
   - Phản hồi/tag tin nhắn
   - Chỉnh sửa/xóa tin nhắn
   - Đánh dấu tin quan trọng

11. KẾT LUẬN

### Tổng quan kiến trúc
- **Frontend (Android)**: Sử dụng MVVM với các thành phần:
  - `Activity/Fragment`: Xử lý giao diện và tương tác người dùng
  - `ViewModel`: Quản lý dữ liệu và logic nghiệp vụ
  - `Repository`: Lớp trung gian giữa local và remote data
  - `Model`: Các đối tượng dữ liệu

- **Backend (Firebase)**:
  - Firestore: Lưu trữ dữ liệu thời gian thực
  - Storage: Lưu trữ file đa phương tiện
  - Authentication: Xác thực người dùng
  - Cloud Functions: Xử lý nghiệp vụ phức tạp

### Điểm mạnh
1. Kiến trúc rõ ràng, dễ bảo trì
2. Hỗ trợ offline tốt
3. Tối ưu hiệu năng với pagination và cache
4. Bảo mật tốt với Firestore Security Rules

### Hạn chế
1. Phụ thuộc vào Firebase
2. Chưa hỗ trợ mã hóa đầu cuối
3. Giới hạn về tính năng so với các ứng dụng chat hiện đại

### Hướng phát triển
- Mở rộng tính năng theo nhu cầu người dùng
- Tối ưu hiệu năng cho thiết bị cấu hình thấp
- Cải thiện trải nghiệm người dùng

=== KẾT THÚC BÁO CÁO PHÂN TÍCH CHI TIẾT ===
