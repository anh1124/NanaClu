package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Comment;
import com.example.nanaclu.data.repository.CommentRepository;
import com.example.nanaclu.ui.adapter.CommentAdapter;
import com.example.nanaclu.ui.profile.ProfileActivity;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class EventDiscussionFragment extends Fragment {
    
    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_EVENT_ID = "event_id";
    
    private String groupId;
    private String eventId;
    
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private CommentAdapter adapter;
    private EditText etComment;
    private ImageButton btnSend;
    private CommentRepository commentRepository;
    
    public static EventDiscussionFragment newInstance(String groupId, String eventId) {
        EventDiscussionFragment fragment = new EventDiscussionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putString(ARG_EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString(ARG_GROUP_ID);
            eventId = getArguments().getString(ARG_EVENT_ID);
        }
        commentRepository = new CommentRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_event_discussion, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        etComment = view.findViewById(R.id.etComment);
        btnSend = view.findViewById(R.id.btnSend);

        setupRecyclerView();
        setupSendButton();
        setupSwipeRefresh();
        loadComments();
        
        return view;
    }
    
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentAdapter(new ArrayList<>(), null, this::openUserProfile, groupId, eventId);
        recyclerView.setAdapter(adapter);
    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }
    
    private void setupSendButton() {
        btnSend.setOnClickListener(v -> {
            String commentText = etComment.getText().toString().trim();
            if (!commentText.isEmpty()) {
                sendComment(commentText);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadComments);
    }
    
    private void sendComment(String commentText) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            android.widget.Toast.makeText(getContext(), "Bạn cần đăng nhập để bình luận", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        commentRepository.addComment(groupId, eventId, commentText, null)
                .addOnSuccessListener(commentId -> {
                    etComment.setText("");
                    loadComments(); // Reload comments
                })
                .addOnFailureListener(error -> {
                    android.widget.Toast.makeText(getContext(), "Lỗi gửi bình luận: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                });
    }
    
    private void loadComments() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        commentRepository.getComments(groupId, eventId, new CommentRepository.CommentsCallback() {
            @Override
            public void onSuccess(java.util.List<Comment> comments) {
                adapter.updateComments(comments);
                if (!comments.isEmpty()) {
                    recyclerView.scrollToPosition(comments.size() - 1);
                }
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("EventDiscussionFragment", "Error loading comments", e);
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }
        });
    }
    
    public void refreshData() {
        loadComments();
    }
}
