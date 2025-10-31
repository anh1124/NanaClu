# Phân tích Logic Nghiệp vụ

## 1. Tính năng cốt lõi

### 1.1 Xác thực người dùng & Bảo mật
**Trường hợp sử dụng**: Đăng nhập an toàn với bảo vệ PIN
**Cách triển khai**: 
- Firebase Auth cho xác thực chính (Google/Email)
- Xác thực thứ cấp bằng PIN với lưu trữ hash
- Quản lý phiên với tự động đăng xuất
**Quy tắc xác thực**:
- PIN phải chính xác 4-6 chữ số
- Tối đa 3 lần thử PIN sai trước khi buộc đăng xuất
- Xác thực định dạng email cho đăng ký
- Yêu cầu độ mạnh mật khẩu

**Ràng buộc nghiệp vụ**:
- Hash PIN được lưu trong SharedPreferences
- Bộ đếm lần thử sai được reset khi đăng nhập thành công
- Tự động đăng xuất sau khi PIN thất bại
- Hệ thống báo cáo nội dung và chặn người dùng

### 1.2 Quản lý nhóm
**Trường hợp sử dụng**: Tạo và quản lý các nhóm cộng đồng
**Cách triển khai**:
- Mã nhóm 6 ký tự duy nhất để tham gia
- Phân quyền theo vai trò (owner > admin > member)
- Denormalization số lượng thành viên để tối ưu hiệu suất
**Quy tắc xác thực**:
- Tên nhóm: 1-50 ký tự, không được trống
- Mô tả: tối đa 500 ký tự
- Mã nhóm: chính xác 6 ký tự, chữ và số, duy nhất
- Chỉ owner mới có thể xóa nhóm
- Chỉ owner/admin mới có thể quản lý thành viên

**Ràng buộc nghiệp vụ**:
- Tạo mã nhóm với cơ chế thử lại để đảm bảo tính duy nhất
- Soft delete cho nhóm (đánh dấu là đã xóa, không xóa thật)
- Số lượng thành viên tự động cập nhật khi tham gia/rời nhóm
- Vai trò owner không thể bị xóa, chỉ có thể chuyển giao
- Hệ thống phê duyệt thành viên mới (requireApproval)

### 1.3 Quản lý bài viết & Nội dung
**Trường hợp sử dụng**: Chia sẻ nội dung trong nhóm với hỗ trợ đa phương tiện
**Cách triển khai**:
- Upload nhiều ảnh lên Firebase Storage
- Hệ thống like/comment với bộ đếm real-time
- Soft delete với quyền của tác giả/admin
**Quy tắc xác thực**:
- Nội dung bài viết: tối đa 1000 ký tự
- Tối đa 5 ảnh mỗi bài viết
- Giới hạn kích thước ảnh: 10MB mỗi ảnh
- Chỉ tác giả hoặc admin nhóm mới có thể xóa bài viết

**Ràng buộc nghiệp vụ**:
- Số lượng like được denormalized trong Post document
- Số lượng comment được cập nhật qua Firestore transactions
- Ảnh được lưu trong Firebase Storage với đường dẫn có tổ chức
- Bài viết đã xóa vẫn còn trong database với cờ deletedAt
- Hệ thống báo cáo nội dung không phù hợp

### 1.4 Hệ thống Chat Real-time
**Trường hợp sử dụng**: Tin nhắn riêng tư và nhóm với chia sẻ ảnh
**Cách triển khai**:
- Firestore snapshot listeners cho cập nhật real-time
- Chat riêng tư với pair keys duy nhất (userId1_userId2)
- Chat nhóm liên kết với thành viên nhóm
- Hỗ trợ tin nhắn text/ảnh/file; ảnh và tệp lưu Firebase Storage
- Hỗ trợ đính kèm file (PDF, DOC, images, audio, video)
**Quy tắc xác thực**:
- Nội dung tin nhắn: tối đa 500 ký tự cho text
- Chat riêng tư: chính xác 2 người tham gia
- Chat nhóm: tối thiểu 2 thành viên
- Tin nhắn ảnh: tối đa 3 ảnh mỗi tin nhắn
- File đính kèm: tối đa 50MB

