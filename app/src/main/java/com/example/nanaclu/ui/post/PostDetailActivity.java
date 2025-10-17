package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private TextView tvAuthor, tvTime, tvContent, tvShowMore, tvLikeCount;
    private ViewGroup layoutTextControls;
    private android.widget.ImageView imgAuthorAvatar;
    private androidx.constraintlayout.widget.ConstraintLayout imageArea;
    private LinearLayout btnLike;
    private ImageView ivLike;

    private String groupId;
    private String postId;
    private boolean isTextExpanded = false;

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
        tvShowMore = findViewById(R.id.tvShowMore);
        layoutTextControls = findViewById(R.id.layoutTextControls);
        imgAuthorAvatar = findViewById(R.id.imgAuthorAvatar);
        imageArea = findViewById(R.id.imageArea);
        btnLike = findViewById(R.id.btnLike);
        ivLike = findViewById(R.id.ivLike);
        tvLikeCount = findViewById(R.id.tvLikeCount);

        rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentsAdapter(new ArrayList<>());
        rvComments.setAdapter(adapter);

        EditText edtComment = findViewById(R.id.edtComment);
        View btnSend = findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            addComment(text);
            edtComment.setText("");
        });

        // Setup like button
        setupLikeButton();

        loadPost();
        loadComments();
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
                    setupExpandableContent(post.content);
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

    private void setupExpandableContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            tvContent.setVisibility(View.GONE);
            layoutTextControls.setVisibility(View.GONE);
            return;
        }

        tvContent.setVisibility(View.VISIBLE);
        
        // First set maxLines to check if text is truncated
        tvContent.setMaxLines(6);
        tvContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvContent.setText(content);

        // Check if text is longer than 6 lines by measuring
        tvContent.post(() -> {
            // Get the line count and check if text is truncated
            int lineCount = tvContent.getLineCount();
            boolean isTextTruncated = tvContent.getLayout() != null && 
                tvContent.getLayout().getEllipsisCount(lineCount - 1) > 0;
            
            // Multiple conditions to show "Xem thêm" button
            boolean shouldShowButton = (lineCount >= 6 && isTextTruncated) || 
                                     content.length() > 300 || 
                                     (lineCount >= 6 && content.length() > 200);
            
            if (shouldShowButton) {
                // Text is longer than 6 lines or very long, show controls area
                layoutTextControls.setVisibility(View.VISIBLE);
                setUnderlinedText(tvShowMore, "Xem thêm");
                isTextExpanded = false;
                
                // Set up click listener for expand/collapse
                tvShowMore.setOnClickListener(v -> toggleTextExpansion());
            } else {
                // Text is short enough, hide controls area
                layoutTextControls.setVisibility(View.GONE);
                tvContent.setMaxLines(Integer.MAX_VALUE);
                tvContent.setEllipsize(null);
            }
        });
    }

    private void toggleTextExpansion() {
        if (isTextExpanded) {
            // Collapse text
            tvContent.setMaxLines(6);
            tvContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
            setUnderlinedText(tvShowMore, "Xem thêm");
            isTextExpanded = false;
        } else {
            // Expand text
            tvContent.setMaxLines(Integer.MAX_VALUE);
            tvContent.setEllipsize(null);
            setUnderlinedText(tvShowMore, "Thu gọn");
            isTextExpanded = true;
        }
    }

    private void setUnderlinedText(TextView textView, String text) {
        android.text.SpannableString spannableString = new android.text.SpannableString(text);
        spannableString.setSpan(new android.text.style.UnderlineSpan(), 0, text.length(), 0);
        textView.setText(spannableString);
    }

    private void setupLikeButton() {
        if (groupId == null || postId == null) return;
        
        // Get current user ID
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() 
            : null;
        
        if (currentUserId == null) {
            btnLike.setVisibility(View.GONE);
            return;
        }

        // Load initial like state and count
        loadLikeStateAndCount(currentUserId);

        // Setup click listener
        btnLike.setOnClickListener(v -> {
            btnLike.setEnabled(false);
            toggleLike(currentUserId);
        });
    }

    private void loadLikeStateAndCount(String currentUserId) {
        // Check if post is liked by current user
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("likes").document(currentUserId)
                .get()
                .addOnSuccessListener(likeDoc -> {
                    boolean isLiked = likeDoc.exists();
                    ivLike.setImageResource(isLiked ? R.drawable.heart1 : R.drawable.heart0);
                })
                .addOnFailureListener(e -> {
                    ivLike.setImageResource(R.drawable.heart0);
                });

        // Load like count
        fetchAndUpdateLikeCount();
    }

    private void toggleLike(String currentUserId) {
        // Check current like state
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("likes").document(currentUserId)
                .get()
                .addOnSuccessListener(likeDoc -> {
                    boolean isLiked = likeDoc.exists();
                    
                    if (isLiked) {
                        // Unlike post
                        db.collection("groups").document(groupId)
                                .collection("posts").document(postId)
                                .collection("likes").document(currentUserId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    ivLike.setImageResource(R.drawable.heart0);
                                    fetchAndUpdateLikeCount();
                                    btnLike.setEnabled(true);
                                })
                                .addOnFailureListener(e -> {
                                    btnLike.setEnabled(true);
                                });
                    } else {
                        // Like post
                        db.collection("groups").document(groupId)
                                .collection("posts").document(postId)
                                .collection("likes").document(currentUserId)
                                .set(new java.util.HashMap<String, Object>())
                                .addOnSuccessListener(aVoid -> {
                                    ivLike.setImageResource(R.drawable.heart1);
                                    fetchAndUpdateLikeCount();
                                    btnLike.setEnabled(true);
                                })
                                .addOnFailureListener(e -> {
                                    btnLike.setEnabled(true);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    btnLike.setEnabled(true);
                });
    }

    private void fetchAndUpdateLikeCount() {
        // Count total likes
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("likes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int likeCount = querySnapshot.size();
                    tvLikeCount.setText(String.valueOf(likeCount));
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PostDetailActivity", "Error fetching like count", e);
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
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .add(data);
    }

    // ----- Simple data holder for comments -----
    public static class Comment {
        public String commentId;
        public String text;
        public Timestamp createdAt;
        public Comment() {}
    }

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.VH> {
        private final List<Comment> items;
        CommentsAdapter(List<Comment> items) { this.items = items; }
        void setItems(List<Comment> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_comment, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos)); }
        @Override public int getItemCount() { return items.size(); }
        
        static class VH extends RecyclerView.ViewHolder {
            TextView tvText;
            
            VH(@NonNull View itemView) { super(itemView);
                tvText = itemView.findViewById(R.id.tvCommentText);
            }
            
            void bind(Comment c) {
                tvText.setText(c.text);
            }
        }
    }
}

