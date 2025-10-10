# UI/UX Analysis

## 1. Design System

### 1.1 Color Palette
- **Primary Color**: #6200EE (Purple 500) - Customizable via ThemeUtils
- **Background**: Standard Material Design backgrounds
- **Text Colors**: Material Design text color hierarchy
- **Error Color**: #F44336 (Red) for validation messages
- **Success/Online**: Green variants for status indicators

### 1.2 Typography
- **Font Family**: Default system fonts (Roboto on Android)
- **Text Sizes**: Following Material Design type scale
- **Text Hierarchy**: 
  - Headlines for screen titles
  - Body text for content
  - Caption for timestamps and metadata

### 1.3 Material Design Components
- **MaterialToolbar**: Custom colored toolbars
- **FloatingActionButton**: For primary actions (create post, send message)
- **RecyclerView**: For all list displays
- **SwipeRefreshLayout**: Pull-to-refresh functionality
- **BottomNavigationView**: Main app navigation

## 2. Screen Analysis

### 2.1 MainActivity (Splash/Loading)
**Layout**: `activity_main.xml`
**Components**:
- ConstraintLayout container
- Centered ProgressBar
- Minimal loading screen

**Purpose**: Initial app loading and authentication check
**User Flow**: Auto-redirects to PinEntryActivity or HomeActivity

### 2.2 PinEntryActivity (Security)
**Layout**: `activity_pin_entry.xml`
**Components**:
- PIN display TextView
- Numeric keypad (0-9, delete)
- Attempts counter
- Security-focused minimal UI

**User Flow**: 
1. User enters 4-digit PIN
2. Verification against stored hash
3. Success → HomeActivity
4. Failure → Retry with attempt counter

### 2.3 HomeActivity (Main Container)
**Layout**: `activity_home.xml`
**Components**:
- MaterialToolbar with custom color
- FrameLayout for fragment container
- BottomNavigationView (4 tabs)

**Navigation Tabs**:
- **Home/Feed**: Posts from joined groups
- **Groups**: Group management
- **Chat**: Conversations list
- **Profile**: User settings

### 2.4 LoginActivity
**Layout**: `activity_login.xml`
**Components**:
- Email/Password input fields
- Login button
- Google Sign-In button
- Register link
- Status TextView for error messages

**User Flow**:
1. Email/Password input
2. Validation feedback
3. Authentication via Firebase
4. Success → HomeActivity

### 2.5 ChatRoomActivity
**Layout**: `activity_chat_room.xml`
**Components**:
- MaterialToolbar with chat title
- RecyclerView for messages
- Message input EditText
- Send button
- Image attachment button
- Photo gallery access

**Features**:
- Real-time message updates
- Image sharing capability
- Message timestamp display
- Sender identification

### 2.6 PhotoGalleryActivity
**Layout**: `activity_photo_gallery.xml`
**Components**:
- MaterialToolbar with "Ảnh đã gửi" title
- RecyclerView with GridLayoutManager (3 columns)
- Google Photos-style grid layout

**User Flow**:
1. Display all images from chat
2. Grid view with 3 columns
3. Tap to open ImageViewerActivity
4. Swipe navigation between images

### 2.7 ProfileFragment
**Layout**: `fragment_profile.xml`
**Components**:
- User avatar display
- Display name and email
- Last login timestamp
- Color picker for toolbar customization
- Security settings row
- Logout button

**Features**:
- Theme customization (toolbar color)
- Security PIN management
- User profile display

## 3. Navigation Structure

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

## 4. Layout Patterns

### 4.1 List Item Layouts
- **Chat Item**: Avatar, name, last message, timestamp
- **Message Item**: Sender info, content, timestamp, delivery status
- **Post Item**: Author info, content, images, like/comment counts
- **Group Item**: Group avatar, name, member count, join status

### 4.2 Form Layouts
- **Linear vertical layouts** for forms
- **Material Design input fields** with proper validation
- **Consistent spacing** using margins and padding
- **Error state handling** with color changes

### 4.3 Grid Layouts
- **Photo Gallery**: 3-column grid using GridLayoutManager
- **Color Picker**: 6-column grid for color swatches
- **Responsive spacing** with density-based calculations

## 5. User Experience Features

### 5.1 Real-time Updates
- **Chat messages**: Instant delivery via Firestore listeners
- **Online status**: User presence indicators
- **Post interactions**: Live like/comment counts

### 5.2 Offline Support
- **Firestore offline persistence** enabled
- **Cached user data** in SharedPreferences
- **Graceful degradation** when offline

### 5.3 Image Handling
- **Glide integration** for efficient image loading
- **Multiple image support** in posts and messages
- **Full-screen image viewer** with swipe navigation
- **Gallery organization** by chat/conversation

### 5.4 Security Features
- **PIN protection** with attempt limiting
- **Secure logout** clearing sensitive data
- **Session management** with automatic timeouts

## 6. Responsive Design

### 6.1 Screen Adaptations
- **Density-independent pixels** for consistent sizing
- **Flexible layouts** using ConstraintLayout
- **Scalable images** with proper aspect ratios
- **Touch target sizes** meeting accessibility guidelines

### 6.2 Orientation Support
- **Portrait-optimized** layouts
- **Landscape considerations** for chat and image viewing
- **State preservation** during configuration changes

## 7. Accessibility Features

### 7.1 Content Descriptions
- **Image content descriptions** for screen readers
- **Button labels** clearly describing actions
- **Navigation announcements** for screen reader users

### 7.2 Touch Accessibility
- **Minimum touch target sizes** (48dp)
- **Adequate spacing** between interactive elements
- **Clear visual feedback** for button presses

### 7.3 Color Accessibility
- **Sufficient color contrast** for text readability
- **Color-independent information** (not relying solely on color)
- **Customizable theme colors** for user preferences

## 8. Animation and Transitions

### 8.1 Navigation Transitions
- **Fragment transitions** between tabs
- **Activity transitions** for deep navigation
- **Smooth animations** for user feedback

### 8.2 Interactive Feedback
- **Button press animations** with ripple effects
- **Loading states** with progress indicators
- **Swipe gestures** for refresh and navigation

### 8.3 Content Animations
- **List item animations** for new content
- **Image loading transitions** with fade-in effects
- **Status change animations** for real-time updates

## 9. Performance Considerations

### 9.1 Image Optimization
- **Glide caching** for efficient image loading
- **Thumbnail generation** for grid views
- **Lazy loading** for large image lists

### 9.2 List Performance
- **RecyclerView optimization** with ViewHolder pattern
- **Pagination support** for large datasets
- **Efficient data binding** to minimize UI updates

### 9.3 Memory Management
- **Proper lifecycle handling** in fragments
- **Image memory management** with Glide
- **Listener cleanup** to prevent memory leaks