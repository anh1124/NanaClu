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
    private Long anchorTs; // for pagination older messages

    private final MutableLiveData<List<Message>> _messages = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<Message>> messages = _messages;

    private final MutableLiveData<Boolean> _sending = new MutableLiveData<>(false);
    public LiveData<Boolean> sending = _sending;

    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    public LiveData<String> error = _error;

    public ChatRoomViewModel() {
        this.chatRepo = new ChatRepository(FirebaseFirestore.getInstance());
        this.msgRepo = new MessageRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance());
    }

    public void init(String chatId) {
        this.chatId = chatId;
        this.anchorTs = null;
        _messages.postValue(new ArrayList<>());
        loadMore();
    }

    public void loadMore() {
        if (chatId == null) return;
        msgRepo.listMessages(chatId, anchorTs, 30).addOnSuccessListener(list -> {
            List<Message> cur = new ArrayList<>(_messages.getValue() != null ? _messages.getValue() : new ArrayList<>());
            cur.addAll(list);
            _messages.postValue(cur);
            if (!list.isEmpty()) {
                Message last = list.get(list.size() - 1);
                anchorTs = last.createdAt; // assuming millis populated from server
            }
        }).addOnFailureListener(e -> _error.postValue(e.getMessage()));
    }

    public void sendText(String text) {
        if (chatId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        _sending.postValue(true);
        msgRepo.sendText(chatId, uid, text)
                .addOnSuccessListener(id -> _sending.postValue(false))
                .addOnFailureListener(e -> { _sending.postValue(false); _error.postValue(e.getMessage()); });
    }

    public void sendImage(Uri uri) {
        if (chatId == null || uri == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        _sending.postValue(true);
        msgRepo.sendImage(chatId, uid, uri)
                .addOnSuccessListener(id -> _sending.postValue(false))
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
                .addOnFailureListener(e -> _error.postValue(e.getMessage()));
    }
}

