package com.example.nanaclu.data.repository;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.nanaclu.data.model.Message;
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
    private static final String CHATS = "chats";
    private static final String MESSAGES = "messages";

    public MessageRepository(FirebaseFirestore db, FirebaseStorage storage) {
        this.db = db;
        this.storage = storage;
    }

    public Task<String> sendText(String chatId, String authorId, String text) {
        if (chatId == null || authorId == null || text == null) return Tasks.forException(new IllegalArgumentException("null"));
        CollectionReference msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
        DocumentReference msgRef = msgs.document();
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", msgRef.getId());
        data.put("authorId", authorId);
        data.put("type", "text");
        data.put("content", text);
        data.put("createdAt", FieldValue.serverTimestamp());
        return msgRef.set(data).continueWithTask(t -> Tasks.forResult(msgRef.getId()));
    }

    public Task<String> sendImage(String chatId, String authorId, Uri imageUri) {
        if (chatId == null || authorId == null || imageUri == null) return Tasks.forException(new IllegalArgumentException("null"));
        String path = "chat_images/" + chatId + "/" + System.currentTimeMillis() + "_" + Math.abs(imageUri.hashCode()) + ".jpg";
        StorageReference ref = storage.getReference().child(path);
        return ref.putFile(imageUri)
                .continueWithTask(ut -> {
                    if (!ut.isSuccessful()) return Tasks.forException(ut.getException());
                    return ref.getDownloadUrl();
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    String url = t.getResult() != null ? t.getResult().toString() : null;
                    if (url == null) return Tasks.forException(new IllegalStateException("No download url"));
                    CollectionReference msgs = db.collection(CHATS).document(chatId).collection(MESSAGES);
                    DocumentReference msgRef = msgs.document();
                    Map<String, Object> data = new HashMap<>();
                    data.put("messageId", msgRef.getId());
                    data.put("authorId", authorId);
                    data.put("type", "image");
                    data.put("content", url);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    return msgRef.set(data).continueWithTask(done -> Tasks.forResult(msgRef.getId()));
                });
    }

    /**
     * Load messages by time descending with simple pagination.
     * If anchorTs is null, load latest; else load older than anchorTs.
     */
    public Task<List<Message>> listMessages(String chatId, @Nullable Long anchorTs, int limit) {
        if (chatId == null) return Tasks.forResult(new ArrayList<>());
        Query q = db.collection(CHATS).document(chatId).collection(MESSAGES)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        if (anchorTs != null) {
            q = q.whereLessThan("createdAt", anchorTs);
        }
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
}

