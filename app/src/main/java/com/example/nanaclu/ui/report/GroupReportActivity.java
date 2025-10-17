package com.example.nanaclu.ui.report;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.nanaclu.R;

/**
 * Activity chứa ActiveGroupReportDashboardFragment
 * Hiển thị dashboard báo cáo cho group admin/moderator
 */
public class GroupReportActivity extends AppCompatActivity {
    
    private String groupId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_report);
        
        // Lấy groupId từ Intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            finish();
            return;
        }
        
        setupToolbar();
        setupFragment();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Quản lý báo cáo");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void setupFragment() {
        // Tạo và hiển thị ActiveGroupReportDashboardFragment
        ActiveGroupReportDashboardFragment fragment = ActiveGroupReportDashboardFragment.newInstance(groupId);
        
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