**Ràng buộc nghiệp vụ**:
- Private chat pair key: userId nhỏ hơn đặt trước (thứ tự từ điển)
- Theo dõi trạng thái gửi tin nhắn
- Quyền thành viên chat được kế thừa từ vai trò nhóm
- Tin nhắn cuối được denormalized trong Chat document để hiển thị danh sách

### 1.5 Quản lý sự kiện
**Trường hợp sử dụng**: Tạo và quản lý sự kiện nhóm với theo dõi RSVP
**Cách triển khai**:
- Tạo sự kiện với xác thực ngày/giờ
- Hệ thống RSVP với theo dõi tham dự
- Cập nhật trạng thái sự kiện tự động dựa trên thời gian
- Nhắc nhở sự kiện: in-app/local notifications; (tùy chọn tương lai) FCM
**Quy tắc xác thực**:
- Tiêu đề sự kiện: 1-100 ký tự
- Thời gian bắt đầu phải trong tương lai
- Thời gian kết thúc phải sau thời gian bắt đầu
- Địa điểm: tối đa 200 ký tự
- Chỉ người tạo sự kiện mới có thể hủy sự kiện

**Ràng buộc nghiệp vụ**:
- Số lượng RSVP được denormalized trong Event document
- Trạng thái sự kiện tự động cập nhật: active → completed (dựa trên thời gian)
- Chỉ thành viên nhóm mới có thể RSVP sự kiện
- Người tạo sự kiện có quyền quản lý đầy đủ
- Hỗ trợ 3 trạng thái RSVP: attending, not_attending, maybe

## 2. Kiến trúc luồng dữ liệu

### 2.1 Luồng dữ liệu MVVM
```
User Action → View → ViewModel → Repository → Firebase → Repository → ViewModel → LiveData → View Update
```

**Ví dụ - Luồng tạo bài viết**:
1. Người dùng điền form trong `CreatePostActivity`
2. View gọi `viewModel.createPost()`
3. ViewModel xác thực dữ liệu và gọi `postRepository.createPost()`
4. Repository upload ảnh lên Firebase Storage
5. Repository tạo Firestore document với URL ảnh
6. Repository trả về success/error cho ViewModel
7. ViewModel cập nhật LiveData
8. View quan sát LiveData và hiển thị kết quả

### 2.2 Luồng dữ liệu Real-time
```
Firestore Change → Snapshot Listener → Repository Callback → ViewModel → LiveData → View Update
```

**Ví dụ - Tin nhắn Chat**:
1. Firestore document được thêm/sửa đổi
2. Snapshot listener trong `ChatRepository` nhận thay đổi
3. Repository phân tích dữ liệu và gọi callback ViewModel
4. ViewModel cập nhật LiveData `messages`
5. `ChatRoomActivity` quan sát LiveData và cập nhật RecyclerView

### 2.3 Luồng upload ảnh
```
User Selection → Compression → Firebase Storage → URL Generation → Firestore Update
```

**Cách triển khai**:
1. Người dùng chọn ảnh từ thư viện/máy ảnh
2. Nén ảnh để giảm kích thước
3. Upload lên Firebase Storage với đường dẫn có tổ chức
4. Lấy download URLs từ Storage
5. Lưu URLs vào Firestore document
6. Hiển thị ảnh sử dụng Glide với caching

### 2.4 Luồng xử lý file đính kèm
```
File Selection → Type Validation → Upload → URL Generation → Message Creation
```

**Cách triển khai**:
1. Người dùng chọn file từ thiết bị
2. Kiểm tra loại file và kích thước (tối đa 50MB)
3. Upload file lên Firebase Storage theo path: `/files/chat_files/{chatId}/{timestamp}_{filename}`
4. Lấy download URL và (tuỳ chọn) tạo `FileAttachment` metadata trên client
5. Lưu vào Message document với `fileUrls` (và/hoặc mảng metadata tuỳ nhu cầu UI)

## 3. Xác thực & Quy tắc nghiệp vụ

### 3.1 Xác thực đầu vào
**Trường văn bản**:
- Loại bỏ khoảng trắng trước khi xác thực
- Ngăn chặn HTML/script injection
- Giới hạn độ dài được thực thi trên client và server
- Xác thực trường bắt buộc với phản hồi người dùng

