package com.example.nanaclu.ui.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.viewmodel.NoticeViewModelFactory;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Notice;
import com.example.nanaclu.ui.adapter.NoticeAdapter;

import java.util.List;
import com.example.nanaclu.utils.NoticeCenter;
import com.example.nanaclu.utils.ThemeUtils;
import com.example.nanaclu.viewmodel.NoticeViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class NotificationsActivity extends AppCompatActivity {
    private NoticeViewModel viewModel;
    private NoticeAdapter adapter;
    private RecyclerView rvNotifications;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private FloatingActionButton fabMarkAllSeen;
    private NoticeCenter noticeCenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        noticeCenter = NoticeCenter.getInstance();
        
        // Get current user ID
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            
        // Initialize repository and factory
        NoticeRepository noticeRepository = new NoticeRepository(FirebaseFirestore.getInstance());
        NoticeViewModelFactory factory = new NoticeViewModelFactory(noticeRepository, currentUid);
        viewModel = new ViewModelProvider(this, factory).get(NoticeViewModel.class);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        observeViewModel();
        
        // Start listening for realtime updates
        viewModel.startListening();
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.startListening();
    }

    /**
     * Manual refresh method to force reload from Firestore
     */
    public void refreshFromFirestore() {
        android.util.Log.d("NotificationsActivity", "Manual refresh from Firestore");
        viewModel.refresh();
    }

    private void initViews() {
        rvNotifications = findViewById(R.id.rvNotifications);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);
        fabMarkAllSeen = findViewById(R.id.fabMarkAllSeen);
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thông báo");
        }

        // Apply theme color
        int color = ThemeUtils.getThemeColor(this);
        toolbar.setBackgroundColor(color);
    }

    private void setupRecyclerView() {
        adapter = new NoticeAdapter(new NoticeAdapter.NoticeClickListener() {
            @Override
            public void onNoticeClick(Notice notice) {
                // Đánh dấu đã xem trước khi navigate
                if (!notice.isSeen()) {
                    if (notice.getId().startsWith("grouped_")) {
                        // Thông báo nhóm - mark seen tất cả thông báo gốc
                        String originalIds = notice.getTargetUserId();
                        if (originalIds != null && !originalIds.isEmpty()) {
                            String[] ids = originalIds.split(",");
                            for (String id : ids) {
                                viewModel.markSeen(id.trim());
                            }
                        }
                    } else {
                        // Thông báo thật - mark seen bình thường
                        viewModel.markSeen(notice.getId());
                    }
                    
                    // Cập nhật UI ngay lập tức
                    notice.setSeen(true);
                    adapter.notifyItemChanged(adapter.getCurrentList().indexOf(notice));
                }
                
                // Navigate đến màn hình đích
                noticeCenter.navigateToTarget(NotificationsActivity.this, notice);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvNotifications.setLayoutManager(layoutManager);
        rvNotifications.setAdapter(adapter);

        // Pagination scroll listener
        rvNotifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (dy > 0) { // Scrolling down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 2) {
                        // Load more when near bottom
                        viewModel.loadMore();
                    }
                }
            }
        });
    }

    private void setupClickListeners() {
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
        });

        fabMarkAllSeen.setOnClickListener(v -> {
            // Log current notices count
            List<Notice> currentNotices = adapter.getCurrentList();
            int totalCount = currentNotices != null ? currentNotices.size() : 0;
            android.util.Log.d("NotificationsActivity", "FAB clicked. Total notices=" + totalCount);

            // Show loading state
            android.widget.Toast.makeText(this, "Đang xóa thông báo...", android.widget.Toast.LENGTH_SHORT).show();

            // Delete all notifications from subcollection
            viewModel.deleteAllNotifications();

            // Update global badge
            noticeCenter.markAllSeen();
            
            // Show success message after a delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                android.widget.Toast.makeText(this, "Đã xóa tất cả thông báo", android.widget.Toast.LENGTH_SHORT).show();
            }, 1000);
        });
    }

    private void observeViewModel() {
        viewModel.notices.observe(this, notices -> {
            adapter.setNotices(notices);
            updateEmptyState(notices);
        });

        viewModel.isLoading.observe(this, isLoading -> {
            if (isLoading) {
                progressBar.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.error.observe(this, error -> {
            if (error != null) {
                android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEmptyState(java.util.List<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ViewModel sẽ tự cleanup
    }
}
