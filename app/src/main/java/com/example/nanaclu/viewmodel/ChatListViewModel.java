package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Chat;
import com.example.nanaclu.data.model.User;
import com.google.android.gms.tasks.Task;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.FriendshipRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatListViewModel extends ViewModel {
    // UI thread items for adapter consumption
    public static class UiThreadItem {
        public String chatId;
        public String name;
        public String lastMessage;
        public long time;
        public String avatarUrl;
        public Chat chat;
        public String groupId; // for group chats
    }

    public static class StartChatResult {
        public String chatId;
        public String chatType; // "private" or "group"
        public String chatTitle;
        public String groupId; // nullable
    }

    private final ChatRepository chatRepo;
    private final FriendshipRepository friendshipRepo;
    private final UserRepository userRepo;

    private Chat lastItem; // pagination anchor

    private final MutableLiveData<List<UiThreadItem>> _uiThreads = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<UiThreadItem>> uiThreads = _uiThreads;

    private final MutableLiveData<Boolean> _hideChatResult = new MutableLiveData<>();
    public LiveData<Boolean> hideChatResult = _hideChatResult;

    private final MutableLiveData<StartChatResult> _startChatEvent = new MutableLiveData<>();
    public LiveData<StartChatResult> startChatEvent = _startChatEvent;

    private final MutableLiveData<List<User>> _friendUsers = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<User>> friendUsers = _friendUsers;

    private final FirebaseFirestore db;

    public ChatListViewModel() {
        this.db = FirebaseFirestore.getInstance();
        this.chatRepo = new ChatRepository(db);
        this.friendshipRepo = new FriendshipRepository(db);
        this.userRepo = new UserRepository(db);
    }

    public void refresh() {
        String uid = currentUid();
        if (uid == null) return;
        chatRepo.listUserChats(uid, 50, null).addOnSuccessListener(chats -> {
            lastItem = chats.isEmpty() ? null : chats.get(chats.size() - 1);
            buildUiItems(chats);
        });
    }

    public void paginate() {
        String uid = currentUid();
        if (uid == null || lastItem == null) return;
        chatRepo.listUserChats(uid, 50, lastItem).addOnSuccessListener(list -> {
            List<Chat> cur = new ArrayList<>();
            List<UiThreadItem> existing = _uiThreads.getValue();
            if (existing != null) {
                // keep existing chats to rebuild map after append
            }
            // Fetch again full up-to-now list is simpler for consistency
            refresh();
            lastItem = list.isEmpty() ? lastItem : list.get(list.size() - 1);
        });
    }

    public void hideChat(String chatId) {
        String uid = currentUid();
        if (uid == null || chatId == null) return;
        chatRepo.hideChatForUser(chatId, uid)
                .addOnSuccessListener(aVoid -> _hideChatResult.postValue(true))
                .addOnFailureListener(e -> _hideChatResult.postValue(false));
    }

    public void startPrivateChat(String otherUserId) {
        String uid = currentUid();
        if (uid == null || otherUserId == null || otherUserId.equals(uid)) return;
        userRepo.getUserById(otherUserId, new UserRepository.UserCallback() {
            @Override public void onSuccess(User user) {
                String title = user != null && user.displayName != null ? user.displayName : "Private Chat";
                chatRepo.getOrCreatePrivateChat(uid, otherUserId)
                        .addOnSuccessListener(chatId -> {
                            java.util.List<String> self = new java.util.ArrayList<>(); self.add(uid);
                            chatRepo.unarchiveForUsers(chatId, self);
                            StartChatResult r = new StartChatResult();
                            r.chatId = chatId;
                            r.chatType = "private";
                            r.chatTitle = title;
                            _startChatEvent.postValue(r);
                        });
            }
            @Override public void onError(Exception e) {
                // no-op
            }
        });
    }

    public void loadFriends() {
        String uid = currentUid();
        if (uid == null) { _friendUsers.postValue(new ArrayList<>()); return; }
        friendshipRepo.listFriends(uid, 100)
                .addOnSuccessListener(friendIds -> {
                    if (friendIds == null || friendIds.isEmpty()) {
                        _friendUsers.postValue(new ArrayList<>());
                        return;
                    }
                    List<User> users = new ArrayList<>();
                    AtomicInteger loaded = new AtomicInteger(0);
                    int total = friendIds.size();
                    for (String fid : friendIds) {
                        userRepo.getUserById(fid, new UserRepository.UserCallback() {
                            @Override public void onSuccess(User u) {
                                if (u != null) users.add(u);
                                if (loaded.incrementAndGet() == total) _friendUsers.postValue(users);
                            }
                            @Override public void onError(Exception e) {
                                if (loaded.incrementAndGet() == total) _friendUsers.postValue(users);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> _friendUsers.postValue(new ArrayList<>()));
    }

    private void buildUiItems(List<Chat> chats) {
        List<UiThreadItem> out = new ArrayList<>();
        if (chats == null || chats.isEmpty()) { _uiThreads.postValue(out); return; }

        String uid = currentUid();
        AtomicInteger resolved = new AtomicInteger(0);
        int total = chats.size();

        for (Chat chat : chats) {
            UiThreadItem item = new UiThreadItem();
            item.chatId = chat.chatId;
            item.lastMessage = chat.lastMessage != null ? chat.lastMessage : "No messages yet";
            item.time = chat.lastMessageAt != null ? chat.lastMessageAt : chat.createdAt;
            item.chat = chat;

            if ("group".equals(chat.type)) {
                item.groupId = chat.groupId;
                if (chat.groupId != null) {
                    db.collection("groups").document(chat.groupId)
                            .get()
                            .addOnSuccessListener(ds -> {
                                String name = ds.getString("name");
                                String avatar = ds.getString("avatarImageId");
                                item.name = name != null ? name : "Group Chat";
                                item.avatarUrl = avatar;
                                out.add(item);
                                if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                            })
                            .addOnFailureListener(e -> {
                                item.name = "Group Chat";
                                out.add(item);
                                if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                            });
                } else {
                    item.name = "Group Chat";
                    out.add(item);
                    if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                }
            } else {
                if (uid == null || chat.pairKey == null) {
                    if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                    continue;
                }
                String[] ids = chat.pairKey.split("_");
                String other = null;
                for (String id : ids) { if (!id.equals(uid)) { other = id; break; } }
                if (other == null) {
                    if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                    continue;
                }
                final UiThreadItem ref = item;
                userRepo.getUserById(other, new UserRepository.UserCallback() {
                    @Override public void onSuccess(User user) {
                        if (user != null) {
                            ref.name = user.displayName != null ? user.displayName : "";
                            ref.avatarUrl = user.photoUrl;
                            out.add(ref);
                        }
                        if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                    }
                    @Override public void onError(Exception e) {
                        if (resolved.incrementAndGet() == total) _uiThreads.postValue(out);
                    }
                });
            }
        }
    }

    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }
}

