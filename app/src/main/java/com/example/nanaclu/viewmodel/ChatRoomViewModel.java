package com.example.nanaclu.viewmodel;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.FileAttachment;
import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.FileRepository;
import com.example.nanaclu.data.repository.MessageRepository;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomViewModel extends ViewModel {
    private final ChatRepository chatRepo;
    private final MessageRepository msgRepo;
    private final NoticeRepository noticeRepo;
    private final UserRepository userRepo;
    private FileRepository fileRepo;
   
    private Long oldestMessageTimestamp = null; // Dùng để tải tin nhắn cũ hơn
    private boolean isFetchingOlderMessages = false;

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

    // File handling LiveData
    private final MutableLiveData<List<FileAttachment>> _chatFiles = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<FileAttachment>> chatFiles = _chatFiles;

    private final MutableLiveData<Integer> _uploadProgress = new MutableLiveData<>(0);
    public LiveData<Integer> uploadProgress = _uploadProgress;

    private final MutableLiveData<Boolean> _uploading = new MutableLiveData<>(false);
    public LiveData<Boolean> uploading = _uploading;

    private final MutableLiveData<String> _fileError = new MutableLiveData<>(null);
    public LiveData<String> fileError = _fileError;

    // Realtime registration
    private com.google.firebase.firestore.ListenerRegistration messagesReg;

    public ChatRoomViewModel() {
        this.chatRepo = new ChatRepository(FirebaseFirestore.getInstance());
        this.msgRepo = new MessageRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance());
        this.noticeRepo = new NoticeRepository(FirebaseFirestore.getInstance());
        this.userRepo = new UserRepository(FirebaseFirestore.getInstance());
    }

    public void initFileRepository(Context context) {
        this.fileRepo = new FileRepository(context);
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
                    if (m != null) {
                        list.add(m);
                        // =================== LOGGING ===================
                        String logMessage = " | Type: " + m.type +
                                            " | Author: " + (m.authorName != null ? m.authorName : "N/A") +
                                            " | CreatedAt: " + m.createdAt;
                        android.util.Log.d("ChatRoomVM_DATA_RAW", logMessage);
                        if ("text".equals(m.type) || "mixed".equals(m.type)) {
                            android.util.Log.d("ChatRoomVM_DATA_RAW", "  -> Content: " + m.content);
                        }
                        if (m.fileAttachments != null && !m.fileAttachments.isEmpty()) {
                            android.util.Log.d("ChatRoomVM_DATA_RAW", "  -> Files: " + m.fileAttachments.size());
                            for(FileAttachment fa : m.fileAttachments) {
                                android.util.Log.d("ChatRoomVM_DATA_RAW", "    -> File Name: " + fa.fileName + " | Size: " + fa.fileSize);
                            }
                        }
                        // =======================================================
                    }
                }
            }
            // Log toàn bộ list trước khi sort
            android.util.Log.d("ChatRoomVM_LIST_RAW", "Trước khi sort: " + list.size() + " messages");
            for (Message m : list) {
                android.util.Log.d("ChatRoomVM_LIST_RAW", "ID: " + m.messageId + ", createdAt: " + m.createdAt + ", type: " + m.type);
            }
            // Sắp xếp tin nhắn theo thời gian tăng dần (tin mới nhất ở cuối)
            java.util.Collections.sort(list, (m1, m2) -> {
                if (m1 == null || m2 == null) return 0;
                long t1 = m1.createdAt;
                long t2 = m2.createdAt;
                return Long.compare(t1, t2);
            });
            // Log lại list sau khi sort
            if (list == null) {
                android.util.Log.e("ChatRoomVM_LIST_SORTED", "List null sau khi sort!");
            } else {
                android.util.Log.d("ChatRoomVM_LIST_SORTED", "Sau khi sort: " + list.size() + " messages");
                for (Message m : list) {
                    String contentLog = m.content != null ? m.content : "<null>";
                    android.util.Log.d("ChatRoomVM_LIST_SORTED", "ID: " + m.messageId + ", createdAt: " + m.createdAt + ", type: " + m.type + ", content: " + contentLog);
                }
            }
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
            // Sắp xếp tin nhắn theo thời gian tăng dần trước khi cập nhật LiveData
java.util.Collections.sort(cur, (m1, m2) -> {
    if (m1 == null || m2 == null) return 0;
    long t1 = m1.createdAt;
    long t2 = m2.createdAt;
    return Long.compare(t1, t2);
});
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
                    // Sắp xếp tin nhắn theo thời gian tăng dần trước khi cập nhật LiveData
