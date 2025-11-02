package com.example.nanaclu.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.R;
import com.example.nanaclu.viewmodel.AuthViewModel;
import com.google.firebase.auth.FirebaseAuth;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        EditText edtDisplayName = findViewById(R.id.edtDisplayName);
        EditText edtEmail = findViewById(R.id.edtEmail);
        EditText edtPassword = findViewById(R.id.edtPassword);
        android.widget.TextView tvStatus = findViewById(R.id.tvStatus);
        Button btnRegister = findViewById(R.id.btnRegister);
        android.widget.TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        btnRegister.setOnClickListener(v -> {
            String displayName = edtDisplayName.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString();

            if (displayName.isEmpty()) {
                edtDisplayName.setError("Vui lòng nhập tên hiển thị");
                edtDisplayName.requestFocus();
                return;
            }

            if (email.isEmpty()) {
                edtEmail.setError("Vui lòng nhập email");
                edtEmail.requestFocus();
                return;
            }

            if (email.length() < 6 || email.length() > 254) {
                edtEmail.setError("Độ dài email không hợp lệ (6-254 ký tự)");
                edtEmail.requestFocus();
                return;
            }

            Pattern strictEmail = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
            if (!strictEmail.matcher(email).matches()) {
                edtEmail.setError("Email chứa ký tự không hợp lệ");
                edtEmail.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                edtPassword.setError("Vui lòng nhập mật khẩu");
                edtPassword.requestFocus();
                return;
            }

            if (password.length() > 128) {
                edtPassword.setError("Mật khẩu không được vượt quá 128 ký tự");
                edtPassword.requestFocus();
                return;
            }

            if (password.length() < 8) {
                edtPassword.setError("Mật khẩu phải có ít nhất 8 ký tự");
                edtPassword.requestFocus();
                return;
            }

            Pattern pwPattern = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$");
            if (!pwPattern.matcher(password).matches()) {
                edtPassword.setError("Mật khẩu phải chứa chữ thường, chữ hoa, số và ký tự đặc biệt");
                edtPassword.requestFocus();
                return;
            }

            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            if (!local.isEmpty()) {
                String pwLower = password.toLowerCase();
                if (pwLower.contains(local.toLowerCase()) || pwLower.contains(displayName.toLowerCase())) {
                    edtPassword.setError("Mật khẩu không được chứa tên hiển thị hoặc phần tên email");
                    edtPassword.requestFocus();
                    return;
                }
            }

            viewModel.registerWithEmail(email, password, displayName);
        });

        tvGoLogin.setOnClickListener(v -> finish());

        viewModel.user.observe(this, user -> {
            if (user != null) {
                FirebaseAuth.getInstance().getCurrentUser().sendEmailVerification()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                tvStatus.setText("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản");
                                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            } else {
                                tvStatus.setText("Đăng ký thành công nhưng gửi email xác thực thất bại");
                                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            }
                        });
            }
        });

        viewModel.error.observe(this, err -> {
            if (err != null) {
                tvStatus.setText(err);
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });
        viewModel.loading.observe(this, this::showLoading);

    }

    private android.app.Dialog loadingDialog;
    private void showLoading(boolean show) {
        if (show) {
            if (loadingDialog == null) {
                loadingDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
                android.widget.FrameLayout root = new android.widget.FrameLayout(this);
                root.setBackgroundColor(0x88000000);
                root.setClickable(true);
                android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.CENTER;
                root.addView(pb, lp);
                loadingDialog.setContentView(root);
                loadingDialog.setCancelable(false);
            }
            if (!loadingDialog.isShowing()) loadingDialog.show();
        } else if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}


