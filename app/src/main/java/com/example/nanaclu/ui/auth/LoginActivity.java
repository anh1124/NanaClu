package com.example.nanaclu.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.R;
import com.example.nanaclu.viewmodel.AuthViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

public class LoginActivity extends AppCompatActivity {

    private AuthViewModel viewModel;
    private GoogleSignInClient googleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        EditText edtEmail = findViewById(R.id.edtEmail);
        EditText edtPassword = findViewById(R.id.edtPassword);
        CheckBox cbRemember = findViewById(R.id.cbRemember);
        TextView tvStatus = findViewById(R.id.tvStatus);
        Button btnLogin = findViewById(R.id.btnLogin);
        com.google.android.material.button.MaterialButton btnGoogle = findViewById(R.id.btnGoogle);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);

        btnLogin.setOnClickListener(v -> {
            String email = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString();
            
            // Validation
            if (email.isEmpty()) {
                edtEmail.setError("Vui lòng nhập email");
                edtEmail.requestFocus();
                return;
            }
            
            if (password.isEmpty()) {
                edtPassword.setError("Vui lòng nhập mật khẩu");
                edtPassword.requestFocus();
                return;
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.setError("Email không hợp lệ");
                edtEmail.requestFocus();
                return;
            }
            
            viewModel.loginWithEmail(email, password);
            getSharedPreferences("auth", MODE_PRIVATE).edit().putBoolean("remember_me", cbRemember.isChecked()).apply();
        });

        tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
        googleLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                com.google.android.gms.tasks.Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    viewModel.loginWithGoogleIdToken(account.getIdToken());
                    getSharedPreferences("auth", MODE_PRIVATE).edit().putBoolean("remember_me", true).apply();
                } else {
                    tvStatus.setText("Google trả về tài khoản rỗng hoặc thiếu ID token");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            } catch (ApiException e) {
                tvStatus.setText("Google Sign-In lỗi: " + e.getStatusCode());
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });

        btnGoogle.setOnClickListener(v -> {
            // Ensure account picker shows by clearing any cached signed-in account
            googleClient.signOut().addOnCompleteListener(task -> googleLauncher.launch(googleClient.getSignInIntent()));
        });

        viewModel.loading.observe(this, this::showLoading);

        viewModel.user.observe(this, user -> {
            if (user != null) {
                // Cache frequently-used user fields locally to avoid DB reads
                android.content.SharedPreferences up = getSharedPreferences("user_profile", MODE_PRIVATE);
                String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                up.edit()
                        .putString("uid", user.getUid())
                        .putString("displayName", user.getDisplayName())
                        .putString("email", user.getEmail())
                        .putString("photoUrl", photoUrl)
                        .apply();

                // Update user's photoUrl in Firestore
                com.example.nanaclu.data.repository.UserRepository userRepo =
                        new com.example.nanaclu.data.repository.UserRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
                userRepo.updateUserPhotoUrl(user.getUid(), photoUrl);

                // Reset PIN when user logs in (different user or re-login)
                android.content.SharedPreferences securityPrefs = getSharedPreferences("security", MODE_PRIVATE);
                String lastLoggedInUser = securityPrefs.getString("last_user_id", "");
                if (!user.getUid().equals(lastLoggedInUser)) {
                    // Different user or first login - reset PIN
                    securityPrefs.edit()
                            .putBoolean("pin_enabled", false)
                            .remove("pin_hash")
                            .putString("last_user_id", user.getUid())
                            .apply();
                }

                tvStatus.setText("Đăng nhập thành công");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                startActivity(new Intent(this, com.example.nanaclu.ui.HomeActivity.class));
                finish();
            }
        });

        viewModel.error.observe(this, err -> {
            if (err != null) {
                tvStatus.setText(err);
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });
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


