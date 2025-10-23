package com.example.nanaclu.ui.security;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.R;
import com.example.nanaclu.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ChangePasswordDialog extends Dialog {
    
    public interface ChangePasswordCallback {
        void onPasswordChanged();
        void onCancel();
    }
    
    private ChangePasswordCallback callback;
    private AuthViewModel authViewModel;
    private TextInputEditText edtCurrentPassword;
    private TextInputEditText edtNewPassword;
    private TextInputEditText edtConfirmPassword;
    private TextView tvErrorMessage;
    private LinearLayout progressContainer;
    private ProgressBar progressBar;
    private MaterialButton btnChangePassword;
    private MaterialButton btnCancel;
    private androidx.lifecycle.LifecycleOwner lifecycleOwner;
    private boolean isSuccessHandled = false;
    
    public ChangePasswordDialog(@NonNull Context context, ChangePasswordCallback callback) {
        super(context);
        this.callback = callback;
        
        // Find the activity from context
        if (context instanceof androidx.appcompat.app.AppCompatActivity) {
            this.lifecycleOwner = (androidx.lifecycle.LifecycleOwner) context;
        } else if (context instanceof android.content.ContextWrapper) {
            android.content.Context baseContext = ((android.content.ContextWrapper) context).getBaseContext();
            if (baseContext instanceof androidx.appcompat.app.AppCompatActivity) {
                this.lifecycleOwner = (androidx.lifecycle.LifecycleOwner) baseContext;
            }
        }
        
        if (lifecycleOwner != null) {
            this.authViewModel = new ViewModelProvider((androidx.lifecycle.ViewModelStoreOwner) lifecycleOwner).get(AuthViewModel.class);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_change_password);
        
        initViews();
        setupListeners();
        observeViewModel();
    }
    
    private void initViews() {
        edtCurrentPassword = findViewById(R.id.edtCurrentPassword);
        edtNewPassword = findViewById(R.id.edtNewPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        progressContainer = findViewById(R.id.progressContainer);
        progressBar = findViewById(R.id.progressBar);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnCancel = findViewById(R.id.btnCancel);
    }
    
    private void setupListeners() {
        btnCancel.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCancel();
            }
            dismiss();
        });
        
        btnChangePassword.setOnClickListener(v -> {
            if (validateInputs()) {
                changePassword();
            }
        });
    }
    
    private void observeViewModel() {
        if (lifecycleOwner != null && authViewModel != null) {
            authViewModel.loading.observe(lifecycleOwner, isLoading -> {
                if (isLoading != null) {
                    progressContainer.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                    btnChangePassword.setEnabled(!isLoading);
                    btnCancel.setEnabled(!isLoading);
                }
            });
            
            authViewModel.error.observe(lifecycleOwner, errorMessage -> {
                if (errorMessage != null && !isSuccessHandled) {
                    if (errorMessage.equals("SUCCESS")) {
                        // Success - let SecurityActivity handle the toast
                        android.util.Log.d("PasswordChange", "SUCCESS detected, calling callback");
                        isSuccessHandled = true;
                        if (callback != null) {
                            callback.onPasswordChanged();
                        }
                        dismiss();
                    } else {
                        // Error message
                        tvErrorMessage.setText(errorMessage);
                        tvErrorMessage.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }
    
    private boolean validateInputs() {
        String currentPassword = edtCurrentPassword.getText().toString().trim();
        String newPassword = edtNewPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();
        
        // Clear previous error
        tvErrorMessage.setVisibility(View.GONE);
        
        // Validate current password
        if (TextUtils.isEmpty(currentPassword)) {
            showError("Vui lòng nhập mật khẩu hiện tại");
            edtCurrentPassword.requestFocus();
            return false;
        }
        
        // Validate new password
        if (TextUtils.isEmpty(newPassword)) {
            showError("Vui lòng nhập mật khẩu mới");
            edtNewPassword.requestFocus();
            return false;
        }
        
        if (newPassword.length() < 6) {
            showError("Mật khẩu mới phải có ít nhất 6 ký tự");
            edtNewPassword.requestFocus();
            return false;
        }
        
        // Validate confirm password
        if (TextUtils.isEmpty(confirmPassword)) {
            showError("Vui lòng xác nhận mật khẩu mới");
            edtConfirmPassword.requestFocus();
            return false;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            showError("Mật khẩu mới và xác nhận không khớp");
            edtConfirmPassword.requestFocus();
            return false;
        }
        
        // Check if new password is different from current
        if (currentPassword.equals(newPassword)) {
            showError("Mật khẩu mới phải khác mật khẩu hiện tại");
            edtNewPassword.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
    
    private void changePassword() {
        String currentPassword = edtCurrentPassword.getText().toString().trim();
        String newPassword = edtNewPassword.getText().toString().trim();
        
        if (authViewModel != null) {
            authViewModel.changePassword(currentPassword, newPassword);
        }
    }
}