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
                            .collection(MEMBERS_COLLECTION).document(uid).set(m);
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
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        System.out.println("GroupRepository: Loading groups for user: " + userId);

        // Simple approach: get all groups and filter by createdBy
        return db.collection(GROUPS_COLLECTION).get()
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        System.out.println("GroupRepository: Found " + snapshot.size() + " total groups in database");

                        List<Group> userGroups = new ArrayList<>();

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            System.out.println("GroupRepository: Processing document: " + doc.getId());

                            // Check if document has the required fields
                            if (doc.contains("name") && doc.contains("createdBy")) {
                                String docName = doc.getString("name");
                                String docCreatedBy = doc.getString("createdBy");
                                System.out.println("GroupRepository: Document " + doc.getId() + " - name: " + docName + ", createdBy: " + docCreatedBy);

                                if (userId.equals(docCreatedBy)) {
                                    System.out.println("GroupRepository: User created this group, adding to list");
                                    Group group = doc.toObject(Group.class);
                                    if (group != null) {
                                        userGroups.add(group);
                                        System.out.println("GroupRepository: Successfully added group: " + group.name);
                                    } else {
                                        System.out.println("GroupRepository: Failed to convert document to Group object");
                                    }
                                } else {
                                    System.out.println("GroupRepository: User did not create this group, skipping");
                                }
                            } else {
                                System.out.println("GroupRepository: Document " + doc.getId() + " missing required fields");
                                System.out.println("GroupRepository: Available fields: " + doc.getData().keySet());
                            }
                        }

                        System.out.println("GroupRepository: Total user groups found: " + userGroups.size());
                        return Tasks.forResult(userGroups);
                    } else {
                        System.out.println("GroupRepository: Failed to get groups: " + task.getException().getMessage());
                        return Tasks.forResult(new ArrayList<>());
                    }
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
        db.collection(GROUPS_COLLECTION)
                .document(groupId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
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
                .set(m);
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


