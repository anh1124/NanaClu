# Tổng quan Project

## 1. Thông tin cơ bản
- Tên project: nanaclu
- Package name: com.example.nanaclu
- Version: 1.0 (versionCode: 1)
- Target SDK: 35 (Android 14)
- Minimum SDK: 31 (Android 12)

## 2. Mục đích ứng dụng
Ứng dụng Android hỗ trợ **quản lý nhóm, sự kiện và tương tác xã hội**. Đây là một hệ thống mạng xã hội mini với trọng tâm là quản lý cộng đồng và nhóm, bao gồm:

- **Quản lý nhóm**: Tạo nhóm public/private, quản lý thành viên, phân quyền admin/owner
- **Bài viết và tương tác**: Đăng bài, bình luận, like/unlike với hỗ trợ ảnh
- **Chat**: Tin nhắn riêng tư và chat nhóm realtime
- **Sự kiện**: Tạo và quản lý sự kiện trong nhóm
- **Xác thực**: Đăng nhập/đăng ký qua Firebase Auth (Google, Email/Password)

## 3. Công nghệ sử dụng
- **Android SDK**: API 35 (compileSdk), minimum API 31
- **Ngôn ngữ**: Java với Java 11 compatibility
- **Kiến trúc**: MVVM (Model-View-ViewModel)
- **Firebase services**:
  - Firebase Auth (Google Sign-in, Email/Password)
  - Firebase Firestore (NoSQL database)
  - Firebase Storage (image storage)
- **Other libraries**:
  - AndroidX Lifecycle (LiveData, ViewModel)
  - Material Design Components
  - Glide (image loading)
  - SwipeRefreshLayout
  - Preference library

## 4. Cấu trúc thư mục
```
app/
├── src/
│   └── main/
│       ├── java/com/example/nanaclu/
│       │   ├── data/
│       │   │   ├── model/          # POJO classes (User, Group, Post, Event, Chat, etc.)
│       │   │   └── repository/     # Firestore repositories
│       │   ├── ui/
│       │   │   ├── auth/          # Login & Register
│       │   │   ├── group/         # Group management
│       │   │   ├── post/          # Post creation & detail
│       │   │   ├── security/      # PIN security
│       │   │   └── [other modules]
│       │   ├── viewmodel/         # ViewModels for MVVM
│       │   ├── utils/             # Helper utilities
│       │   ├── MainActivity.java
│       │   └── HomeActivity.java
│       ├── res/                   # Resources (layouts, strings, etc.)
│       └── AndroidManifest.xml
└── build.gradle
```

## 5. Permissions
- **CAMERA**: Cho phép chụp ảnh để đăng bài hoặc cập nhật avatar
- **READ_EXTERNAL_STORAGE**: Đọc ảnh từ bộ nhớ (Android 12 và thấp hơn)
- **READ_MEDIA_IMAGES**: Đọc ảnh từ bộ nhớ (Android 13+)
- **Hardware camera**: Tùy chọn, không bắt buộc (required="false")

## 6. Đặc điểm kiến trúc
- **MVVM Pattern**: View không gọi trực tiếp Firestore, chỉ thông qua ViewModel
- **Repository Pattern**: Tách biệt logic truy cập dữ liệu
- **Firebase Integration**: Sử dụng Firestore cho realtime data và Storage cho ảnh
- **Security**: Hỗ trợ PIN bảo mật với PinEntryActivity
- **Theme Support**: Custom toolbar color với ThemeUtils