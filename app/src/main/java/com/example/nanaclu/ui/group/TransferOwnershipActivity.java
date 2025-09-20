package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.repository.GroupRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class TransferOwnershipActivity extends AppCompatActivity {
    
    private String groupId;
    private String currentUserId;
    private String currentUserRole;
    private GroupRepository groupRepository;

    private RecyclerView recyclerView;
    private GroupMembersAdapter adapter;
    private List<Member> members = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        // Get data from Intent
        groupId = getIntent().getStringExtra("groupId");
        currentUserId = getIntent().getStringExtra("currentUserId");
        if (groupId == null || currentUserId == null) {
            finish();
            return;
        }

        // Initialize repository
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadMembers();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Phân quyền");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        recyclerView = findViewById(R.id.recyclerView);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupMembersAdapter(members, this::onMemberClick, currentUserId);
        recyclerView.setAdapter(adapter);

        // Hide search view for this activity
        findViewById(R.id.searchView).setVisibility(android.view.View.GONE);
    }

    private void loadMembers() {
        // Load current user's role first
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member me) {
                currentUserRole = me != null ? me.role : null;
                fetchMembers();
            }

            @Override
            public void onError(Exception e) {
                currentUserRole = null;
                fetchMembers();
            }
        });
    }

    private void fetchMembers() {
        groupRepository.getGroupMembers(groupId, new GroupRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Member> allMembers) {
                // Filter out current user
                members.clear();
                for (Member member : allMembers) {
                    if (!member.userId.equals(currentUserId)) {
                        members.add(member);
                    }
                }
                // Load user display names for dialog titles
                loadUserDataForMembers();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TransferOwnershipActivity.this, "Lỗi khi tải danh sách thành viên", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserDataForMembers() {
        com.example.nanaclu.data.repository.UserRepository userRepo = new com.example.nanaclu.data.repository.UserRepository(FirebaseFirestore.getInstance());
        for (Member m : members) {
            userRepo.getUserById(m.userId, new com.example.nanaclu.data.repository.UserRepository.UserCallback() {
                @Override
                public void onSuccess(com.example.nanaclu.data.model.User user) {
                    if (user != null) m.userName = user.displayName;
                    adapter.updateMembers(members);
                }

                @Override
                public void onError(Exception e) {
                    // leave userName as is (might be null -> fallback to userId)
                    adapter.updateMembers(members);
                }
            });
        }
        adapter.updateMembers(members);
    }

    private void onMemberClick(Member member) {
        if (member.userId.equals(currentUserId)) {
            Toast.makeText(this, "Bạn không thể thao tác với chính mình", Toast.LENGTH_SHORT).show();
            return;
        }

        showMemberActionsMenu(member);
    }

    private void showMemberActionsMenu(Member member) {
        String memberName = member.userName != null && !member.userName.isEmpty()
            ? member.userName
            : member.userId;

        java.util.List<String> actionsList = new java.util.ArrayList<>();
        // Determine actions based on current user's role
        if ("owner".equals(currentUserRole)) {
            if (!"owner".equals(member.role)) {
                // Owner can manage admins and members
                actionsList.add("Xóa khỏi nhóm");
                actionsList.add("Chuyển thành " + ("admin".equals(member.role) ? "thành viên" : "admin"));
                actionsList.add("Chuyển thành chủ sở hữu");
            }
        } else if ("admin".equals(currentUserRole)) {
            if ("member".equals(member.role)) {
                // Admin can only act on members
                actionsList.add("Xóa khỏi nhóm");
                actionsList.add("Chuyển thành admin");
            }
        }

        if (actionsList.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Thao tác với " + memberName)
                .setMessage("Không có thao tác khả dụng")
                .setPositiveButton("Đóng", null)
                .show();
            return;
        }

        String[] actions = actionsList.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Thao tác với " + memberName)
                .setItems(actions, (dialog, which) -> {
                    String selected = actions[which];
                    if (selected.startsWith("Xóa")) {
                        showRemoveMemberConfirmation(member);
                    } else if (selected.startsWith("Chuyển thành chủ")) {
                        showTransferConfirmation(member);
                    } else if (selected.equals("Chuyển thành admin")) {
                        changeRole(member, "admin");
                    } else if (selected.equals("Chuyển thành thành viên")) {
                        changeRole(member, "member");
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showRemoveMemberConfirmation(Member member) {
        String memberName = member.userName != null && !member.userName.isEmpty()
            ? member.userName
            : member.userId;

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa thành viên")
                .setMessage("Bạn có chắc chắn muốn xóa " + memberName + " khỏi nhóm không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    removeMember(member);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showTransferConfirmation(Member member) {
        String memberName = member.userName != null && !member.userName.isEmpty()
            ? member.userName
            : member.userId;

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận chuyển quyền sở hữu")
                .setMessage("Bạn có chắc chắn muốn chuyển quyền sở hữu nhóm cho " + memberName + " không?\n\nBạn sẽ trở thành admin của nhóm.")
                .setPositiveButton("Chuyển quyền", (dialog, which) -> {
                    transferOwnership(member);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void removeMember(Member member) {
        groupRepository.removeMember(groupId, member.userId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(TransferOwnershipActivity.this, "Đã xóa thành viên thành công", Toast.LENGTH_SHORT).show();
                loadMembers(); // Reload list
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TransferOwnershipActivity.this, "Lỗi khi xóa thành viên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void changeRole(Member member, String newRole) {
        groupRepository.updateMemberRole(groupId, member.userId, newRole, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                String roleText = "admin".equals(newRole) ? "admin" : "thành viên";
                Toast.makeText(TransferOwnershipActivity.this, "Đã chuyển thành " + roleText + " thành công", Toast.LENGTH_SHORT).show();
                loadMembers(); // Reload list
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TransferOwnershipActivity.this, "Lỗi khi thay đổi quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void transferOwnership(Member member) {
        groupRepository.transferOwnership(groupId, currentUserId, member.userId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(TransferOwnershipActivity.this, "Đã chuyển quyền sở hữu thành công", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TransferOwnershipActivity.this, "Lỗi khi chuyển quyền sở hữu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
}
