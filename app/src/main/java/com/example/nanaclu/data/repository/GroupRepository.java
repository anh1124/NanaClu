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

    public interface GroupCallback {
        void onSuccess(Group group);
        void onError(Exception e);
    }
}


