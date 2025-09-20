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

    /** Ensure there is exactly one chat per groupId with type=group - now stored in groups/{groupId}/chats/ */
    public Task<String> getOrCreateGroupChat(String groupId) {
        if (groupId == null || groupId.isEmpty()) return Tasks.forException(new IllegalArgumentException("groupId empty"));

        // Check if group chat already exists in groups/{groupId}/chats/
        return db.collection("groups")
                .document(groupId)
                .collection("chats")
                .whereEqualTo("type", "group")
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) return Tasks.forException(task.getException());
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        return Tasks.forResult(task.getResult().getDocuments().get(0).getId());
                    }

                    // Create new group chat in groups/{groupId}/chats/{chatId}
                    DocumentReference chatRef = db.collection("groups")
                            .document(groupId)
                            .collection("chats")
                            .document();
                    String chatId = chatRef.getId();
                    Map<String, Object> chatMap = new HashMap<>();
                    chatMap.put("chatId", chatId);
                    chatMap.put("type", "group");
                    chatMap.put("memberCount", 1);
                    chatMap.put("groupId", groupId);
                    chatMap.put("createdAt", FieldValue.serverTimestamp());

                    // Add current user as a member so it appears in their chat list
                    String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                    ChatMember self = new ChatMember();
                    self.userId = uid;
                    self.joinedAt = System.currentTimeMillis();

                    return chatRef.set(chatMap)
                            .continueWithTask(t -> {
                                if (uid != null) {
                                    return chatRef.collection(MEMBERS).document(uid).set(self);
                                } else {
                                    return Tasks.forResult(null);
                                }
                            })
                            .continueWithTask(t -> Tasks.forResult(chatId));
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
                        Boolean hidden = memberDoc.getBoolean("hidden");
                        if (hidden != null && hidden) continue; // skip chats hidden by this user
                        DocumentReference chatRef = memberDoc.getReference().getParent().getParent();
                        if (chatRef != null) jobs.add(chatRef.get());
                    }
                    return Tasks.whenAllSuccess(jobs).continueWith(tt -> {
                        // De-duplicate by chatId and prefer group-path docs, then docs with type set
                        Map<String, Chat> bestById = new HashMap<>();
                        Map<String, Integer> scoreById = new HashMap<>();
                        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                        for (Object o : tt.getResult()) {
                            DocumentSnapshot ds = (DocumentSnapshot) o;
                            Chat c = ds.toObject(Chat.class);
                            if (c == null) continue;
                            // Skip invalid private chats (missing pairKey/other user)
                            if ("private".equals(c.type)) {
                                if (c.pairKey == null || currentUid == null) continue;
                                String[] ids = c.pairKey.split("_");
                                String other = null;
                                for (String id : ids) { if (!id.equals(currentUid)) { other = id; break; } }
                                if (other == null || other.isEmpty()) continue;
                            }
                            if (c.chatId == null || c.chatId.isEmpty()) c.chatId = ds.getId();

                            // Derive if this chat doc lives under groups/{groupId}/chats/{chatId}
                            int score = 0;
                            com.google.firebase.firestore.DocumentReference ref = ds.getReference();
                            com.google.firebase.firestore.DocumentReference maybeGroupDoc = ref.getParent() != null ? ref.getParent().getParent() : null;
                            boolean isGroupPath = maybeGroupDoc != null && maybeGroupDoc.getParent() != null && "groups".equals(maybeGroupDoc.getParent().getId());
                            if (isGroupPath) {
                                score += 2;
                                if ((c.groupId == null || c.groupId.isEmpty())) {
                                    c.groupId = maybeGroupDoc.getId();
                                }
                            }
                            if (c.type != null && !c.type.isEmpty()) score += 1;

                            Integer prev = scoreById.get(c.chatId);
                            if (prev == null || score > prev) {
                                bestById.put(c.chatId, c);
                                scoreById.put(c.chatId, score);
                            }
                        }
                        List<Chat> chats = new ArrayList<>(bestById.values());
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

        // Update in root chats collection (for private chats)
        Task<Void> rootUpdate = db.collection(CHATS).document(chatId).update(data);

        // Also try to update in any group chat doc found via collectionGroup("chats")
        Task<Void> groupUpdate = db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    com.google.firebase.firestore.DocumentSnapshot doc = t.getResult().getDocuments().get(0);
                    return doc.getReference().update(data);
                });

        return Tasks.whenAll(rootUpdate, groupUpdate).continueWithTask(t -> Tasks.forResult(null));
    }

    public Task<Void> updateLastRead(String chatId, String userId, long ts) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));

        // Update in root chats path
        Task<Void> rootUpdate = db.collection(CHATS).document(chatId)
                .collection(MEMBERS).document(userId)
                .update("lastReadAt", ts);

        // Also try to update in group chat membership if exists
        Task<Void> groupUpdate = db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                    return chatDoc.getReference().collection(MEMBERS).document(userId)
                            .update("lastReadAt", ts);
                });
        return Tasks.whenAll(rootUpdate, groupUpdate).continueWithTask(t -> Tasks.forResult(null));
    }

    /** Hide chat for current user (soft delete). Only when all members hide (private) then delete chat doc. */
    public Task<Void> hideChatForUser(String chatId, String userId) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        DocumentReference rootMemberRef = db.collection(CHATS).document(chatId).collection(MEMBERS).document(userId);
        Task<Void> root = rootMemberRef.update("hidden", true);
        Task<Void> group = db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) return Tasks.forResult(null);
                    com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                    return chatDoc.getReference().collection(MEMBERS).document(userId).update("hidden", true);
                });
        return Tasks.whenAll(root, group).continueWithTask(t -> tryDeleteIfBothHidden(chatId));
    }

    private Task<Void> tryDeleteIfBothHidden(String chatId) {
        DocumentReference chatRef = db.collection(CHATS).document(chatId);
        return chatRef.get().continueWithTask(t -> {
            if (!t.isSuccessful() || t.getResult() == null) return Tasks.forResult(null);
            com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult();
            String type = chatDoc.getString("type");
            if (!"private".equals(type)) {
                // Only auto-delete private chats when both sides hide
                return Tasks.forResult(null);
            }
            return chatRef.collection(MEMBERS).get().continueWithTask(mt -> {
                if (!mt.isSuccessful() || mt.getResult() == null) return Tasks.forResult(null);
                int total = mt.getResult().size();
                int hidden = 0;
                for (com.google.firebase.firestore.DocumentSnapshot md : mt.getResult().getDocuments()) {
                    Boolean h = md.getBoolean("hidden");
                    if (h != null && h) hidden++;
                }
                if (total > 0 && hidden == total) {
                    return chatRef.delete();
                }
                return Tasks.forResult(null);
            });
        });
    }
}

