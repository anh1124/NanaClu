package com.example.nanaclu.ui.admin;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nanaclu.R;
import com.example.nanaclu.data.repository.AdminRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {
    private AdminRepository adminRepository;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        
        adminRepository = new AdminRepository(FirebaseFirestore.getInstance());
        
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
            // Hiển thị thông báo đang xử lý
            Toast.makeText(this, "Đang xuất dữ liệu...", Toast.LENGTH_SHORT).show();
            btnSaveJson.setEnabled(false);
            
            // Gọi hàm export dữ liệu từ AdminRepository
            adminRepository.exportAllDataToJson(new AdminRepository.ExportDataCallback() {
                @Override
                public void onSuccess(String jsonData) {
                    // Lưu file JSON vào thư mục Downloads
                    saveJsonToFile(jsonData);
                    btnSaveJson.setEnabled(true);
                }
                
                @Override
                public void onError(Exception e) {
                    Toast.makeText(AdminDashboardActivity.this, "Lỗi xuất dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSaveJson.setEnabled(true);
                }
            });
        });
    }
    
    /**
     * Lưu dữ liệu JSON vào file trong thư mục Downloads
     */
    private void saveJsonToFile(String jsonData) {
        try {
            // Tạo tên file với timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = "nanaclu_backup_" + timestamp + ".json";
            
            // Lấy thư mục Downloads
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File jsonFile = new File(downloadsDir, fileName);
            
            // Ghi dữ liệu vào file
            FileWriter writer = new FileWriter(jsonFile);
            writer.write(jsonData);
            writer.close();
            
            Toast.makeText(this, "Đã lưu file: " + fileName, Toast.LENGTH_LONG).show();
            
        } catch (IOException e) {
            Toast.makeText(this, "Lỗi lưu file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
