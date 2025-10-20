package com.example.nanaclu.ui.group.statistics;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.GroupStatistics;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.repository.StatisticsRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Activity hiển thị thống kê hoạt động nhóm
 */
public class GroupStatisticsActivity extends AppCompatActivity {
    
    private String groupId;
    private String currentUserId;
    private Group currentGroup;
    
    // Repositories
    private GroupRepository groupRepository;
    private StatisticsRepository statisticsRepository;
    
    // UI Components
    private TabLayout tabPeriod;
    private View cardNavigation;
    private MaterialButton btnPrevious, btnNext;
    private TextView tvCurrentPeriod;
    private RecyclerView recyclerViewStats;
    private StatisticsAdapter statisticsAdapter;
    // Bỏ tvSummary vì không còn mục chi tiết
    
    // State management
    private String currentMetric = "posts"; // "posts", "events", "members"
    private boolean isMonthlyView = true; // true = monthly, false = yearly
    private int currentPage = 0;
    private int currentYear = Calendar.getInstance().get(Calendar.YEAR);
    
    // Data
    private List<GroupStatistics> allData = new ArrayList<>();
    private List<GroupStatistics> visibleData = new ArrayList<>();
    
    // Metrics options
    private String[] metricOptions = {"Số bài đăng", "Số sự kiện", "Số thành viên mới"};
    private String[] metricKeys = {"posts", "events", "members"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_statistics);
        
        // Get data from intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            Toast.makeText(this, "Lỗi: Không tìm thấy ID nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize repositories
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
        statisticsRepository = new StatisticsRepository(FirebaseFirestore.getInstance());
        
        setupToolbar();
        setupUI();
        checkPermissions();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thống kê hoạt động");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupUI() {
        // Initialize UI components
        tabPeriod = findViewById(R.id.tabPeriod);
        cardNavigation = findViewById(R.id.cardNavigation);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        tvCurrentPeriod = findViewById(R.id.tvCurrentPeriod);
        recyclerViewStats = findViewById(R.id.recyclerViewStats);
        
        // Setup RecyclerView
        statisticsAdapter = new StatisticsAdapter();
        recyclerViewStats.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewStats.setAdapter(statisticsAdapter);
        
        // Không cần metric tabs nữa vì hiển thị tất cả metrics trong RecyclerView
        
        // Setup period tabs
        tabPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isMonthlyView = (tab.getPosition() == 0);
                currentPage = 0;
                // Clear old data
                allData.clear();
                visibleData.clear();
                // Update adapter
                statisticsAdapter.setMonthlyView(isMonthlyView);
                statisticsAdapter.setStatisticsList(allData);
                
                // Chỉ hiển thị navigation cho tab năm
                if (isMonthlyView) {
                    cardNavigation.setVisibility(View.GONE);
                } else {
                    cardNavigation.setVisibility(View.VISIBLE);
                }
                
                loadStatistics();
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Setup navigation buttons - chỉ hoạt động cho tab năm
        btnPrevious.setOnClickListener(v -> {
            if (!isMonthlyView && currentPage > 0) {
                currentPage--;
                // Add button animation
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                updatePagination();
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (!isMonthlyView && currentPage < allData.size() - 1) {
                currentPage++;
                // Add button animation
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                updatePagination();
            }
        });
    }
    
    private void checkPermissions() {
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member member) {
                if (member == null || 
                    (!member.role.equals("owner") && !member.role.equals("admin"))) {
                    Toast.makeText(GroupStatisticsActivity.this, 
                        "Chỉ Owner và Admin mới có quyền xem thống kê", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                
                // Load group info and statistics
                loadGroupInfo();
                loadStatistics();
            }
            
            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupStatisticsActivity.this, 
                    "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    private void loadGroupInfo() {
        groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                currentGroup = group;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Thống kê: " + group.name);
                }
            }
            
            @Override
            public void onError(Exception e) {
                // Continue without group name
            }
        });
    }
    
    private void loadStatistics() {
        if (isMonthlyView) {
            loadMonthlyStatistics();
        } else {
            loadYearlyStatistics();
        }
    }
    
    private void loadMonthlyStatistics() {
        statisticsRepository.getMonthlyStatistics(groupId, currentYear, new StatisticsRepository.StatisticsCallback() {
            @Override
            public void onSuccess(List<GroupStatistics> statistics) {
                allData = statistics;
                
                // Sắp xếp tháng mới nhất lên trên cùng
                allData.sort((a, b) -> b.periodKey.compareTo(a.periodKey));
                
                updatePagination();
            }
            
            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupStatisticsActivity.this, 
                    "Lỗi tải thống kê: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // Update RecyclerView với data rỗng
                statisticsAdapter.setMonthlyView(isMonthlyView);
                statisticsAdapter.setStatisticsList(new ArrayList<>());
            }
        });
    }
    
    private void loadYearlyStatistics() {
        // Load last 5 years
        int startYear = currentYear - 4;
        statisticsRepository.getYearlyStatistics(groupId, startYear, currentYear, new StatisticsRepository.StatisticsCallback() {
            @Override
            public void onSuccess(List<GroupStatistics> statistics) {
                allData = statistics;
                // Set currentPage to current year
                currentPage = 0;
                for (int i = 0; i < allData.size(); i++) {
                    if (allData.get(i).periodKey.equals(String.valueOf(currentYear))) {
                        currentPage = i;
                        break;
                    }
                }
                updatePagination();
                // Update RecyclerView với data mới
                statisticsAdapter.setMonthlyView(isMonthlyView);
                statisticsAdapter.setStatisticsList(visibleData);
            }
            
            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupStatisticsActivity.this, 
                    "Lỗi tải thống kê: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // Update RecyclerView với data rỗng
                statisticsAdapter.setMonthlyView(isMonthlyView);
                statisticsAdapter.setStatisticsList(new ArrayList<>());
            }
        });
    }
    
    private void updatePagination() {
        if (isMonthlyView) {
            // Tab tháng: hiển thị tất cả data, không cần navigation
            cardNavigation.setVisibility(View.GONE);
            statisticsAdapter.setMonthlyView(isMonthlyView);
            statisticsAdapter.setStatisticsList(allData);
        } else {
            // Tab năm: chỉ hiển thị 1 năm duy nhất (năm đang chọn)
            int totalPeriods = allData.size();
            if (totalPeriods == 0) {
                // Không có data
                visibleData = new ArrayList<>();
                cardNavigation.setVisibility(View.GONE);
            } else {
                // Chỉ hiển thị 1 năm tại vị trí currentPage
                visibleData = new ArrayList<>();
                if (currentPage < allData.size()) {
                    visibleData.add(allData.get(currentPage));
                }
                
                // Update navigation buttons
                btnPrevious.setEnabled(currentPage > 0);
                btnNext.setEnabled(currentPage < totalPeriods - 1);
                
                // Hiển thị năm hiện tại
                if (currentPage < allData.size()) {
                    String periodKey = allData.get(currentPage).periodKey;
                    tvCurrentPeriod.setText("Năm " + periodKey);
                }
                
                cardNavigation.setVisibility(View.VISIBLE);
            }
            
            // Update RecyclerView với chỉ 1 năm
            statisticsAdapter.setMonthlyView(isMonthlyView);
            statisticsAdapter.setStatisticsList(visibleData);
        }
    }
    
    // Bỏ updateSummary vì không còn mục chi tiết
    
    private String getMetricDisplayName(String metric) {
        switch (metric) {
            case "posts":
                return "bài đăng";
            case "events":
                return "sự kiện";
            case "members":
                return "thành viên mới";
            default:
                return "hoạt động";
        }
    }
}
