package com.example.nanaclu.ui.admin;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
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
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private AdminRepository adminRepository;
    private Button btnSaveJson;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        
        // Log thông tin quyền
        android.util.Log.d("AdminExport", "Checking permissions...");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int writePermission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int readPermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            android.util.Log.d("AdminExport", "WRITE_EXTERNAL_STORAGE permission: " + 
                (writePermission == android.content.pm.PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            android.util.Log.d("AdminExport", "READ_EXTERNAL_STORAGE permission: " + 
                (readPermission == android.content.pm.PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
        
        adminRepository = new AdminRepository(FirebaseFirestore.getInstance());
        
        btnSaveJson = findViewById(R.id.btnSaveJson);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        
        // Set up save JSON button click listener
        btnSaveJson.setOnClickListener(v -> checkStoragePermission());
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
     * Kiểm tra quyền truy cập bộ nhớ
     */
    private void checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                // Nếu chưa có quyền, yêu cầu
                requestPermissions(
                    new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE
                );
            } else {
                // Đã có quyền, tiến hành export
                exportDataToJson();
            }
        } else {
            // Dưới Android 6.0 không cần yêu cầu quyền runtime
            exportDataToJson();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Quyền đã được cấp
                exportDataToJson();
            } else {
                Toast.makeText(this, "Cần cấp quyền truy cập bộ nhớ để lưu file", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Bắt đầu quá trình export dữ liệu
     */
    private void exportDataToJson() {
        btnSaveJson.setEnabled(false);
        Toast.makeText(this, "Đang xuất dữ liệu...", Toast.LENGTH_SHORT).show();
        
        adminRepository.exportAllDataToJson(new AdminRepository.ExportDataCallback() {
            @Override
            public void onSuccess(String jsonData) {
                saveJsonToFile(jsonData);
                btnSaveJson.setEnabled(true);
            }
            
            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AdminDashboardActivity.this, 
                        "Lỗi xuất dữ liệu: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    btnSaveJson.setEnabled(true);
                });
            }
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
            
            // Tạo thư mục Download/nanaclu trong bộ nhớ trong
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File nanacluDir = new File(downloadDir, "nanaclu");
            
            if (!nanacluDir.exists()) {
                boolean created = nanacluDir.mkdirs();
                android.util.Log.d("AdminExport", "Created nanaclu directory: " + created);
            }
            
            // Debug: Log thông tin thư mục
            android.util.Log.d("AdminExport", "Download directory: " + downloadDir.getAbsolutePath());
            android.util.Log.d("AdminExport", "Nanaclu directory path: " + nanacluDir.getAbsolutePath());
            android.util.Log.d("AdminExport", "Can read: " + nanacluDir.canRead());
            android.util.Log.d("AdminExport", "Can write: " + nanacluDir.canWrite());
            android.util.Log.d("AdminExport", "Exists: " + nanacluDir.exists());
            
            // Đặt thư mục đích là thư mục nanaclu vừa tạo
            File exportDir = nanacluDir;
            
            File jsonFile = new File(exportDir, fileName);
            android.util.Log.d("AdminExport", "File will be saved to: " + jsonFile.getAbsolutePath());
            
            // Ghi dữ liệu vào file
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonData);
                android.util.Log.d("AdminExport", "File written successfully");
                
                // Kiểm tra file sau khi ghi
                android.util.Log.d("AdminExport", "File exists after write: " + jsonFile.exists());
                android.util.Log.d("AdminExport", "File size: " + (jsonFile.exists() ? jsonFile.length() : 0) + " bytes");
                
                String successMsg = "Đã lưu file: " + fileName + "\nĐường dẫn: " + jsonFile.getAbsolutePath();
                // Mở thư mục chứa file
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                android.net.Uri uri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    jsonFile
                );
                intent.setDataAndType(uri, "*/*");
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                
                // Hiển thị thông báo
                Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
                
            } catch (IOException e) {
                android.util.Log.e("AdminExport", "Error writing file", e);
                throw e;
            }
            
        } catch (Exception e) {
            String errorMsg = "Lỗi lưu file: " + e.getMessage();
            android.util.Log.e("AdminExport", errorMsg, e);
            
            // Thêm thông tin chi tiết lỗi
            errorMsg += "\n\nChi tiết lỗi:\n";
            errorMsg += "- Message: " + e.getMessage() + "\n";
            if (e.getCause() != null) {
                errorMsg += "- Cause: " + e.getCause().getMessage() + "\n";
            }
            errorMsg += "\nVui lòng kiểm tra quyền ghi bộ nhớ ngoài (External Storage) trong cài đặt ứng dụng.";
            
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }
    }
}
