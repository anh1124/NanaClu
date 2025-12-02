package com.example.nanaclu.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.ui.profile.ProfileActivity;
import com.example.nanaclu.data.model.Comment;
import com.example.nanaclu.utils.KeyboardUtils;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.google.firebase.Timestamp;
import com.example.nanaclu.data.repository.CommentRepository;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.model.User;
import com.google.firebase.auth.FirebaseAuth;

import com.example.nanaclu.utils.NoNetworkException;
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
    private CommentRepository commentRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView rvComments;
    private CommentsAdapter adapter;
    private TextView tvAuthor, tvTime, tvContent, tvShowMore, tvLikeCount;
    private ViewGroup layoutTextControls;
    private View btnShare;
    private android.widget.ImageView imgAuthorAvatar;
    private FrameLayout imageArea;
    private FrameLayout videoContainer;
    private ImageView ivVideoThumb, ivPlayOverlay;
    private TextView tvVideoDuration;
    private LinearLayout btnLike;
    private ImageView ivLike;

    // Poll views
    private LinearLayout layoutPoll;
    private LinearLayout layoutPollOptions;
    private TextView tvPollTitle, tvPollDescription, tvPollStatus;
    private com.google.firebase.firestore.ListenerRegistration pollOptionsListener;
    private final java.util.List<com.google.firebase.firestore.ListenerRegistration> myVoteListeners = new java.util.ArrayList<>();

    private String groupId;
    private String postId;
    private String postAuthorId;
    private boolean isTextExpanded = false;
    // Poll flags for menu conditions
    private Boolean pollAnonymousFlag = null;
    private Boolean pollHideResultFlag = null;
    private String currentUserId;
    private String currentUserRole; // owner | admin | member

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);

        commentRepository = new CommentRepository(db);

        // Setup toolbar with back button and menu
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
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
        btnShare = findViewById(R.id.btnShare);
        videoContainer = findViewById(R.id.videoContainer);
        ivVideoThumb = findViewById(R.id.ivVideoThumb);
        ivPlayOverlay = findViewById(R.id.ivPlayOverlay);
        tvVideoDuration = findViewById(R.id.tvVideoDuration);

        // Poll views
        layoutPoll = findViewById(R.id.layoutPoll);
        tvPollTitle = findViewById(R.id.tvPollTitle);
        tvPollDescription = findViewById(R.id.tvPollDescription);
        layoutPollOptions = findViewById(R.id.layoutPollOptions);
        tvPollStatus = findViewById(R.id.tvPollStatus);

        rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentsAdapter(new ArrayList<>(), authorId -> {
            // Open profile when clicking on comment author
            Intent intent = new Intent(PostDetailActivity.this, ProfileActivity.class);
            intent.putExtra("userId", authorId);
            startActivity(intent);
        }, this);
        rvComments.setAdapter(adapter);

        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        EditText edtComment = findViewById(R.id.edtComment);
        View btnSend = findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            
            // Clear the input field first
            edtComment.setText("");
            
            // Hide the keyboard
            KeyboardUtils.hideKeyboard(this, edtComment);
            
            // Add the comment
            addComment(text);
        });

        // Setup like button
        setupLikeButton();

        // Setup share button
        btnShare.setOnClickListener(v -> {
            com.example.nanaclu.utils.ShareLinkUtils.copyPostLink(this, postId);
        });

        loadPost();
        loadComments();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.post_detail_menu, menu);
        // Initially hide extra actions; will update once post & role loaded
        menu.findItem(R.id.action_view_voters).setVisible(false);
        menu.findItem(R.id.action_view_results).setVisible(false);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (pollAnonymousFlag != null) {
            boolean anonymous = Boolean.TRUE.equals(pollAnonymousFlag);
            menu.findItem(R.id.action_view_voters).setVisible(!anonymous);
        }
        if (pollHideResultFlag != null) {
            boolean hideResult = Boolean.TRUE.equals(pollHideResultFlag);
            boolean elevated = "owner".equals(currentUserRole) || "admin".equals(currentUserRole);
            menu.findItem(R.id.action_view_results).setVisible(hideResult && elevated);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { finish(); return true; }
        if (id == R.id.action_report) {
            showReportDialog();
            return true;
        }
        if (id == R.id.action_view_voters) {
            openVotersList();
            return true;
        }
        if (id == R.id.action_view_results) {
            openPollResults();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadPost() {
        android.util.Log.d("PostDetailActivity", "=== START loadPost ===");
        android.util.Log.d("PostDetailActivity", "groupId: " + groupId);
        android.util.Log.d("PostDetailActivity", "postId: " + postId);
        
        if (groupId == null || postId == null) {
            android.util.Log.e("PostDetailActivity", "groupId or postId is null");
            return;
        }
        
        android.util.Log.d("PostDetailActivity", "Loading post from Firestore...");
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .get()
                .addOnSuccessListener(doc -> {
                    android.util.Log.d("PostDetailActivity", "Firestore query completed");
                    android.util.Log.d("PostDetailActivity", "Document exists: " + doc.exists());
                    android.util.Log.d("PostDetailActivity", "Document ID: " + doc.getId());
                    
                    Post post = doc.toObject(Post.class);
                    if (post == null) {
                        android.util.Log.e("PostDetailActivity", "Post is null - document exists: " + doc.exists());
                        android.util.Log.e("PostDetailActivity", "Document data: " + doc.getData());
                        return;
                    }
                    // store author for notices
                    postAuthorId = post.authorId;
                    android.util.Log.d("PostDetailActivity", "Post loaded successfully. postAuthorId: " + postAuthorId);
                    
                    // Load author name
                    loadAuthorName(post.authorId);
                    
                    tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(post.createdAt));
                    setupExpandableContent(post.content);
                    
                    // Setup images or video
                    boolean isPoll = post.type != null && "poll".equals(post.type);
                    if (isPoll) {
                        // Show poll, hide media
                        if (imageArea != null) imageArea.setVisibility(View.GONE);
                        if (videoContainer != null) videoContainer.setVisibility(View.GONE);
                        setupPollDisplay(post);
                        // update flags for menu
                        pollAnonymousFlag = post.pollAnonymous != null && post.pollAnonymous;
                        pollHideResultFlag = post.pollHideResult != null && post.pollHideResult;
                        // load current user's group role
                        if (currentUserId != null) {
                            new com.example.nanaclu.data.repository.GroupRepository(FirebaseFirestore.getInstance())
                                    .getMemberById(groupId, currentUserId, new com.example.nanaclu.data.repository.GroupRepository.MemberCallback() {
                                        @Override
                                        public void onSuccess(com.example.nanaclu.data.model.Member member) {
                                            currentUserRole = member != null ? member.role : null;
                                            invalidateOptionsMenu();
                                        }
                                        @Override
                                        public void onError(Exception e) {
                                            currentUserRole = null;
                                            invalidateOptionsMenu();
                                        }
                                    });
                        } else {
                            invalidateOptionsMenu();
                        }
                    } else {
                        if (post.videoUrl != null && !post.videoUrl.isEmpty()) {
                            setupVideo(post);
                        } else {
                            setupImages(post.imageUrls);
                        }
                    }
                });
    }

    private void clearPollListeners() {
        if (pollOptionsListener != null) { try { pollOptionsListener.remove(); } catch (Exception ignored) {} pollOptionsListener = null; }
        if (!myVoteListeners.isEmpty()) {
            for (com.google.firebase.firestore.ListenerRegistration reg : myVoteListeners) {
                try { if (reg != null) reg.remove(); } catch (Exception ignored) {}
            }
            myVoteListeners.clear();
        }
    }

    private void setupPollDisplay(Post post) {
        if (layoutPoll == null || layoutPollOptions == null) return;
        layoutPoll.setVisibility(View.VISIBLE);
        // Full title & description (no ellipsize)
        tvPollTitle.setText(post.pollTitle != null ? post.pollTitle : "");
        tvPollDescription.setText(post.pollDescription != null ? post.pollDescription : "");

        boolean ended = post.pollDeadline != null && System.currentTimeMillis() > post.pollDeadline;
        if (ended) {
            tvPollStatus.setText("Đã kết thúc");
        } else if (post.pollDeadline != null) {
            java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(this);
            String dateStr = df.format(new java.util.Date(post.pollDeadline));
            tvPollStatus.setText("Kết thúc: " + dateStr);
        } else {
            tvPollStatus.setText("");
        }

        layoutPollOptions.removeAllViews();
        clearPollListeners();

        pollOptionsListener = new com.example.nanaclu.data.repository.PostRepository(FirebaseFirestore.getInstance())
                .listenPollOptions(post.groupId, post.postId, optionDocs -> {
                    layoutPollOptions.removeAllViews();

                    long totalVotes = 0L;
                    for (com.google.firebase.firestore.DocumentSnapshot d : optionDocs) {
                        Long c = d.getLong("voteCount");
                        if (c != null) totalVotes += c;
                    }
                    final boolean hideResult = post.pollHideResult != null && post.pollHideResult;

                    for (com.google.firebase.firestore.DocumentSnapshot d : optionDocs) {
                        String optionId = d.getId();
                        String text = d.getString("text");
                        Long c = d.getLong("voteCount");
                        long count = c != null ? c : 0L;
                        int percent = (totalVotes > 0) ? (int) (count * 100 / totalVotes) : 0;

                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.VERTICAL);
                        int pad = (int) (4 * getResources().getDisplayMetrics().density);
                        row.setPadding(0, pad, 0, pad);

                        LinearLayout firstLine = new LinearLayout(this);
                        firstLine.setOrientation(LinearLayout.HORIZONTAL);
                        firstLine.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        android.widget.CheckBox cb = new android.widget.CheckBox(this);
                        cb.setEnabled(!ended);

                        TextView tvOptionText = new TextView(this);
                        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        tvOptionText.setLayoutParams(lpText);
                        tvOptionText.setText(text != null ? text : "");

                        TextView tvCount = new TextView(this);
                        if (hideResult) {
                            tvCount.setVisibility(View.GONE);
                        } else {
                            tvCount.setVisibility(View.VISIBLE);
                            tvCount.setText(count + " phiếu");
                        }

                        firstLine.addView(cb);
                        firstLine.addView(tvOptionText);
                        firstLine.addView(tvCount);

                        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (4 * getResources().getDisplayMetrics().density));
                        lpBar.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
                        bar.setLayoutParams(lpBar);
                        bar.setMax(100);
                        bar.setProgress(percent);

                        row.addView(firstLine);
                        row.addView(bar);

                        row.setOnClickListener(v -> cb.performClick());
                        cb.setOnClickListener(v -> {
                            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                            if (currentUserId == null) return;
                            if (!com.example.nanaclu.utils.NetworkUtils.isNetworkAvailable(this)) {
                                Toast.makeText(this, "Không có kết nối Internet", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            android.util.Log.d("PollVote", "[Detail] voteOption click: group="+post.groupId+", post="+post.postId+", option="+optionId+", user="+currentUserId);
                            new com.example.nanaclu.data.repository.PostRepository(FirebaseFirestore.getInstance())
                                    .voteOption(post.groupId, post.postId, optionId, currentUserId,
                                            aVoid -> android.util.Log.d("PollVote", "[Detail] vote success for option="+optionId),
                                            e -> android.util.Log.e("PollVote", "[Detail] vote error", e));
                        });

                        // Keep my checked state
                        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                        if (currentUserId != null) {
                            com.google.firebase.firestore.DocumentReference optRef = FirebaseFirestore.getInstance()
                                    .collection("groups").document(post.groupId)
                                    .collection("posts").document(post.postId)
                                    .collection("options").document(optionId);
                            com.google.firebase.firestore.ListenerRegistration reg = optRef
                                    .collection("votes").document(currentUserId)
                                    .addSnapshotListener((snap, err) -> {
                                        if (err != null) {
                                            android.util.Log.e("PollVote", "[Detail] listen my vote error", err);
                                            return;
                                        }
                                        boolean checked = snap != null && snap.exists();
                                        cb.setOnCheckedChangeListener(null);
                                        cb.setChecked(checked);
                                    });
                            myVoteListeners.add(reg);
                        }

                        layoutPollOptions.addView(row);
                    }

                    // Add option button if allowed
                    boolean allowAdd = post.pollAllowAddOption != null && post.pollAllowAddOption && !ended;
                    if (allowAdd) {
                        TextView tvAdd = new TextView(this);
                        tvAdd.setText("+ Thêm lựa chọn");
                        tvAdd.setTextColor(0xFF1976D2);
                        tvAdd.setPadding(0, (int)(8*getResources().getDisplayMetrics().density), 0, 0);
                        tvAdd.setOnClickListener(v -> {
                            android.widget.EditText input = new android.widget.EditText(this);
                            input.setHint("Nhập lựa chọn");
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Thêm lựa chọn")
                                    .setView(input)
                                    .setPositiveButton("Thêm", (dialog, which) -> {
                                        String txt = input.getText() != null ? input.getText().toString().trim() : "";
                                        if (txt.isEmpty()) return;
                                        new com.example.nanaclu.data.repository.PostRepository(FirebaseFirestore.getInstance())
                                                .addPollOption(post.groupId, post.postId, txt,
                                                        aVoid -> android.util.Log.d("PollVote", "[Detail] add option success"),
                                                        e -> android.util.Log.e("PollVote", "[Detail] add option error", e));
                                    })
                                    .setNegativeButton("Hủy", null)
                                    .show();
                        });
                        layoutPollOptions.addView(tvAdd);
                    }
                }, error -> {
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("PostDetailActivity", error);
                    android.util.Log.e("PollVote", "[Detail] listenPollOptions error", error);
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
        
        // Tính kích thước màn hình và max heights
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenHeight = metrics.heightPixels;
        int maxImageHeight = (int) (screenHeight * 0.4f);
        // Khoảng cách giữa các ảnh ~ 1mm
        int spacePx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_MM, 1, metrics);

        java.util.function.Consumer<android.widget.ImageView> commonCenterCrop = iv -> {
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setAdjustViewBounds(true);
        };

        java.util.function.BiConsumer<String, android.widget.ImageView> loadInto = (imageUrl, target) -> {
            Glide.with(this)
                    .load(imageUrl)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                            .placeholder(R.drawable.image_background)
                            .error(R.drawable.image_background))
                    .into(target);
        };

        int count = urls.size();
        if (count == 1) {
            android.widget.ImageView imageView = new android.widget.ImageView(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            imageView.setLayoutParams(lp);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            imageView.setMaxHeight(maxImageHeight);
            loadInto.accept(urls.get(0), imageView);
            imageView.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 0));
            imageArea.addView(imageView);
            return;
        }

        if (count == 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 2; i++) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, maxImageHeight);
                p.weight = 1;
                if (i == 1) p.leftMargin = spacePx;
                iv.setLayoutParams(p);
                commonCenterCrop.accept(iv);
                loadInto.accept(urls.get(i), iv);
                final int index = i;
                iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                row.addView(iv);
            }
            imageArea.addView(row);
            return;
        }

        if (count == 3) {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            
            // Left large image
            android.widget.ImageView leftIv = new android.widget.ImageView(this);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, maxImageHeight);
            leftLp.weight = 1;
            leftIv.setLayoutParams(leftLp);
            commonCenterCrop.accept(leftIv);
            loadInto.accept(urls.get(0), leftIv);
            leftIv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 0));
            
            // Right column with 2 images
            LinearLayout rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, maxImageHeight);
            rightLp.weight = 1;
            rightLp.leftMargin = spacePx;
            rightCol.setLayoutParams(rightLp);
            
            for (int i = 1; i < 3; i++) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
                p.weight = 1;
                if (i == 2) p.topMargin = spacePx;
                iv.setLayoutParams(p);
                commonCenterCrop.accept(iv);
                loadInto.accept(urls.get(i), iv);
                final int index = i;
                iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                rightCol.addView(iv);
            }
            
            container.addView(leftIv);
            container.addView(rightCol);
            imageArea.addView(container);
            return;
        }

        if (count >= 4) {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            
            // Top row: 2 images
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            topLp.weight = 1;
            topRow.setLayoutParams(topLp);
            
            for (int i = 0; i < 2; i++) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
                p.weight = 1;
                if (i == 1) p.leftMargin = spacePx;
                iv.setLayoutParams(p);
                commonCenterCrop.accept(iv);
                loadInto.accept(urls.get(i), iv);
                final int index = i;
                iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                topRow.addView(iv);
            }
            
            // Bottom row: 2 images (or 1 image + overlay if more than 4)
            LinearLayout bottomRow = new LinearLayout(this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            bottomLp.weight = 1;
            bottomLp.topMargin = spacePx;
            bottomRow.setLayoutParams(bottomLp);
            
            // First image in bottom row
            android.widget.ImageView iv2 = new android.widget.ImageView(this);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            p2.weight = 1;
            iv2.setLayoutParams(p2);
            commonCenterCrop.accept(iv2);
            loadInto.accept(urls.get(2), iv2);
            iv2.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 2));
            bottomRow.addView(iv2);
            
            // Second image in bottom row (or overlay if more than 4)
            if (count == 4) {
                // Just show the 4th image
                android.widget.ImageView iv3 = new android.widget.ImageView(this);
                LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
                p3.weight = 1;
                p3.leftMargin = spacePx;
                iv3.setLayoutParams(p3);
                commonCenterCrop.accept(iv3);
                loadInto.accept(urls.get(3), iv3);
                iv3.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 3));
                bottomRow.addView(iv3);
            } else {
                // Show overlay with "+X" for more than 4 images
                FrameLayout overlay = new FrameLayout(this);
                LinearLayout.LayoutParams overlayParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
                overlayParams.weight = 1;
                overlayParams.leftMargin = spacePx;
                overlay.setLayoutParams(overlayParams);
                
                // Background image (4th image)
                android.widget.ImageView overlayIv = new android.widget.ImageView(this);
                overlayIv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                overlayIv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                loadInto.accept(urls.get(3), overlayIv);
                overlay.addView(overlayIv);
                
                // Dim overlay
                View dim = new View(this);
                dim.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                dim.setBackgroundColor(0x80000000);
                overlay.addView(dim);
                
                // "+X" text
                TextView plusText = new TextView(this);
                plusText.setText("+" + (count - 4));
                plusText.setTextColor(0xFFFFFFFF);
                plusText.setTextSize(24);
                plusText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textParams.gravity = android.view.Gravity.CENTER;
                plusText.setLayoutParams(textParams);
                overlay.addView(plusText);
                
                // Click to open image viewer starting from 4th image
                overlay.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 3));
                bottomRow.addView(overlay);
            }
            
            container.addView(topRow);
            container.addView(bottomRow);
            imageArea.addView(container);
        }
    }
    
    private void setupVideo(Post post) {
        // Hide image container
        imageArea.setVisibility(View.GONE);
        
        // Show video container
        videoContainer.setVisibility(View.VISIBLE);
        
        // Load video thumbnail
        Glide.with(this)
            .load(post.videoThumbUrl)
            .apply(new com.bumptech.glide.request.RequestOptions()
                .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                .placeholder(R.drawable.image_background)
                .error(R.drawable.image_background))
            .into(ivVideoThumb);
        
        // Set video duration
        tvVideoDuration.setText(formatDuration(post.videoDurationMs));
        
        // Set click listener to open video player
        videoContainer.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, com.example.nanaclu.ui.video.VideoPlayerActivity.class);
            intent.putExtra("videoUrl", post.videoUrl);
            intent.putExtra("postId", post.postId);
            startActivity(intent);
        });
    }
    
    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0:00";
        
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }
    
    private void openImageViewer(ArrayList<String> urls, int index) {
        android.content.Intent intent = new android.content.Intent(this, ImageViewerActivity.class);
        intent.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGES, urls);
        intent.putExtra(ImageViewerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private void openVotersList() {
        Intent i = new Intent(this, VotersListActivity.class);
        i.putExtra(VotersListActivity.EXTRA_GROUP_ID, groupId);
        i.putExtra(VotersListActivity.EXTRA_POST_ID, postId);
        startActivity(i);
    }

    private void openPollResults() {
        Intent i = new Intent(this, PollResultsActivity.class);
        i.putExtra(PollResultsActivity.EXTRA_GROUP_ID, groupId);
        i.putExtra(PollResultsActivity.EXTRA_POST_ID, postId);
        startActivity(i);
    }

    private void loadComments() {
        if (groupId == null || postId == null) {
            android.util.Log.e("PostDetailActivity", "❌ groupId or postId is null - groupId: " + groupId + ", postId: " + postId);
            return;
        }
        
        android.util.Log.d("PostDetailActivity", "🔄 Loading comments for post: " + postId + " in group: " + groupId);
        
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .orderBy("createdAt")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        android.util.Log.e("PostDetailActivity", "❌ Error loading comments: " + e.getMessage());
                        return;
                    }
                    if (snap == null) {
                        android.util.Log.w("PostDetailActivity", "⚠️ Comments snapshot is null");
                        return;
                    }
                    
                    android.util.Log.d("PostDetailActivity", "📥 Received " + snap.getDocuments().size() + " comments from Firestore");
                    
                    List<Comment> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Comment c = new Comment();
                        c.commentId = d.getId();
                        c.content = d.getString("content"); // Map from "content" to "content"
                        c.createdAt = d.getTimestamp("createdAt");
                        c.authorId = d.getString("authorId");
                        
                        android.util.Log.d("PostDetailActivity", "📝 Comment: " + c.commentId + 
                            " | authorId: " + c.authorId + 
                            " | content: " + (c.content != null ? c.content.substring(0, Math.min(20, c.content.length())) + "..." : "null"));
                        
                        list.add(c);
                    }
                    
                    android.util.Log.d("PostDetailActivity", "✅ Created " + list.size() + " Comment objects, now loading user info...");
                    // Load user info for all comments
                    loadUserInfoForComments(list);
                });
    }

    private void showReportDialog() {
        if (groupId != null && postId != null && postAuthorId != null) {
            com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment reportSheet = 
                com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment.newInstance(
                    groupId, postId, postAuthorId);
            reportSheet.show(getSupportFragmentManager(), "report_bottom_sheet");
        } else {
            Toast.makeText(this, "Không thể báo cáo bài này", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteCommentDialog(Comment comment) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xóa bình luận")
                .setMessage("Bạn có chắc muốn xóa bình luận này?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    deleteComment(comment);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteComment(Comment comment) {
        FirebaseFirestore.getInstance()
                .collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments").document(comment.commentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("PostDetailActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi xóa bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadUserInfoForComments(List<Comment> comments) {
        android.util.Log.d("PostDetailActivity", "👥 Loading user info for " + (comments != null ? comments.size() : 0) + " comments");
        
        if (comments == null || comments.isEmpty()) {
            android.util.Log.d("PostDetailActivity", "⚠️ No comments to load user info for");
            adapter.setItems(comments);
            return;
        }

        com.example.nanaclu.data.repository.UserRepository userRepo = new com.example.nanaclu.data.repository.UserRepository(FirebaseFirestore.getInstance());
        int[] loadedCount = {0};
        
        for (Comment comment : comments) {
            // Skip comments with null authorId
            if (comment.authorId == null || comment.authorId.isEmpty()) {
                android.util.Log.w("PostDetailActivity", "⚠️ Comment " + comment.commentId + " has null/empty authorId");
                comment.authorName = "Người dùng";
                comment.authorAvatar = null;
                loadedCount[0]++;
                if (loadedCount[0] == comments.size()) {
                    android.util.Log.d("PostDetailActivity", "✅ All user info loaded (with nulls), updating adapter");
                    adapter.setItems(comments);
                }
                continue;
            }
            
            android.util.Log.d("PostDetailActivity", "🔄 Loading user info for authorId: " + comment.authorId);
            
            userRepo.getUserById(comment.authorId, new com.example.nanaclu.data.repository.UserRepository.UserCallback() {
                @Override
                public void onSuccess(com.example.nanaclu.data.model.User user) {
                    comment.authorName = user.displayName;
                    comment.authorAvatar = user.photoUrl;
                    loadedCount[0]++;
                    
                    android.util.Log.d("PostDetailActivity", "✅ Loaded user info for " + comment.authorId + 
                        " | name: " + comment.authorName + 
                        " | avatar: " + (comment.authorAvatar != null ? "has_avatar" : "no_avatar") +
                        " | progress: " + loadedCount[0] + "/" + comments.size());
                    
                    if (loadedCount[0] == comments.size()) {
                        android.util.Log.d("PostDetailActivity", "🎉 All user info loaded successfully, updating adapter");
                        adapter.setItems(comments);
                    }
                }

                @Override
                public void onError(Exception e) {
                    android.util.Log.e("PostDetailActivity", "❌ Failed to load user info for " + comment.authorId + ": " + e.getMessage());
                    comment.authorName = "Người dùng";
                    comment.authorAvatar = null;
                    loadedCount[0]++;
                    
                    if (loadedCount[0] == comments.size()) {
                        android.util.Log.d("PostDetailActivity", "⚠️ All user info loaded (with errors), updating adapter");
                        adapter.setItems(comments);
                    }
                }
            });
        }
    }


    private void addComment(String text) {
        android.util.Log.d("PostDetailActivity", "=== START addComment ===");
        KeyboardUtils.hideKeyboard(this);
        android.util.Log.d("PostDetailActivity", "Comment text: " + text);
        android.util.Log.d("PostDetailActivity", "groupId: " + groupId);
        android.util.Log.d("PostDetailActivity", "postId: " + postId);
        android.util.Log.d("PostDetailActivity", "postAuthorId: " + postAuthorId);
        
        if (groupId == null || postId == null) {
            android.util.Log.e("PostDetailActivity", "❌ Cannot add comment, groupId or postId is null");
            return;
        }

        android.util.Log.d("PostDetailActivity", "Adding comment via CommentRepository...");
        commentRepository.addComment(groupId, postId, text, null)
                .addOnSuccessListener(commentId -> {
                    android.util.Log.d("PostDetailActivity", "✅ Comment added successfully with id: " + commentId);
                    // Reload comments to reflect new one
                    loadComments();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PostDetailActivity", "❌ Failed to add comment via CommentRepository", e);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("PostDetailActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi thêm bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ...

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.VH> {
        private final List<Comment> items;
        private final OnCommentClickListener listener;
        private final PostDetailActivity activity;
        
        interface OnCommentClickListener {
            void onAuthorClick(String authorId);
        }
        
        CommentsAdapter(List<Comment> items, OnCommentClickListener listener, PostDetailActivity activity) { 
            this.items = items; 
            this.listener = listener;
            this.activity = activity;
        }
        
        void setItems(List<Comment> list) { 
            android.util.Log.d("PostDetailActivity", "🔄 CommentsAdapter.setItems() called with " + (list != null ? list.size() : 0) + " items");
            items.clear(); 
            items.addAll(list); 
            notifyDataSetChanged();
            android.util.Log.d("PostDetailActivity", "✅ CommentsAdapter updated, itemCount: " + getItemCount());
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_comment, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos), listener, activity); }
        @Override public int getItemCount() { return items.size(); }
        
        static class VH extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvAuthorName, tvTime, tvText;
            
            VH(@NonNull View itemView) { 
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvText = itemView.findViewById(R.id.tvCommentText);
            }
            
            void bind(Comment c, OnCommentClickListener listener, PostDetailActivity activity) {
                android.util.Log.d("PostDetailActivity", "🎨 Binding comment: " + c.commentId + 
                    " | authorName: " + c.authorName + 
                    " | authorId: " + c.authorId + 
                    " | content: " + (c.content != null ? c.content.substring(0, Math.min(20, c.content.length())) + "..." : "null"));
                
                // Set author name
                tvAuthorName.setText(c.authorName != null ? c.authorName : "Người dùng");
                
                // Set time
                if (c.createdAt != null) {
                    long timeMs = c.createdAt.toDate().getTime();
                    tvTime.setText(formatTime(timeMs));
                } else {
                    tvTime.setText("");
                }
                
                // Set comment text
                tvText.setText(c.content != null ? c.content : "");
                
                // Load avatar
                if (c.authorAvatar != null && !c.authorAvatar.isEmpty()) {
                    Glide.with(itemView.getContext())
                        .load(c.authorAvatar)
                        .apply(new com.bumptech.glide.request.RequestOptions()
                            .transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop())
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person))
                        .into(ivAvatar);
                } else {
                    // Create text avatar
                    setTextAvatar(ivAvatar, c.authorName, null);
                }
                
                // Set click listeners
                ivAvatar.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAuthorClick(c.authorId);
                    }
                });
                
                tvAuthorName.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAuthorClick(c.authorId);
                    }
                });

                // Long press to delete own comment
                itemView.setOnLongClickListener(v -> {
                    String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                    if (currentUserId != null && currentUserId.equals(c.authorId)) {
                        activity.showDeleteCommentDialog(c);
                        return true;
                    }
                    return false;
                });
            }
            
            private String formatTime(long timeMs) {
                long now = System.currentTimeMillis();
                long diff = now - timeMs;
                
                if (diff < 60000) { // Less than 1 minute
                    return "Vừa xong";
                } else if (diff < 3600000) { // Less than 1 hour
                    return (diff / 60000) + " phút trước";
                } else if (diff < 86400000) { // Less than 1 day
                    return (diff / 3600000) + " giờ trước";
                } else {
                    return (diff / 86400000) + " ngày trước";
                }
            }
            
            private void setTextAvatar(ImageView img, String displayName, String email) {
                String text;
                if (displayName != null && !displayName.isEmpty()) {
                    text = displayName.substring(0, 1).toUpperCase();
                } else if (email != null && !email.isEmpty()) {
                    text = email.substring(0, 1).toUpperCase();
                } else {
                    text = "U";
                }
                
                try {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setAntiAlias(true);
                    paint.setColor(0xFF2196F3);
                    canvas.drawCircle(100, 100, 100, paint);
                    paint.setColor(android.graphics.Color.WHITE);
                    paint.setTextSize(80);
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
                    float y = 100 + (fm.descent - fm.ascent) / 2 - fm.descent;
                    canvas.drawText(text, 100, y, paint);
                    img.setImageBitmap(bitmap);
                } catch (Exception e) {
                    img.setImageResource(R.drawable.ic_person);
                }
            }
        }
    }
}

