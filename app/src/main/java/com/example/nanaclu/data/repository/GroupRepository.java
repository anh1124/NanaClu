package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.Member;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupRepository {
    private FirebaseFirestore db;
    private static final String GROUPS_COLLECTION = "groups";
    private static final String MEMBERS_COLLECTION = "members";

    public GroupRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Task<Void> createGroup(Group group) {
        return db.collection(GROUPS_COLLECTION).document(group.groupId).set(group)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        // Add creator as owner member
                        Member member = new Member();
                        member.userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        member.role = "owner";
                        member.joinedAt = System.currentTimeMillis();
                        member.status = "active";

                        return db.collection(GROUPS_COLLECTION)
                                .document(group.groupId)
                                .collection(MEMBERS_COLLECTION)
                                .document(member.userId)
                                .set(member);
                    }
                    return task;
                });
    }

    /** Generate a unique short code for the group (e.g., 6 chars) */
    public Task<String> generateUniqueCode(int length) {
        return tryGenerateCode(length, 0);
    }

    private Task<String> tryGenerateCode(int length, int attempt) {
        if (attempt > 8) {
            return Tasks.forException(new Exception("Could not generate unique code"));
        }
        String code = randomCode(length);
        return db.collection(GROUPS_COLLECTION)
                .whereEqualTo("code", code)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return Tasks.forException(task.getException() != null ? task.getException() : new Exception("Query failed"));
                    }
                    if (task.getResult() == null || task.getResult().isEmpty()) {
                        return Tasks.forResult(code);
                    } else {
                        return tryGenerateCode(length, attempt + 1);
                    }
                });
    }

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < length; i++) sb.append(CODE_CHARS[rnd.nextInt(CODE_CHARS.length)]);
        return sb.toString();
    }

    /** Join group by short code */
    public Task<Void> joinGroupByCode(String code) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return Tasks.forException(new IllegalStateException("Not logged in"));
        if (code == null || code.isEmpty()) return Tasks.forException(new IllegalArgumentException("Empty code"));
        return db.collection(GROUPS_COLLECTION)
                .whereEqualTo("code", code)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        return Tasks.forException(new Exception("Không tìm thấy nhóm với mã này"));
                    }
                    DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                    String groupId = doc.getId();
                    Member m = new Member();
                    m.userId = uid;
                    m.role = "member";
                    m.status = "active";
                    m.joinedAt = System.currentTimeMillis();
                    return db.collection(GROUPS_COLLECTION).document(groupId)
                            .collection(MEMBERS_COLLECTION).document(uid).set(m)
                            .continueWithTask(task2 -> {
                                if (task2.isSuccessful()) {
                                    // Update member count
                                    return db.collection(GROUPS_COLLECTION).document(groupId)
                                            .update("memberCount", com.google.firebase.firestore.FieldValue.increment(1));
                                }
                                return task2;
                            });
                });
    }

    /** Upload group image (avatar or cover) to Firebase Storage and return download URL */
    public void uploadGroupImage(byte[] imageData, String type,
                                com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                com.google.android.gms.tasks.OnFailureListener onFailure) {
        com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
        com.google.firebase.storage.StorageReference ref = storage.getReference()
                .child("group_images")
                .child(type)
                .child("img_" + System.currentTimeMillis() + ".jpg");
        com.google.firebase.storage.UploadTask ut = ref.putBytes(imageData);
        ut.addOnSuccessListener(ts -> ref.getDownloadUrl()
                .addOnSuccessListener(uri -> onSuccess.onSuccess(uri.toString()))
                .addOnFailureListener(onFailure))
          .addOnFailureListener(onFailure);
    }


    public Task<List<Group>> loadUserGroups() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) return Tasks.forResult(new ArrayList<>());
        System.out.println("GroupRepository: Loading groups for user (by membership): " + userId);

        return loadJoinedGroupIds()
                .continueWithTask(task -> {
                    java.util.Set<String> ids = task.getResult();
                    if (ids == null || ids.isEmpty()) {
                        System.out.println("GroupRepository: No joined group IDs found");
                        return Tasks.forResult(new ArrayList<Group>());
                    }
                    java.util.List<com.google.android.gms.tasks.Task<DocumentSnapshot>> jobs = new ArrayList<>();
                    for (String id : ids) {
                        jobs.add(db.collection(GROUPS_COLLECTION).document(id).get());
                    }
                    return Tasks.whenAllSuccess(jobs).continueWith(t -> {
                        java.util.List<Group> groups = new ArrayList<>();
                        for (Object o : t.getResult()) {
                            DocumentSnapshot ds = (DocumentSnapshot) o;
                            Group g = ds.toObject(Group.class);
                            if (g != null) groups.add(g);
                        }
                        System.out.println("GroupRepository: Loaded groups by membership = " + groups.size());
                        return groups;
                    });
                });
    }

    public void getGroupById(String groupId, GroupCallback callback) {
        db.collection(GROUPS_COLLECTION).document(groupId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Group group = documentSnapshot.toObject(Group.class);
                        if (group != null) {
                            callback.onSuccess(group);
                        } else {
                            callback.onError(new Exception("Failed to parse group data"));
                        }
                    } else {
                        callback.onError(new Exception("Group not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void getMemberById(String groupId, String userId, MemberCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Member member = documentSnapshot.toObject(Member.class);
                        if (member != null) {
                            callback.onSuccess(member);
                        } else {
                            callback.onError(new Exception("Failed to parse member data"));
                        }
                    } else {
                        callback.onError(new Exception("Member not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateGroup(Group group, UpdateCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(group.groupId)
                .set(group)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void deleteGroup(String groupId, UpdateCallback callback) {
        // Delete all subcollections first, then delete the group document
        deleteGroupSubcollections(groupId)
                .addOnSuccessListener(aVoid -> {
                    // After subcollections are deleted, delete the main group document
                    db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .delete()
                            .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private com.google.android.gms.tasks.Task<Void> deleteGroupSubcollections(String groupId) {
        // Delete members, posts, and any other subcollections
        return deleteMembers(groupId)
                .continueWithTask(task -> deletePosts(groupId))
                .continueWithTask(task -> deleteEvents(groupId))
                .continueWithTask(task -> deleteChats(groupId));
    }

    private com.google.android.gms.tasks.Task<Void> deleteMembers(String groupId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                        batch.delete(doc.getReference());
                    }
                    return batch.commit();
                });
    }

    private com.google.android.gms.tasks.Task<Void> deletePosts(String groupId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection("posts")
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                        batch.delete(doc.getReference());
                        // Also delete post subcollections (likes, comments)
                        deletePostSubcollections(groupId, doc.getId());
                    }
                    return batch.commit();
                });
    }

    private com.google.android.gms.tasks.Task<Void> deleteEvents(String groupId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection("events")
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                        batch.delete(doc.getReference());
                    }
                    return batch.commit();
                });
    }

    private com.google.android.gms.tasks.Task<Void> deleteChats(String groupId) {
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection("chats")
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                        batch.delete(doc.getReference());
                    }
                    return batch.commit();
                });
    }

    private void deletePostSubcollections(String groupId, String postId) {
        // Delete likes subcollection
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection("posts")
                .document(postId)
                .collection("likes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit();
                });

        // Delete comments subcollection
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection("posts")
                .document(postId)
                .collection("comments")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit();
                });
    }

    /**
     * Lấy danh sách groupId mà user hiện tại là member (dùng collectionGroup("members"))
     */
    public Task<java.util.Set<String>> loadJoinedGroupIds() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) return Tasks.forResult(new java.util.HashSet<>());

        return db.collectionGroup(MEMBERS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .continueWith(task -> {
                    java.util.Set<String> groupIds = new java.util.HashSet<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            DocumentReference memberRef = doc.getReference();
                            DocumentReference groupRef = memberRef.getParent().getParent();
                            if (groupRef != null) {
                                groupIds.add(groupRef.getId());
                            }
                        }
                    }
                    return groupIds;
                });
    }

    public void getGroupMembers(String groupId, MembersCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Member> members = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Member member = doc.toObject(Member.class);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                    callback.onSuccess(members);
                })
                .addOnFailureListener(callback::onError);
    }

    public void removeMember(String groupId, String userId, UpdateCallback callback) {
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Update member count
                    updateMemberCount(groupId, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Tham gia nhóm theo groupId (mã nhóm): thêm document vào groups/{groupId}/members/{uid}
     */
    public com.google.android.gms.tasks.Task<Void> joinGroupById(String groupId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null || groupId == null || groupId.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Thiu uid hobc groupId"));
        }
        Member m = new Member();
        m.userId = uid;
        m.role = "member";
        m.status = "active";
        m.joinedAt = System.currentTimeMillis();
        return db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .document(uid)
                .set(m)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        // Update member count
                        return db.collection(GROUPS_COLLECTION).document(groupId)
                                .update("memberCount", com.google.firebase.firestore.FieldValue.increment(1));
                    }
                    return task;
                });
    }

    public void transferOwnership(String groupId, String fromUserId, String toUserId, UpdateCallback callback) {
        // Update old owner to admin
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(MEMBERS_COLLECTION)
                .document(fromUserId)
                .update("role", "admin")
                .addOnSuccessListener(aVoid -> {
                    // Update new owner
                    db.collection(GROUPS_COLLECTION)
                            .document(groupId)
                            .collection(MEMBERS_COLLECTION)
                            .document(toUserId)
                            .update("role", "owner")
                            .addOnSuccessListener(aVoid1 -> {
                                // Update group createdBy
                                db.collection(GROUPS_COLLECTION)
                                        .document(groupId)
                                        .update("createdBy", toUserId)
                                        .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                                        .addOnFailureListener(callback::onError);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private void updateMemberCount(String groupId, UpdateCallback callback) {
        getGroupMembers(groupId, new MembersCallback() {
            @Override
            public void onSuccess(List<Member> members) {
                db.collection(GROUPS_COLLECTION)
                        .document(groupId)
                        .update("memberCount", members.size())
                        .addOnSuccessListener(aVoid -> callback.onSuccess())
                        .addOnFailureListener(callback::onError);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface GroupCallback {
        void onSuccess(Group group);
        void onError(Exception e);
    }

    public interface MemberCallback {
        void onSuccess(Member member);
        void onError(Exception e);
    }

    public interface UpdateCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface MembersCallback {
        void onSuccess(List<Member> members);
        void onError(Exception e);
    }
}