java.util.Collections.sort(updated, (m1, m2) -> {
    if (m1 == null || m2 == null) return 0;
    long t1 = m1.createdAt;
    long t2 = m2.createdAt;
    return Long.compare(t1, t2);
});
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
                    // Create notice for other chat members
                    createMessageNotice(text);
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
                    // Create notice for other chat members
                    createMessageNotice("Đã gửi ảnh");
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
        msgRepo.softDeleteMessage(chatId, messageId, chatType, groupId)
                .addOnSuccessListener(aVoid -> {
                    // Don't need to manually refresh - realtime listener will handle it
                    // Just log success
                    android.util.Log.d("ChatRoomVM", "Message deleted successfully, waiting for realtime update");
                })
                .addOnFailureListener(e -> _error.postValue(e.getMessage()));
    }

    private void refreshMessages() {
        if (chatId == null) return;
        // Reset pagination and load fresh messages once; realtime listener will keep updating
        anchorTs = null;
        msgRepo.listMessages(chatId, null, 30, chatType, groupId).addOnSuccessListener(list -> {
            // Sắp xếp tin nhắn theo thời gian tăng dần trước khi cập nhật LiveData
            java.util.Collections.sort(list, (m1, m2) -> {
                if (m1 == null || m2 == null) return 0;
                long t1 = m1.createdAt;
                long t2 = m2.createdAt;
                return Long.compare(t1, t2);
            });
            _messages.postValue(list);
            if (!list.isEmpty()) {
                Message last = list.get(list.size() - 1);
                anchorTs = last.createdAt;
            }
        });
    }

    // File handling methods
    public void uploadFiles(List<Uri> fileUris) {
        if (chatId == null || fileUris == null || fileUris.isEmpty() || fileRepo == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        _uploading.postValue(true);
        _fileError.postValue(null);

        // Validate files first
        fileRepo.validateFiles(fileUris, new FileRepository.FileValidationCallback() {
            @Override
            public void onValid(List<FileAttachment> validFiles) {
                // Upload files
                fileRepo.uploadFiles(fileUris, validFiles, chatId, uid, new FileRepository.ProgressCallback() {
                    @Override
                    public void onProgress(int progress) {
                        _uploadProgress.postValue(progress);
                    }

                    @Override
                    public void onSuccess() {
                        _uploading.postValue(false);
                        _uploadProgress.postValue(100);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        _uploading.postValue(false);
                        _fileError.postValue("Lỗi upload: " + e.getMessage());
                    }
                }).addOnSuccessListener(uploadedFiles -> {
                    // Send message with file attachments
                    sendFileMessage(uploadedFiles);
                }).addOnFailureListener(e -> {
                    _uploading.postValue(false);
                    _fileError.postValue("Lỗi upload: " + e.getMessage());
                });
            }

            @Override
            public void onInvalid(List<String> errors) {
                _uploading.postValue(false);
                _fileError.postValue(String.join("\n", errors));
            }
        });
    }

    private void sendFileMessage(List<FileAttachment> fileAttachments) {
        if (chatId == null || fileAttachments == null || fileAttachments.isEmpty()) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        // Create message with file attachments
        Message message = new Message();
        message.authorId = uid;
        message.type = "file";
        message.content = fileAttachments.size() + " file(s)";
        message.fileAttachments = fileAttachments;
        message.createdAt = System.currentTimeMillis();

        // Send message using existing repository
        msgRepo.sendMessage(chatId, message, chatType, groupId)
                .addOnSuccessListener(messageId -> {
                    // Message sent successfully
                    // Create notice for other chat members
                    createMessageNotice("Đã gửi file");
                })
                .addOnFailureListener(e -> {
                    _fileError.postValue("Lỗi gửi tin nhắn: " + e.getMessage());
                });
    }

    public void downloadFile(FileAttachment attachment) {
        if (fileRepo == null) return;

        attachment.isDownloading = true;
        fileRepo.downloadFile(attachment, new FileRepository.ProgressCallback() {
            @Override
            public void onProgress(int progress) {
                attachment.downloadProgress = progress;
            }

            @Override
            public void onSuccess() {
                attachment.isDownloading = false;
                // Notify UI update if needed
            }

            @Override
            public void onFailure(Exception e) {
                attachment.isDownloading = false;
                _fileError.postValue("Lỗi tải file: " + e.getMessage());
            }
        });
    }

    public void loadChatFiles() {
        if (chatId == null) return;

        // Get all file messages from current chat
        List<FileAttachment> allFiles = new ArrayList<>();
        List<Message> currentMessages = _messages.getValue();
        if (currentMessages != null) {
            for (Message message : currentMessages) {
                if (message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                    allFiles.addAll(message.fileAttachments);
                }
            }
        }
        _chatFiles.postValue(allFiles);
    }

    private void createMessageNotice(String previewText) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUid == null) return;

        // Get current user name
        userRepo.getUserById(currentUid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                String actorName = user != null ? user.displayName : "Người dùng";
                
                // Get chat members and create notices
                getChatMembers(chatId, new ChatMembersCallback() {
                    @Override
                    public void onSuccess(List<String> memberIds) {
                        // Remove current user from recipients
                        List<String> targetUids = new ArrayList<>();
                        for (String memberId : memberIds) {
                            if (!memberId.equals(currentUid)) {
                                targetUids.add(memberId);
                            }
                        }
                        
                        if (!targetUids.isEmpty()) {
                            noticeRepo.createMessageNotice(chatId, chatType, groupId, currentUid, actorName, targetUids, previewText);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        // Fallback: try to create notice anyway
                        android.util.Log.e("ChatRoomViewModel", "Error getting chat members", e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                // Fallback with default name
                String actorName = "Người dùng";
                getChatMembers(chatId, new ChatMembersCallback() {
                    @Override
                    public void onSuccess(List<String> memberIds) {
                        List<String> targetUids = new ArrayList<>();
                        for (String memberId : memberIds) {
                            if (!memberId.equals(currentUid)) {
                                targetUids.add(memberId);
                            }
                        }
                        
                        if (!targetUids.isEmpty()) {
                            noticeRepo.createMessageNotice(chatId, chatType, groupId, currentUid, actorName, targetUids, previewText);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("ChatRoomViewModel", "Error getting chat members", e);
                    }
                });
            }
        });
    }

    private void getChatMembers(String chatId, ChatMembersCallback callback) {
        chatRepo.getChatMembers(chatId)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    private interface ChatMembersCallback {
        void onSuccess(List<String> memberIds);
        void onError(Exception e);
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

