package com.example.nanaclu.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
 * Fragment hiển thị danh sách bạn bè (accepted friends)
 */
public class FriendsListFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private UserSearchAdapter adapter;
    private FriendshipRepository friendshipRepository;
    private UserRepository userRepository;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        progressBar = view.findViewById(R.id.progressBar);

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
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener(this::loadFriends);

        // Load friends initially
        loadFriends();
    }

    private void loadFriends() {
        if (currentUserId == null) return;

        showProgress(true);
        
        friendshipRepository.listFriends(currentUserId, 50) // Load up to 50 friends
                .addOnSuccessListener(friendIds -> {
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);
                    
                    if (friendIds.isEmpty()) {
                        showEmptyState();
                        return;
                    }
                    
                    // Load user details for each friend
                    loadUserDetails(friendIds);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Lỗi tải danh sách bạn bè: " + e.getMessage());
                });
    }

    private void loadUserDetails(List<String> friendIds) {
        List<User> friends = new ArrayList<>();
        int total = friendIds.size();
        int[] loaded = {0}; // Array để có thể modify trong lambda

        if (total == 0) {
            adapter.setUsers(friends);
            showEmptyState();
            return;
        }

        for (String friendId : friendIds) {
            userRepository.getUserById(friendId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        friends.add(user);
                    }
                    loaded[0]++;
                    
                    // Khi load xong tất cả users
                    if (loaded[0] == total) {
                        adapter.setUsers(friends);
                        showResults(friends.size() > 0);
                    }
                }

                @Override
                public void onError(Exception e) {
                    loaded[0]++;
                    
                    // Khi load xong tất cả users (kể cả lỗi)
                    if (loaded[0] == total) {
                        adapter.setUsers(friends);
                        showResults(friends.size() > 0);
                    }
                }
            });
        }
    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState() {
        tvEmpty.setText("Bạn chưa có bạn bè nào");
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
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Refresh data when fragment becomes visible
     */
    @Override
    public void onResume() {
        super.onResume();
        if (currentUserId != null) {
            loadFriends();
        }
    }
}
