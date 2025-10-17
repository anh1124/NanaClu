package com.example.nanaclu.ui.chat;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.ui.adapter.MemberAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class MembersActivity extends AppCompatActivity {
    private RecyclerView rvMembers;
    private MemberAdapter adapter;
    private MaterialToolbar toolbar;
    private FirebaseFirestore db;
    
    private String chatId;
    private String chatType;
    private String groupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_members);
        
        initViews();
        getIntentData();
        setupToolbar();
        setupRecyclerView();
        loadMembers();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvMembers = findViewById(R.id.rvMembers);
        db = FirebaseFirestore.getInstance();
    }

    private void getIntentData() {
        chatId = getIntent().getStringExtra("chatId");
        chatType = getIntent().getStringExtra("chatType");
        groupId = getIntent().getStringExtra("groupId");
        
        String chatTitle = getIntent().getStringExtra("chatTitle");
        if (chatTitle != null) {
            toolbar.setTitle("Thành viên - " + chatTitle);
        } else {
            toolbar.setTitle("Thành viên");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new MemberAdapter(new ArrayList<>());
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(adapter);
    }

    private void loadMembers() {
        if ("group".equals(chatType) && groupId != null) {
            loadGroupMembers();
        } else {
            loadChatMembers();
        }
    }

    private void loadGroupMembers() {
        db.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> memberIds = (List<String>) documentSnapshot.get("members");
                        if (memberIds != null && !memberIds.isEmpty()) {
                            loadUsersByIds(memberIds);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải thành viên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadChatMembers() {
        db.collection("chats")
                .document(chatId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> participants = (List<String>) documentSnapshot.get("participants");
                        if (participants != null && !participants.isEmpty()) {
                            loadUsersByIds(participants);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải thành viên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUsersByIds(List<String> userIds) {
        List<User> members = new ArrayList<>();
        final int[] loadedCount = {0}; // Track completed requests

        if (userIds.isEmpty()) {
            adapter.updateMembers(members);
            return;
        }

        for (String userId : userIds) {
            db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            User user = userDoc.toObject(User.class);
                            if (user != null) {
                                user.userId = userDoc.getId();
                                members.add(user);
                            }
                        }

                        loadedCount[0]++;
                        // Update adapter when all requests completed
                        if (loadedCount[0] == userIds.size()) {
                            adapter.updateMembers(members);
                        }
                    })
                    .addOnFailureListener(e -> {
                        loadedCount[0]++;
                        // Update adapter even if some requests failed
                        if (loadedCount[0] == userIds.size()) {
                            adapter.updateMembers(members);
                        }
                        Toast.makeText(this, "Lỗi tải thông tin user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
