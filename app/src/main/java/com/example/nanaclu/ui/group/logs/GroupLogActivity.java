package com.example.nanaclu.ui.group.logs;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.GroupLog;
import com.example.nanaclu.data.repository.LogRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupLogActivity extends AppCompatActivity implements GroupLogAdapter.OnLogClickListener {

    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 100;
    private static final int PAGE_SIZE = 20;

    private String groupId;
    private LogRepository logRepository;
    private GroupLogAdapter adapter;
    private List<GroupLog> logs = new ArrayList<>();
    private DocumentSnapshot lastDoc;
    private boolean isLoading = false;
    private boolean hasMoreData = true;

    // Filter components
    private AutoCompleteTextView etActorFilter;
    private AutoCompleteTextView etTypeFilter;
    private LogRepository.LogFilters currentFilters = new LogRepository.LogFilters();

    // UI components
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_logs);

        // Get group ID from intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            Toast.makeText(this, "Lỗi: Không tìm thấy ID nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize repository
        logRepository = new LogRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadInitialData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Nhật ký hoạt động");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        etActorFilter = findViewById(R.id.etActorFilter);
        etTypeFilter = findViewById(R.id.etTypeFilter);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupLogAdapter(logs, this, this);
        recyclerView.setAdapter(adapter);

        // Setup pagination
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isLoading && hasMoreData && dy > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                            loadMoreData();
                        }
                    }
                }
            }
        });

        // Setup filter buttons
        findViewById(R.id.btnApplyFilter).setOnClickListener(v -> applyFilters());
        findViewById(R.id.btnClearFilter).setOnClickListener(v -> clearFilters());

        // Setup type filter dropdown
        setupTypeFilter();

        // Load actors for filter
        loadActorsForFilter();
    }

    private void setupTypeFilter() {
        String[] logTypes = {
            "Tất cả",
            "Tạo bài viết",
            "Xóa bài viết", 
            "Bình luận",
            "Tạo sự kiện",
            "Hủy sự kiện",
            "RSVP sự kiện",
            "Cập nhật nhóm",
            "Cập nhật ảnh nhóm",
            "Duyệt thành viên",
            "Từ chối thành viên",
            "Xóa thành viên",
            "Chặn thành viên",
            "Bỏ chặn thành viên",
            "Chuyển quyền sở hữu",
            "Thay đổi quyền",
            "Thay đổi chính sách",
            "Xóa nhóm"
        };

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_dropdown_item_1line, logTypes);
        etTypeFilter.setAdapter(typeAdapter);
        etTypeFilter.setText("Tất cả", false);
    }

    private void loadActorsForFilter() {
        logRepository.getDistinctActors(groupId, new LogRepository.ActorsCallback() {
            @Override
            public void onSuccess(List<Map<String, String>> actors) {
                List<String> actorNames = new ArrayList<>();
                actorNames.add("Tất cả");
                for (Map<String, String> actor : actors) {
                    actorNames.add(actor.get("name"));
                }
                
                ArrayAdapter<String> actorAdapter = new ArrayAdapter<>(GroupLogActivity.this,
                        android.R.layout.simple_dropdown_item_1line, actorNames);
                etActorFilter.setAdapter(actorAdapter);
                etActorFilter.setText("Tất cả", false);
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("GroupLogActivity", "Failed to load actors", e);
            }
        });
    }

    private void loadInitialData() {
        showLoading(true);
        logs.clear();
        lastDoc = null;
        hasMoreData = true;
        loadMoreData();
    }

    private void loadMoreData() {
        if (isLoading || !hasMoreData) return;

        isLoading = true;
        showLoading(true);

        if (hasActiveFilters()) {
            logRepository.queryLogs(groupId, currentFilters, PAGE_SIZE, lastDoc, new LogRepository.LogsCallback() {
                @Override
                public void onSuccess(List<GroupLog> newLogs, DocumentSnapshot newLastDoc) {
                    handleLoadSuccess(newLogs, newLastDoc);
                }

                @Override
                public void onError(Exception e) {
                    handleLoadError(e);
                }
            });
        } else {
            logRepository.getGroupLogs(groupId, PAGE_SIZE, lastDoc, new LogRepository.LogsCallback() {
                @Override
                public void onSuccess(List<GroupLog> newLogs, DocumentSnapshot newLastDoc) {
                    handleLoadSuccess(newLogs, newLastDoc);
                }

                @Override
                public void onError(Exception e) {
                    handleLoadError(e);
                }
            });
        }
    }

    private void handleLoadSuccess(List<GroupLog> newLogs, DocumentSnapshot newLastDoc) {
        isLoading = false;
        showLoading(false);

        if (lastDoc == null) {
            // Initial load
            logs.clear();
            logs.addAll(newLogs);
            adapter.updateLogs(logs);
        } else {
            // Load more
            logs.addAll(newLogs);
            adapter.addLogs(newLogs);
        }

        lastDoc = newLastDoc;
        hasMoreData = newLogs.size() == PAGE_SIZE;

        updateEmptyState();
    }

    private void handleLoadError(Exception e) {
        isLoading = false;
        showLoading(false);
        Toast.makeText(this, "Lỗi khi tải nhật ký: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (logs.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean hasActiveFilters() {
        return (currentFilters.actorId != null && !currentFilters.actorId.isEmpty()) ||
               (currentFilters.type != null && !currentFilters.type.isEmpty()) ||
               currentFilters.dateRange != null;
    }

    private void applyFilters() {
        // Reset pagination
        lastDoc = null;
        hasMoreData = true;

        // Apply actor filter
        String selectedActor = etActorFilter.getText().toString();
        if ("Tất cả".equals(selectedActor)) {
            currentFilters.actorId = null;
        } else {
            // Find actor ID by name (simplified - in real app, you'd maintain a mapping)
            currentFilters.actorId = null; // TODO: Implement actor name to ID mapping
        }

        // Apply type filter
        String selectedType = etTypeFilter.getText().toString();
        if ("Tất cả".equals(selectedType)) {
            currentFilters.type = null;
        } else {
            currentFilters.type = mapTypeNameToType(selectedType);
        }

        // Reload data
        loadInitialData();
    }

    private String mapTypeNameToType(String typeName) {
        switch (typeName) {
            case "Tạo bài viết": return GroupLog.TYPE_POST_CREATED;
            case "Xóa bài viết": return GroupLog.TYPE_POST_DELETED;
            case "Bình luận": return GroupLog.TYPE_COMMENT_ADDED;
            case "Tạo sự kiện": return GroupLog.TYPE_EVENT_CREATED;
            case "Hủy sự kiện": return GroupLog.TYPE_EVENT_CANCELLED;
            case "RSVP sự kiện": return GroupLog.TYPE_EVENT_RSVP;
            case "Cập nhật nhóm": return GroupLog.TYPE_GROUP_UPDATED;
            case "Cập nhật ảnh nhóm": return GroupLog.TYPE_GROUP_IMAGE_UPDATED;
            case "Duyệt thành viên": return GroupLog.TYPE_MEMBER_APPROVED;
            case "Từ chối thành viên": return GroupLog.TYPE_MEMBER_REJECTED;
            case "Xóa thành viên": return GroupLog.TYPE_MEMBER_REMOVED;
            case "Chặn thành viên": return GroupLog.TYPE_MEMBER_BLOCKED;
            case "Bỏ chặn thành viên": return GroupLog.TYPE_MEMBER_UNBLOCKED;
            case "Chuyển quyền sở hữu": return GroupLog.TYPE_OWNERSHIP_TRANSFERRED;
            case "Thay đổi quyền": return GroupLog.TYPE_ROLE_CHANGED;
            case "Thay đổi chính sách": return GroupLog.TYPE_POLICY_CHANGED;
            case "Xóa nhóm": return GroupLog.TYPE_GROUP_DELETED;
            default: return null;
        }
    }

    private void clearFilters() {
        etActorFilter.setText("Tất cả", false);
        etTypeFilter.setText("Tất cả", false);
        currentFilters = new LogRepository.LogFilters();
        loadInitialData();
    }

    @Override
    public void onLogClick(GroupLog log) {
        // Navigate to related content based on targetType and targetId
        // This is a simplified implementation - you can expand based on your needs
        switch (log.targetType) {
            case GroupLog.TARGET_POST:
                // Navigate to post detail
                Toast.makeText(this, "Xem chi tiết bài viết: " + log.targetId, Toast.LENGTH_SHORT).show();
                break;
            case GroupLog.TARGET_EVENT:
                // Navigate to event detail
                Toast.makeText(this, "Xem chi tiết sự kiện: " + log.targetId, Toast.LENGTH_SHORT).show();
                break;
            case GroupLog.TARGET_MEMBER:
                // Navigate to member profile
                Toast.makeText(this, "Xem thông tin thành viên: " + log.targetId, Toast.LENGTH_SHORT).show();
                break;
            case GroupLog.TARGET_GROUP:
            case GroupLog.TARGET_SETTINGS:
                // Navigate to group settings
                Toast.makeText(this, "Xem cài đặt nhóm", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "Không thể xem chi tiết cho loại này", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_logs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_export_json) {
            exportToJson();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportToJson() {
        // Check storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_WRITE_STORAGE);
            return;
        }

        performExport();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performExport();
            } else {
                Toast.makeText(this, "Cần quyền ghi file để xuất dữ liệu", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performExport() {
        showLoading(true);
        
        logRepository.exportLogsToJson(groupId, currentFilters, new LogRepository.ExportCallback() {
            @Override
            public void onSuccess(String jsonString) {
                showLoading(false);
                saveAndShareJson(jsonString);
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(GroupLogActivity.this, "Lỗi khi xuất dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAndShareJson(String jsonString) {
        try {
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "group_" + groupId + "_logs_" + timestamp + ".json";

            // Save to Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);

            FileWriter writer = new FileWriter(file);
            writer.write(jsonString);
            writer.close();

            // Share the file
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            Uri fileUri = Uri.fromFile(file);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nhật ký hoạt động nhóm");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Nhật ký hoạt động nhóm được xuất từ NanaClu");

            startActivity(Intent.createChooser(shareIntent, "Chia sẻ nhật ký hoạt động"));

            Toast.makeText(this, "Đã lưu file: " + filename, Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Toast.makeText(this, "Lỗi khi lưu file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
