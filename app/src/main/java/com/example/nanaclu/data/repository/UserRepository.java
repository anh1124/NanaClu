package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {
    private FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public void getUserById(String userId, UserCallback callback) {
        db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onError(new Exception("Failed to parse user data"));
                        }
                    } else {
                        callback.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateUserPhotoUrl(String userId, String photoUrl) {
        if (photoUrl != null && !photoUrl.isEmpty()) {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .update("photoUrl", photoUrl)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("UserRepository", "Updated photoUrl for user: " + userId);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("UserRepository", "Failed to update photoUrl for user: " + userId, e);
                    });
        }
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(Exception e);
    }
}
