package com.example.nanaclu.ui.group;

import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.repository.GroupRepository;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class GroupBlockedUsersActivity extends AppCompatActivity {
    private String groupId;
    private GroupRepository groupRepository;
    private ArrayAdapter<String> adapter;
    private final List<String> blockedIds = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_blocked_users);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Đã chặn");
        }

        groupId = getIntent().getStringExtra("groupId");
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        ListView listView = findViewById(R.id.listView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, blockedIds);
        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String userId = blockedIds.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Bỏ chặn thành viên")
                    .setMessage("Bạn có chắc muốn bỏ chặn người dùng này?")
                    .setPositiveButton("Bỏ chặn", (dialog, which) -> unblock(userId))
                    .setNegativeButton("Hủy", null)
                    .show();
            return true;
        });

        loadBlocked();
    }

    private void loadBlocked() {
        groupRepository.getBlockedUsers(groupId, new GroupRepository.IdsCallback() {
            @Override
            public void onSuccess(List<String> ids) {
                blockedIds.clear();
                blockedIds.addAll(ids);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupBlockedUsersActivity.this, "Lỗi tải danh sách: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unblock(String userId) {
        groupRepository.unblockUser(groupId, userId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(GroupBlockedUsersActivity.this, "Đã bỏ chặn", Toast.LENGTH_SHORT).show();
                loadBlocked();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupBlockedUsersActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

