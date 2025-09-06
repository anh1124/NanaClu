package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;

public class GroupSettingsActivity extends AppCompatActivity {

    private String groupId;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);

        // Lấy dữ liệu từ Intent
        groupId = getIntent().getStringExtra("groupId");
        currentUserId = getIntent().getStringExtra("currentUserId");

        setupToolbar();
        setupUI();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Cài đặt nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        // TODO: Thêm các chức năng cài đặt nhóm:
        // - Đổi tên nhóm
        // - Đổi mô tả nhóm
        // - Đổi ảnh đại diện
        // - Đổi ảnh bìa
        // - Cài đặt quyền riêng tư (public/private)
        // - Xóa nhóm
        // - Chuyển quyền owner
        
        Toast.makeText(this, "Chức năng cài đặt nhóm đang được phát triển", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
