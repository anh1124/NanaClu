package com.example.nanaclu.data.repository;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.example.nanaclu.data.model.User;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.tasks.Task;

public class AuthRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public Task<AuthResult> registerWithEmail(String email, String password, String displayName) {
        return auth.createUserWithEmailAndPassword(email, password)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    FirebaseUser fUser = auth.getCurrentUser();
                    if (fUser == null) throw new IllegalStateException("User null after register");
                    Map<String, Object> userDoc = new HashMap<>();
                    long now = System.currentTimeMillis();
                    userDoc.put("userId", fUser.getUid());
                    userDoc.put("createdAt", now);
                    userDoc.put("email", email);
                    userDoc.put("displayName", displayName);
                    userDoc.put("avatarImageId", null);
                    userDoc.put("lastLoginAt", now);
                    userDoc.put("status", "online");
                    return db.collection("users").document(fUser.getUid()).set(userDoc)
                            .continueWithTask(v -> com.google.android.gms.tasks.Tasks.forResult(task.getResult()));
                });
    }

    public Task<AuthResult> loginWithEmail(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    FirebaseUser fUser = auth.getCurrentUser();
                    if (fUser == null) throw new IllegalStateException("User null after login");
                    long now = System.currentTimeMillis();
                    return db.collection("users").document(fUser.getUid())
                            .update("lastLoginAt", now, "status", "online")
                            .continueWithTask(v -> com.google.android.gms.tasks.Tasks.forResult(task.getResult()));
                });
    }

    public Task<Void> logout() {
        FirebaseUser fUser = auth.getCurrentUser();
        if (fUser != null) {
            db.collection("users").document(fUser.getUid())
                    .update("status", "offline");
        }
        auth.signOut();
        return com.google.android.gms.tasks.Tasks.forResult(null);
    }

    // Google Sign-In using ID token (One Tap or traditional SignInClient)
    public Task<AuthResult> loginWithGoogleIdToken(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        return auth.signInWithCredential(credential)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    FirebaseUser fUser = auth.getCurrentUser();
                    if (fUser == null) throw new IllegalStateException("User null after google login");
                    long now = System.currentTimeMillis();
                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("userId", fUser.getUid());
                    userDoc.put("email", fUser.getEmail());
                    userDoc.put("displayName", fUser.getDisplayName());
                    userDoc.put("avatarImageId", null);
                    userDoc.put("lastLoginAt", now);
                    userDoc.put("status", "online");
                    userDoc.put("createdAt", now);
                    return db.collection("users").document(fUser.getUid())
                            .set(userDoc, com.google.firebase.firestore.SetOptions.merge())
                            .continueWithTask(v -> com.google.android.gms.tasks.Tasks.forResult(task.getResult()));
                });
    }
}


