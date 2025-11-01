# UI/UX Analysis
Navigation Structure

```
MainActivity (Splash)
├── PinEntryActivity (Security Gate)
└── HomeActivity (Main Container)
    ├── FeedFragment (Tab 1)
    ├── GroupsFragment (Tab 2)
    ├── ChatFragment (Tab 3)
    │   └── ChatRoomActivity
    │       ├── MembersActivity
    │       └── PhotoGalleryActivity
    │           └── ImageViewerActivity
    └── ProfileFragment (Tab 4)
        ├── SecurityActivity
        └── Color Picker Dialog

Authentication Flow:
LoginActivity ←→ RegisterActivity
└── HomeActivity (after success)
```

## Toolbar & Notification Badge
- Toolbar ở FeedFragment có action Notifications.
- Badge biểu thị trạng thái chưa đọc bằng cách đổi icon:
  - Có chưa đọc: `ic_notifications_active_24`
  - Không có: `ic_notifications_none_24`
- Nguồn dữ liệu: `NoticeCenter` cung cấp `LiveData<Integer> unreadCount`.
- Observer gắn với `getViewLifecycleOwner()` để tránh leak/lifecycle crash.
