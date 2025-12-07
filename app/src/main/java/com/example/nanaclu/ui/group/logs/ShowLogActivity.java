package com.example.nanaclu.ui.group.logs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.GroupLog;
import com.example.nanaclu.data.repository.GroupRepository;
import com.google.firebase.firestore.FirebaseFirestore;

public class ShowLogActivity extends AppCompatActivity {

    private String logId;
    private String groupId;
    private GroupRepository groupRepository;

    private TextView tvTitle;
    private TextView tvContent;
    private TextView tvOldValue;
    private TextView tvNewValue;
    private TextView tvMemberInfo;
    private TextView tvActionDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_log);

        // Get data from intent
        logId = getIntent().getStringExtra("logId");
        groupId = getIntent().getStringExtra("groupId");

        if (logId == null || groupId == null) {
            Toast.makeText(this, "Dữ liệu log không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadLogDetails();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chi tiết nhật ký");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        tvTitle = findViewById(R.id.tvTitle);
        tvContent = findViewById(R.id.tvContent);
        tvOldValue = findViewById(R.id.tvOldValue);
        tvNewValue = findViewById(R.id.tvNewValue);
        tvMemberInfo = findViewById(R.id.tvMemberInfo);
        tvActionDetails = findViewById(R.id.tvActionDetails);

        // Initially hide all detail views
        findViewById(R.id.layoutCodeChange).setVisibility(View.GONE);
        findViewById(R.id.layoutMemberAction).setVisibility(View.GONE);
    }

    private void loadLogDetails() {
        // Load the specific log from Firestore
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .collection("logs")
                .document(logId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GroupLog log = documentSnapshot.toObject(GroupLog.class);
                        if (log != null) {
                            displayLogDetails(log);
                        } else {
                            showError("Không thể tải dữ liệu log");
                        }
                    } else {
                        showError("Log không tồn tại");
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Lỗi khi tải log: " + e.getMessage());
                });
    }

    private void displayLogDetails(GroupLog log) {
        // Set basic information
        tvTitle.setText(getLogTitle(log.type));
        tvContent.setText(log.message != null ? log.message : "Không có mô tả");

        // Handle different log types
        switch (log.type) {
            case GroupLog.TYPE_GROUP_CODE_CHANGED:
                showCodeChangeDetails(log);
                break;
            case GroupLog.TYPE_MEMBER_APPROVED:
            case GroupLog.TYPE_MEMBER_REJECTED:
            case GroupLog.TYPE_MEMBER_REMOVED:
            case GroupLog.TYPE_MEMBER_BLOCKED:
            case GroupLog.TYPE_MEMBER_UNBLOCKED:
                showMemberActionDetails(log);
                break;
            default:
                // For other types, just show the basic message
                break;
        }
    }

    private void showCodeChangeDetails(GroupLog log) {
        findViewById(R.id.layoutCodeChange).setVisibility(View.VISIBLE);

        if (log.metadata != null) {
            String oldCode = (String) log.metadata.get("oldCode");
            String newCode = (String) log.metadata.get("newCode");

            tvOldValue.setText(oldCode != null ? oldCode : "N/A");
            tvNewValue.setText(newCode != null ? newCode : "N/A");
        } else {
            tvOldValue.setText("N/A");
            tvNewValue.setText("N/A");
        }
    }

    private void showMemberActionDetails(GroupLog log) {
        findViewById(R.id.layoutMemberAction).setVisibility(View.VISIBLE);

        // Get member information from targetName or targetId
        String memberInfo = log.targetName != null ? log.targetName : "ID: " + log.targetId;
        tvMemberInfo.setText(memberInfo);

        // Set action details based on log type
        String actionDetails = getActionDetails(log.type);
        tvActionDetails.setText(actionDetails);
    }

    private String getLogTitle(String type) {
        switch (type) {
            case GroupLog.TYPE_GROUP_CODE_CHANGED:
                return "Thay đổi mã nhóm";
            case GroupLog.TYPE_MEMBER_APPROVED:
                return "Duyệt yêu cầu tham gia";
            case GroupLog.TYPE_MEMBER_REJECTED:
                return "Từ chối yêu cầu tham gia";
            case GroupLog.TYPE_MEMBER_REMOVED:
                return "Xóa thành viên";
            case GroupLog.TYPE_MEMBER_BLOCKED:
                return "Chặn thành viên";
            case GroupLog.TYPE_MEMBER_UNBLOCKED:
                return "Bỏ chặn thành viên";
            default:
                return "Chi tiết nhật ký";
        }
    }

    private String getActionDetails(String type) {
        switch (type) {
            case GroupLog.TYPE_MEMBER_APPROVED:
                return "Đã duyệt thành viên tham gia nhóm";
            case GroupLog.TYPE_MEMBER_REJECTED:
                return "Đã từ chối yêu cầu tham gia nhóm của thành viên";
            case GroupLog.TYPE_MEMBER_REMOVED:
                return "Đã xóa thành viên khỏi nhóm";
            case GroupLog.TYPE_MEMBER_BLOCKED:
                return "Đã chặn thành viên trong nhóm";
            case GroupLog.TYPE_MEMBER_UNBLOCKED:
                return "Đã bỏ chặn thành viên trong nhóm";
            default:
                return "Hành động đã được thực hiện";
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
