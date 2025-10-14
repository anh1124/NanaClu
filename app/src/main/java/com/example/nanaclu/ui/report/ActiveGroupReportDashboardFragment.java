package com.example.nanaclu.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.ReportModel;
import com.example.nanaclu.data.repository.ReportRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment hiển thị dashboard báo cáo cho group admin/moderator
 * Có 2 tab: Pending Reports và Handled Reports
 */
public class ActiveGroupReportDashboardFragment extends Fragment {
    
    private static final String ARG_GROUP_ID = "group_id";
    
    private String groupId;
    private ReportRepository reportRepository;
    
    private TabLayout tabLayout;
    private RecyclerView rvReports;
    private TextView tvEmptyState;
    private ReportListAdapter adapter;
    
    private List<ReportModel> pendingReports = new ArrayList<>();
    private List<ReportModel> handledReports = new ArrayList<>();
    
    /**
     * Tạo instance mới của fragment với groupId
     */
    public static ActiveGroupReportDashboardFragment newInstance(String groupId) {
        ActiveGroupReportDashboardFragment fragment = new ActiveGroupReportDashboardFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString(ARG_GROUP_ID);
        }
        
        reportRepository = new ReportRepository(FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_dashboard, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupTabLayout();
        setupRecyclerView();
        loadInitialData();
    }
    
    /**
     * Khởi tạo các view components
     */
    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tabLayout);
        rvReports = view.findViewById(R.id.rvReports);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
    }
    
    /**
     * Thiết lập TabLayout với 2 tab: Pending và Handled
     */
    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Chờ xử lý"));
        tabLayout.addTab(tabLayout.newTab().setText("Đã xử lý"));
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        showPendingReports();
                        break;
                    case 1:
                        showHandledReports();
                        break;
                }
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    /**
     * Thiết lập RecyclerView với adapter
     */
    private void setupRecyclerView() {
        adapter = new ReportListAdapter(new ArrayList<>(), this::onReportItemClicked);
        rvReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReports.setAdapter(adapter);
    }
    
    /**
     * Load dữ liệu ban đầu cho cả 2 tab
     */
    private void loadInitialData() {
        android.util.Log.d("ReportDashboard", "Loading initial data for group: " + groupId);
        loadPendingReports();
        loadHandledReports();
    }
    
    /**
     * Load danh sách báo cáo đang chờ xử lý
     */
    private void loadPendingReports() {
        android.util.Log.d("ReportDashboard", "Loading pending reports for group: " + groupId);
        reportRepository.fetchGroupReports(groupId, "pending", new ReportRepository.Callback<List<ReportModel>>() {
            @Override
            public void onSuccess(List<ReportModel> reports) {
                android.util.Log.d("ReportDashboard", "Loaded " + (reports != null ? reports.size() : 0) + " pending reports");
                pendingReports.clear();
                if (reports != null) {
                    pendingReports.addAll(reports);
                }
                
                // Nếu đang ở tab pending, cập nhật UI
                if (tabLayout.getSelectedTabPosition() == 0) {
                    showPendingReports();
                }
            }
            
            @Override
            public void onError(Exception e) {
                android.util.Log.e("ReportDashboard", "Error loading pending reports", e);
                // Hiển thị lỗi cho user
                if (getContext() != null) {
                    android.widget.Toast.makeText(getContext(), "Lỗi tải báo cáo: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    /**
     * Load danh sách báo cáo đã xử lý (tất cả trừ pending)
     */
    private void loadHandledReports() {
        reportRepository.fetchGroupReports(groupId, null, new ReportRepository.Callback<List<ReportModel>>() {
            @Override
            public void onSuccess(List<ReportModel> allReports) {
                handledReports.clear();
                if (allReports != null) {
                    // Filter ra các report đã xử lý (không phải pending)
                    for (ReportModel report : allReports) {
                        if (!"pending".equals(report.status)) {
                            handledReports.add(report);
                        }
                    }
                }
                
                // Nếu đang ở tab handled, cập nhật UI
                if (tabLayout.getSelectedTabPosition() == 1) {
                    showHandledReports();
                }
            }
            
            @Override
            public void onError(Exception e) {
                android.util.Log.e("ReportDashboard", "Error loading handled reports", e);
            }
        });
    }
    
    /**
     * Hiển thị danh sách báo cáo đang chờ xử lý
     */
    private void showPendingReports() {
        adapter.setItems(pendingReports);
        updateEmptyState(pendingReports.isEmpty());
    }
    
    /**
     * Hiển thị danh sách báo cáo đã xử lý
     */
    private void showHandledReports() {
        adapter.setItems(handledReports);
        updateEmptyState(handledReports.isEmpty());
    }
    
    /**
     * Cập nhật trạng thái empty state
     */
    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            rvReports.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            
            if (tabLayout.getSelectedTabPosition() == 0) {
                tvEmptyState.setText("Không có báo cáo nào đang chờ xử lý");
            } else {
                tvEmptyState.setText("Không có báo cáo nào đã được xử lý");
            }
        } else {
            rvReports.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
        }
    }
    
    /**
     * Xử lý khi click vào một report item
     */
    private void onReportItemClicked(ReportModel report) {
        // Mở ReportDetailFragment trong cùng Activity
        ReportDetailFragment detailFragment = ReportDetailFragment.newInstance(groupId, report.reportId);
        
        requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack("report_detail")
            .commit();
    }
    
    /**
     * Refresh dữ liệu khi fragment được hiển thị lại
     */
    @Override
    public void onResume() {
        super.onResume();
        loadInitialData();
    }
}
