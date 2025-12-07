[![Video Demo](https://img.youtube.com/vi/m02R2U3QeCg/0.jpg)](https://youtu.be/m02R2U3QeCg)

# NANACLU - Community & Group Management App

A comprehensive Android application built with Firebase that enables users to create and manage communities, share content, communicate through real-time chat, and organize events. Designed as a mini social network platform with a focus on community building and group management.

## 🎯 Project Overview

NANACLU is a feature-rich social media application that combines group management, real-time messaging, content sharing, and event organization. Built with modern Android development practices and Firebase backend services, it provides a complete community platform experience.

---

## ✨ Key Features

### 🔐 Authentication & User Management
- **Multi-provider Authentication**: Login/Register with Google OAuth or Email/Password via Firebase Auth
- **Profile Management**: Custom user profiles with avatar images stored in Firebase Storage
- **App Security**: PIN-based app lock feature for enhanced privacy

### 👥 Group Management
- **Create & Join Groups**: Support for both public and private groups
- **Member Management**: Invite, approve/reject members, role-based permissions (Owner/Admin/Member)
- **Group Settings**: Edit group info, transfer ownership, notification preferences
- **Group Analytics**: Member statistics, activity logs, and group insights
- **Moderation Tools**: Block/unblock users, pending member approvals

### 📝 Content & Social Features
- **Rich Posts**: Create posts with text and multiple images
- **Real-time Interactions**: Like/unlike posts, real-time comments with threading
- **Content Moderation**: Pending post approval for private groups
- **Polls & Surveys**: Create polls, view results, and track voter participation

### 💬 Real-time Chat
- **Private & Group Chat**: One-on-one and group conversations
- **Rich Media Sharing**: Send images, files, and documents (up to 50MB)
- **Chat Gallery**: Browse shared photos and files within chat rooms
- **Message Management**: Hide conversations, message status indicators

### 📅 Event Management
- **Event Creation**: Schedule events within groups with full details
- **RSVP System**: Attend/Maybe/Not attending responses
- **Calendar Integration**: Monthly calendar view with event listings
- **Event Discussions**: Comment and discuss upcoming events

### 🔔 Notifications & Communication
- **In-app Notifications**: Real-time badge counts and notification center
- **Activity Tracking**: System for tracking likes, comments, mentions
- **Friend System**: Send/receive friend requests, manage friend lists
- **User Search**: Find and connect with other users

### 🔍 Search & Discovery
- **User Search**: Find users by name or email
- **Group Discovery**: Browse and join available groups
- **Advanced Filtering**: Search with various criteria and filters

### 📊 Administration & Analytics
- **Admin Dashboard**: Comprehensive admin panel for system management
- **User Reports**: Report inappropriate content or users
- **Statistics**: Group and user activity analytics
- **Data Export**: Export system data for analysis

---

## 🏗️ Technology Stack & Architecture

### Core Technologies
- **Language**: Java 11
- **Platform**: Android (Min SDK: 31/API 31, Target SDK: 35/API 35)
- **Architecture**: MVVM (Model-View-ViewModel) with Repository pattern
- **Database**: Firebase Firestore (NoSQL cloud database)
- **Authentication**: Firebase Authentication (Google OAuth + Email/Password)
- **Storage**: Firebase Cloud Storage for media files
- **Real-time Updates**: Firestore snapshot listeners for live data

### UI & Media
- **UI Framework**: AndroidX, Material Design Components
- **Image Processing**: Glide for efficient image loading and caching
- **Video Playback**: Media3 ExoPlayer for video content
- **Responsive Design**: Adaptive layouts for various screen sizes

### MVVM Architecture Flow
```
Model (POJO Classes)
    ↓
Repository Layer (Data Access)
    ↓
ViewModel (Business Logic + LiveData)
    ↓
View (Activity/Fragment + Data Binding)
```

### Key Components
- **Models**: Plain Java objects mapping to Firestore documents (User, Group, Post, Message, etc.)
- **Repositories**: Data access layer handling Firebase operations
- **ViewModels**: UI state management with LiveData observables
- **Activities/Fragments**: UI components observing ViewModel data

---

## 📂 Project Structure

```
app/src/main/java/com/example/nanaclu/
├── data/
│   ├── model/           # POJO classes (User, Group, Post, Message, etc.)
│   └── repository/      # Firebase data repositories
├── ui/
│   ├── adapter/         # RecyclerView adapters
│   ├── auth/           # Authentication screens
│   ├── home/           # Main feed and navigation
│   ├── group/          # Group management screens
│   ├── chat/           # Chat and messaging
│   ├── post/           # Post creation and details
│   ├── event/          # Event management
│   ├── profile/        # User profile management
│   ├── search/         # Search functionality
│   ├── notifications/  # In-app notifications
│   ├── admin/          # Admin dashboard
│   └── security/       # App security features
├── viewmodel/          # ViewModel classes
├── utils/             # Helper utilities
└── base/              # Base classes
```

### 📦 Firestore Database Schema

```
users/{userId} (User document)
├── images/{imageId} (UserImage subcollection)

groups/{groupId} (Group document)
├── members/{userId} (Member subcollection)
├── posts/{postId} (Post subcollection)
│   ├── comments/{commentId} (Comment subcollection)
│   └── likes/{userId} (Like subcollection)
├── events/{eventId} (Event subcollection)
│   └── participants/{userId} (Participant subcollection)
└── logs/{logId} (GroupLog subcollection)

chats/{chatId} (Chat document)
├── members/{userId} (ChatMember subcollection)
└── messages/{messageId} (Message subcollection)

notices/{noticeId} (In-app notifications)
reports/{reportId} (User reports)
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **Java Development Kit (JDK)**: Version 11
- **Firebase Project**: Set up with Authentication, Firestore, and Storage enabled
- **Google Services**: Configure google-services.json

### Installation & Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/anh1124/NanaClu.git
   cd NanaClu
   ```

2. **Open in Android Studio**
   - Import the project as an existing Android Studio project
   - Wait for Gradle sync to complete

3. **Firebase Configuration**
   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com)
   - Enable Authentication (Google + Email/Password)
   - Enable Firestore Database
   - Enable Storage
   - Download `google-services.json` and place it in `app/` directory

4. **Configure Firestore Security Rules**
   - Copy the security rules from `DOC/03_firebase_integration.md`
   - Apply them in Firebase Console > Firestore > Rules

5. **Configure Storage Security Rules**
   - Set up appropriate storage rules for image/file access
   - Reference: `firebase_storage_rules.txt`

6. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

### 🔧 Configuration

#### Firebase BOM Version
The project uses Firebase BOM 34.1.0. Update in `app/build.gradle` if needed:
```gradle
implementation platform('com.google.firebase:firebase-bom:34.1.0')
```

#### Android SDK Requirements
- **Minimum SDK**: 31 (Android 12)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35

---

## 📋 Development Guidelines

### MVVM Architecture Principles
1. **Views** (Activities/Fragments) observe data from ViewModels only
2. **ViewModels** contain business logic and expose LiveData
3. **Repositories** handle data operations with Firebase
4. **Models** are plain POJOs mapping to Firestore documents

### Code Style Standards
- **Class Names**: PascalCase (e.g., `UserViewModel`, `PostRepository`)
- **Variable Names**: camelCase (e.g., `createdAt`, `authorId`)
- **Package Structure**: Follow the established folder hierarchy
- **Documentation**: Comprehensive comments for Repositories and ViewModels

### Firebase Best Practices
- **Security Rules**: Users can only modify their own data
- **Data Validation**: Client-side validation before Firebase operations
- **Offline Support**: Firestore persistence enabled by default
- **Indexing**: Create composite indexes for complex queries
- **Storage URLs**: Use Firebase Storage URLs instead of base64 encoding

---

## 🔄 Data Synchronization Strategy

### Real-time Updates
- **Chat**: Firestore snapshot listeners for instant message delivery
- **Comments**: Real-time comment threads with live updates
- **Feed**: Pull-to-refresh with `startAfter` pagination
- **Notifications**: Badge counts calculated client-side for performance

### Performance Optimization
- **Caching**: ViewModel-level caching to reduce Firebase calls
- **Pagination**: Cursor-based pagination for large datasets
- **Background Tasks**: Avoid Firebase calls when app is backgrounded
- **Query Optimization**: Strategic use of indexes and query conditions

---

## 🛠️ Data Models

### Core Entity Classes

```java
// User Profile with Image References
public class User {
    public String userId;
    public long createdAt;
    public String email;
    public String displayName;
    public String avatarImageId;  // References images subcollection
    public long lastLoginAt;
    public String status;         // "online" | "offline"
}

// Group with Member Management
public class Group {
    public String groupId;
    public String name;
    public String description;
    public String avatarImageId;  // Firebase Storage reference
    public String coverImageId;
    public String createdBy;
    public long createdAt;
    public boolean isPublic;      // Public/Private group flag
    public int memberCount;
    public int postCount;
}

// Real-time Chat System
public class Chat {
    public String chatId;
    public long createdAt;
    public String type;           // "private" | "group"
    public int memberCount;
}

public class Message {
    public String messageId;
    public String authorId;
    public String type;           // "text" | "image" | "file"
    public String content;
    public long createdAt;
}

// Social Content
public class Post {
    public String postId;
    public String authorId;
    public String content;
    public List<String> imageUrls; // Multiple images support
    public long createdAt;
    public int likeCount;
    public int commentCount;
}

// Event Management
public class Event {
    public String eventId;
    public String title;
    public String description;
    public long startAt;
    public long endAt;
    public String imageId;
    public String createdBy;
    public String status;         // "scheduled" | "canceled" | "ended"
    public int maxParticipants;
}
```

---

## 🎯 Roadmap & Future Enhancements

### 🚨 High Priority (Immediate Implementation)
- [ ] **Firebase Cloud Messaging (FCM)**: Push notifications for messages, mentions, and events
- [ ] **Global Search**: Advanced search across posts, users, and groups
- [ ] **Content Moderation**: Reporting system and admin moderation tools

### ⚡ Medium Priority (Next Phase)
- [ ] **Advanced Offline Mode**: Queue actions and conflict resolution
- [ ] **Performance Monitoring**: Analytics and crash reporting
- [ ] **Enhanced Security**: Additional authentication methods

### 🌟 Future Features
- [ ] **Multi-language Support**: Internationalization (i18n)
- [ ] **Voice/Video Calling**: Real-time communication features
- [ ] **Advanced Analytics**: User behavior insights and recommendations

---

## 📊 Testing & Quality Assurance

### Current Test Coverage
- **Unit Tests**: Repository and ViewModel logic
- **Integration Tests**: Firebase operations
- **UI Tests**: Critical user flows

### Performance Benchmarks
- **Cold Start**: < 3 seconds
- **Chat Message Delivery**: < 100ms latency
- **Image Loading**: Optimized with Glide caching
- **Memory Usage**: Efficient ViewModel lifecycle management

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Review Checklist
- [ ] MVVM architecture followed
- [ ] Firebase security rules updated if needed
- [ ] Unit tests added for new functionality
- [ ] Code style standards maintained
- [ ] Documentation updated

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Acknowledgments

- **Firebase**: For providing excellent backend services
- **Android Jetpack**: For modern Android development components
- **Material Design**: For beautiful and consistent UI components
- **Open Source Community**: For libraries and tools that made this project possible

---

## 📞 Support

For questions, issues, or contributions:
- **GitHub Issues**: [Report bugs or request features](https://github.com/anh1124/NanaClu/issues)
- **Documentation**: Check the `DOC/` folder for detailed guides
- **Firebase Console**: Monitor app performance and usage

---

**Version**: 1.0.0  
**Last Updated**: December 2025  
**Maintained by**: [anh1124](https://github.com/anh1124)
