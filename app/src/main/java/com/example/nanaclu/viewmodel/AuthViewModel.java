package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.repository.AuthRepository;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends ViewModel {
    private final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>(null);

    public LiveData<Boolean> loading = _loading;
    public LiveData<String> error = _error;
    public LiveData<FirebaseUser> user = _user;

    public void checkCurrentUser() {
        _user.setValue(repository.getCurrentUser());
    }

    public void registerWithEmail(String email, String password, String displayName) {
        _loading.setValue(true);
        _error.setValue(null);
        repository.registerWithEmail(email, password, displayName)
                .addOnCompleteListener(task -> {
                    _loading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(repository.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Register failed");
                    }
                });
    }

    public void loginWithEmail(String email, String password) {
        _loading.setValue(true);
        _error.setValue(null);
        repository.loginWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    _loading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(repository.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Login failed");
                    }
                });
    }

    public void loginWithGoogleIdToken(String idToken) {
        _loading.setValue(true);
        _error.setValue(null);
        repository.loginWithGoogleIdToken(idToken)
                .addOnCompleteListener(task -> {
                    _loading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(repository.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Google Login failed");
                    }
                });
    }

    public void logout() {
        repository.logout();
        _user.setValue(null);
    }
}


