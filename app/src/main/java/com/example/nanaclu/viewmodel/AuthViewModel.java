package com.example.nanaclu.viewmodel;

import android.content.Context;

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
                        String errorMessage = "Đăng ký thất bại";
                        if (task.getException() != null) {
                            String originalMessage = task.getException().getMessage();
                            if (originalMessage != null) {
                                String lowerMessage = originalMessage.toLowerCase();
                                // Translate common Firebase auth errors to Vietnamese
                                if (lowerMessage.contains("email already in use")) {
                                    errorMessage = "Email đã được sử dụng";
                                } else if (lowerMessage.contains("weak password")) {
                                    errorMessage = "Mật khẩu quá yếu";
                                } else if (lowerMessage.contains("invalid email")) {
                                    errorMessage = "Email không hợp lệ";
                                } else if (lowerMessage.contains("too many requests")) {
                                    errorMessage = "Quá nhiều lần thử. Vui lòng thử lại sau";
                                } else if (lowerMessage.contains("network")) {
                                    errorMessage = "Lỗi kết nối mạng";
                                } else {
                                    errorMessage = originalMessage; // Keep original if no translation found
                                }
                            }
                        }
                        _error.setValue(errorMessage);
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
                        String errorMessage = "Đăng nhập thất bại";
                        if (task.getException() != null) {
                            String originalMessage = task.getException().getMessage();
                            if (originalMessage != null) {
                                String lowerMessage = originalMessage.toLowerCase();
                                // Translate common Firebase auth errors to Vietnamese
                                if (lowerMessage.contains("supplied auth credential is incorrect") || 
                                    lowerMessage.contains("invalid credential") || 
                                    (lowerMessage.contains("incorrect") && lowerMessage.contains("credential")) ||
                                    lowerMessage.contains("malformed") ||
                                    lowerMessage.contains("expired")) {
                                    errorMessage = "Sai tên đăng nhập hoặc mật khẩu";
                                } else if (lowerMessage.contains("user not found")) {
                                    errorMessage = "Tài khoản không tồn tại";
                                } else if (lowerMessage.contains("wrong password")) {
                                    errorMessage = "Sai mật khẩu";
                                } else if (lowerMessage.contains("invalid email")) {
                                    errorMessage = "Email không hợp lệ";
                                } else if (lowerMessage.contains("user disabled")) {
                                    errorMessage = "Tài khoản đã bị vô hiệu hóa";
                                } else if (lowerMessage.contains("too many requests")) {
                                    errorMessage = "Quá nhiều lần thử. Vui lòng thử lại sau";
                                } else if (lowerMessage.contains("network")) {
                                    errorMessage = "Lỗi kết nối mạng";
                                } else {
                                    errorMessage = originalMessage; // Keep original if no translation found
                                }
                            }
                        }
                        _error.setValue(errorMessage);
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
                        String errorMessage = "Đăng nhập Google thất bại";
                        if (task.getException() != null) {
                            String originalMessage = task.getException().getMessage();
                            if (originalMessage != null) {
                                String lowerMessage = originalMessage.toLowerCase();
                                // Translate common Firebase auth errors to Vietnamese
                                if (lowerMessage.contains("network")) {
                                    errorMessage = "Lỗi kết nối mạng";
                                } else if (lowerMessage.contains("cancelled")) {
                                    errorMessage = "Đăng nhập bị hủy";
                                } else if (lowerMessage.contains("invalid")) {
                                    errorMessage = "Lỗi xác thực Google";
                                } else if (lowerMessage.contains("account")) {
                                    errorMessage = "Lỗi tài khoản Google";
                                } else {
                                    errorMessage = originalMessage; // Keep original if no translation found
                                }
                            }
                        }
                        _error.setValue(errorMessage);
                    }
                });
    }

    public void logout() {
        repository.logout();
        _user.setValue(null);
    }
    
    public void logout(Context context) {
        repository.logout(context).addOnCompleteListener(task -> {
            _user.setValue(null);
        });
    }

    public void changePassword(String currentPassword, String newPassword) {
        _loading.setValue(true);
        _error.setValue(null);
        repository.changePassword(currentPassword, newPassword)
                .addOnCompleteListener(task -> {
                    _loading.setValue(false);
                    if (task.isSuccessful()) {
                        // Password changed successfully - let the dialog handle success message
                        _error.setValue("SUCCESS");
                    } else {
                        String errorMessage = "Đổi mật khẩu thất bại";
                        if (task.getException() != null) {
                            String originalMessage = task.getException().getMessage();
                            if (originalMessage != null) {
                                String lowerMessage = originalMessage.toLowerCase();
                                // Translate common Firebase auth errors to Vietnamese
                                if (lowerMessage.contains("wrong password") || 
                                    lowerMessage.contains("invalid credential") ||
                                    lowerMessage.contains("incorrect")) {
                                    errorMessage = "Mật khẩu hiện tại không đúng";
                                } else if (lowerMessage.contains("weak password")) {
                                    errorMessage = "Mật khẩu mới quá yếu (tối thiểu 8 ký tự, gồm chữ hoa/thường, số và ký tự đặc biệt)";
                                } else if (lowerMessage.contains("requires recent authentication")) {
                                    errorMessage = "Vui lòng đăng nhập lại để đổi mật khẩu";
                                } else if (lowerMessage.contains("too many requests")) {
                                    errorMessage = "Quá nhiều lần thử. Vui lòng thử lại sau";
                                } else if (lowerMessage.contains("network")) {
                                    errorMessage = "Lỗi kết nối mạng";
                                } else if (lowerMessage.contains("not using email/password")) {
                                    errorMessage = "Chỉ có thể đổi mật khẩu với tài khoản email/mật khẩu";
                                } else {
                                    errorMessage = originalMessage; // Keep original if no translation found
                                }
                            }
                        }
                        _error.setValue(errorMessage);
                    }
                });
    }
}


