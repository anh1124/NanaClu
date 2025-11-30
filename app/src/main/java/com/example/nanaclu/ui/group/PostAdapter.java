package com.example.nanaclu.ui.group;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.PostRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.utils.NetworkUtils;
import com.example.nanaclu.data.model.Group;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị post trong GroupDetail với lưới ảnh giống Facebook
 */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface PostActionListener {
        void onLike(Post post);
        void onComment(Post post);
        void onShare(Post post);
        void onDelete(Post post);
        void onReport(Post post);
    }

    private final List<Post> posts = new ArrayList<>();
    private final PostRepository postRepository;
    private final PostActionListener actionListener;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final NoticeRepository noticeRepository;
    private final String currentUserId;
    private final boolean showGroupName; // true for feed, false for group detail

    public PostAdapter(PostRepository postRepository, PostActionListener actionListener) {
        this(postRepository, actionListener, false); // Default: don't show group name
    }

    public PostAdapter(PostRepository postRepository, PostActionListener actionListener, boolean showGroupName) {
        this.postRepository = postRepository;
        this.actionListener = actionListener;
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());
        this.groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
        this.noticeRepository = new NoticeRepository(FirebaseFirestore.getInstance());
        this.currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        this.showGroupName = showGroupName;
    }

    public void setItems(List<Post> newItems) {
        posts.clear();
        if (newItems != null) posts.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItems(List<Post> moreItems) {
        if (moreItems == null || moreItems.isEmpty()) return;
        int start = posts.size();
        posts.addAll(moreItems);
        notifyItemRangeInserted(start, moreItems.size());
    }
    
    public void removePost(String postId) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).postId != null && posts.get(i).postId.equals(postId)) {
                posts.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthorName, tvCreatedAt, tvContent, tvGroupName;
        TextView tvShowMore;
        ViewGroup layoutTextControls;
        ImageView btnLike; View btnComment, btnShare;
        ImageView ivAuthorAvatar;
        View btnMore;
        ViewGroup imageContainer;
        TextView tvLikeCount, tvCommentCount;
        
        // State for text expansion
        private boolean isTextExpanded = false;
        private String fullText = "";

        // image grid views
        ImageView ivOne;
        LinearLayout groupTwo, groupThree, groupFour;
        ImageView ivTwoLeft, ivTwoRight;
        ImageView ivThreeLeft, ivThreeTopRight, ivThreeBottomRight;
        ImageView ivFourTopLeft, ivFourTopRight, ivFourBottomLeft, ivFourBottomRight;
        View overlayMore; TextView tvMoreCount;
        
        // video views
        FrameLayout videoContainer;
        ImageView ivVideoThumb;
        ImageView ivPlayOverlay;
        TextView tvVideoDuration;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvShowMore = itemView.findViewById(R.id.tvShowMore);
            layoutTextControls = itemView.findViewById(R.id.layoutTextControls);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
            btnShare = itemView.findViewById(R.id.btnShare);
            ivAuthorAvatar = itemView.findViewById(R.id.ivAuthorAvatar);
            btnMore = itemView.findViewById(R.id.btnMore);

            imageContainer = itemView.findViewById(R.id.imageContainer);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            
            // Video views
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivVideoThumb = itemView.findViewById(R.id.ivVideoThumb);
            ivPlayOverlay = itemView.findViewById(R.id.ivPlayOverlay);
            tvVideoDuration = itemView.findViewById(R.id.tvVideoDuration);
        }

        void bind(Post post) {
            android.util.Log.d("PostAdapter", "bind: Post ID: " + post.postId + ", GroupID: " + post.groupId);

            // Group name (only show in feed)
            if (showGroupName && tvGroupName != null) {
                tvGroupName.setVisibility(View.VISIBLE);
                if (post.groupId != null && !post.groupId.isEmpty()) {
                    loadGroupName(post.groupId);
                } else {
                    tvGroupName.setText("No Group");
                    android.util.Log.w("PostAdapter", "bind: Post has no groupId");
                }
            } else if (tvGroupName != null) {
                tvGroupName.setVisibility(View.GONE);
            }

            // Author name/time
            tvAuthorName.setText("...");
            tvCreatedAt.setText(android.text.format.DateUtils.getRelativeTimeSpanString(post.createdAt));
            
            // Setup expandable content
            setupExpandableContent(post.content);
            
            tvLikeCount.setText(String.valueOf(post.likeCount));
            tvCommentCount.setText(String.valueOf(post.commentCount));

            // Load author info (name + avatar). Use photoUrl from User model if available
            userRepository.getUserById(post.authorId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    tvAuthorName.setText(user.displayName != null ? user.displayName : "Member");

                    // Try photoUrl first (Google photo stored in User model)
                    if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                        String url = user.photoUrl;
                        if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                            url += (url.contains("?")?"&":"?") + "sz=128";
                        }
                        Glide.with(itemView.getContext())
                                .load(url)
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .circleCrop()
                                .into(ivAuthorAvatar);
                        return;
                    }

                    // If no photoUrl, try to get it from Firebase Auth and update the user record
                    if (user.email != null && !user.email.isEmpty()) {
                        tryUpdateUserPhotoFromAuth(user);
                    }

                    // Fallback to avatarImageId (base64)
                    if (user.avatarImageId != null && !user.avatarImageId.isEmpty()) {
                        postRepository.getUserImageBase64(user.userId, user.avatarImageId, base64 -> {
                            if (base64 == null) { setTextAvatar(ivAuthorAvatar, user.displayName, user.email); return; }
                            try {
                                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bmp != null) {
                                    Glide.with(itemView.getContext())
                                            .load(bmp)
                                            .placeholder(R.mipmap.ic_launcher_round)
                                            .error(R.mipmap.ic_launcher_round)
                                            .circleCrop()
                                            .into(ivAuthorAvatar);
                                } else {
                                    setTextAvatar(ivAuthorAvatar, user.displayName, user.email);
                                }
                            } catch (Exception ignored) { setTextAvatar(ivAuthorAvatar, user.displayName, user.email); }
                        }, e -> setTextAvatar(ivAuthorAvatar, user.displayName, user.email));
                    } else {
                        // text avatar fallback
                        setTextAvatar(ivAuthorAvatar, user.displayName, user.email);
                    }
                }

                @Override
                public void onError(Exception e) {
                    tvAuthorName.setText("Member");
                    setTextAvatar(ivAuthorAvatar, null, null);
                }
            });

            // Setup video or images (mutual exclusion)
            if (post.hasVideo && post.videoUrl != null && post.videoThumbUrl != null) {
                setupVideoDisplay(post);
            } else {
                setupImagesDynamic(post);
            }

            ivAuthorAvatar.setOnClickListener(v -> openProfile(post.authorId));
            tvAuthorName.setOnClickListener(v -> openProfile(post.authorId));

            // Set icon like ban đầu theo trạng thái
            if (currentUserId != null) {
                postRepository.isPostLiked(post.groupId, post.postId, currentUserId,
                        liked -> btnLike.setImageResource(liked ? R.drawable.heart1 : R.drawable.heart0),
                        e -> btnLike.setImageResource(R.drawable.heart0));
            } else {
                btnLike.setImageResource(R.drawable.heart0);
            }

            btnLike.setOnClickListener(v -> {
                if (currentUserId == null) return;
                
                // Check network connection
                if (!NetworkUtils.isNetworkAvailable(itemView.getContext())) {
                    android.widget.Toast.makeText(itemView.getContext(), 
                        "Không có kết nối Internet", 
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Disable button temporarily to prevent double-click
                btnLike.setEnabled(false);
                
                postRepository.isPostLiked(post.groupId, post.postId, currentUserId, liked -> {
                    if (liked) {
                        // Unlike post
                        postRepository.unlikePost(post.groupId, post.postId, currentUserId, aVoid -> {
                            // Update icon and fetch new like count
                            btnLike.setImageResource(R.drawable.heart0);
                            fetchAndUpdateLikeCount(post);
                            btnLike.setEnabled(true);
                        }, e -> {
                            android.widget.Toast.makeText(itemView.getContext(), 
                                "Lỗi khi bỏ thích bài viết", 
                                android.widget.Toast.LENGTH_SHORT).show();
                            btnLike.setEnabled(true);
                        });
                    } else {
                        // Like post
                        postRepository.likePost(post.groupId, post.postId, currentUserId, aVoid -> {
                            // Update icon and fetch new like count
                            btnLike.setImageResource(R.drawable.heart1);
                            fetchAndUpdateLikeCount(post);
                            btnLike.setEnabled(true);
                            
                            // Create notice for post author
                            createLikeNotice(post);
                        }, e -> {
                            android.widget.Toast.makeText(itemView.getContext(), 
                                "Lỗi khi thích bài viết", 
                                android.widget.Toast.LENGTH_SHORT).show();
                            btnLike.setEnabled(true);
                        });
                    }
                }, e -> {
                    android.widget.Toast.makeText(itemView.getContext(), 
                        "Lỗi kết nối", 
                        android.widget.Toast.LENGTH_SHORT).show();
                    btnLike.setEnabled(true);
                });
                if (actionListener != null) actionListener.onLike(post);
            });
            btnComment.setOnClickListener(v -> {
                if (!NetworkUtils.isNetworkAvailable(itemView.getContext())) {
                    android.widget.Toast.makeText(itemView.getContext(), 
                        "Không có kết nối Internet", 
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (actionListener != null) actionListener.onComment(post);
            });
            btnShare.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onShare(post);
            });

            btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu menu = new android.widget.PopupMenu(itemView.getContext(), btnMore);
                if (currentUserId != null && currentUserId.equals(post.authorId)) {
                    menu.getMenu().add("Xóa bài đăng").setOnMenuItemClickListener(item -> {
                        // Hiển thị dialog xác nhận trước khi xóa
                        new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                                .setTitle("Xóa bài đăng")
                                .setMessage("Bạn có chắc muốn xóa bài đăng này? Hành động này không thể hoàn tác.")
                                .setPositiveButton("Xóa", (dialog, which) -> {
                                    if (actionListener != null) actionListener.onDelete(post);
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                        return true;
                    });
                } else {
                    menu.getMenu().add("Báo cáo").setOnMenuItemClickListener(item -> {
                        if (actionListener != null) actionListener.onReport(post);
                        return true;
                    });
                }
                menu.show();
            });
        }

        private void setupExpandableContent(String content) {
            if (content == null || content.trim().isEmpty()) {
                tvContent.setVisibility(View.GONE);
                layoutTextControls.setVisibility(View.GONE);
                return;
            }

            fullText = content;
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

        private void fetchAndUpdateLikeCount(Post post) {
            // Fetch updated like count from database
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(post.groupId)
                    .collection("posts")
                    .document(post.postId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Long likeCount = documentSnapshot.getLong("likeCount");
                            if (likeCount != null) {
                                post.likeCount = likeCount.intValue();
                                tvLikeCount.setText(String.valueOf(post.likeCount));
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("PostAdapter", "Error fetching like count", e);
                    });
        }

        void setupImagesDynamic(Post post) {
            imageContainer.removeAllViews();
            List<String> urls = post.imageUrls == null ? new ArrayList<>() : post.imageUrls;
            if (urls.isEmpty()) return;

            // Tính kích thước màn hình và max heights
            android.util.DisplayMetrics metrics = itemView.getResources().getDisplayMetrics();
            int screenHeight = metrics.heightPixels;
            int maxImageHeight = (int) (screenHeight * 0.4f);
            // Khoảng cách giữa các ảnh ~ 1mm
            int spacePx = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_MM, 1, metrics);

            java.util.function.Consumer<ImageView> commonCenterCrop = iv -> {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setAdjustViewBounds(true);
            };

            java.util.function.BiConsumer<String, ImageView> loadInto = (imageUrl, target) -> {
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .apply(new RequestOptions()
                                .transform(new CenterCrop())
                                .placeholder(R.drawable.image_background)
                                .error(R.drawable.image_background))
                        .into(target);
            };

            int count = urls.size();
            if (count == 1) {
                ImageView imageView = new ImageView(itemView.getContext());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                imageView.setLayoutParams(lp);
                imageView.setAdjustViewBounds(true);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setMaxHeight(maxImageHeight);
                loadInto.accept(urls.get(0), imageView);
                imageView.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 0));
                imageContainer.addView(imageView);
                return;
            }

            if (count == 2) {
                LinearLayout row = new LinearLayout(itemView.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < 2; i++) {
                    ImageView iv = new ImageView(itemView.getContext());
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, maxImageHeight);
                    p.weight = 1f;
                    // Thêm khoảng cách giữa 2 ảnh
                    if (i == 0) p.setMargins(0, 0, spacePx / 2, 0);
                    else p.setMargins(spacePx / 2, 0, 0, 0);
                    iv.setLayoutParams(p);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
                    final int index = i;
                    iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                    row.addView(iv);
                }
                imageContainer.addView(row);
                return;
            }

            if (count == 3) {
                LinearLayout row = new LinearLayout(itemView.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                // Left big
                ImageView left = new ImageView(itemView.getContext());
                LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, maxImageHeight);
                lpLeft.weight = 1f;
                lpLeft.setMargins(0, 0, spacePx / 2, 0);
                left.setLayoutParams(lpLeft);
                commonCenterCrop.accept(left);
                loadInto.accept(urls.get(0), left);
                left.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 0));
                row.addView(left);

                // Right column two small
                LinearLayout col = new LinearLayout(itemView.getContext());
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, maxImageHeight);
                lpCol.weight = 1f;
                lpCol.setMargins(spacePx / 2, 0, 0, 0);
                col.setLayoutParams(lpCol);
                for (int i = 1; i < 3; i++) {
                    ImageView iv = new ImageView(itemView.getContext());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxImageHeight / 2);
                    if (i == 1) lp.setMargins(0, 0, 0, spacePx / 2); // khoảng cách giữa 2 ảnh nhỏ
                    else lp.setMargins(0, spacePx / 2, 0, 0);
                    iv.setLayoutParams(lp);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
                    final int index = i;
                    iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                    col.addView(iv);
                }
                row.addView(col);
                imageContainer.addView(row);
                return;
            }

            // 4 or more
            android.widget.GridLayout grid = new android.widget.GridLayout(itemView.getContext());
            grid.setColumnCount(2);
            grid.setRowCount(2);
            for (int i = 0; i < 4; i++) {
                int colIdx = i % 2;
                int rowIdx = i / 2;
                int left = colIdx == 0 ? 0 : spacePx / 2;
                int right = colIdx == 0 ? spacePx / 2 : 0;
                int top = rowIdx == 0 ? 0 : spacePx / 2;
                int bottom = rowIdx == 0 ? spacePx / 2 : 0;

                if (i < 3 || count == 4) {
                    ImageView iv = new ImageView(itemView.getContext());
                    android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                    params.width = 0;
                    params.height = maxImageHeight / 2;
                    params.columnSpec = android.widget.GridLayout.spec(colIdx, 1f);
                    params.rowSpec = android.widget.GridLayout.spec(rowIdx, 1f);
                    params.setMargins(left, top, right, bottom);
                    iv.setLayoutParams(params);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
                    final int index = i;
                    iv.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), index));
                    grid.addView(iv);
                } else {
                    // overlay cell
                    FrameLayout overlay = new FrameLayout(itemView.getContext());
                    android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                    params.width = 0;
                    params.height = maxImageHeight / 2;
                    params.columnSpec = android.widget.GridLayout.spec(colIdx, 1f);
                    params.rowSpec = android.widget.GridLayout.spec(rowIdx, 1f);
                    params.setMargins(left, top, right, bottom);
                    overlay.setLayoutParams(params);
                    ImageView iv = new ImageView(itemView.getContext());
                    iv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    loadInto.accept(urls.get(3), iv);
                    overlay.addView(iv);
                    View dim = new View(itemView.getContext());
                    dim.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    dim.setBackgroundColor(0x80000000);
                    overlay.addView(dim);
                    TextView tv = new TextView(itemView.getContext());
                    tv.setText("+" + (count - 4));
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setTextSize(24);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    FrameLayout.LayoutParams tParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tParams.gravity = android.view.Gravity.CENTER;
                    tv.setLayoutParams(tParams);
                    overlay.addView(tv);
                    // Open viewer at 3rd index (the first hidden image)
                    overlay.setOnClickListener(v -> openImageViewer(new ArrayList<>(urls), 3));
                    grid.addView(overlay);
                }
            }
            imageContainer.addView(grid);
        }

        private void setupVideoDisplay(Post post) {
            // Hide image container
            imageContainer.setVisibility(View.GONE);
            
            // Show video container
            videoContainer.setVisibility(View.VISIBLE);
            
            // Load video thumbnail
            Glide.with(itemView.getContext())
                .load(post.videoThumbUrl)
                .apply(new RequestOptions()
                    .transform(new CenterCrop())
                    .placeholder(R.drawable.image_background)
                    .error(R.drawable.image_background))
                .into(ivVideoThumb);
            
            // Set video duration
            tvVideoDuration.setText(formatDuration(post.videoDurationMs));
            
            // Set click listener to open video player
            videoContainer.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(
                    itemView.getContext(), com.example.nanaclu.ui.video.VideoPlayerActivity.class);
                intent.putExtra("videoUrl", post.videoUrl);
                intent.putExtra("postId", post.postId);
                itemView.getContext().startActivity(intent);
            });
        }

        private String formatDuration(long durationMs) {
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        private void setTextAvatar(ImageView img, String displayName, String email) {
            String text;
            if (displayName != null && !displayName.isEmpty()) text = displayName.substring(0,1).toUpperCase();
            else if (email != null && !email.isEmpty()) text = email.substring(0,1).toUpperCase();
            else text = "U";
            try {
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setAntiAlias(true);
                paint.setColor(0xFF6200EA);
                canvas.drawCircle(100, 100, 100, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(80f);
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                android.graphics.Rect bounds = new android.graphics.Rect();
                paint.getTextBounds(text, 0, text.length(), bounds);
                canvas.drawText(text, 100, 100 + bounds.height()/2f, paint);
                img.setImageBitmap(bitmap);
            } catch (Exception ignored) {
                img.setImageResource(R.mipmap.ic_launcher_round);
            }
        }
        private void openImageViewer(ArrayList<String> urls, int index) {
            android.content.Intent intent = new android.content.Intent(
                    itemView.getContext(), com.example.nanaclu.ui.post.ImageViewerActivity.class);
            intent.putStringArrayListExtra("images", urls);
            intent.putExtra("index", index);
            itemView.getContext().startActivity(intent);
        }

        private void openProfile(String uid) {
            if (uid == null || uid.trim().isEmpty()) {
                android.widget.Toast.makeText(itemView.getContext(), "Không thể mở hồ sơ: thiếu userId", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Directly open profile
            android.content.Context ctx = itemView.getContext();
            android.content.Intent i = new android.content.Intent(ctx, com.example.nanaclu.ui.profile.ProfileActivity.class);
            i.putExtra("userId", uid);
            if (!(ctx instanceof android.app.Activity)) {
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            try {
                ctx.startActivity(i);
            } catch (Exception e) {
                android.widget.Toast.makeText(ctx, "Không thể mở hồ sơ", android.widget.Toast.LENGTH_SHORT).show();
            }
        }

        private void loadGroupName(String groupId) {
            if (groupId == null || groupId.isEmpty()) {
                tvGroupName.setText("Unknown Group");
                android.util.Log.e("PostAdapter", "loadGroupName: groupId is null or empty");
                return;
            }

            android.util.Log.d("PostAdapter", "loadGroupName: Loading group with ID: " + groupId);

            groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
                @Override
                public void onSuccess(Group group) {
                    if (group != null && group.name != null) {
                        tvGroupName.setText(group.name);
                        android.util.Log.d("PostAdapter", "loadGroupName: Loaded group name: " + group.name);
                        // Add click listener to go to group detail
                        tvGroupName.setOnClickListener(v -> {
                            android.util.Log.d("PostAdapter", "Group name clicked, groupId: " + groupId);
                            if (groupId == null || groupId.isEmpty()) {
                                android.widget.Toast.makeText(itemView.getContext(), "Lỗi: groupId = null", android.widget.Toast.LENGTH_SHORT).show();
                                return;
                            }
                            android.content.Context ctx = itemView.getContext();
                            android.content.Intent intent = new android.content.Intent(ctx, GroupDetailActivity.class);
                            intent.putExtra("groupId", groupId);
                            if (!(ctx instanceof android.app.Activity)) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            }
                            try {
                                ctx.startActivity(intent);
                            } catch (Exception e) {
                                android.widget.Toast.makeText(ctx, "Không thể mở nhóm: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        tvGroupName.setText("Unknown Group");
                        android.util.Log.e("PostAdapter", "loadGroupName: Group is null or has no name");
                    }
                }

                @Override
                public void onError(Exception e) {
                    tvGroupName.setText("Unknown Group");
                    android.util.Log.e("PostAdapter", "loadGroupName: Error loading group", e);
                }
            });
        }

    }

    private void tryUpdateUserPhotoFromAuth(User user) {
        // Check if this user is currently signed in to Firebase Auth
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        com.google.firebase.auth.FirebaseUser currentAuthUser = auth.getCurrentUser();

        if (currentAuthUser != null && currentAuthUser.getUid().equals(user.userId) && currentAuthUser.getPhotoUrl() != null) {
            // This is the current user and they have a photo URL in Auth
            String photoUrl = currentAuthUser.getPhotoUrl().toString();

            // Update the user record in Firestore
            com.example.nanaclu.data.repository.UserRepository userRepo =
                    new com.example.nanaclu.data.repository.UserRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
            userRepo.updateUserPhotoUrl(user.userId, photoUrl);

            // Update the local user object and reload the avatar
            user.photoUrl = photoUrl;
            String url = photoUrl;
            if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                url += (url.contains("?")?"&":"?") + "sz=128";
            }
            // Note: We can't reload the avatar here because we're in a different context
            // The avatar will be updated on the next refresh
        }
    }

    private void createLikeNotice(Post post) {
        if (currentUserId == null || currentUserId.equals(post.authorId)) {
            return; // Không tạo thông báo cho chính mình
        }

        // Lấy tên user hiện tại
        userRepository.getUserById(currentUserId, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                String actorName = user != null ? user.displayName : "Người dùng";
                noticeRepository.createPostLiked(post.groupId, post.postId, currentUserId, actorName, post.authorId);
            }

            @Override
            public void onError(Exception e) {
                // Fallback với tên mặc định
                noticeRepository.createPostLiked(post.groupId, post.postId, currentUserId, "Người dùng", post.authorId);
            }
        });
    }
}
