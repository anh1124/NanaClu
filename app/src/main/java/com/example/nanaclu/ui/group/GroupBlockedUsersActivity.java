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

import android.widget.Button;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class GroupBlockedUsersActivity extends AppCompatActivity {
    private String groupId;
    private GroupRepository groupRepository;
    private UserSelectAdapter adapter;
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

        TextView tvEmpty = findViewById(R.id.tvEmpty);
        ListView listView = findViewById(R.id.listView);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        adapter = new UserSelectAdapter(this, blockedIds, currentUserId);
        listView.setAdapter(adapter);
        listView.setEmptyView(tvEmpty);

        Button btnUnblock = findViewById(R.id.btnUnblock);
        btnUnblock.setOnClickListener(v -> actUnblockSelected());

        loadBlocked();
    }

    private void loadBlocked() {
        groupRepository.getBlockedUsers(groupId, new GroupRepository.IdsCallback() {
            @Override
            public void onSuccess(List<String> ids) {
                blockedIds.clear();
                blockedIds.addAll(ids);
                adapter.setItems(blockedIds);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupBlockedUsersActivity.this, getString(R.string.error_loading_list_clean, e.getMessage()), Toast.LENGTH_SHORT).show();
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

    private void actUnblockSelected() {
        List<String> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Chưa chọn thành viên", Toast.LENGTH_SHORT).show();
            return;
        }
        final int total = selected.size();
        final int[] done = {0};
        for (String userId : selected) {
            groupRepository.unblockUser(groupId, userId, new GroupRepository.UpdateCallback() {
                @Override
                public void onSuccess() {
                    done[0]++;
                    if (done[0] == total) {
                        Toast.makeText(GroupBlockedUsersActivity.this, R.string.toast_unblocked_clean, Toast.LENGTH_SHORT).show();
                        loadBlocked();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(GroupBlockedUsersActivity.this, getString(R.string.error_action_clean, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

