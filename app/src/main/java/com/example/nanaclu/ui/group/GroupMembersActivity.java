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
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Thành viên nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
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
                filterMembers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterMembers(newText);
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
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member member) {
                currentUserMember = member;
                loadMembers();
            }

            @Override
            public void onError(Exception e) {
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
                allMembers = members;
                filteredMembers = new ArrayList<>(members);
                loadUserDataForMembers();
            }

            @Override
            public void onError(Exception e) {
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
                    adapter.updateMembers(filteredMembers);
                }

                @Override
                public void onError(Exception e) {
                    // If can't load user data, use userId as fallback
                    member.userName = member.userId;
                    member.userEmail = "";
                    member.avatarImageId = "";
                    adapter.updateMembers(filteredMembers);
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

        if (isOwnProfile) {
            // Show limited menu for own profile
            showMemberMenu(member, false);
        } else if (isAdminOrOwner) {
            // Show full menu for admin/owner
            showMemberMenu(member, true);
        } else {
            // Show limited menu for regular members
            showMemberMenu(member, false);
        }
    }

    private void showMemberMenu(Member member, boolean isAdminOrOwner) {
        // Determine current user's role and target member role
        String myRole = currentUserMember != null ? currentUserMember.role : null;
        String targetRole = member.role;
        boolean isSelf = member.userId.equals(currentUserId);

        List<String> items = new ArrayList<>();
        items.add("Thông tin");
        items.add("Bài đăng");

        if (!isSelf) {
            if ("owner".equals(myRole)) {
                // Owner: can manage admins and members
                if (!"owner".equals(targetRole)) {
                    items.add("Nâng quyền"); // toggle admin/member
                    items.add("Kick");
                }
            } else if ("admin".equals(myRole)) {
                // Admin: can only act on members; cannot act on owner/admin
                if ("member".equals(targetRole)) {
                    items.add("Nâng quyền"); // promote to admin
                    items.add("Kick");
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
            case "Nâng quyền":
                promoteMember(member);
                break;
            case "Kick":
                kickMember(member);
                break;
        }
    }

    private void showMemberInfo(Member member) {
        // TODO: Implement show member info
        Toast.makeText(this, "Hiển thị thông tin thành viên: " + member.userId, Toast.LENGTH_SHORT).show();
    }

    private void showMemberPosts(Member member) {
        // TODO: Implement show member posts
        Toast.makeText(this, "Hiển thị bài đăng của: " + member.userId, Toast.LENGTH_SHORT).show();
    }

    private void promoteMember(Member member) {
        // TODO: Implement promote member
        Toast.makeText(this, "Nâng quyền thành viên: " + member.userId, Toast.LENGTH_SHORT).show();
    }

    private void kickMember(Member member) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận kick thành viên")
                .setMessage("Bạn có chắc chắn muốn kick thành viên này khỏi nhóm không?")
                .setPositiveButton("Kick", (dialog, which) -> {
                    // TODO: Implement kick member
                    Toast.makeText(this, "Đã kick thành viên: " + member.userId, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
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
