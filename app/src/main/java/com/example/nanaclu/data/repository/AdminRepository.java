package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;

public class AdminRepository {
    private final FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";

    public AdminRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Kiểm tra quyền admin của user hiện tại (theo trường isadmin trên Firestore).
     * Success: callback.onResult(true/false)
     * Error: callback.onError(exception)
     */
    public void checkIsAdmin(String userId, AdminCheckCallback callback) {
        db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        // getBoolean có thể trả về null nếu không có field, xử lý rõ ràng
                        Boolean isadmin = doc.getBoolean("isadmin");
                        callback.onResult(Boolean.TRUE.equals(isadmin));
                    } else {
                        callback.onResult(false);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public interface AdminCheckCallback {
        void onResult(boolean isAdmin);
        void onError(Exception e);
    }
}
