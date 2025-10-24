## Bộ Use Case theo mức độ chi tiết — NanaClu

Cập nhật: 2025-10-24

Phạm vi: Ứng dụng mạng xã hội định hướng nhóm/cộng đồng NanaClu (Android, Firebase Auth/Firestore/Storage).

### Actors chính
- **Khách (Guest)**: Chưa đăng nhập; có thể xem màn hình chào, đăng ký/đăng nhập.
- **Người dùng (Member/User)**: Đã xác thực; tham gia nhóm, đăng bài, bình luận, chat, RSVP sự kiện.
- **Chủ nhóm/Quản trị nhóm (Group Owner/Admin)**: Tạo/quản lý nhóm, quản trị thành viên, kiểm duyệt nội dung, tạo sự kiện.
- **Admin hệ thống**: Xem/bXử lý báo cáo vi phạm ở phạm vi toàn hệ thống.
- **Dịch vụ Firebase** (tác nhân hệ thống): Xác thực, lưu trữ/đồng bộ dữ liệu, lưu media.

---

## Use Case Tổng quát (High-level / Conceptual)
Mô tả các khả năng lớn của hệ thống, không đi sâu từng bước.

1) **Xác thực & Bảo mật**: Đăng ký/đăng nhập (Email/Google), thiết lập/nhập PIN bảo vệ ứng dụng.
2) **Khám phá & Quản lý Nhóm**: Tạo nhóm, tìm/tham gia bằng mã, quản trị vai trò thành viên.
3) **Bảng tin & Nội dung**: Tạo bài viết (ảnh), xem feed, like, bình luận theo thời gian thực.
4) **Nhắn tin**: Chat riêng và chat nhóm, gửi tin nhắn văn bản/ảnh/file, gallery media.
5) **Sự kiện**: Tạo sự kiện nhóm, RSVP, theo dõi tham dự.
6) **Báo cáo & Kiểm duyệt**: Báo cáo nội dung, dashboard cho admin nhóm/hệ thống.
7) **Thông báo trong app (định hướng)**: Cập nhật hoạt động mới (bài viết, bình luận, chat, sự kiện) theo thời gian thực.

---

## Use Case Mức 0 (Context Level)
Dùng cho sơ đồ ngữ cảnh/Use Case Diagram tổng thể: mỗi use case là một chức năng chính.

### Liệt kê Use Case chính theo Actor
- **Khách**
  - UC-01: Đăng ký tài khoản
  - UC-02: Đăng nhập (Email/Google)

- **Người dùng**
  - UC-03: Thiết lập/nhập mã PIN ứng dụng
  - UC-04: Cập nhật hồ sơ cá nhân
  - UC-05: Tìm kiếm/Khám phá nhóm
  - UC-06: Tham gia nhóm bằng mã mời
  - UC-07: Rời nhóm
  - UC-08: Tạo bài viết (kèm ảnh)
  - UC-09: Xem feed đa‑nhóm
  - UC-10: Like/Bình luận bài viết
  - UC-11: Mở chat và gửi tin nhắn (text/ảnh/file)
  - UC-12: Xem/RSVP sự kiện
  - UC-13: Báo cáo nội dung vi phạm

- **Chủ nhóm/Quản trị nhóm**
  - UC-14: Tạo nhóm
  - UC-15: Quản lý thành viên (phân quyền, chấp thuận, chặn)
  - UC-16: Kiểm duyệt nội dung trong nhóm
  - UC-17: Tạo/Quản lý sự kiện nhóm

- **Admin hệ thống**
  - UC-18: Xem và xử lý báo cáo toàn hệ thống

- **Firebase (Hệ thống)**
  - UC-19: Xác thực người dùng
  - UC-20: Lưu trữ/đồng bộ dữ liệu thời gian thực
  - UC-21: Lưu trữ media (ảnh/file)
  - UC-22: Phát sinh thông báo trong app (định hướng triển khai)

Gợi ý quan hệ bao hàm/mở rộng (include/extend):
- UC-08 “Tạo bài viết” include “Upload ảnh”.
- UC-11 “Gửi tin nhắn” extend “Gửi ảnh/file đính kèm”.
- UC-15 “Quản lý thành viên” include “Tìm thành viên”, “Cập nhật vai trò”.

---

## Use Case Mức 1 (Detailed Level) — Mô tả ngắn gọn theo bước
Mỗi UC gồm: Mô tả, Actor, Tiền điều kiện, Hậu điều kiện, Kích hoạt, Luồng chính, Ngoại lệ.

