package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.repository.PostRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class PendingPostsActivity extends AppCompatActivity implements PendingPostAdapter.PendingPostActionListener {

    private String groupId;
    private GroupRepository groupRepository;
    private PostRepository postRepository;
    private RecyclerView recyclerView;
    private View emptyView;
    private PendingPostAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_posts);

        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, "Thiếu groupId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
        postRepository = new PostRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupRecycler();
        checkPermissionAndLoad();
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Duyệt bài viết");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PendingPostAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);
    }

    private void checkPermissionAndLoad() {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member member) {
                if (member == null || member.role == null || !("owner".equals(member.role) || "admin".equals(member.role))) {
                    Toast.makeText(PendingPostsActivity.this, "Bạn không có quyền truy cập", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                loadPendingPosts();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(PendingPostsActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadPendingPosts() {
        postRepository.getPendingPosts(groupId, new PostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Post> posts) {
                adapter.setItems(posts);
                updateEmptyState(posts.isEmpty());
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(PendingPostsActivity.this, "Lỗi tải danh sách: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEmptyState(boolean empty) {
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onApprove(Post post) {
        postRepository.approvePost(groupId, post.postId, new PostRepository.PostCallback() {
            @Override
            public void onSuccess(Post p) {
                Toast.makeText(PendingPostsActivity.this, "Đã duyệt bài", Toast.LENGTH_SHORT).show();
                loadPendingPosts();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(PendingPostsActivity.this, "Lỗi duyệt bài: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onReject(Post post) {
        postRepository.rejectPost(groupId, post.postId, new PostRepository.PostCallback() {
            @Override
            public void onSuccess(Post p) {
                Toast.makeText(PendingPostsActivity.this, "Đã từ chối bài", Toast.LENGTH_SHORT).show();
                loadPendingPosts();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(PendingPostsActivity.this, "Lỗi từ chối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}


