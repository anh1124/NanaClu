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
                .addOnFailureListener(e -> {
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("UserRepository", e);
                    callback.onError(e);
                });
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
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("UserRepository", e);
                        android.util.Log.e("UserRepository", "Failed to update photoUrl for user: " + userId, e);
                    });
        }
    }

    public void updateUserDisplayName(String userId, String displayName, UserUpdateCallback callback) {
        if (displayName == null || displayName.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Display name cannot be empty"));
            return;
        }
        
        db.collection(USERS_COLLECTION)
                .document(userId)
                .update("displayName", displayName.trim())
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("UserRepository", "Updated displayName for user: " + userId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("UserRepository", e);
                    android.util.Log.e("UserRepository", "Failed to update displayName for user: " + userId, e);
                    callback.onError(e);
                });
    }

    public void updateUserProfile(String userId, String displayName, String photoUrl, UserUpdateCallback callback) {
        if (displayName == null || displayName.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Display name cannot be empty"));
            return;
        }

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("displayName", displayName.trim());
        if (photoUrl != null && !photoUrl.isEmpty()) {
            updates.put("photoUrl", photoUrl);
        }

        db.collection(USERS_COLLECTION)
                .document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("UserRepository", "Updated profile for user: " + userId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("UserRepository", e);
                    android.util.Log.e("UserRepository", "Failed to update profile for user: " + userId, e);
                    callback.onError(e);
                });
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(Exception e);
    }

    public interface UserUpdateCallback {
        void onSuccess();
        void onError(Exception e);
    }
}
