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
            getSupportActionBar().setTitle("Chuyển chủ sở hữu");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        recyclerView = findViewById(R.id.recyclerView);
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupMembersAdapter(members, this::onMemberClick);
        recyclerView.setAdapter(adapter);
        
        // Hide search view for this activity
        findViewById(R.id.searchView).setVisibility(android.view.View.GONE);
    }

    private void loadMembers() {
        groupRepository.getGroupMembers(groupId, new GroupRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Member> allMembers) {
                // Filter out current user (owner)
                members.clear();
                for (Member member : allMembers) {
                    if (!member.userId.equals(currentUserId)) {
                        members.add(member);
                    }
                }
                adapter.updateMembers(members);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TransferOwnershipActivity.this, "Lỗi khi tải danh sách thành viên", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onMemberClick(Member member) {
        if (member.userId.equals(currentUserId)) {
            Toast.makeText(this, "Bạn không thể chuyển quyền sở hữu cho chính mình", Toast.LENGTH_SHORT).show();
            return;
        }

        showTransferConfirmation(member);
    }

    private void showTransferConfirmation(Member member) {
        String memberName = member.userName != null && !member.userName.isEmpty() 
            ? member.userName 
            : member.userId;

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận chuyển quyền sở hữu")
                .setMessage("Bạn có chắc chắn muốn chuyển quyền sở hữu nhóm cho " + memberName + " không?")
                .setPositiveButton("Chuyển quyền", (dialog, which) -> {
                    transferOwnership(member);
                })
                .setNegativeButton("Hủy", null)
                .show();
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