**Xác thực ảnh**:
- Kiểm tra loại file (chỉ JPEG, PNG)
- Giới hạn kích thước (10MB mỗi ảnh)
- Giới hạn kích thước để tối ưu hiệu suất
- Phát hiện file độc hại

**Xác thực ngày/giờ**:
- Ngày sự kiện phải trong tương lai
- Thời gian kết thúc sau thời gian bắt đầu
- Xử lý múi giờ cho sự kiện
- Tính nhất quán định dạng ngày

**Xác thực file đính kèm**:
- Kiểm tra loại file (PDF, DOC, images, audio, video)
- Giới hạn kích thước (50MB)
- Kiểm tra MIME type
- Ngăn chặn file độc hại

### 3.2 Hệ thống phân quyền
**Quyền nhóm**:
- Owner: Kiểm soát đầy đủ (xóa nhóm, quản lý tất cả thành viên, chuyển giao quyền sở hữu)
- Admin: Quản lý thành viên (trừ owner), kiểm duyệt nội dung, tạo sự kiện
- Member: Tạo bài viết, bình luận, tham gia sự kiện

**Quyền nội dung**:
- Tác giả có thể chỉnh sửa/xóa nội dung của mình
- Admin nhóm có thể kiểm duyệt bất kỳ nội dung nào trong nhóm của họ
- Admin hệ thống có thể kiểm duyệt bất kỳ nội dung nào trên toàn cục

**Quyền chat**:
- Chat riêng tư: Chỉ người tham gia mới có thể gửi tin nhắn
- Chat nhóm: Chỉ thành viên nhóm mới có thể tham gia
- Chỉnh sửa tin nhắn: Chỉ người gửi trong vòng 5 phút sau khi gửi

**Quyền sự kiện**:
- Chỉ người tạo sự kiện mới có thể hủy/chỉnh sửa
- Chỉ thành viên nhóm mới có thể RSVP
- Admin nhóm có thể quản lý sự kiện

### 3.3 Quy tắc tính nhất quán dữ liệu
**Tính nhất quán Denormalization**:
- Số lượng thành viên được cập nhật nguyên tử với thay đổi thành viên
- Số lượng like/comment được cập nhật qua Firestore transactions
- Tên hiển thị người dùng được lan truyền đến tất cả documents liên quan

**Tính toàn vẹn tham chiếu**:
- Cập nhật cascade cho thay đổi tên hiển thị người dùng
- Soft delete để duy trì mối quan hệ dữ liệu
- Dọn dẹp dữ liệu mồ côi qua background functions

### 3.4 Quy tắc bảo mật
**Bảo vệ PIN**:
- Hash PIN với salt
- Giới hạn số lần thử sai
- Tự động đăng xuất sau thất bại

**Báo cáo nội dung**:
- Hệ thống báo cáo với phân loại lý do
- Workflow kiểm duyệt cho moderator
- Theo dõi trạng thái xử lý báo cáo

## 4. Chiến lược xử lý lỗi

### 4.1 Xử lý lỗi mạng
**Hỗ trợ offline**:
- Firestore offline persistence được bật
- Dữ liệu cache được hiển thị khi offline
- Hàng đợi operations cho khi kết nối được khôi phục
- Phản hồi người dùng cho trạng thái offline

**Xử lý timeout**:
- Timeout 30 giây cho hầu hết operations
- Cơ chế retry với exponential backoff
- Tùy chọn người dùng để retry operations thất bại
- Degradation nhẹ nhàng cho các tính năng không quan trọng

### 4.2 Xử lý lỗi người dùng
**Lỗi xác thực**:
- Phản hồi xác thực real-time
- Thông báo lỗi rõ ràng bằng ngôn ngữ của người dùng
- Highlight lỗi cho từng trường cụ thể
- Ngăn chặn submit form không hợp lệ

**Lỗi phân quyền**:
- Thông báo rõ ràng khi hành động không được phép
- Ẩn các UI elements cho hành động không được phép
- Chuyển hướng đến màn hình phù hợp khi cần
- Xử lý nhẹ nhàng các thay đổi phân quyền

