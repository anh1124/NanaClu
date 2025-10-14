package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;



import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_POST_ID = "postId";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView rvComments;
    private CommentsAdapter adapter;
    private TextView tvAuthor, tvTime, tvContent;
    private android.widget.ImageView imgAuthorAvatar;
    private androidx.constraintlayout.widget.ConstraintLayout imageArea;

    // Debounce storage per comment
    private final Map<String, Integer> pendingLikeIncrements = new HashMap<>();
    private final Map<String, Runnable> pendingRunnables = new HashMap<>();

    private String groupId;
    private String postId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);

        // Setup toolbar with back button
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        tvAuthor = findViewById(R.id.tvAuthor);
        tvTime = findViewById(R.id.tvTime);
        tvContent = findViewById(R.id.tvContent);
        imgAuthorAvatar = findViewById(R.id.imgAuthorAvatar);
        imageArea = findViewById(R.id.imageArea);

        rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentsAdapter(new ArrayList<>(), new CommentActionListener() {
            @Override public void onLikeClicked(Comment c, int position) { likeWithDebounce(c, position); }
        });
        rvComments.setAdapter(adapter);

        EditText edtComment = findViewById(R.id.edtComment);
        View btnSend = findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            addComment(text);
            edtComment.setText("");
        });

        loadPost();
        loadComments();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup pending debounce tasks
        for (Runnable r : pendingRunnables.values()) handler.removeCallbacks(r);
        pendingRunnables.clear();
        pendingLikeIncrements.clear();
    }

    private void loadPost() {
        if (groupId == null || postId == null) return;
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .get()
                .addOnSuccessListener(doc -> {
                    Post post = doc.toObject(Post.class);
                    if (post == null) return;
                    
                    // Load author name
                    loadAuthorName(post.authorId);
                    
                    tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(post.createdAt));
                    tvContent.setVisibility(TextUtils.isEmpty(post.content) ? View.GONE : View.VISIBLE);
                    tvContent.setText(post.content);
                    setupImages(post.imageUrls);
                });
    }

    private void loadAuthorName(String authorId) {
        if (authorId == null) {
            tvAuthor.setText("Unknown User");
            return;
        }
        
        // Load user profile from Firestore
        db.collection("users").document(authorId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String displayName = userDoc.getString("displayName");
                        if (displayName != null && !displayName.isEmpty()) {
                            tvAuthor.setText(displayName);
                        } else {
                            String email = userDoc.getString("email");
                            tvAuthor.setText(email != null ? email : "User");
                        }
                        
                        // Load avatar
                        String photoUrl = userDoc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                .load(photoUrl)
                                .circleCrop()
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .into(imgAuthorAvatar);
                        } else {
                            imgAuthorAvatar.setImageResource(R.mipmap.ic_launcher_round);
                        }
                    } else {
                        tvAuthor.setText("Unknown User");
                        imgAuthorAvatar.setImageResource(R.mipmap.ic_launcher_round);
                    }
                })
                .addOnFailureListener(e -> {
                    tvAuthor.setText("Unknown User");
                    imgAuthorAvatar.setImageResource(R.mipmap.ic_launcher_round);
                });
    }
    
    private void setupImages(List<String> urls) {
        imageArea.removeAllViews();
        if (urls == null || urls.isEmpty()) return;
        // Simple vertical list of images for detail screen; tap to open viewer
        for (int i = 0; i < urls.size(); i++) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(urls.get(i))
                    .placeholder(R.drawable.image_background)
                    .error(R.drawable.image_background)
                    .into(iv);
            final int index = i;
            iv.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, ImageViewerActivity.class);
                intent.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGES, new ArrayList<>(urls));
                intent.putExtra(ImageViewerActivity.EXTRA_INDEX, index);
                startActivity(intent);
            });
            imageArea.addView(iv);
        }
    }

    private void loadComments() {
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .orderBy("createdAt")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    List<Comment> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Comment c = d.toObject(Comment.class);
                        if (c == null) continue;
                        c.commentId = d.getId();
                        list.add(c);
                    }
                    adapter.setItems(list);
                });
    }

    private void addComment(String text) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        data.put("likeCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .add(data);
    }

    private void likeWithDebounce(@NonNull Comment c, int position) {
        // Immediate local update
        c.likeCount += 1;
        adapter.notifyItemChanged(position, "like_only");

        // Track pending increments
        int pending = pendingLikeIncrements.containsKey(c.commentId) ? pendingLikeIncrements.get(c.commentId) : 0;
        pendingLikeIncrements.put(c.commentId, pending + 1);

        // Reset timer
        if (pendingRunnables.containsKey(c.commentId)) {
            handler.removeCallbacks(pendingRunnables.get(c.commentId));
        }
        Runnable r = () -> {
            Integer inc = pendingLikeIncrements.remove(c.commentId);
            pendingRunnables.remove(c.commentId);
            if (inc == null || inc <= 0) return;
            db.collection("groups").document(groupId)
                    .collection("posts").document(postId)
                    .collection("comments").document(c.commentId)
                    .update("likeCount", FieldValue.increment(inc));
        };
        pendingRunnables.put(c.commentId, r);
        handler.postDelayed(r, 3000);
    }

    // ----- Simple data holder for comments -----
    public static class Comment {
        public String commentId;
        public String text;
        public long likeCount;
        public Timestamp createdAt;
        public Comment() {}
    }

    interface CommentActionListener {
        void onLikeClicked(Comment c, int position);
    }

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.VH> {
        private final List<Comment> items;
        private final CommentActionListener listener;
        CommentsAdapter(List<Comment> items, CommentActionListener l) { this.items = items; this.listener = l; }
        void setItems(List<Comment> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_comment, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos), pos, listener); }
        @Override public int getItemCount() { return items.size(); }
        @Override public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && payloads.contains("like_only")) {
                h.tvLikes.setText(String.valueOf(items.get(pos).likeCount));
            } else {
                super.onBindViewHolder(h, pos, payloads);
            }
        }
        static class VH extends RecyclerView.ViewHolder {
            TextView tvText, tvLikes; 
            android.widget.ImageButton btnLike;
            boolean isLiked = false;
            
            VH(@NonNull View itemView) { super(itemView);
                tvText = itemView.findViewById(R.id.tvCommentText);
                tvLikes = itemView.findViewById(R.id.tvLikeCount);
                btnLike = itemView.findViewById(R.id.btnLikeComment);
            }
            
            void bind(Comment c, int position, CommentActionListener l) {
                tvText.setText(c.text);
                tvLikes.setText(String.valueOf(c.likeCount));
                
                // Set initial icon state
                btnLike.setImageResource(isLiked ? R.drawable.heart1 : R.drawable.heart0);
                
                btnLike.setOnClickListener(v -> { 
                    if (l != null) {
                        // Toggle like state
                        isLiked = !isLiked;
                        btnLike.setImageResource(isLiked ? R.drawable.heart1 : R.drawable.heart0);
                        l.onLikeClicked(c, position); 
                    }
                });
            }
        }
    }
}

