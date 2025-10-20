package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.repository.LogRepository;
import com.example.nanaclu.ui.report.GroupReportActivity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GroupSettingsActivity extends AppCompatActivity {

    private String groupId;
    private String currentUserId;
    private Group currentGroup;
    private GroupRepository groupRepository;
    private Switch switchPrivacy;
    private TextView tvPrivacyStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);

        // Lấy dữ liệu từ Intent
        groupId = getIntent().getStringExtra("groupId");
        currentUserId = getIntent().getStringExtra("currentUserId");

        // Initialize repository
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadGroupData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Cài đặt nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        // Setup click listeners for cards
        findViewById(R.id.cardEditInfo).setOnClickListener(v -> openEditInfoActivity());
        findViewById(R.id.cardGroupLogs).setOnClickListener(v -> openGroupLogs());
        findViewById(R.id.cardDeleteGroup).setOnClickListener(v -> showDeleteGroupDialog());
        View cardPermissions = findViewById(R.id.cardPermissions);
        if (cardPermissions != null) {
            cardPermissions.setOnClickListener(v -> openPermissions());
        }
        View cardBlocked = findViewById(R.id.cardBlockedUsers);
        if (cardBlocked != null) {
            cardBlocked.setOnClickListener(v -> openBlockedUsers());
        }
        View cardPending = findViewById(R.id.cardPendingMembers);
        if (cardPending != null) {
            cardPending.setOnClickListener(v -> openPendingMembers());
        }
        View cardMembers = findViewById(R.id.cardMembersList);
        if (cardMembers != null) {
            cardMembers.setOnClickListener(v -> openMembers());
        }
        View cardReports = findViewById(R.id.cardReports);
        if (cardReports != null) {
            cardReports.setOnClickListener(v -> openReports());
        }

        // Setup switch: Không cần duyệt (ON) / Cần phê duyệt (OFF)
        switchPrivacy = findViewById(R.id.switchPrivacy);
        tvPrivacyStatus = findViewById(R.id.tvPrivacyStatus);
        switchPrivacy.setOnCheckedChangeListener(this::onPrivacySwitchChanged);
    }

    private void openPermissions() {
        Intent intent = new Intent(this, TransferOwnershipActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("currentUserId", currentUserId);
        startActivity(intent);
    }

    private void loadGroupData() {
        groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                currentGroup = group;
                updateUI();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupSettingsActivity.this, "Lỗi khi tải thông tin nhóm", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void updateUI() {
        if (currentGroup != null) {
            // Temporarily disable listener to avoid triggering dialog
            switchPrivacy.setOnCheckedChangeListener(null);
            boolean noApprovalNeeded = currentGroup.requireApproval == false;
            switchPrivacy.setChecked(noApprovalNeeded);
            switchPrivacy.setOnCheckedChangeListener(this::onPrivacySwitchChanged);
            updatePrivacyStatusText();
        }
    }

    private void updatePrivacyStatusText() {
        if (currentGroup != null) {
            boolean noApprovalNeeded = currentGroup.requireApproval == false;
            String status = noApprovalNeeded ? "Không cần duyệt" : "Cần phê duyệt";
            tvPrivacyStatus.setText("Tham gia nhóm: " + status);
        }
    }

    private void openEditInfoActivity() {
        Intent intent = new Intent(this, EditGroupInfoActivity.class);
        intent.putExtra("groupId", groupId);
        startActivityForResult(intent, 100);
    }

    private void openGroupLogs() {
        Intent intent = new Intent(this, com.example.nanaclu.ui.group.logs.GroupLogActivity.class);
        intent.putExtra("groupId", groupId);
        startActivity(intent);
    }

    private void onPrivacySwitchChanged(CompoundButton buttonView, boolean isChecked) {
        if (currentGroup == null) return;

        // isChecked = Không cần duyệt; !isChecked = Cần phê duyệt
        String currentStatus = (currentGroup.requireApproval == false) ? "Không cần duyệt" : "Cần phê duyệt";
        String newStatus = isChecked ? "Không cần duyệt" : "Cần phê duyệt";

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thay đổi")
                .setMessage("Bạn có muốn thay đổi chính sách tham gia từ '" + currentStatus + "' thành '" + newStatus + "' không?")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    currentGroup.requireApproval = !isChecked;
                    // Optional: keep isPublic synced for legacy displays
                    currentGroup.isPublic = isChecked;
                    groupRepository.updateGroup(currentGroup, new GroupRepository.UpdateCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(GroupSettingsActivity.this, "Đã cập nhật chính sách tham gia", Toast.LENGTH_SHORT).show();
                            // Log policy change
                            LogRepository logRepo = new LogRepository(FirebaseFirestore.getInstance());
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("requireApproval", currentGroup.requireApproval);
                            logRepo.logGroupAction(groupId, "policy_changed", "settings", groupId, null, metadata);
                            updateUI();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(GroupSettingsActivity.this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            // Revert switch state without triggering listener
                            switchPrivacy.setOnCheckedChangeListener(null);
                            switchPrivacy.setChecked(!isChecked);
                            switchPrivacy.setOnCheckedChangeListener(GroupSettingsActivity.this::onPrivacySwitchChanged);
                        }
                    });
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    // Revert switch state without triggering listener
                    switchPrivacy.setOnCheckedChangeListener(null);
                    switchPrivacy.setChecked(!isChecked);
                    switchPrivacy.setOnCheckedChangeListener(GroupSettingsActivity.this::onPrivacySwitchChanged);
                })
                .show();
    }

    private void showDeleteGroupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa nhóm")
                .setMessage("Bạn có chắc chắn muốn xóa nhóm này không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteGroup())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteGroup() {
        groupRepository.deleteGroup(groupId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(GroupSettingsActivity.this, "Đã xóa nhóm thành công", Toast.LENGTH_SHORT).show();

                // Navigate back to My Groups fragment
                Intent resultIntent = new Intent();
                resultIntent.putExtra("action", "group_deleted");
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupSettingsActivity.this, "Lỗi khi xóa nhóm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Group info was updated, reload group data
            loadGroupData();
        }
    }

    private void openPendingMembers() {
        Intent i = new Intent(this, GroupPendingMembersActivity.class);
        i.putExtra("groupId", groupId);
        startActivity(i);
    }

    private void openBlockedUsers() {
        Intent i = new Intent(this, GroupBlockedUsersActivity.class);
        i.putExtra("groupId", groupId);
        startActivity(i);
    }

    private void openMembers() {
        Intent i = new Intent(this, GroupMembersActivity.class);
        i.putExtra("groupId", groupId);
        startActivity(i);
    }

    private void openReports() {
        // Mở Activity chứa ActiveGroupReportDashboardFragment
        Intent i = new Intent(this, GroupReportActivity.class);
        i.putExtra("groupId", groupId);
        startActivity(i);
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
