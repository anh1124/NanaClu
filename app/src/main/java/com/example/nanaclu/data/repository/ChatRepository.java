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
                        // Chat exists, but ensure current user is a member
                        String existingChatId = task.getResult().getDocuments().get(0).getId();
                        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                        if (uid != null) {
                            DocumentReference chatRef = task.getResult().getDocuments().get(0).getReference();
                            return chatRef.collection(MEMBERS).document(uid).get().continueWithTask(memberTask -> {
                                if (!memberTask.isSuccessful() || memberTask.getResult() == null || !memberTask.getResult().exists()) {
                                    // User is not a member, add them to both locations
                                    android.util.Log.d("ChatRepository", "Adding user " + uid + " to existing group chat " + existingChatId);
                                    ChatMember newMember = new ChatMember();
                                    newMember.userId = uid;
                                    newMember.joinedAt = System.currentTimeMillis();

                                    DocumentReference mainChatRef = db.collection(CHATS).document(existingChatId);
                                    Task<Void> groupMember = chatRef.collection(MEMBERS).document(uid).set(newMember);
                                    Task<Void> mainMember = mainChatRef.collection(MEMBERS).document(uid).set(newMember);
                                    return Tasks.whenAll(groupMember, mainMember)
                                            .continueWithTask(t -> Tasks.forResult(existingChatId));
                                } else {
                                    android.util.Log.d("ChatRepository", "User " + uid + " already member of group chat " + existingChatId);
                                    return Tasks.forResult(existingChatId);
                                }
                            });
                        } else {
                            return Tasks.forResult(existingChatId);
                        }
                    }

                    // Create new group chat in groups/{groupId}/chats/{chatId}
                    DocumentReference chatRef = db.collection("groups")
                            .document(groupId)
                            .collection("chats")
                            .document();
                    String chatId = chatRef.getId();
                    android.util.Log.d("ChatRepository", "Creating new group chat " + chatId + " for group " + groupId);
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

                    // Also create in main chats collection for consistency
                    DocumentReference mainChatRef = db.collection(CHATS).document(chatId);

                    return Tasks.whenAll(chatRef.set(chatMap), mainChatRef.set(chatMap))
                            .continueWithTask(t -> {
                                if (uid != null) {
                                    android.util.Log.d("ChatRepository", "Adding creator " + uid + " as member of new group chat");
                                    // Add member to both locations
                                    Task<Void> groupMember = chatRef.collection(MEMBERS).document(uid).set(self);
                                    Task<Void> mainMember = mainChatRef.collection(MEMBERS).document(uid).set(self);
                                    return Tasks.whenAll(groupMember, mainMember);
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

    /**
     * Archive (soft delete) chat for a user: hide thread and clear local history baseline.
     * Sets: hidden=true, clearedAt=now, lastReadAt=now for the membership of this user.
     * Still keeps the chat document; if later all members are hidden (private), auto-delete is applied.
     */
    public Task<Void> archiveForUser(String chatId, String userId, long now) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        android.util.Log.d("ChatRepository", "archiveForUser: chatId=" + chatId + ", userId=" + userId + ", now=" + now);

        Map<String, Object> updates = new HashMap<>();
        updates.put("hidden", true);
        updates.put("clearedAt", now);
        updates.put("lastReadAt", now);

        // Root path membership
        Task<Void> root = db.collection(CHATS).document(chatId)
                .collection(MEMBERS).document(userId)
                .update(updates);

        // Group path membership (if exists)
        Task<Void> group = db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                    return chatDoc.getReference().collection(MEMBERS).document(userId).update(updates);
                });

        return Tasks.whenAll(root, group).continueWithTask(t -> tryDeleteIfBothHidden(chatId));
    }

    /**
     * Unarchive (unhide) chat for the given users: sets hidden=false for each membership.
     * Does not modify clearedAt; previous history threshold remains intact.
     */
    public Task<Void> unarchiveForUsers(String chatId, java.util.List<String> userIds) {
        if (chatId == null || userIds == null || userIds.isEmpty()) return Tasks.forResult(null);
        android.util.Log.d("ChatRepository", "unarchiveForUsers: chatId=" + chatId + ", users=" + userIds.size());

        List<Task<Void>> tasks = new ArrayList<>();
        for (String uid : userIds) {
            if (uid == null) continue;
            Map<String, Object> updates = new HashMap<>();
            updates.put("hidden", false);

            // Root path
            tasks.add(db.collection(CHATS).document(chatId)
                    .collection(MEMBERS).document(uid)
                    .update(updates));

            // Group path (if exists)
            tasks.add(db.collectionGroup("chats")
                    .whereEqualTo("chatId", chatId)
                    .limit(1)
                    .get()
                    .continueWithTask(t -> {
                        if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                            return Tasks.forResult(null);
                        }
                        com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                        return chatDoc.getReference().collection(MEMBERS).document(uid).update(updates);
                    }));
        }
        return Tasks.whenAll(tasks);
    }

    /**
     * Read the clearedAt baseline for a user's membership in a chat. Returns 0L if not set.
     */
    public Task<Long> getClearedAt(String chatId, String userId) {
        if (chatId == null || userId == null) return Tasks.forResult(0L);

        // Try root membership first
        Task<Long> root = db.collection(CHATS).document(chatId)
                .collection(MEMBERS).document(userId)
                .get()
                .continueWith(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) return null;
                    Long v = t.getResult().getLong("clearedAt");
                    return v;
                });

        return root.continueWithTask(rt -> {
            Long val = rt.getResult();
            if (val != null) return Tasks.forResult(val);
            // Fallback to group membership if any
            return db.collectionGroup("chats")
                    .whereEqualTo("chatId", chatId)
                    .limit(1)
                    .get()
                    .continueWithTask(t -> {
                        if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                            return Tasks.forResult(0L);
                        }
                        com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                        return chatDoc.getReference().collection(MEMBERS).document(userId).get()
                                .continueWith(mt -> {
                                    if (!mt.isSuccessful() || mt.getResult() == null || !mt.getResult().exists()) return 0L;
                                    Long v2 = mt.getResult().getLong("clearedAt");
                                    return v2 != null ? v2 : 0L;
                                });
                    });
        });
    }

    /** Hide chat for current user (soft delete). Only when all members hide (private) then delete chat doc. */
    public Task<Void> hideChatForUser(String chatId, String userId) {
        if (chatId == null || userId == null) return Tasks.forException(new IllegalArgumentException("null"));
        android.util.Log.d("ChatRepository", "hideChatForUser: chatId=" + chatId + ", userId=" + userId);
        DocumentReference rootMemberRef = db.collection(CHATS).document(chatId).collection(MEMBERS).document(userId);
        Task<Void> root = rootMemberRef.update("hidden", true);
        Task<Void> group = db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || t.getResult() == null || t.getResult().isEmpty()) {
                        android.util.Log.d("ChatRepository", "No group chat found for chatId: " + chatId);
                        return Tasks.forResult(null);
                    }
                    com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult().getDocuments().get(0);
                    android.util.Log.d("ChatRepository", "Updating group chat member hidden for: " + chatDoc.getReference().getPath());
                    return chatDoc.getReference().collection(MEMBERS).document(userId).update("hidden", true);
                });
        return Tasks.whenAll(root, group).continueWithTask(t -> {
            android.util.Log.d("ChatRepository", "Both updates completed, checking if should delete chat");
            return tryDeleteIfBothHidden(chatId);
        });
    }

    private Task<Void> tryDeleteIfBothHidden(String chatId) {
        DocumentReference chatRef = db.collection(CHATS).document(chatId);
        return chatRef.get().continueWithTask(t -> {
            if (!t.isSuccessful() || t.getResult() == null) return Tasks.forResult(null);
            com.google.firebase.firestore.DocumentSnapshot chatDoc = t.getResult();
            String type = chatDoc.getString("type");
            android.util.Log.d("ChatRepository", "tryDeleteIfBothHidden: chatId=" + chatId + ", type=" + type);
            if (!"private".equals(type)) {
                // Only auto-delete private chats when both sides hide
                android.util.Log.d("ChatRepository", "Not a private chat, skipping auto-delete");
                return Tasks.forResult(null);
            }
            return chatRef.collection(MEMBERS).get().continueWithTask(mt -> {
                if (!mt.isSuccessful() || mt.getResult() == null) return Tasks.forResult(null);
                int total = mt.getResult().size();
                int hidden = 0;
                android.util.Log.d("ChatRepository", "Checking members: total=" + total);
                for (com.google.firebase.firestore.DocumentSnapshot md : mt.getResult().getDocuments()) {
                    Boolean h = md.getBoolean("hidden");
                    android.util.Log.d("ChatRepository", "Member " + md.getId() + " hidden=" + h);
                    if (h != null && h) hidden++;
                }
                android.util.Log.d("ChatRepository", "Hidden count: " + hidden + "/" + total);
                if (total > 0 && hidden == total) {
                    android.util.Log.d("ChatRepository", "All members hidden, deleting chat document");
                    // Also delete messages subcollection
                    return chatRef.collection("messages").get().continueWithTask(msgTask -> {
                        if (msgTask.isSuccessful() && msgTask.getResult() != null) {
                            com.google.firebase.firestore.WriteBatch batch = db.batch();
                            for (com.google.firebase.firestore.DocumentSnapshot msgDoc : msgTask.getResult().getDocuments()) {
                                batch.delete(msgDoc.getReference());
                            }
                            return batch.commit().continueWithTask(batchTask -> chatRef.delete());
                        } else {
                            return chatRef.delete();
                        }
                    });
                } else {
                    android.util.Log.d("ChatRepository", "Not all members hidden, keeping chat document");
                }
                return Tasks.forResult(null);
            });
        });
    }

    /** Delete group chat completely (for admin/owner) */
    public Task<Void> deleteGroupChat(String chatId, String groupId) {
        if (chatId == null || groupId == null) return Tasks.forException(new IllegalArgumentException("null"));

        // Delete from both locations: main chats collection and group's chats subcollection
        Task<Void> deleteMain = db.collection(CHATS).document(chatId).delete();
        Task<Void> deleteGroup = db.collection("groups")
                .document(groupId)
                .collection("chats")
                .document(chatId)
                .delete();

        return Tasks.whenAll(deleteMain, deleteGroup);
    }

    /**
     * Get chat members for a given chatId
     */
    public Task<List<String>> getChatMembers(String chatId) {
        if (chatId == null) return Tasks.forException(new IllegalArgumentException("chatId null"));
        
        // Try to get members from root chats collection first
        return getRootChatMembers(chatId)
                .continueWithTask(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        return Tasks.forResult(task.getResult());
                    }
                    
                    // If not found in root, try group chats
                    return getGroupChatMembers(chatId);
                });
    }
    
    private Task<List<String>> getRootChatMembers(String chatId) {
        return db.collection(CHATS).document(chatId).collection(MEMBERS)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        return new ArrayList<String>();
                    }
                    
                    List<String> memberIds = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        memberIds.add(doc.getId());
                    }
                    return memberIds;
                });
    }
    
    private Task<List<String>> getGroupChatMembers(String chatId) {
        return db.collectionGroup("chats")
                .whereEqualTo("chatId", chatId)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return Tasks.forException(new Exception("Chat not found"));
                    }
                    
                    DocumentSnapshot chatDoc = task.getResult().getDocuments().get(0);
                    return chatDoc.getReference().collection(MEMBERS).get()
                            .continueWith(memberTask -> {
                                if (!memberTask.isSuccessful()) {
                                    throw memberTask.getException();
                                }
                                
                                List<String> groupMemberIds = new ArrayList<>();
                                for (DocumentSnapshot doc : memberTask.getResult().getDocuments()) {
                                    groupMemberIds.add(doc.getId());
                                }
                                return groupMemberIds;
                            });
                });
    }
}

