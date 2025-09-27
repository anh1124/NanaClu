# Kiến trúc MVVM

## 1. Sơ đồ kiến trúc
```
┌─────────────────────────────────────────────────────────────┐
│                    VIEW LAYER                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Activities    │  │   Fragments     │  │   Adapters   │ │
│  │ - LoginActivity │  │ - FeedFragment  │  │ - PostAdapter│ │
│  │ - HomeActivity  │  │ - ChatFragment  │  │ - ChatAdapter│ │
│  │ - ChatRoom...   │  │ - GroupsFragment│  │ - EventAdapter│ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              ↕️ (Observer Pattern)
                         LiveData Observation
┌─────────────────────────────────────────────────────────────┐
│                   VIEWMODEL LAYER                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  AuthViewModel  │  │ ChatListViewModel│  │GroupViewModel│ │
│  │ - user: LiveData│  │ - threads: LD   │  │ - groups: LD │ │
│  │ - loading: LD   │  │ - refresh()     │  │ - createGroup│ │
│  │ - loginWithEmail│  │ - loadMore()    │  │ - joinGroup  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              ↕️ (Repository Pattern)
                         Business Logic Calls
┌─────────────────────────────────────────────────────────────┐
│                  REPOSITORY LAYER                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  AuthRepository │  │  ChatRepository │  │GroupRepository│ │
│  │ - getCurrentUser│  │ - listUserChats │  │ - createGroup│ │
│  │ - loginWithEmail│  │ - sendMessage   │  │ - getGroups  │ │
│  │ - registerWith..│  │ - listenMessages│  │ - joinGroup  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              ↕️ (Firebase SDK)
                         Data Access Layer
┌─────────────────────────────────────────────────────────────┐
│                    MODEL/DATA LAYER                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Firebase Auth   │  │ Firebase Store  │  │ POJO Models  │ │
│  │ - User sessions │  │ - Collections   │  │ - User.java  │ │
│  │ - Authentication│  │ - Documents     │  │ - Group.java │ │
│  │ - Providers     │  │ - Realtime      │  │ - Post.java  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 2. Phân tích từng tầng

### 2.1 View Layer
**Chức năng**: Hiển thị UI và xử lý user interactions

#### Activities Example:
```java
// LoginActivity.java - Quan sát ViewModel thông qua LiveData
public class LoginActivity extends AppCompatActivity {
    private AuthViewModel viewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Observer pattern - View quan sát ViewModel
        viewModel.user.observe(this, user -> {
            if (user != null) {
                // Navigate to main app
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        });
        
        viewModel.loading.observe(this, loading -> {
            btnLogin.setEnabled(!loading);
        });
        
        viewModel.error.observe(this, error -> {
            if (error != null) tvStatus.setText(error);
        });
        
        // User interaction triggers ViewModel method
        btnLogin.setOnClickListener(v -> {
            viewModel.loginWithEmail(email, password);
        });
    }
}
```

#### Fragments Example:
```java
// ChatFragment.java - Fragment với ViewModel
public class ChatFragment extends Fragment {
    private ChatListViewModel viewModel;
    
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ChatListViewModel.class);
        
        // Observe data changes
        viewModel.threads.observe(getViewLifecycleOwner(), chats -> {
            adapter.updateChats(chats);
        });
        
        // Trigger data loading
        viewModel.refresh();
    }
}
```

### 2.2 ViewModel Layer
**Chức năng**: Xử lý business logic, quản lý UI state, cung cấp data cho View

#### ViewModel Implementation:
```java
// AuthViewModel.java - Quản lý authentication state
public class AuthViewModel extends ViewModel {
    private final AuthRepository repository = new AuthRepository();
    
    // Private MutableLiveData for internal updates
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>(null);
    
    // Public LiveData for View observation
    public LiveData<Boolean> loading = _loading;
    public LiveData<String> error = _error;
    public LiveData<FirebaseUser> user = _user;
    
    // Business logic methods
    public void loginWithEmail(String email, String password) {
        _loading.setValue(true);
        _error.setValue(null);
        
        repository.loginWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    _loading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(repository.getCurrentUser());
                    } else {
                        _error.setValue("Login failed");
                    }
                });
    }
}
```

#### Chat ViewModel với Realtime:
```java
// ChatRoomViewModel.java - Realtime messaging
public class ChatRoomViewModel extends ViewModel {
    private final MessageRepository msgRepo;
    private ListenerRegistration messagesReg;
    
    private final MutableLiveData<List<Message>> _messages = new MutableLiveData<>();
    public LiveData<List<Message>> messages = _messages;
    
