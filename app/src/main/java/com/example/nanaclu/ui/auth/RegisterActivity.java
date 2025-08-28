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
    }
}


