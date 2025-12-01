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
    private com.google.android.material.switchmaterial.SwitchMaterial switchPostApproval;
    private TextView tvPostApprovalStatus;
    private com.google.android.material.switchmaterial.SwitchMaterial switchEventCreation;
    private TextView tvEventCreationStatus;
    private View cardPendingPosts;
    private TextView tvGroupCode;
    private com.google.android.material.button.MaterialButton btnRegenerateCode;

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
        View cardStatistics = findViewById(R.id.cardStatistics);
        if (cardStatistics != null) {
            cardStatistics.setOnClickListener(v -> openStatistics());
        }

        // Setup switch: Không cần duyệt (ON) / Cần phê duyệt (OFF) cho tham gia nhóm
        switchPrivacy = findViewById(R.id.switchPrivacy);
        tvPrivacyStatus = findViewById(R.id.tvPrivacyStatus);
        switchPrivacy.setOnCheckedChangeListener(this::onPrivacySwitchChanged);

        // Setup post approval controls
        switchPostApproval = findViewById(R.id.switchPostApproval);
        tvPostApprovalStatus = findViewById(R.id.tvPostApprovalStatus);
        cardPendingPosts = findViewById(R.id.cardPendingPosts);
        if (cardPendingPosts != null) {
            cardPendingPosts.setOnClickListener(v -> openPendingPosts());
        }
        if (switchPostApproval != null) {
            switchPostApproval.setOnCheckedChangeListener(this::onPostApprovalSwitchChanged);
        }

        // Setup event creation controls
        switchEventCreation = findViewById(R.id.switchEventCreation);
        tvEventCreationStatus = findViewById(R.id.tvEventCreationStatus);
        if (switchEventCreation != null) {
            switchEventCreation.setOnCheckedChangeListener(this::onEventCreationSwitchChanged);
        }

        // Setup group code UI
        tvGroupCode = findViewById(R.id.tvGroupCode);
        btnRegenerateCode = findViewById(R.id.btnRegenerateCode);
        
        if (tvGroupCode != null) {
            tvGroupCode.setOnClickListener(v -> copyGroupCodeToClipboard());
        }
        
        if (btnRegenerateCode != null) {
            btnRegenerateCode.setOnClickListener(v -> showRegenerateCodeDialog());
        }
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

            // Update post approval UI
            if (switchPostApproval != null) {
                switchPostApproval.setOnCheckedChangeListener(null);
                boolean postApprovalEnabled = currentGroup.requirePostApproval;
                switchPostApproval.setChecked(postApprovalEnabled);
                switchPostApproval.setOnCheckedChangeListener(this::onPostApprovalSwitchChanged);
                updatePostApprovalStatusText();
            }
            if (cardPendingPosts != null) {
                cardPendingPosts.setVisibility(currentGroup.requirePostApproval ? View.VISIBLE : View.GONE);
            }

            // Update event creation UI
            if (switchEventCreation != null) {
                switchEventCreation.setOnCheckedChangeListener(null);
                boolean eventCreationAllowed = currentGroup.allowMemberCreateEvents;
                switchEventCreation.setChecked(eventCreationAllowed);
                switchEventCreation.setOnCheckedChangeListener(this::onEventCreationSwitchChanged);
                updateEventCreationStatusText();
            }

            // Update group code UI
            if (tvGroupCode != null) {
                tvGroupCode.setText(currentGroup.code != null ? currentGroup.code : "N/A");
            }
            
            // Check permission for regenerate code button
            checkRegenerateCodePermission();
        }
    }

    private void updatePrivacyStatusText() {
        if (currentGroup != null) {
            boolean noApprovalNeeded = currentGroup.requireApproval == false;
            String status = noApprovalNeeded ? "Không cần duyệt" : "Cần phê duyệt";
            tvPrivacyStatus.setText("Tham gia nhóm: " + status);
        }
    }

    private void updatePostApprovalStatusText() {
        if (currentGroup != null && tvPostApprovalStatus != null) {
            String status = currentGroup.requirePostApproval ? "Bật" : "Tắt";
            tvPostApprovalStatus.setText(status);
        }
    }

    private void updateEventCreationStatusText() {
        if (currentGroup != null && tvEventCreationStatus != null) {
            String status = currentGroup.allowMemberCreateEvents ? "Có thể" : "Không thể";
            tvEventCreationStatus.setText("Cho phép thành viên tạo sự kiện: " + status);
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

    private void onPostApprovalSwitchChanged(CompoundButton buttonView, boolean isChecked) {
        if (currentGroup == null) return;

        String currentStatus = currentGroup.requirePostApproval ? "Bật" : "Tắt";
        String newStatus = isChecked ? "Bật" : "Tắt";

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thay đổi")
                .setMessage("Thay đổi chế độ duyệt bài từ '" + currentStatus + "' sang '" + newStatus + "'?")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    groupRepository.updatePostApprovalSetting(groupId, isChecked, new GroupRepository.UpdateCallback() {
                        @Override
                        public void onSuccess() {
                            currentGroup.requirePostApproval = isChecked;
                            updatePostApprovalStatusText();
                            if (cardPendingPosts != null) {
                                cardPendingPosts.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                            }
                            Toast.makeText(GroupSettingsActivity.this, "Đã cập nhật duyệt bài", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(GroupSettingsActivity.this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            if (switchPostApproval != null) {
                                switchPostApproval.setOnCheckedChangeListener(null);
                                switchPostApproval.setChecked(!isChecked);
                                switchPostApproval.setOnCheckedChangeListener(GroupSettingsActivity.this::onPostApprovalSwitchChanged);
                            }
                        }
                    });
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    if (switchPostApproval != null) {
                        switchPostApproval.setOnCheckedChangeListener(null);
                        switchPostApproval.setChecked(!isChecked);
                        switchPostApproval.setOnCheckedChangeListener(GroupSettingsActivity.this::onPostApprovalSwitchChanged);
                    }
                })
                .show();
    }

    private void onEventCreationSwitchChanged(CompoundButton buttonView, boolean isChecked) {
        if (currentGroup == null) return;

        String currentStatus = currentGroup.allowMemberCreateEvents ? "Có thể" : "Không thể";
        String newStatus = isChecked ? "Có thể" : "Không thể";

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thay đổi")
                .setMessage("Thay đổi quyền tạo sự kiện từ '" + currentStatus + "' sang '" + newStatus + "'?")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    groupRepository.updateEventCreationSetting(groupId, isChecked, new GroupRepository.UpdateCallback() {
                        @Override
                        public void onSuccess() {
                            currentGroup.allowMemberCreateEvents = isChecked;
                            updateEventCreationStatusText();
                            Toast.makeText(GroupSettingsActivity.this, "Đã cập nhật quyền tạo sự kiện", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("GroupSettingsActivity", e);
                            String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                            if (errorMessage != null) {
                                Toast.makeText(GroupSettingsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(GroupSettingsActivity.this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                            if (switchEventCreation != null) {
                                switchEventCreation.setOnCheckedChangeListener(null);
                                switchEventCreation.setChecked(!isChecked);
                                switchEventCreation.setOnCheckedChangeListener(GroupSettingsActivity.this::onEventCreationSwitchChanged);
                            }
                        }
                    });
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    if (switchEventCreation != null) {
                        switchEventCreation.setOnCheckedChangeListener(null);
                        switchEventCreation.setChecked(!isChecked);
                        switchEventCreation.setOnCheckedChangeListener(GroupSettingsActivity.this::onEventCreationSwitchChanged);
                    }
                })
                .show();
    }

    private void openPendingPosts() {
        Intent i = new Intent(this, com.example.nanaclu.ui.post.PendingPostsActivity.class);
        i.putExtra("groupId", groupId);
        startActivity(i);
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

    private void openStatistics() {
        // Kiểm tra quyền Owner/Admin
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(com.example.nanaclu.data.model.Member member) {
                if (member != null && ("owner".equals(member.role) || "admin".equals(member.role))) {
                    Intent i = new Intent(GroupSettingsActivity.this, 
                        com.example.nanaclu.ui.group.statistics.GroupStatisticsActivity.class);
                    i.putExtra("groupId", groupId);
                    startActivity(i);
                } else {
                    Toast.makeText(GroupSettingsActivity.this, 
                        "Chỉ Owner và Admin mới có quyền xem thống kê", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupSettingsActivity.this, 
                    "Lỗi kiểm tra quyền", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkRegenerateCodePermission() {
        if (btnRegenerateCode == null) return;
        
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(com.example.nanaclu.data.model.Member member) {
                if (member != null && ("owner".equals(member.role) || "admin".equals(member.role))) {
                    btnRegenerateCode.setEnabled(true);
                    btnRegenerateCode.setAlpha(1.0f);
                } else {
                    btnRegenerateCode.setEnabled(false);
                    btnRegenerateCode.setAlpha(0.5f);
                }
            }
            
            @Override
            public void onError(Exception e) {
                btnRegenerateCode.setEnabled(false);
                btnRegenerateCode.setAlpha(0.5f);
            }
        });
    }

    private void showRegenerateCodeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Random lại mã mời?")
                .setMessage("Mã mời cũ sẽ không còn sử dụng được. Bạn có chắc chắn?")
                .setPositiveButton("Random", (dialog, which) -> regenerateGroupCode())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void regenerateGroupCode() {
        if (currentGroup == null) return;
        
        String oldCode = currentGroup.code;
        
        // Show loading state
        if (btnRegenerateCode != null) {
            btnRegenerateCode.setEnabled(false);
            btnRegenerateCode.setText("Đang tạo...");
        }
        
        groupRepository.regenerateGroupCode(groupId, new GroupRepository.RegenerateCodeCallback() {
            @Override
            public void onSuccess(String newCode) {
                // Update UI
                if (tvGroupCode != null) {
                    tvGroupCode.setText(newCode);
                }
                
                // Update current group object
                currentGroup.code = newCode;
                
                // Log the action
                com.example.nanaclu.data.repository.LogRepository logRepo = 
                        new com.example.nanaclu.data.repository.LogRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
                java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("oldCode", oldCode);
                metadata.put("newCode", newCode);
                logRepo.logGroupAction(groupId, "code_regenerated", "group", groupId, null, metadata);
                
                // Show success message
                Toast.makeText(GroupSettingsActivity.this, "Mã mới: " + newCode, Toast.LENGTH_LONG).show();
                
                // Reset button state
                if (btnRegenerateCode != null) {
                    btnRegenerateCode.setEnabled(true);
                    btnRegenerateCode.setText("Random");
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupSettingsActivity.this, "Lỗi tạo mã mới: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Reset button state
                if (btnRegenerateCode != null) {
                    btnRegenerateCode.setEnabled(true);
                    btnRegenerateCode.setText("Random");
                }
            }
        });
    }

    private void copyGroupCodeToClipboard() {
        if (currentGroup == null || currentGroup.code == null) return;
        
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Group Code", currentGroup.code);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Đã sao chép mã: " + currentGroup.code, Toast.LENGTH_SHORT).show();
    }
}
