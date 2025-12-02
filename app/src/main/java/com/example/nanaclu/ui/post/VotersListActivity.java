package com.example.nanaclu.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VotersListActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_POST_ID = "postId";

    private RecyclerView recyclerView;
    private SimpleUserAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voters_list);
        getSupportActionBar();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleUserAdapter(new ArrayList<>(), userId -> {
            Intent i = new Intent(this, com.example.nanaclu.ui.profile.ProfileActivity.class);
            i.putExtra("userId", userId);
            startActivity(i);
        });
        recyclerView.setAdapter(adapter);

        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String postId = getIntent().getStringExtra(EXTRA_POST_ID);
        loadVoters(groupId, postId);
    }

    private void loadVoters(String groupId, String postId) {
        if (groupId == null || postId == null) { finish(); return; }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("options")
                .get()
                .addOnSuccessListener(optionsSnap -> {
                    Set<String> voterIds = new HashSet<>();
                    List<com.google.android.gms.tasks.Task<?>> tasks = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot opt : optionsSnap.getDocuments()) {
                        com.google.android.gms.tasks.Task<?> t = opt.getReference().collection("votes").get()
                                .addOnSuccessListener(votesSnap -> {
                                    for (com.google.firebase.firestore.DocumentSnapshot v : votesSnap.getDocuments()) {
                                        voterIds.add(v.getId());
                                    }
                                });
                        tasks.add(t);
                    }
                    com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                            .addOnSuccessListener(done -> fetchUsers(new ArrayList<>(voterIds)))
                            .addOnFailureListener(e -> finish());
                })
                .addOnFailureListener(e -> finish());
    }

    private void fetchUsers(List<String> userIds) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<SimpleUser> users = new ArrayList<>();
        List<com.google.android.gms.tasks.Task<?>> tasks = new ArrayList<>();
        for (String uid : userIds) {
            tasks.add(db.collection("users").document(uid).get().addOnSuccessListener(u -> {
                String name = u.getString("displayName");
                if (name == null || name.isEmpty()) name = u.getString("email");
                users.add(new SimpleUser(uid, name != null ? name : uid));
            }));
        }
        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(done -> adapter.setItems(users))
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi tải danh sách", Toast.LENGTH_SHORT).show());
    }

    public static class SimpleUser {
        public final String id;
        public final String name;
        public SimpleUser(String id, String name) { this.id = id; this.name = name; }
    }

    public interface OnUserClick { void onClick(String userId); }

    public static class SimpleUserAdapter extends RecyclerView.Adapter<SimpleUserViewHolder> {
        private final List<SimpleUser> items;
        private final OnUserClick onUserClick;
        public SimpleUserAdapter(List<SimpleUser> items, OnUserClick onUserClick) {
            this.items = items; this.onUserClick = onUserClick;
        }
        public void setItems(List<SimpleUser> newItems) { items.clear(); items.addAll(newItems); notifyDataSetChanged(); }
        @Override public SimpleUserViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new SimpleUserViewHolder(v);
        }
        @Override public void onBindViewHolder(SimpleUserViewHolder holder, int position) {
            SimpleUser u = items.get(position);
            ((android.widget.TextView) holder.itemView.findViewById(android.R.id.text1)).setText(u.name);
            holder.itemView.setOnClickListener(v -> onUserClick.onClick(u.id));
        }
        @Override public int getItemCount() { return items.size(); }
    }

    public static class SimpleUserViewHolder extends RecyclerView.ViewHolder {
        public SimpleUserViewHolder(View itemView) { super(itemView); }
    }
}
