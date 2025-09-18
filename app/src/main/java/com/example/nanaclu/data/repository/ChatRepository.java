package com.example.nanaclu.data.repository;

import androidx.annotation.Nullable;

import com.example.nanaclu.data.model.Chat;
import com.example.nanaclu.data.model.ChatMember;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore-only repository for chat threads and membership.
 * - chats/{chatId}
 * - chats/{chatId}/members/{userId}
 */
public class ChatRepository {
    private final FirebaseFirestore db;
    private static final String CHATS = "chats";
    private static final String MEMBERS = "members";

    public ChatRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Create or return a private chat between two users. Uses pairKey = sorted uidA_uidB for lookup.
     */
    public Task<String> getOrCreatePrivateChat(String currentUid, String otherUid) {
        if (currentUid == null || otherUid == null) return Tasks.forException(new IllegalArgumentException("uid null"));
        String a = currentUid.compareTo(otherUid) < 0 ? currentUid : otherUid;
        String b = currentUid.compareTo(otherUid) < 0 ? otherUid : currentUid;
        String pairKey = a + "_" + b;
        return db.collection(CHATS)
                .whereEqualTo("type", "private")
                .whereEqualTo("pairKey", pairKey)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) return Tasks.forException(task.getException());
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        return Tasks.forResult(task.getResult().getDocuments().get(0).getId());
                    }
                    // Create new chat
                    DocumentReference chatRef = db.collection(CHATS).document();
                    String chatId = chatRef.getId();
                    Chat chat = new Chat();
                    chat.chatId = chatId;
                    chat.type = "private";
                    chat.memberCount = 2;
                    chat.pairKey = pairKey;
                    chat.createdAt = System.currentTimeMillis();

                    Map<String, Object> chatMap = new HashMap<>();
                    chatMap.put("chatId", chat.chatId);
                    chatMap.put("type", chat.type);
                    chatMap.put("memberCount", chat.memberCount);
                    chatMap.put("pairKey", chat.pairKey);
                    chatMap.put("createdAt", FieldValue.serverTimestamp());

                    ChatMember m1 = new ChatMember();
                    m1.userId = currentUid;
                    m1.joinedAt = System.currentTimeMillis();
                    ChatMember m2 = new ChatMember();
                    m2.userId = otherUid;
                    m2.joinedAt = System.currentTimeMillis();

                    return chatRef.set(chatMap)
                            .continueWithTask(t -> chatRef.collection(MEMBERS).document(currentUid).set(m1))
                            .continueWithTask(t -> chatRef.collection(MEMBERS).document(otherUid).set(m2))
                            .continueWithTask(t -> Tasks.forResult(chatId));
                });
    }

    /** Ensure there is exactly one chat per groupId with type=group */
    public Task<String> getOrCreateGroupChat(String groupId) {
        if (groupId == null || groupId.isEmpty()) return Tasks.forException(new IllegalArgumentException("groupId empty"));
        return db.collection(CHATS)
                .whereEqualTo("type", "group")
                .whereEqualTo("groupId", groupId)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) return Tasks.forException(task.getException());
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        return Tasks.forResult(task.getResult().getDocuments().get(0).getId());
                    }
                    DocumentReference chatRef = db.collection(CHATS).document();
                    String chatId = chatRef.getId();
                    Map<String, Object> chatMap = new HashMap<>();
                    chatMap.put("chatId", chatId);
                    chatMap.put("type", "group");
                    chatMap.put("memberCount", 0);
                    chatMap.put("groupId", groupId);
                    chatMap.put("createdAt", FieldValue.serverTimestamp());
                    return chatRef.set(chatMap).continueWithTask(t -> Tasks.forResult(chatId));
                });
    }

    /**
     * List chats that a user participates in using collectionGroup("members").
     * Then load parent chat documents and sort by lastMessageAt desc on client (best effort).
     */
    public Task<List<Chat>> listUserChats(String uid, int limit, @Nullable Chat last) {
        if (uid == null) return Tasks.forResult(new ArrayList<>());
        return db.collectionGroup(MEMBERS)
                .whereEqualTo("userId", uid)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null) return Tasks.forResult(new ArrayList<Chat>());
                    List<Task<DocumentSnapshot>> jobs = new ArrayList<>();
                    for (DocumentSnapshot memberDoc : t.getResult().getDocuments()) {
                        DocumentReference chatRef = memberDoc.getReference().getParent().getParent();
                        if (chatRef != null) jobs.add(chatRef.get());
                    }
                    return Tasks.whenAllSuccess(jobs).continueWith(tt -> {
                        List<Chat> chats = new ArrayList<>();
                        for (Object o : tt.getResult()) {
                            DocumentSnapshot ds = (DocumentSnapshot) o;
                            Chat c = ds.toObject(Chat.class);
                            if (c != null) chats.add(c);
                        }
                        // Client-side sort by lastMessageAt desc
                        chats.sort((c1, c2) -> {
                            long t1 = c1 != null && c1.lastMessageAt != null ? c1.lastMessageAt : 0L;
                            long t2 = c2 != null && c2.lastMessageAt != null ? c2.lastMessageAt : 0L;
                            return Long.compare(t2, t1);
                        });
                        if (limit > 0 && chats.size() > limit) {
                            return chats.subList(0, limit);
                        }
                        return chats;
                    });
                });
    }

    public Task<Void> addMember(String chatId, String userId) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        ChatMember m = new ChatMember();
        m.userId = userId;
        m.joinedAt = System.currentTimeMillis();
        DocumentReference chatRef = db.collection(CHATS).document(chatId);
        return chatRef.collection(MEMBERS).document(userId).set(m)
                .continueWithTask(t -> chatRef.update("memberCount", FieldValue.increment(1)));
    }

    public Task<Void> removeMember(String chatId, String userId) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        DocumentReference chatRef = db.collection(CHATS).document(chatId);
        return chatRef.collection(MEMBERS).document(userId).delete()
                .continueWithTask(t -> chatRef.update("memberCount", FieldValue.increment(-1)));
    }

    public Task<Void> updateLastMessageMeta(String chatId, String messageText, String authorId, long createdAtMillis) {
        if (chatId == null) return Tasks.forException(new IllegalArgumentException("chatId null"));
        Map<String, Object> data = new HashMap<>();
        data.put("lastMessage", messageText);
        data.put("lastMessageAuthorId", authorId);
        data.put("lastMessageAt", createdAtMillis);
        return db.collection(CHATS).document(chatId).update(data);
    }

    public Task<Void> updateLastRead(String chatId, String userId, long ts) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        return db.collection(CHATS).document(chatId)
                .collection(MEMBERS).document(userId)
                .update("lastReadAt", ts);
    }
}

