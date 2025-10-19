package com.example.nanaclu.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.ui.adapter.UserSearchAdapter;
import com.example.nanaclu.ui.profile.ProfileActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity tìm kiếm người dùng
 * Real-time search với pagination
 */
public class SearchUsersActivity extends AppCompatActivity {

    private TextInputEditText editTextSearch;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private UserSearchAdapter adapter;
    private UserRepository userRepository;
    private String lastQuery = "";
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private com.google.firebase.firestore.DocumentSnapshot lastDocumentSnapshot = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users);

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize views
        editTextSearch = findViewById(R.id.editTextSearch);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        // Initialize repository
        userRepository = new UserRepository(FirebaseFirestore.getInstance());

        // Setup RecyclerView
        adapter = new UserSearchAdapter(new ArrayList<>(), this::openUserProfile);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Setup search
        setupSearch();

        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener(this::refreshSearch);

        // Setup scroll listener for pagination
        setupScrollListener();

        // Show empty state initially
        showEmptyState("Nhập tên để tìm kiếm người dùng");
    }

    private void setupSearch() {
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.equals(lastQuery)) return;

                lastQuery = query;
                
                if (query.isEmpty()) {
                    // Clear results
                    adapter.setUsers(new ArrayList<>());
                    showEmptyState("Nhập tên để tìm kiếm người dùng");
                } else {
                    // Search users
                    searchUsers(query, true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Load more when scrolling near bottom
                if (!isLoading && hasMoreData && 
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 2) {
                    searchUsers(lastQuery, false);
                }
            }
        });
    }

    private void searchUsers(String query, boolean isNewSearch) {
        if (query.isEmpty() || isLoading) return;

        isLoading = true;
        if (isNewSearch) {
            showProgress(true);
            lastDocumentSnapshot = null;
            hasMoreData = true;
        }

        // Tạo Firestore query
        Query firestoreQuery = FirebaseFirestore.getInstance()
                .collection("users")
                .whereGreaterThanOrEqualTo("displayName", query)
                .whereLessThan("displayName", query + "\uf8ff")
                .orderBy("displayName")
                .limit(10);

        // Nếu có lastDocumentSnapshot (pagination), thêm startAfter
        if (lastDocumentSnapshot != null && !isNewSearch) {
            firestoreQuery = firestoreQuery.startAfter(lastDocumentSnapshot);
        }

        firestoreQuery.get()
                .addOnSuccessListener(querySnapshot -> {
                    isLoading = false;
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);

                    if (querySnapshot.isEmpty()) {
                        if (isNewSearch) {
                            adapter.setUsers(new ArrayList<>());
                            showEmptyState("Không tìm thấy người dùng nào");
                        }
                        hasMoreData = false;
                        return;
                    }

                    List<User> users = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null && user.userId != null) {
                            users.add(user);
                        }
                    }

                    if (isNewSearch) {
                        adapter.setUsers(users);
                        showResults(true);
                    } else {
                        adapter.addUsers(users);
                    }

                    // Update pagination
                    if (!querySnapshot.getDocuments().isEmpty()) {
                        lastDocumentSnapshot = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    }
                    
                    if (users.size() < 10) {
                        hasMoreData = false;
                    }
                })
                .addOnFailureListener(e -> {
                    isLoading = false;
                    showProgress(false);
                    swipeRefresh.setRefreshing(false);
                    
                    android.util.Log.e("SearchUsersActivity", "Search failed", e);
                    Toast.makeText(this, "Lỗi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void refreshSearch() {
        if (lastQuery.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        searchUsers(lastQuery, true);
    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showResults(boolean show) {
        if (show) {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }
}
