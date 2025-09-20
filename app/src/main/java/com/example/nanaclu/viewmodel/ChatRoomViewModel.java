package com.example.nanaclu.viewmodel;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.MessageRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomViewModel extends ViewModel {
    private final ChatRepository chatRepo;
    private final MessageRepository msgRepo;

    private String chatId;
    private String chatType;
    private String groupId;
    private Long anchorTs; // for pagination older messages

    private final MutableLiveData<List<Message>> _messages = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<Message>> messages = _messages;

    private final MutableLiveData<Boolean> _sending = new MutableLiveData<>(false);
    public LiveData<Boolean> sending = _sending;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public LiveData<Boolean> loading = _loading;

    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    public LiveData<String> error = _error;

    // Realtime registration
    private com.google.firebase.firestore.ListenerRegistration messagesReg;

    public ChatRoomViewModel() {
        this.chatRepo = new ChatRepository(FirebaseFirestore.getInstance());
        this.msgRepo = new MessageRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance());
    }

    public void init(String chatId) {
        init(chatId, "private", null);
    }

    public void init(String chatId, String chatType, String groupId) {
        this.chatId = chatId;
        this.chatType = chatType;
        this.groupId = groupId;
        this.anchorTs = null;
        // detach previous listener if any
        if (messagesReg != null) {
            android.util.Log.d("ChatRoomVM", "Detaching previous messages listener");
            messagesReg.remove();
            messagesReg = null;
        }
        _messages.postValue(new ArrayList<>());
        android.util.Log.d("ChatRoomVM", "init: chatId=" + chatId + ", chatType=" + chatType + ", groupId=" + groupId);
        // Attach realtime listener
        messagesReg = msgRepo.listenMessages(chatId, chatType, groupId, (snap, err) -> {
            if (err != null) {
                _error.postValue(err.getMessage());
                return;
            }
            List<Message> list = new ArrayList<>();
            if (snap != null) {
                for (com.google.firebase.firestore.DocumentSnapshot ds : snap.getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) list.add(m);
                }
            }
            android.util.Log.d("ChatRoomVM", "onMessages: size=" + list.size());
            _messages.postValue(list);
            if (!list.isEmpty()) {
                Message last = list.get(list.size() - 1);
                anchorTs = last.createdAt;
            }
        });

    }

    public void loadMore() {
        if (chatId == null) {
            android.util.Log.d("ChatRoomViewModel", "loadMore: chatId is null");
            return;
        }
        android.util.Log.d("ChatRoomViewModel", "loadMore: chatId=" + chatId + ", chatType=" + chatType + ", groupId=" + groupId + ", anchorTs=" + anchorTs);
        msgRepo.listMessages(chatId, anchorTs, 30, chatType, groupId).addOnSuccessListener(list -> {
            android.util.Log.d("ChatRoomViewModel", "loadMore: loaded " + list.size() + " messages");
            List<Message> cur = new ArrayList<>(_messages.getValue() != null ? _messages.getValue() : new ArrayList<>());
            cur.addAll(list);
            _messages.postValue(cur);
            if (!list.isEmpty()) {
                Message last = list.get(list.size() - 1);
                anchorTs = last.createdAt; // assuming millis populated from server
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("ChatRoomViewModel", "loadMore failed: " + e.getMessage(), e);
            _error.postValue(e.getMessage());
        });
    }

    /** Pull-to-refresh: load older messages before the first currently loaded item */
    public void loadOlderMessages() {
        if (chatId == null) return;
        List<Message> cur = _messages.getValue();
        if (cur == null || cur.isEmpty()) {
            // Nothing loaded yet, just load initial
            loadMore();
            return;
        }
        // Find earliest timestamp currently in list
        Long earliest = null;
        for (Message m : cur) {
            if (m == null || m.createdAt <= 0L) continue;
            if (earliest == null || m.createdAt < earliest) earliest = m.createdAt;
        }
        if (earliest == null) { loadMore(); return; }
        _loading.postValue(true);
        msgRepo.listMessagesBefore(chatId, earliest, 20, chatType, groupId)
                .addOnSuccessListener(older -> {
                    // Prepend to current list (keep ASC order)
                    List<Message> updated = new ArrayList<>(older);
                    updated.addAll(cur);
                    _messages.postValue(updated);
                    _loading.postValue(false);
                })
                .addOnFailureListener(ex -> {
                    _loading.postValue(false);
                    _error.postValue(ex.getMessage());
                });
    }

    public void sendText(String text) {
        if (chatId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        _sending.postValue(true);
        msgRepo.sendText(chatId, uid, text, chatType, groupId)
                .addOnSuccessListener(id -> {
                    _sending.postValue(false);
                    // Rely on realtime listener to update UI
                })
                .addOnFailureListener(e -> { _sending.postValue(false); _error.postValue(e.getMessage()); });
    }

    public void sendImage(Uri uri) {
        if (chatId == null || uri == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        _sending.postValue(true);
        msgRepo.sendImage(chatId, uid, uri, chatType, groupId)
                .addOnSuccessListener(id -> {
                    _sending.postValue(false);
                    // Rely on realtime listener to update UI
                })
                .addOnFailureListener(e -> { _sending.postValue(false); _error.postValue(e.getMessage()); });
    }

    public void markRead() {
        if (chatId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        long now = System.currentTimeMillis();
        chatRepo.updateLastRead(chatId, uid, now);
    }

    public void editMessage(String messageId, String newText) {
        if (chatId == null) return;
        msgRepo.editMessage(chatId, messageId, newText)
                .addOnFailureListener(e -> _error.postValue(e.getMessage()));
    }

    public void recallMessage(String messageId) {
        if (chatId == null) return;
        msgRepo.softDeleteMessage(chatId, messageId)
                .addOnSuccessListener(aVoid -> refreshMessages())
                .addOnFailureListener(e -> _error.postValue(e.getMessage()));
    }

    private void refreshMessages() {
        if (chatId == null) return;
        // Reset pagination and load fresh messages once; realtime listener will keep updating
        anchorTs = null;
        msgRepo.listMessages(chatId, null, 30, chatType, groupId).addOnSuccessListener(list -> {
            _messages.postValue(list);
            if (!list.isEmpty()) {
                Message last = list.get(list.size() - 1);
                anchorTs = last.createdAt;
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (messagesReg != null) {
            android.util.Log.d("ChatRoomVM", "onCleared: removing messages listener");
            messagesReg.remove();
            messagesReg = null;
        }
    }
}