### 4.3 Xử lý lỗi hệ thống
**Lỗi Firebase**:
- Xử lý cụ thể cho các lỗi Firebase phổ biến
- Thông báo lỗi thân thiện với người dùng
- Retry tự động cho lỗi tạm thời
- Tùy chọn fallback khi có thể

**Lỗi Storage**:
- Xử lý thất bại upload ảnh
- Khôi phục upload một phần
- Xử lý vượt quá hạn mức Storage
- Fallback hosting ảnh thay thế

### 4.4 Xử lý lỗi file
**Upload file thất bại**:
- Thông báo lỗi cụ thể cho từng loại file
- Retry upload với progress tracking
- Validation file trước khi upload
- Cleanup file tạm khi thất bại

## 5. Tác vụ nền & Operations bất đồng bộ

### 5.1 Xử lý ảnh
**Pipeline Upload**:
- Thread nền cho nén ảnh
- Theo dõi progress cho upload lớn
- Batch upload cho nhiều ảnh
- Dọn dẹp file tạm

**Chiến lược Caching**:
- Glide cho caching và loading ảnh
- Preload ảnh để UX tốt hơn
- Quản lý kích thước cache
- Khả năng ảnh offline

### 5.2 Đồng bộ dữ liệu
**Real-time Listeners**:
- Firestore snapshot listeners cho dữ liệu live
- Xử lý kết nối lại tự động
- Quản lý lifecycle listener
- Ngăn chặn memory leak

**Đồng bộ nền**:
- Làm mới dữ liệu định kỳ khi app ở background
- Đồng bộ trạng thái người dùng và presence
- Cập nhật notification badges
- Dọn dẹp dữ liệu cache cũ

### 5.3 Xử lý thông báo
**In‑App Notifications (hiện tại)**:
- Badge/list trong app dựa trên Firestore listeners
- Local notifications cho nhắc nhở sự kiện

**Push Notifications (tương lai)**:
- Tích hợp FCM khi sẵn sàng
- Nhóm/throttling và deep linking từ push

**Thông báo trong app**:
- Cập nhật trạng thái real-time
- Toast messages cho hành động người dùng
- Progress indicators cho operations dài
- Phản hồi thành công/lỗi

### 5.4 Xử lý file đính kèm
**Upload Pipeline**:
- Background thread cho upload file
- Progress tracking cho file lớn
- Validation file trước khi upload
- Cleanup file tạm sau upload

**File Management**:
- Local storage cho file đã download
- Cache management cho file
- Offline access cho file đã download
- File type validation và security

## 6. Tối ưu hiệu suất

### 6.1 Tối ưu Database
**Tối ưu Query**:
- Compound indexes cho queries phức tạp
- Pagination cho datasets lớn
- Giới hạn queries đến các fields cần thiết
- Cache dữ liệu được truy cập thường xuyên

**Chiến lược Denormalization**:
- Lưu trữ giá trị đã tính toán (counts, aggregates)
- Duplicate dữ liệu được truy cập thường xuyên
- Đánh đổi storage để có hiệu suất query tốt hơn
- Duy trì tính nhất quán với transactions

### 6.2 Hiệu suất UI
**Tối ưu List**:
- RecyclerView với ViewHolder pattern
- Lazy loading cho ảnh
- Pagination cho infinite scroll
- Efficient data binding

**Quản lý Memory**:
- Xử lý lifecycle đúng cách
- Tối ưu memory ảnh
- Cleanup listener trong onDestroy
- Tránh memory leaks trong async operations

### 6.3 Tối ưu Network
**Transfer dữ liệu**:
- Giảm thiểu kích thước payload
- Nén ảnh trước khi upload
- Sử dụng định dạng ảnh phù hợp
- Implement request deduplication

**Chiến lược Caching**:
- HTTP caching cho static resources
- Database caching cho dữ liệu được truy cập thường xuyên
- Image caching với giới hạn kích thước
- Chiến lược cache invalidation

### 6.4 Tối ưu File Management
**Upload Optimization**:
- Batch upload cho multiple files
- Compression cho file lớn
- Progress tracking cho UX tốt hơn
- Retry mechanism cho failed uploads

**Storage Optimization**:
- Local caching cho file thường dùng
- Cleanup file không cần thiết
- Efficient file type detection
- Security validation trước khi storage