package com.example.nanaclu.ui.admin;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nanaclu.R;
import com.example.nanaclu.utils.EventAttendeesCleanup;

public class DatabaseCleanupActivity extends AppCompatActivity {
    
    private TextView tvStatus;
    private Button btnCleanupAll, btnCleanupGroup, btnCleanupEvent;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_cleanup);
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        btnCleanupAll = findViewById(R.id.btnCleanupAll);
        btnCleanupGroup = findViewById(R.id.btnCleanupGroup);
        btnCleanupEvent = findViewById(R.id.btnCleanupEvent);
    }
    
    private void setupListeners() {
        btnCleanupAll.setOnClickListener(v -> {
            // Lưu ý: Chỉ dùng cho testing, không nên chạy trên production
            tvStatus.setText("Đang dọn dẹp tất cả groups...");
            btnCleanupAll.setEnabled(false);
            
            // TODO: Implement cleanup for all groups if needed
            Toast.makeText(this, "Chức năng này cần được implement cẩn thận", Toast.LENGTH_LONG).show();
            btnCleanupAll.setEnabled(true);
        });
        
        btnCleanupGroup.setOnClickListener(v -> {
            String groupId = "8ab93a7e-964d-4f29-bc22-335f331a9f18"; // Replace with actual group ID
            tvStatus.setText("Đang dọn dẹp group: " + groupId);
            btnCleanupGroup.setEnabled(false);
            
            EventAttendeesCleanup.cleanupGroupEventAttendees(groupId)
                    .addOnSuccessListener(aVoid -> {
                        tvStatus.setText("Dọn dẹp group thành công!");
                        Toast.makeText(this, "Dọn dẹp group thành công", Toast.LENGTH_SHORT).show();
                        btnCleanupGroup.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        tvStatus.setText("Lỗi dọn dẹp group: " + e.getMessage());
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnCleanupGroup.setEnabled(true);
                    });
        });
        
        btnCleanupEvent.setOnClickListener(v -> {
            String groupId = "8ab93a7e-964d-4f29-bc22-335f331a9f18"; // Replace with actual group ID
            String eventId = "rlC9a1zI3f5v85scBqJw"; // Replace with actual event ID
            tvStatus.setText("Đang dọn dẹp event: " + eventId);
            btnCleanupEvent.setEnabled(false);
            
            EventAttendeesCleanup.cleanupEventAttendees(groupId, eventId)
                    .addOnSuccessListener(aVoid -> {
                        tvStatus.setText("Dọn dẹp event thành công!");
                        Toast.makeText(this, "Dọn dẹp event thành công", Toast.LENGTH_SHORT).show();
                        btnCleanupEvent.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        tvStatus.setText("Lỗi dọn dẹp event: " + e.getMessage());
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnCleanupEvent.setEnabled(true);
                    });
        });
    }
}
