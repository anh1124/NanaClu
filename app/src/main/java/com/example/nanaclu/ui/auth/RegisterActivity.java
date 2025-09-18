package com.example.nanaclu.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.R;
import com.example.nanaclu.viewmodel.AuthViewModel;

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
            viewModel.registerWithEmail(
                    edtEmail.getText().toString().trim(),
                    edtPassword.getText().toString(),
                    edtDisplayName.getText().toString().trim()
            );
        });

        tvGoLogin.setOnClickListener(v -> finish());

        viewModel.user.observe(this, user -> {
            if (user != null) {
                tvStatus.setText("Đăng ký thành công");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                finish();
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


