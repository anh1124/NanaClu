package com.example.nanaclu.data.repository;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Firestore + Storage message operations */
public class MessageRepository {
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private static final String CHATS = "chats";
    private static final String MESSAGES = "messages";

    public MessageRepository(FirebaseFirestore db, FirebaseStorage storage) {
        this.db = db;
        this.storage = storage;
        this.chatRepository = new ChatRepository(db);
        this.userRepository = new UserRepository(db);
    }

    public Task<String> sendText(String chatId, String authorId, String text, String chatType, String groupId) {
        if (chatId == null || authorId == null || text == null) return Tasks.forException(new IllegalArgumentException("null"));

        // For now, use a simple approach - get current user's name from FirebaseAuth
        String authorName = "Unknown";
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getDisplayName() != null) {
            authorName = currentUser.getDisplayName();
        }

        // Determine correct collection path based on chat type
        CollectionReference msgs;
        if ("group".equals(chatType) && groupId != null) {
            // Group chat: groups/{groupId}/chats/{chatId}/messages
            msgs = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection(MESSAGES);
        } else {
            // Private chat: chats/{chatId}/messages
            msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
        }

        DocumentReference msgRef = msgs.document();
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", msgRef.getId());
        data.put("authorId", authorId);
        data.put("authorName", authorName);
        data.put("type", "text");
        data.put("content", text);
        data.put("createdAt", FieldValue.serverTimestamp());
        return msgRef.set(data).continueWithTask(t -> {
            if (t.isSuccessful()) {
                // Update last message metadata
                chatRepository.updateLastMessageMeta(chatId, text, authorId, System.currentTimeMillis());
            }
            return Tasks.forResult(msgRef.getId());
        });
    }

    // Backward compatibility method
    public Task<String> sendText(String chatId, String authorId, String text) {
        return sendText(chatId, authorId, text, "private", null);
    }

    public Task<String> sendImage(String chatId, String authorId, Uri imageUri) {
        if (chatId == null || authorId == null || imageUri == null) return Tasks.forException(new IllegalArgumentException("null"));
        String path = "chat_images/" + chatId + "/" + System.currentTimeMillis() + "_" + Math.abs(imageUri.hashCode()) + ".jpg";
        StorageReference ref = storage.getReference().child(path);

        // Get display name once for authorName field
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        final String finalAuthorName = (currentUser != null && currentUser.getDisplayName() != null)
                ? currentUser.getDisplayName() : "Unknown";

        return ref.putFile(imageUri)
                .continueWithTask(ut -> {
                    if (!ut.isSuccessful()) return Tasks.forException(ut.getException());
                    return ref.getDownloadUrl();
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    String url = t.getResult() != null ? t.getResult().toString() : null;
                    if (url == null) return Tasks.forException(new IllegalStateException("No download url"));

                    // Use private chat path for backward compatibility
                    CollectionReference msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
                    DocumentReference msgRef = msgs.document();
                    Map<String, Object> data = new HashMap<>();
                    data.put("messageId", msgRef.getId());
                    data.put("authorId", authorId);
                    data.put("authorName", finalAuthorName);
                    data.put("type", "image");
                    data.put("content", url);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    return msgRef.set(data).continueWithTask(done -> {
                        if (done.isSuccessful()) {
                            // Update last message metadata for image
                            chatRepository.updateLastMessageMeta(chatId, "📷 Image", authorId, System.currentTimeMillis());
                        }
                        return Tasks.forResult(msgRef.getId());
                    });
                });
    }

    public Task<String> sendImage(String chatId, String authorId, Uri imageUri, String chatType, String groupId) {
        if (chatId == null || authorId == null || imageUri == null) return Tasks.forException(new IllegalArgumentException("null"));
        String path = "chat_images/" + chatId + "/" + System.currentTimeMillis() + "_" + Math.abs(imageUri.hashCode()) + ".jpg";
        StorageReference ref = storage.getReference().child(path);

        // Get display name once for authorName field
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        final String finalAuthorName = (currentUser != null && currentUser.getDisplayName() != null)
                ? currentUser.getDisplayName() : "Unknown";

        return ref.putFile(imageUri)
                .continueWithTask(ut -> {
                    if (!ut.isSuccessful()) return Tasks.forException(ut.getException());
                    return ref.getDownloadUrl();
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    String url = t.getResult() != null ? t.getResult().toString() : null;
                    if (url == null) return Tasks.forException(new IllegalStateException("No download url"));

                    // Determine correct collection path based on chat type
                    CollectionReference msgs;
                    if ("group".equals(chatType) && groupId != null) {
                        // Group chat: groups/{groupId}/chats/{chatId}/messages
                        msgs = db.collection("groups")
                                .document(groupId)
                                .collection("chats")
                                .document(chatId)
                                .collection(MESSAGES);
                    } else {
                        // Private chat: chats/{chatId}/messages
                        msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
                    }

                    DocumentReference msgRef = msgs.document();
                    Map<String, Object> data = new HashMap<>();
                    data.put("messageId", msgRef.getId());
                    data.put("authorId", authorId);
                    data.put("authorName", finalAuthorName);
                    data.put("type", "image");
                    data.put("content", url);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    return msgRef.set(data).continueWithTask(done -> {
                        if (done.isSuccessful()) {
                            // Update last message metadata for image
                            chatRepository.updateLastMessageMeta(chatId, "📷 Image", authorId, System.currentTimeMillis());
                        }
                        return Tasks.forResult(msgRef.getId());
                    });
                });
    }

    /**
     * Load messages by time descending with simple pagination.
     * If anchorTs is null, load latest; else load older than anchorTs.
     */
    public Task<List<Message>> listMessages(String chatId, @Nullable Long anchorTs, int limit) {
        if (chatId == null) return Tasks.forResult(new ArrayList<>());
        android.util.Log.d("MessageRepository", "listMessages: chatId=" + chatId + ", anchorTs=" + anchorTs + ", limit=" + limit);

        Query q = db.collection(CHATS).document(chatId).collection(MESSAGES)
                .orderBy("createdAt", Query.Direction.ASCENDING);
        if (anchorTs != null) {
            q = q.whereGreaterThan("createdAt", anchorTs);
        }
        if (limit > 0) q = q.limit(limit);

        return q.get().continueWith(t -> {
            List<Message> list = new ArrayList<>();
            if (t.isSuccessful() && t.getResult() != null) {
                android.util.Log.d("MessageRepository", "listMessages: found " + t.getResult().size() + " documents");
                for (DocumentSnapshot ds : t.getResult().getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) {
                        android.util.Log.d("MessageRepository", "Message: " + m.messageId + ", content: " + m.content);
                        list.add(m);
                    }
                }
            } else {
                android.util.Log.e("MessageRepository", "listMessages failed: " + (t.getException() != null ? t.getException().getMessage() : "unknown"));
            }
            android.util.Log.d("MessageRepository", "listMessages: returning " + list.size() + " messages");
            return list;
        });
    }

    public Task<List<Message>> listMessages(String chatId, @Nullable Long anchorTs, int limit, String chatType, String groupId) {
        if (chatId == null) return Tasks.forResult(new ArrayList<>());
        android.util.Log.d("MessageRepository", "listMessages: chatId=" + chatId + ", chatType=" + chatType + ", groupId=" + groupId);

        // Determine correct collection path based on chat type
        Query q;
        if ("group".equals(chatType) && groupId != null) {
            // Group chat: groups/{groupId}/chats/{chatId}/messages
            q = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        } else {
            // Private chat: chats/{chatId}/messages
            q = db.collection(CHATS).document(chatId).collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        }

        if (anchorTs != null) {
            q = q.whereGreaterThan("createdAt", anchorTs);
        }
        if (limit > 0) q = q.limit(limit);

        return q.get().continueWith(t -> {
            List<Message> list = new ArrayList<>();
            if (t.isSuccessful() && t.getResult() != null) {
                android.util.Log.d("MessageRepository", "listMessages: found " + t.getResult().size() + " documents");
                for (DocumentSnapshot ds : t.getResult().getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) {
                        android.util.Log.d("MessageRepository", "Message: " + m.messageId + ", content: " + m.content);
                        list.add(m);
                    }
                }
            } else {
                android.util.Log.e("MessageRepository", "listMessages failed: " + (t.getException() != null ? t.getException().getMessage() : "unknown"));
            }
            android.util.Log.d("MessageRepository", "listMessages: returning " + list.size() + " messages");
            return list;
        });
    }

    
    /**
     * Realtime listener for messages with proper collection path based on chat type.
     * Order: by createdAt ASC so UI can append.
     */
    public com.google.firebase.firestore.ListenerRegistration listenMessages(
            String chatId,
            String chatType,
            String groupId,
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener
    ) {
        android.util.Log.d("MessageRepoRT", "attach: chatId=" + chatId + ", chatType=" + chatType + ", groupId=" + groupId);
        if (chatId == null) return null;
        Query q;
        if ("group".equals(chatType) && groupId != null) {
            q = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        } else {
            q = db.collection(CHATS)
                    .document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.ASCENDING);
        }
        return q.addSnapshotListener((snap, err) -> {
            if (err != null) {
                android.util.Log.e("MessageRepoRT", "onEvent error: " + err.getMessage(), err);
            } else if (snap != null) {
                android.util.Log.d("MessageRepoRT", "onEvent: count=" + snap.size());
            }
            if (listener != null) listener.onEvent(snap, err);
        });
    }
 /**
     * Lắng nghe các tin nhắn mới nhất và bất kỳ tin nhắn nào mới hơn sau đó.
     * @param limit Số lượng tin nhắn gần nhất để tải ban đầu.
     */
    public com.google.firebase.firestore.ListenerRegistration listenForLatestMessages(
            String chatId, String chatType, String groupId, int limit,
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {

        if (chatId == null) return null;

        Query q;
        if ("group".equals(chatType) && groupId != null) {
            q = db.collection("groups").document(groupId)
                    .collection("chats").document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit);
        } else {
            q = db.collection(CHATS).document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit);
        }

        return q.addSnapshotListener((snap, err) -> {
            if (err != null) {
                android.util.Log.e("MessageRepoRT", "listenForLatestMessages error: " + err.getMessage(), err);
            } else if (snap != null) {
                android.util.Log.d("MessageRepoRT", "listenForLatestMessages: count=" + snap.size());
            }
            if (listener != null) listener.onEvent(snap, err);
        });
    }

    public Task<Void> editMessage(String chatId, String messageId, String newText) {
        if (chatId == null || messageId == null || newText == null) return Tasks.forException(new IllegalArgumentException("null"));
        Map<String, Object> data = new HashMap<>();
        data.put("content", newText);
        data.put("editedAt", System.currentTimeMillis());
        return db.collection(CHATS).document(chatId).collection(MESSAGES).document(messageId).update(data);
    }

    public Task<Void> softDeleteMessage(String chatId, String messageId) {
        if (chatId == null || messageId == null) return Tasks.forException(new IllegalArgumentException("null"));
        Map<String, Object> data = new HashMap<>();
        data.put("deletedAt", System.currentTimeMillis());
        data.put("content", "Tin nhắn đã được thu hồi");
        return db.collection(CHATS).document(chatId).collection(MESSAGES).document(messageId).update(data);
    }

    public Task<Void> softDeleteMessage(String chatId, String messageId, String chatType, String groupId) {
        if (chatId == null || messageId == null) return Tasks.forException(new IllegalArgumentException("null"));
        Map<String, Object> data = new HashMap<>();
        data.put("deletedAt", System.currentTimeMillis());
        data.put("content", "Tin nhắn đã được thu hồi");

        // Determine correct collection path based on chat type
        DocumentReference msgRef;
        if ("group".equals(chatType) && groupId != null) {
            // Group chat: groups/{groupId}/chats/{chatId}/messages/{messageId}
            msgRef = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection(MESSAGES)
                    .document(messageId);
        } else {
            // Private chat: chats/{chatId}/messages/{messageId}
            msgRef = db.collection(CHATS)
                    .document(chatId)
                    .collection(MESSAGES)
                    .document(messageId);
        }

        return msgRef.update(data);
    }
    // Load messages older than a timestamp (ASC order for UI; uses whereLessThan)
    public Task<List<Message>> listMessagesBefore(String chatId, @Nullable Long beforeTs, int limit) {
        if (chatId == null || beforeTs == null) return Tasks.forResult(new ArrayList<>());
        Query q = db.collection(CHATS).document(chatId)
                .collection(MESSAGES)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .whereLessThan("createdAt", beforeTs);
        if (limit > 0) q = q.limit(limit);
        return q.get().continueWith(t -> {
            List<Message> list = new ArrayList<>();
            if (t.isSuccessful() && t.getResult() != null) {
                for (DocumentSnapshot ds : t.getResult().getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) list.add(m);
                }
            }
            return list;
        });
    }

       // Overload for group/private based on chatType
       public Task<List<Message>> listMessagesBefore(String chatId, @Nullable Long beforeTs, int limit, String chatType, String groupId) {
        if (chatId == null || beforeTs == null) return Tasks.forResult(new ArrayList<>());
        Query q;
        if ("group".equals(chatType) && groupId != null) {
            q = db.collection("groups").document(groupId)
                    .collection("chats").document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.DESCENDING) // Lấy tin nhắn cũ hơn -> DESC
                    .whereLessThan("createdAt", beforeTs)
                    .limit(limit);
        } else {
            q = db.collection(CHATS).document(chatId)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.DESCENDING) // Lấy tin nhắn cũ hơn -> DESC
                    .whereLessThan("createdAt", beforeTs)
                    .limit(limit);
        }
        return q.get().continueWith(t -> {
            List<Message> list = new ArrayList<>();
            if (t.isSuccessful() && t.getResult() != null) {
                for (DocumentSnapshot ds : t.getResult().getDocuments()) {
                    Message m = ds.toObject(Message.class);
                    if (m != null) list.add(m);
                }
            }
            return list;
        });
    }


    // Generic method to send any message type
    public Task<String> sendMessage(String chatId, Message message, String chatType, String groupId) {
        if (chatId == null || message == null) return Tasks.forException(new IllegalArgumentException("null"));

        // Get author name if not set
        if (message.authorName == null || message.authorName.isEmpty()) {
            com.google.firebase.auth.FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            message.authorName = (currentUser != null && currentUser.getDisplayName() != null)
                ? currentUser.getDisplayName() : "Unknown";
        }

        CollectionReference msgs;
        if ("group".equals(chatType) && groupId != null) {
            // Group chat: groups/{groupId}/chats/{chatId}/messages
            msgs = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection(MESSAGES);
        } else {
            // Private chat: chats/{chatId}/messages
            msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
        }

        DocumentReference msgRef = msgs.document();
        message.messageId = msgRef.getId();

        // Set server timestamp if not set
        if (message.createdAt == 0) {
            message.createdAt = System.currentTimeMillis();
        }

        return msgRef.set(message).continueWithTask(task -> {
            if (task.isSuccessful()) {
                // Update last message metadata
                String lastMessageContent = getLastMessageContent(message);
                chatRepository.updateLastMessageMeta(chatId, lastMessageContent, message.authorId, message.createdAt);
                return Tasks.forResult(msgRef.getId());
            } else {
                throw task.getException();
            }
        });
    }

    private String getLastMessageContent(Message message) {
        switch (message.type) {
            case "text":
                return message.content;
            case "image":
                return "📷 Image";
            case "file":
                if (message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                    int count = message.fileAttachments.size();
                    return "📎 " + count + " file" + (count > 1 ? "s" : "");
                }
                return "📎 File";
            case "mixed":
                return "📎 Mixed content";
            default:
                return message.content != null ? message.content : "Message";
        }
    }

}