    public void init(String chatId) {
        // Setup realtime listener
        messagesReg = msgRepo.listenMessages(chatId, (snap, err) -> {
            if (err != null) {
                _error.postValue(err.getMessage());
                return;
            }
            List<Message> list = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot ds : snap.getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) list.add(m);
                }
            }
            _messages.postValue(list);
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        if (messagesReg != null) {
            messagesReg.remove();
        }
    }
}
```

### 2.3 Repository Layer
**Chức năng**: Trừu tượng hóa data access, quản lý data sources

#### Repository Pattern Implementation:
```java
// ChatRepository.java - Data access abstraction
public class ChatRepository {
    private final FirebaseFirestore db;
    
    public ChatRepository(FirebaseFirestore db) {
        this.db = db;
    }
    
    // Async data operations
    public Task<List<Chat>> listUserChats(String userId, int limit, Chat lastItem) {
        Query query = db.collection("chats")
                .whereArrayContains("memberIds", userId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .limit(limit);
                
        if (lastItem != null) {
            query = query.startAfter(lastItem.lastMessageAt);
        }
        
        return query.get().continueWith(task -> {
            List<Chat> chats = new ArrayList<>();
            if (task.isSuccessful()) {
                for (DocumentSnapshot doc : task.getResult()) {
                    Chat chat = doc.toObject(Chat.class);
                    if (chat != null) chats.add(chat);
                }
            }
            return chats;
        });
    }
    
    // Realtime listeners
    public ListenerRegistration listenMessages(String chatId, 
            EventListener<QuerySnapshot> listener) {
        return db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }
}
```

#### Repository với Firebase Storage:
```java
// PostRepository.java - Handling file uploads
public class PostRepository {
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    
    public void uploadMultipleImages(List<byte[]> imageDataList, 
            OnSuccessListener<List<String>> onSuccess) {
        List<Task<String>> uploadTasks = new ArrayList<>();
        
        for (byte[] imageData : imageDataList) {
            String fileName = "post_" + System.currentTimeMillis() + ".jpg";
            StorageReference ref = storage.getReference()
                    .child("images/posts/" + fileName);
                    
            Task<String> uploadTask = ref.putBytes(imageData)
                    .continueWithTask(task -> ref.getDownloadUrl())
                    .continueWith(task -> task.getResult().toString());
                    
            uploadTasks.add(uploadTask);
        }
        
        Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(onSuccess);
    }
}
```

### 2.4 Model Layer
**Chức năng**: Định nghĩa data structures và business entities

#### POJO Models:
```java
// User.java - Firebase Firestore POJO
public class User {
    public String userId;
    public long createdAt;
    public String email;
    public String displayName;
    public String avatarImageId;
    public long lastLoginAt;
    public String status; // "online" | "offline"
    
    public User() {} // Required for Firestore
    
    public User(String userId, String email, String displayName) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = System.currentTimeMillis();
        this.lastLoginAt = System.currentTimeMillis();
        this.status = "online";
    }
}
```

```java
// Message.java - Chat message model
public class Message {
    public String messageId;
    public String chatId;
    public String senderId;
    public String content;
    public String type; // "text" | "image" | "file"
    public long createdAt;
    public long editedAt;
    public boolean isDeleted;
    public List<String> imageUrls;
    
    public Message() {}
}
```

## 3. Luồng dữ liệu (Data Flow)

### 3.1 User Action Flow:
```
1. User clicks login button (View)
2. View calls viewModel.loginWithEmail() (View → ViewModel)
3. ViewModel calls repository.loginWithEmail() (ViewModel → Repository)
4. Repository calls Firebase Auth API (Repository → Firebase)
5. Firebase returns result (Firebase → Repository)
6. Repository returns Task to ViewModel (Repository → ViewModel)
7. ViewModel updates LiveData (ViewModel internal)
8. View observes LiveData change (ViewModel → View)
9. View updates UI (View internal)
```

### 3.2 Realtime Data Flow:
```
1. ViewModel sets up listener via Repository
2. Repository creates Firestore snapshot listener
3. Firebase sends data changes to listener
4. Repository forwards data to ViewModel callback
5. ViewModel updates LiveData
6. View automatically updates via Observer
```

## 4. Key Benefits của MVVM Implementation

### 4.1 Separation of Concerns:
- **View**: Chỉ quan tâm UI rendering và user interactions
- **ViewModel**: Business logic và state management
- **Repository**: Data access và caching logic
- **Model**: Pure data structures

### 4.2 Testability:
- ViewModel có thể test độc lập với View
- Repository có thể mock cho unit testing
- LiveData giúp test async operations

### 4.3 Lifecycle Awareness:
- LiveData tự động handle lifecycle
- ViewModel survive configuration changes
- Automatic cleanup khi Fragment/Activity destroyed

### 4.4 Reactive Programming:
- Observer pattern với LiveData
- Automatic UI updates khi data changes
- Realtime features với Firestore listeners