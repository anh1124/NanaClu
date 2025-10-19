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
import com.example.nanaclu.ui.adapter.FriendAdapter;
import com.example.nanaclu.ui.profile.ProfileActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment hiển thị danh sách lời mời kết bạn (incoming và outgoing)
 */
public class FriendRequestsFragment extends Fragment implements FriendAdapter.OnFriendActionListener {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private FriendAdapter adapter;
    private FriendshipRepository friendshipRepository;
    private UserRepository userRepository;
    private String currentUserId;

    private List<User> allUsers = new ArrayList<>();
    private List<FriendAdapter.RequestType> allRequestTypes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_requests, container, false);
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
        adapter = new FriendAdapter(allUsers, allRequestTypes, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener(this::loadFriendRequests);

        // Load friend requests initially
        loadFriendRequests();
    }

    private void loadFriendRequests() {
        if (currentUserId == null) return;

        showProgress(true);
        allUsers.clear();
        allRequestTypes.clear();
        loadingTasks = 0; // Reset counter

        // Load both incoming and outgoing requests
        loadIncomingRequests();
        loadOutgoingRequests();
    }

    private void loadIncomingRequests() {
        android.util.Log.d("FriendRequestsFragment", "Loading incoming requests for user: " + currentUserId);
        friendshipRepository.listIncomingRequests(currentUserId, 50)
                .addOnSuccessListener(friendIds -> {
                    android.util.Log.d("FriendRequestsFragment", "Incoming requests loaded: " + friendIds.size() + " requests");
                    if (!friendIds.isEmpty()) {
                        android.util.Log.d("FriendRequestsFragment", "Incoming friend IDs: " + friendIds);
                        loadUserDetails(friendIds, FriendAdapter.RequestType.INCOMING, () -> checkLoadingComplete());
                    } else {
                        checkLoadingComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FriendRequestsFragment", "Failed to load incoming requests", e);
                    checkLoadingComplete();
                });
    }

    private void loadOutgoingRequests() {
        friendshipRepository.listOutgoingRequests(currentUserId, 50)
                .addOnSuccessListener(friendIds -> {
                    if (!friendIds.isEmpty()) {
                        loadUserDetails(friendIds, FriendAdapter.RequestType.OUTGOING, () -> checkLoadingComplete());
                    } else {
                        checkLoadingComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FriendRequestsFragment", "Failed to load outgoing requests", e);
                    checkLoadingComplete();
                });
    }

    private int loadingTasks = 0;
    private void checkLoadingComplete() {
        loadingTasks++;
        android.util.Log.d("FriendRequestsFragment", "Loading task completed. Total tasks: " + loadingTasks + "/2");
        if (loadingTasks >= 2) { // 2 tasks: incoming + outgoing
            loadingTasks = 0;
            showProgress(false);
            swipeRefresh.setRefreshing(false);
            
            android.util.Log.d("FriendRequestsFragment", "All loading completed. Total users: " + allUsers.size());
            if (allUsers.isEmpty()) {
                showEmptyState();
            } else {
                adapter.setUsers(allUsers, allRequestTypes);
                showResults(true);
            }
        }
    }

    private void loadUserDetails(List<String> friendIds, FriendAdapter.RequestType requestType, Runnable onComplete) {
        if (friendIds.isEmpty()) {
            onComplete.run();
            return;
        }

        int total = friendIds.size();
        int[] loaded = {0}; // Array để có thể modify trong lambda

        for (String friendId : friendIds) {
            userRepository.getUserById(friendId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        // Kiểm tra xem user đã tồn tại chưa để tránh duplicate
                        boolean exists = false;
                        for (User existingUser : allUsers) {
                            if (existingUser.userId != null && existingUser.userId.equals(user.userId)) {
                                exists = true;
                                break;
                            }
                        }
                        
                        if (!exists) {
                            allUsers.add(user);
                            allRequestTypes.add(requestType);
                        }
                    }
                    loaded[0]++;
                    
                    // Khi load xong tất cả users
                    if (loaded[0] == total) {
                        onComplete.run();
                    }
                }

                @Override
                public void onError(Exception e) {
                    loaded[0]++;
                    
                    // Khi load xong tất cả users (kể cả lỗi)
                    if (loaded[0] == total) {
                        onComplete.run();
                    }
                }
            });
        }
    }

    @Override
    public void onAcceptFriendRequest(String userId) {
        showProgress(true);
        friendshipRepository.acceptFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    adapter.removeUser(userId);
                    Toast.makeText(getContext(), "Đã chấp nhận lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    android.util.Log.e("FriendRequestsFragment", "Failed to accept friend request", e);
                    Toast.makeText(getContext(), "Lỗi chấp nhận lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDeclineFriendRequest(String userId) {
        showProgress(true);
        friendshipRepository.declineFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    adapter.removeUser(userId);
                    Toast.makeText(getContext(), "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    android.util.Log.e("FriendRequestsFragment", "Failed to decline friend request", e);
                    Toast.makeText(getContext(), "Lỗi từ chối lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onCancelFriendRequest(String userId) {
        showProgress(true);
        friendshipRepository.cancelFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    adapter.removeUser(userId);
                    Toast.makeText(getContext(), "Đã hủy lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    android.util.Log.e("FriendRequestsFragment", "Failed to cancel friend request", e);
                    Toast.makeText(getContext(), "Lỗi hủy lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onUserClick(String userId) {
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState() {
        tvEmpty.setText("Không có lời mời kết bạn nào");
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
            loadFriendRequests();
        }
    }
}
