package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class GroupMembersActivity extends AppCompatActivity {

    private String groupId;
    private String currentUserId;
    private Group currentGroup;
    private Member currentUserMember;
    private GroupRepository groupRepository;
    private UserRepository userRepository;

    private RecyclerView recyclerView;
    private GroupMembersAdapter adapter;
    private SearchView searchView;
    private List<Member> allMembers = new ArrayList<>();
    private List<Member> filteredMembers = new ArrayList<>();
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        // Get data from Intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            finish();
            return;
        }

        // Get current user ID
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (userId != null) {
            currentUserId = userId;
        } else {
            finish();
            return;
        }

        // Initialize repositories
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        android.util.Log.d("GroupMembersActivity", "=== Setting up toolbar ===");
        android.util.Log.d("GroupMembersActivity", "Toolbar found: " + (toolbar != null));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Thành viên nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            android.util.Log.d("GroupMembersActivity", "SupportActionBar is available, title set to: Thành viên nhóm");
        } else {
            android.util.Log.w("GroupMembersActivity", "SupportActionBar is null!");
        }

        // Check toolbar theme and styling
        if (toolbar != null) {
            android.util.Log.d("GroupMembersActivity", "Toolbar background: " + toolbar.getBackground());
            android.util.Log.d("GroupMembersActivity", "Toolbar theme: " + toolbar.getContext().getTheme());
            android.util.Log.d("GroupMembersActivity", "Toolbar popup theme: " + toolbar.getPopupTheme());
        }

        // Inflate the menu for admin/owner
        toolbar.inflateMenu(R.menu.menu_group_members);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        updateMenuVisibility();
    }

    private void updateMenuVisibility() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        android.view.Menu menu = toolbar.getMenu();
        if (menu == null) return;

        boolean isAdminOrOwner = currentUserMember != null &&
                ("admin".equals(currentUserMember.role) || "owner".equals(currentUserMember.role));

        MenuItem blockedUsersItem = menu.findItem(R.id.action_blocked_users);
        if (blockedUsersItem != null) {
            blockedUsersItem.setVisible(isAdminOrOwner);
        }
    }

    private boolean onMenuItemClick(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_blocked_users) {
            Intent intent = new Intent(this, GroupBlockedUsersActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
            return true;
        }
        return false;
    }

    private void setupUI() {
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupMembersAdapter(filteredMembers, this::onMemberClick, currentUserId);
        recyclerView.setAdapter(adapter);

        // Setup SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query != null ? query : "";
                filterMembers(currentQuery);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText != null ? newText : "";
                filterMembers(currentQuery);
                return true;
            }
        });
    }

    private void loadData() {
        // Load group data
        groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                currentGroup = group;
                loadCurrentUserMember();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupMembersActivity.this, "Lỗi khi tải thông tin nhóm", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadCurrentUserMember() {
        android.util.Log.d("GroupMembers", "Loading current user member: " + currentUserId + " in group: " + groupId);
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member member) {
                android.util.Log.d("GroupMembers", "Current user member found: " + member.role);
                currentUserMember = member;
                updateMenuVisibility();
                loadMembers();
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("GroupMembers", "Current user member NOT found: " + e.getMessage());
                // User might not be a member
                currentUserMember = null;
                loadMembers();
            }
        });
    }

    private void loadMembers() {
        groupRepository.getGroupMembers(groupId, new GroupRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Member> members) {
                android.util.Log.d("GroupMembers", "Loaded " + members.size() + " members");
                boolean currentUserFound = false;
                for (Member member : members) {
                    android.util.Log.d("GroupMembers", "Member: " + member.userId + ", role: " + member.role);
                    if (member.userId.equals(currentUserId)) {
                        currentUserFound = true;
                        android.util.Log.d("GroupMembers", "Current user found in members list");
                    }
                }
                if (!currentUserFound) {
                    android.util.Log.w("GroupMembers", "Current user NOT found in members list!");
                    // If current user is not in the list but has access to this activity,
                    // they should be added (this can happen in edge cases)
                    if (currentUserMember != null) {
                        android.util.Log.d("GroupMembers", "Adding current user to members list");
                        members.add(currentUserMember);
                    }
                }

                allMembers = members;
                filteredMembers = new ArrayList<>(members);
                loadUserDataForMembers();
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("GroupMembers", "Error loading members: " + e.getMessage());
                Toast.makeText(GroupMembersActivity.this, "Lỗi khi tải danh sách thành viên", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserDataForMembers() {
        // Load user data for each member
        for (Member member : allMembers) {
            userRepository.getUserById(member.userId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    member.userName = user.displayName;
                    member.userEmail = user.email;
                    member.avatarImageId = user.avatarImageId;
                    filterMembers(currentQuery);
                }

                @Override
                public void onError(Exception e) {
                    // If can't load user data, use userId as fallback
                    member.userName = member.userId;
                    member.userEmail = "";
                    member.avatarImageId = "";
                    filterMembers(currentQuery);
                }
            });
        }
    }

    private void filterMembers(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredMembers = new ArrayList<>(allMembers);
        } else {
            filteredMembers = new ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();

            for (Member member : allMembers) {
                // Search by user name or user ID
                String searchText = "";
                if (member.userName != null && !member.userName.isEmpty()) {
                    searchText = member.userName.toLowerCase();
                } else {
                    searchText = member.userId.toLowerCase();
                }

                if (searchText.contains(lowerQuery)) {
                    filteredMembers.add(member);
                }
            }
        }
        adapter.updateMembers(filteredMembers);
    }

    private void onMemberClick(Member member) {
        if (currentUserMember == null) {
            Toast.makeText(this, "Bạn không có quyền xem thông tin thành viên", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isAdminOrOwner = "admin".equals(currentUserMember.role) || "owner".equals(currentUserMember.role);
        boolean isOwnProfile = member.userId.equals(currentUserId);

        // Always show menu with admin/owner privileges if user has them
        // regardless of whether they're viewing their own profile or others
        showMemberMenu(member, isAdminOrOwner);
    }

    private void showMemberMenu(Member member, boolean isAdminOrOwner) {
        // Determine current user's role and target member role
        String myRole = currentUserMember != null ? currentUserMember.role : null;
        String targetRole = member.role;
        boolean isSelf = member.userId.equals(currentUserId);

        List<String> items = new ArrayList<>();
        items.add("Thông tin");
        items.add("Bài đăng");

        // Show role management options for admin/owner
        if ("owner".equals(myRole)) {
            // Owner: can manage admins and members; can transfer ownership
            if (!"owner".equals(targetRole)) {
                items.add("Thay đổi vai trò"); // choose owner/admin/member
                if (!isSelf) { // Can't kick or block themselves
                    items.add("Kick");
                    if ("member".equals(targetRole)) items.add("Block");
                }
            }
        } else if ("admin".equals(myRole)) {
            // Admin: can only act on members
            if ("member".equals(targetRole)) {
                items.add("Thay đổi vai trò"); // promote to admin
                if (!isSelf) { // Can't kick or block themselves
                    items.add("Kick");
                    items.add("Block");
                }
            }
        }

        String[] menuItems = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Chọn hành động")
                .setItems(menuItems, (dialog, which) -> {
                    String selectedItem = menuItems[which];
                    handleMenuAction(selectedItem, member);
                })
                .show();
    }

    private void handleMenuAction(String action, Member member) {
        switch (action) {
            case "Thông tin":
                showMemberInfo(member);
                break;
            case "Bài đăng":
                showMemberPosts(member);
                break;
            case "Thay đổi vai trò":
                changeMemberRole(member);
                break;
            case "Kick":
                kickMember(member, groupId);
                break;
            case "Block":
                blockMember(member);
                break;
        }
    }

    private void showMemberInfo(Member member) {
        // Mở profile của user
        Intent intent = new Intent(this, com.example.nanaclu.ui.profile.ProfileActivity.class);
        intent.putExtra("userId", member.userId);
        startActivity(intent);
    }

    private void showMemberPosts(Member member) {
        // Tạm thời mở profile (có thể điều hướng tới danh sách bài đăng của user sau)
        Intent intent = new Intent(this, com.example.nanaclu.ui.profile.ProfileActivity.class);
        intent.putExtra("userId", member.userId);
        startActivity(intent);
    }

    private void promoteMember(Member member) {
        if (currentUserMember == null) return;
        String myRole = currentUserMember.role;
        String targetRole = member.role != null ? member.role : "member";

        if ("owner".equals(myRole)) {
            // Owner: toggle admin/member (không can thiệp owner)
            if ("owner".equals(targetRole)) {
                Toast.makeText(this, "Không thể thay đổi quyền của owner", Toast.LENGTH_SHORT).show();
                return;
            }
            String newRole = "admin".equals(targetRole) ? "member" : "admin";
            groupRepository.updateMemberRole(groupId, member.userId, newRole, new GroupRepository.UpdateCallback() {
                @Override public void onSuccess() {
                    Toast.makeText(GroupMembersActivity.this, ("admin".equals(newRole)?"Đã nâng quyền thành admin":"Đã chuyển thành thành viên"), Toast.LENGTH_SHORT).show();
                    loadMembers();
                }
                @Override public void onError(Exception e) {
                    Toast.makeText(GroupMembersActivity.this, "Lỗi thay đổi quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else if ("admin".equals(myRole)) {
            // Admin: chỉ nâng member lên admin
            if (!"member".equals(targetRole)) {
                Toast.makeText(this, "Chỉ có thể nâng quyền thành viên thường", Toast.LENGTH_SHORT).show();
                return;
            }
            groupRepository.updateMemberRole(groupId, member.userId, "admin", new GroupRepository.UpdateCallback() {
                @Override public void onSuccess() {
                    Toast.makeText(GroupMembersActivity.this, "Đã nâng quyền thành admin", Toast.LENGTH_SHORT).show();
                    loadMembers();
                }
                @Override public void onError(Exception e) {
                    Toast.makeText(GroupMembersActivity.this, "Lỗi thay đổi quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void changeMemberRole(Member member) {
        if (currentUserMember == null) return;
        String myRole = currentUserMember.role;
        String targetRole = member.role != null ? member.role : "member";

        if ("owner".equals(myRole)) {
            // Owner: choose role (owner/admin/member)
            String[] roles = new String[]{"Owner", "Admin", "Member"};
            int checked = "owner".equals(targetRole) ? 0 : ("admin".equals(targetRole) ? 1 : 2);
            new AlertDialog.Builder(this)
                    .setTitle("Chọn vai trò")
                    .setSingleChoiceItems(roles, checked, null)
                    .setPositiveButton("Xác nhận", (d, w) -> {
                        AlertDialog ad = (AlertDialog) d;
                        int idx = ad.getListView().getCheckedItemPosition();
                        if (idx == 0) {
                            // Transfer ownership: current owner -> admin; target -> owner
                            if (member.userId.equals(currentUserId)) {
                                Toast.makeText(GroupMembersActivity.this, "Không thể chuyển quyền owner cho chính mình", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            groupRepository.transferOwnership(groupId, currentUserId, member.userId, new GroupRepository.UpdateCallback() {
                                @Override public void onSuccess() {
                                    Toast.makeText(GroupMembersActivity.this, "Đã chuyển owner", Toast.LENGTH_SHORT).show();
                                    loadMembers();
                                }
                                @Override public void onError(Exception e) {
                                    Toast.makeText(GroupMembersActivity.this, "Lỗi chuyển owner: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else if (idx == 1) {
                            groupRepository.updateMemberRole(groupId, member.userId, "admin", new GroupRepository.UpdateCallback() {
                                @Override public void onSuccess() { Toast.makeText(GroupMembersActivity.this, "Đã nâng quyền thành admin", Toast.LENGTH_SHORT).show(); loadMembers(); }
                                @Override public void onError(Exception e) { Toast.makeText(GroupMembersActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                            });
                        } else {
                            groupRepository.updateMemberRole(groupId, member.userId, "member", new GroupRepository.UpdateCallback() {
                                @Override public void onSuccess() { Toast.makeText(GroupMembersActivity.this, "Đã chuyển thành viên", Toast.LENGTH_SHORT).show(); loadMembers(); }
                                @Override public void onError(Exception e) { Toast.makeText(GroupMembersActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                            });
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } else if ("admin".equals(myRole)) {
            // Admin: only promote member -> admin
            if (!"member".equals(targetRole)) {
                Toast.makeText(this, "Chỉ có thể thay đổi vai trò khác", Toast.LENGTH_SHORT).show();
                return;
            }
            groupRepository.updateMemberRole(groupId, member.userId, "admin", new GroupRepository.UpdateCallback() {
                @Override public void onSuccess() { Toast.makeText(GroupMembersActivity.this, "Đã nâng quyền thành admin", Toast.LENGTH_SHORT).show(); loadMembers(); }
                @Override public void onError(Exception e) { Toast.makeText(GroupMembersActivity.this, "Lỗi thay đổi: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
            });
        } else {
            Toast.makeText(this, "Bạn không có quyền", Toast.LENGTH_SHORT).show();
        }
    }

    private void blockMember(Member member) {
        // Only allow block for member targets
        if (!"member".equals(member.role)) {
            Toast.makeText(this, "Chỉ chọn thành viên thường", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Chọn thành viên? ")
                .setMessage("Thêm vào danh sách chặn, không thể tham gia lại nhóm")
                .setPositiveButton("Block", (d, w) -> {
                    groupRepository.blockUser(groupId, member.userId, new GroupRepository.UpdateCallback() {
                        @Override public void onSuccess() {
                            Toast.makeText(GroupMembersActivity.this, "Đã chặn thành công", Toast.LENGTH_SHORT).show();
                            // Remove from current list
                            for (int i = 0; i < allMembers.size(); i++) {
                                if (allMembers.get(i).userId.equals(member.userId)) { allMembers.remove(i); break; }
                            }
                            filterMembers(currentQuery);
                        }
                        @Override public void onError(Exception e) {
                            Toast.makeText(GroupMembersActivity.this, "Lỗi block: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void kickMember(Member member, String groupId) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận kick thành viên")
                .setMessage("Bạn có chắc chắn muốn kick thành viên này khỏi nhóm không?")
                .setPositiveButton("Kick", (dialog, which) -> {
                    // Quy tắc để kích hoạt menu, nhưng vẫn kiểm tra an toàn
                    String myRole = currentUserMember != null ? currentUserMember.role : null;
                    String targetRole = member.role != null ? member.role : "member";
                    if ("admin".equals(myRole) && !"member".equals(targetRole)) {
                        Toast.makeText(this, "Admin chỉ có thể kick thành viên thường", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if ("owner".equals(targetRole)) {
                        Toast.makeText(this, "Không thể kick owner", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Kick from group and group chat
                    kickUserFromGroupAndChat(member, groupId);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void kickUserFromGroupAndChat(Member member, String groupId) {
        // First, kick from group membership
        groupRepository.removeMember(groupId, member.userId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                android.util.Log.d("GroupMembers", "Successfully removed member from group: " + member.userId);

                // Send kick notice to user
                sendKickNotice(member, groupId);

                // Try to kick from group chat
                kickUserFromGroupChat(member, groupId);

                // Remove from current list
                for (int i = 0; i < allMembers.size(); i++) {
                    if (allMembers.get(i).userId.equals(member.userId)) {
                        allMembers.remove(i);
                        break;
                    }
                }
                filterMembers(currentQuery);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupMembersActivity.this, "Lỗi kick thành viên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void kickUserFromGroupChat(Member member, String groupId) {
        // Get or create group chat to ensure it exists
        com.example.nanaclu.data.repository.ChatRepository chatRepo = new com.example.nanaclu.data.repository.ChatRepository(FirebaseFirestore.getInstance());

        chatRepo.getOrCreateGroupChat(groupId)
                .addOnSuccessListener(chatId -> {
                    android.util.Log.d("GroupMembers", "Found group chat: " + chatId + " for group: " + groupId);

                    // Remove user from chat membership
                    chatRepo.removeMember(chatId, member.userId)
                            .addOnSuccessListener(aVoid -> {
                                android.util.Log.d("GroupMembers", "Successfully removed user from group chat: " + member.userId);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e("GroupMembers", "Failed to remove user from group chat: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("GroupMembers", "Failed to get group chat for group: " + groupId + ", error: " + e.getMessage());
                });
    }

    private void sendKickNotice(Member member, String groupId) {
        // Get group name for the notice
        FirebaseFirestore.getInstance().collection("groups").document(groupId)
                .get()
                .addOnSuccessListener(groupDoc -> {
                    String groupName = groupDoc.getString("name");
                    if (groupName == null) groupName = "Nhóm";

                    // Create kick notice
                    com.example.nanaclu.data.model.Notice notice = new com.example.nanaclu.data.model.Notice();
                    notice.setType("group_kicked");
                    notice.setTitle("Bị kick khỏi nhóm");
                    notice.setMessage("Bạn đã bị kick khỏi nhóm '" + groupName + "'");
                    notice.setObjectType("group");
                    notice.setObjectId(groupId);
                    notice.setTargetUserId(member.userId);
                    notice.setSeen(false);
                    notice.setCreatedAt(System.currentTimeMillis());

                    // Send notice
                    com.example.nanaclu.data.repository.NoticeRepository noticeRepo =
                            new com.example.nanaclu.data.repository.NoticeRepository(FirebaseFirestore.getInstance());
                    noticeRepo.createNotice(notice);
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
