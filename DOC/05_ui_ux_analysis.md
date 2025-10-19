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