### UC-01 — Đăng ký tài khoản
- **Actor**: Khách
- **Mô tả**: Tạo tài khoản bằng Email/Password hoặc Google.
- **Tiền điều kiện**: Ứng dụng cài đặt hợp lệ, mạng khả dụng.
- **Hậu điều kiện**: Tài khoản `users/{uid}` được tạo/ghi nhận; trạng thái “online”.
- **Kích hoạt**: Người dùng chọn “Đăng ký”.
- **Luồng chính**:
  1. Nhập email, mật khẩu, tên hiển thị hoặc chọn Google Sign‑In.
  2. Hệ thống xác thực qua Firebase Auth.
  3. Tạo/merge tài liệu người dùng trong `users/{uid}`.
  4. Điều hướng vào `HomeActivity`.
- **Ngoại lệ**:
  - E1: Email không hợp lệ/mật khẩu yếu → Hiển thị lỗi, yêu cầu sửa.
  - E2: Tài khoản đã tồn tại → Gợi ý đăng nhập.

### UC-02 — Đăng nhập
- **Actor**: Khách
- **Tiền điều kiện**: Tài khoản hợp lệ; có mạng.
- **Hậu điều kiện**: Đăng nhập thành công; `status=online` cập nhật.
- **Kích hoạt**: Chọn “Đăng nhập”.
- **Luồng chính**: Nhập thông tin → Firebase Auth → Cập nhật `lastLoginAt` → Vào `HomeActivity`.
- **Ngoại lệ**: Sai thông tin; tài khoản bị khóa; mạng lỗi.

### UC-03 — Thiết lập/nhập mã PIN
- **Actor**: Người dùng
- **Tiền điều kiện**: Đã đăng nhập.
- **Hậu điều kiện**: PIN hash lưu trong SharedPreferences; bảo vệ màn hình chính.
- **Kích hoạt**: Người dùng bật khóa PIN trong `SecurityActivity` hoặc vào app khi PIN đã bật.
- **Luồng chính**:
  1. Thiết lập PIN (tạo và xác nhận).
  2. Khi mở app, nhập PIN để vào `HomeActivity`.
- **Ngoại lệ**: Quá số lần nhập sai → Buộc đăng xuất.

### UC-04 — Tìm kiếm/Khám phá nhóm
- **Actor**: Người dùng
- **Tiền điều kiện**: Đã đăng nhập.
- **Hậu điều kiện**: Người dùng tìm thấy nhóm phù hợp.
- **Kích hoạt**: Mở `GroupsFragment` và dùng tìm kiếm/danh mục.
- **Luồng chính**: Mở danh sách → Nhập từ khóa → Xem chi tiết nhóm.
- **Ngoại lệ**: Không có kết quả → Hiển thị trạng thái rỗng/gợi ý.

### UC-05 — Tham gia nhóm bằng mã mời
- **Actor**: Người dùng
- **Tiền điều kiện**: Có mã nhóm hợp lệ.
- **Hậu điều kiện**: Bản ghi thành viên `groups/{groupId}/members/{userId}` được tạo.
- **Kích hoạt**: Chọn “Nhập mã tham gia”.
- **Luồng chính**: Nhập mã (6 ký tự) → Xác thực mã → Ghi membership → Vào feed nhóm.
- **Ngoại lệ**: Mã sai/nhóm riêng cần phê duyệt → Thông báo trạng thái chờ.

### UC-06 — Tạo nhóm
- **Actor**: Chủ nhóm tiềm năng (Người dùng)
- **Tiền điều kiện**: Đã đăng nhập.
- **Hậu điều kiện**: `groups/{groupId}` được tạo; người tạo là owner; mã mời sinh ra.
- **Kích hoạt**: Chọn “Tạo nhóm”.
- **Luồng chính**: Nhập tên/mô tả/riêng tư → Tạo nhóm → Sinh mã mời → Chia sẻ mã.
- **Ngoại lệ**: Tên trống/quá dài; lỗi mạng.

### UC-07 — Tạo bài viết (kèm ảnh)
- **Actor**: Người dùng (thành viên nhóm)
- **Tiền điều kiện**: Là thành viên nhóm; có quyền đăng.
- **Hậu điều kiện**: `posts/{postId}` tạo dưới nhóm; ảnh lưu Storage; feed cập nhật realtime.
- **Kích hoạt**: Bấm “Tạo bài viết” trong nhóm.
- **Luồng chính**:
  1. Nhập nội dung (≤1000 ký tự), chọn tối đa 5 ảnh.
  2. Nén & upload ảnh lên Storage → lấy URLs.
  3. Ghi document Post với `imageUrls`, `author*`, counters=0.
  4. Feed hiển thị bài mới.
- **Ngoại lệ**: Ảnh vượt kích thước; mạng lỗi; quyền không đủ.

