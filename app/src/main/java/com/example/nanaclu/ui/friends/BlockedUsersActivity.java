package com.example.nanaclu.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.FriendshipRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.ui.adapter.UserSearchAdapter;
import com.example.nanaclu.ui.profile.ProfileActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity hiển thị danh sách các user đã bị chặn
 */
public class BlockedUsersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private UserSearchAdapter adapter;
    private FriendshipRepository friendshipRepository;
    private UserRepository userRepository;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Get current user
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            showError("Chưa đăng nhập");
            return;
        }

        // Initialize repositories
        friendshipRepository = new FriendshipRepository(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(FirebaseFirestore.getInstance());

        // Setup RecyclerView
        adapter = new UserSearchAdapter(new ArrayList<>(), this::openUserProfile);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener(this::loadBlockedUsers);

        // Load blocked users initially
        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        if (currentUserId == null) return;

        showProgress(true);
        
        // Query friendships where current user is the blocker
        FirebaseFirestore.getInstance()
                .collection("friendships")
                .whereArrayContains("members", currentUserId)
                .whereEqualTo("status", "blocked")
                .whereEqualTo("blockedBy", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);
                    
                    if (querySnapshot.isEmpty()) {
                        showEmptyState();
                        return;
                    }
                    
                    List<String> blockedUserIds = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        com.example.nanaclu.data.model.Friendship friendship = doc.toObject(com.example.nanaclu.data.model.Friendship.class);
                        if (friendship != null && friendship.members != null) {
                            for (String memberId : friendship.members) {
                                if (!memberId.equals(currentUserId)) {
                                    blockedUserIds.add(memberId);
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Load user details for blocked users
                    loadUserDetails(blockedUserIds);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Lỗi tải danh sách người dùng đã chặn: " + e.getMessage());
                });
    }

    private void loadUserDetails(List<String> blockedUserIds) {
        List<User> blockedUsers = new ArrayList<>();
        int total = blockedUserIds.size();
        int[] loaded = {0}; // Array để có thể modify trong lambda

        if (total == 0) {
            adapter.setUsers(blockedUsers);
            showEmptyState();
            return;
        }

        for (String blockedUserId : blockedUserIds) {
            userRepository.getUserById(blockedUserId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        blockedUsers.add(user);
                    }
                    loaded[0]++;
                    
                    // Khi load xong tất cả users
                    if (loaded[0] == total) {
                        adapter.setUsers(blockedUsers);
                        showResults(blockedUsers.size() > 0);
                    }
                }

                @Override
                public void onError(Exception e) {
                    loaded[0]++;
                    
                    // Khi load xong tất cả users (kể cả lỗi)
                    if (loaded[0] == total) {
                        adapter.setUsers(blockedUsers);
                        showResults(blockedUsers.size() > 0);
                    }
                }
            });
        }
    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState() {
        tvEmpty.setText("Bạn chưa chặn ai");
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showResults(boolean hasResults) {
        if (hasResults) {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            showEmptyState();
        }
    }

    private void showError(String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Refresh data when activity becomes visible
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserId != null) {
            loadBlockedUsers();
        }
    }
}
