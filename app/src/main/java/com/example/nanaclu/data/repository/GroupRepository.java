package com.example.nanaclu.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GroupRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<DocumentReference> createGroup(String name, boolean isPublic) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            return com.google.android.gms.tasks.Tasks.forException(new IllegalStateException("Not logged in"));
        }
        long now = System.currentTimeMillis();
        Map<String, Object> group = new HashMap<>();
        group.put("groupId", null); // will be set after add
        group.put("name", name);
        group.put("avatarImageId", null);
        group.put("coverImageId", null);
        group.put("description", "");
        group.put("createdBy", uid);
        group.put("createdAt", now);
        group.put("isPublic", isPublic);
        group.put("memberCount", 1);
        group.put("postCount", 0);
        return db.collection("groups").add(group)
                .addOnSuccessListener(ref -> ref.update("groupId", ref.getId()));
    }
}