### UC-08 — Xem feed và tương tác (Like/Bình luận)
- **Actor**: Người dùng
- **Tiền điều kiện**: Là thành viên ít nhất một nhóm.
- **Hậu điều kiện**: Ghi like vào `likes`, bình luận vào `comments`; bộ đếm cập nhật.
- **Kích hoạt**: Mở `FeedFragment` hoặc chi tiết bài viết.
- **Luồng chính**: Cuộn feed → Like/Unlike → Viết bình luận → Thấy cập nhật realtime.
- **Ngoại lệ**: Bài bị xóa/đã khóa; mạng lỗi.

### UC-09 — Mở chat và gửi tin nhắn (text/ảnh/file)
- **Actor**: Người dùng
- **Tiền điều kiện**: Là thành viên chat (riêng hoặc nhóm); quyền gửi.
- **Hậu điều kiện**: `messages/{messageId}` tạo; `chat.lastMessage*` cập nhật; gallery cập nhật.
- **Kích hoạt**: Mở `ChatFragment`/`ChatRoomActivity`.
- **Luồng chính**:
  1. Chọn cuộc trò chuyện (hoặc tạo mới với người dùng khác).
  2. Nhập nội dung hoặc chọn ảnh/file (≤50MB).
  3. Gửi; người nhận thấy tin nhắn realtime.
- **Ngoại lệ**: File quá lớn/định dạng cấm; không còn quyền trong nhóm.

### UC-10 — Tạo sự kiện nhóm
- **Actor**: Chủ nhóm/Admin nhóm
- **Tiền điều kiện**: Quyền tạo sự kiện trong nhóm.
- **Hậu điều kiện**: `events/{eventId}` tạo; thông tin hiện trên lịch/nguồn sự kiện.
- **Kích hoạt**: Chọn “Tạo sự kiện”.
- **Luồng chính**: Nhập tiêu đề, thời gian tương lai, địa điểm → Lưu sự kiện → Hiển thị trong nhóm.
- **Ngoại lệ**: Thời gian không hợp lệ; quyền không đủ.

### UC-11 — RSVP sự kiện
- **Actor**: Người dùng
- **Tiền điều kiện**: Là thành viên nhóm có sự kiện.
- **Hậu điều kiện**: Bản ghi RSVP được tạo/cập nhật; counters denormalized cập nhật.
- **Kích hoạt**: Mở chi tiết sự kiện và chọn trạng thái.
- **Luồng chính**: Chọn Attending/Maybe/Not attending → Lưu.
- **Ngoại lệ**: Sự kiện đã kết thúc/đã hủy; mạng lỗi.

### UC-12 — Báo cáo nội dung vi phạm
- **Actor**: Người dùng, Admin nhóm/hệ thống (xử lý)
- **Tiền điều kiện**: Nội dung tồn tại; người dùng đăng nhập.
- **Hậu điều kiện**: Tạo `reports/{reportId}`; trạng thái báo cáo theo dõi.
- **Kích hoạt**: Chọn “Báo cáo” ở bài viết/bình luận/tin nhắn.
- **Luồng chính**: Chọn lý do → Gửi báo cáo → Admin xem và xử lý (ẩn/xóa/cảnh cáo).
- **Ngoại lệ**: Báo cáo trùng lặp; quyền không đủ để thực thi hành động.

### UC-13 — Quản lý thành viên nhóm (phân quyền/chặn/xóa)
- **Actor**: Chủ nhóm/Admin nhóm
- **Tiền điều kiện**: Có quyền quản trị; nhóm tồn tại.
- **Hậu điều kiện**: Vai trò/thành viên được cập nhật trong `members`.
- **Kích hoạt**: Mở màn hình quản lý thành viên.
- **Luồng chính**: Tìm thành viên → Chọn hành động (nâng hạ vai trò, chặn, xóa) → Lưu.
- **Ngoại lệ**: Không thể xóa owner; xung đột quyền; mạng lỗi.

---

## Ghi chú triển khai/nghiệp vụ (tham chiếu nhanh)
- Dữ liệu chính: `users`, `groups/{groupId}/{members|posts|events|chats}`, `chats/{chatId}/{members|messages}`, `reports`.
- Ràng buộc điển hình: độ dài nội dung; tối đa 5 ảnh/bài; file ≤50MB; mã nhóm 6 ký tự; chỉ owner xóa nhóm; soft‑delete cho nội dung.
- Realtime: Listener cho feed, bình luận, chat, RSVP; counters denormalized; phân trang cho danh sách lớn.
- Bảo mật: Quy tắc Firestore theo membership/role; PIN app‑lock; chặn người dùng.
- Thông báo trong app: đề xuất `users/{uid}/notices` (đang lộ trình) để badge/bảng thông báo.

---

Tài liệu này nhằm phục vụ môn Phân tích & Thiết kế Hệ thống: cung cấp cả bức tranh tổng quát (High‑level), danh mục UC mức ngữ cảnh (Level 0), và mô tả bước UC mức chi tiết (Level 1) để hỗ trợ lập trình/kiểm thử.