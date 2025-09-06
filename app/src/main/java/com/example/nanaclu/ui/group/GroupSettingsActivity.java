package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
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
import com.google.firebase.firestore.FirebaseFirestore;

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
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Cài đặt nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        // Setup click listeners for cards
        findViewById(R.id.cardEditInfo).setOnClickListener(v -> openEditInfoActivity());
        findViewById(R.id.cardDeleteGroup).setOnClickListener(v -> showDeleteGroupDialog());
        
        // Setup privacy switch
        switchPrivacy = findViewById(R.id.switchPrivacy);
        tvPrivacyStatus = findViewById(R.id.tvPrivacyStatus);
        switchPrivacy.setOnCheckedChangeListener(this::onPrivacySwitchChanged);
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
            switchPrivacy.setChecked(currentGroup.isPublic);
            switchPrivacy.setOnCheckedChangeListener(this::onPrivacySwitchChanged);
            updatePrivacyStatusText();
        }
    }

    private void updatePrivacyStatusText() {
        if (currentGroup != null) {
            String status = currentGroup.isPublic ? "Công khai" : "Riêng tư";
            tvPrivacyStatus.setText("Nhóm " + status);
        }
    }

    private void openEditInfoActivity() {
        Intent intent = new Intent(this, EditGroupInfoActivity.class);
        intent.putExtra("groupId", groupId);
        startActivityForResult(intent, 100);
    }

    private void onPrivacySwitchChanged(CompoundButton buttonView, boolean isChecked) {
        if (currentGroup == null) return;

        String currentStatus = currentGroup.isPublic ? "Công khai" : "Riêng tư";
        String newStatus = isChecked ? "Công khai" : "Riêng tư";

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thay đổi")
                .setMessage("Bạn có muốn thay đổi nhóm từ " + currentStatus + " thành " + newStatus + " không?")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    currentGroup.isPublic = isChecked;
                    groupRepository.updateGroup(currentGroup, new GroupRepository.UpdateCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(GroupSettingsActivity.this, "Đã thay đổi quyền riêng tư thành công", Toast.LENGTH_SHORT).show();
                            updatePrivacyStatusText();
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
