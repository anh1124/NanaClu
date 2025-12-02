package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.repository.GroupRepository;

import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class GroupPendingMembersActivity extends AppCompatActivity {
    private String groupId;
    private GroupRepository groupRepository;
    private UserSelectAdapter adapter;
    private final List<String> pendingIds = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_pending_members);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_pending_members));
        }

        groupId = getIntent().getStringExtra("groupId");
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        TextView tvEmpty = findViewById(R.id.tvEmpty);
        ListView listView = findViewById(R.id.listView);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        adapter = new UserSelectAdapter(this, pendingIds, currentUserId);
        listView.setAdapter(adapter);
        listView.setEmptyView(tvEmpty);

        Button btnApprove = findViewById(R.id.btnApprove);
        Button btnReject = findViewById(R.id.btnReject);
        CheckBox cbSelectAll = findViewById(R.id.cbSelectAll);

        btnApprove.setOnClickListener(v -> actOnSelected(true));
        btnReject.setOnClickListener(v -> actOnSelected(false));

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adapter.setSelectedAll(isChecked);
        });

        loadPending();
    }

    private void loadPending() {
        groupRepository.getPendingUsers(groupId, new GroupRepository.IdsCallback() {
            @Override
            public void onSuccess(List<String> ids) {
                pendingIds.clear();
                pendingIds.addAll(ids);
                adapter.setItems(pendingIds);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupPendingMembersActivity.this, getString(R.string.error_loading_list_clean, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actOnSelected(boolean approve) {
        // Get selected user IDs from adapter
        List<String> selectedUserIds = adapter.getSelected();

        if (selectedUserIds.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một thành viên", Toast.LENGTH_SHORT).show();
            return;
        }

        // Process each selected user with proper callback handling
        final int totalSelected = selectedUserIds.size();
        final int[] processedCount = {0};
        final boolean[] hasError = {false};

        for (String userId : selectedUserIds) {
            if (approve) {
                groupRepository.approvePendingUser(groupId, userId, new GroupRepository.UpdateCallback() {
                    @Override
                    public void onSuccess() {
                        processedCount[0]++;
                        if (processedCount[0] == totalSelected) {
                            if (!hasError[0]) {
                                Toast.makeText(GroupPendingMembersActivity.this, getString(R.string.toast_approved_clean), Toast.LENGTH_SHORT).show();
                            }
                            loadPending(); // Refresh the list
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        hasError[0] = true;
                        processedCount[0]++;
                        Toast.makeText(GroupPendingMembersActivity.this, getString(R.string.error_action_clean, e.getMessage()), Toast.LENGTH_SHORT).show();
                        if (processedCount[0] == totalSelected) {
                            loadPending(); // Refresh the list even on error
                        }
                    }
                });
            } else {
                groupRepository.rejectPendingUser(groupId, userId, new GroupRepository.UpdateCallback() {
                    @Override
                    public void onSuccess() {
                        processedCount[0]++;
                        if (processedCount[0] == totalSelected) {
                            if (!hasError[0]) {
                                Toast.makeText(GroupPendingMembersActivity.this, getString(R.string.toast_rejected_clean), Toast.LENGTH_SHORT).show();
                            }
                            loadPending(); // Refresh the list
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        hasError[0] = true;
                        processedCount[0]++;
                        Toast.makeText(GroupPendingMembersActivity.this, getString(R.string.error_action_clean, e.getMessage()), Toast.LENGTH_SHORT).show();
                        if (processedCount[0] == totalSelected) {
                            loadPending(); // Refresh the list even on error
                        }
                    }
                });
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}