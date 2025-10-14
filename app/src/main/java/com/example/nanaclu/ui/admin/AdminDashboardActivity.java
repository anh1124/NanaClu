package com.example.nanaclu.ui.admin;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nanaclu.R;
import com.google.android.material.appbar.MaterialToolbar;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("admin dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        // Đặt màu trắng cho icon quay lại
        toolbar.getNavigationIcon().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
        Button btnSaveJson = findViewById(R.id.btnSaveJson);
        btnSaveJson.setOnClickListener(v -> {
            Toast.makeText(this, "Chức năng chưa khả dụng", Toast.LENGTH_SHORT).show();
        });
    }
}
