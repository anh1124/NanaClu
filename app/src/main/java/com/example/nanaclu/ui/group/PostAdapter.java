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
import com.example.nanaclu.data.model.User;
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
        void onDelete(Post post);
        void onReport(Post post);
    }

    private final List<Post> posts = new ArrayList<>();
    private final PostRepository postRepository;
    private final PostActionListener actionListener;
    private final UserRepository userRepository;
    private final String currentUserId;

    public PostAdapter(PostRepository postRepository, PostActionListener actionListener) {
        this.postRepository = postRepository;
        this.actionListener = actionListener;
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());
        this.currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
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
        TextView tvAuthorName, tvCreatedAt, tvContent;
        ImageView btnLike; View btnComment;
        ImageView ivAuthorAvatar;
        View btnMore;
        ViewGroup imageContainer;
        TextView tvLikeCount;

        // image grid views
        ImageView ivOne;
        LinearLayout groupTwo, groupThree, groupFour;
        ImageView ivTwoLeft, ivTwoRight;
        ImageView ivThreeLeft, ivThreeTopRight, ivThreeBottomRight;
        ImageView ivFourTopLeft, ivFourTopRight, ivFourBottomLeft, ivFourBottomRight;
        View overlayMore; TextView tvMoreCount;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            tvContent = itemView.findViewById(R.id.tvContent);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
            ivAuthorAvatar = itemView.findViewById(R.id.ivAuthorAvatar);
            btnMore = itemView.findViewById(R.id.btnMore);

            imageContainer = itemView.findViewById(R.id.imageContainer);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
        }

        void bind(Post post) {
            // Author name/time
            tvAuthorName.setText("...");
            tvCreatedAt.setText(android.text.format.DateUtils.getRelativeTimeSpanString(post.createdAt));
            tvContent.setVisibility(post.content == null || post.content.trim().isEmpty() ? View.GONE : View.VISIBLE);
            tvContent.setText(post.content);
            tvLikeCount.setText(String.valueOf(post.likeCount));

            // Load author info (name + avatar) giống profile: ưu tiên avatar base64; fallback chữ cái đầu
            userRepository.getUserById(post.authorId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    tvAuthorName.setText(user.displayName != null ? user.displayName : "Member");
                    if (user.avatarImageId != null && !user.avatarImageId.isEmpty()) {
                        // Load avatar base64 by avatarImageId
                        postRepository.getUserImageBase64(user.userId, user.avatarImageId, base64 -> {
                            if (base64 == null) return;
                            try {
                                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bmp != null) ivAuthorAvatar.setImageBitmap(bmp);
                            } catch (Exception ignored) {}
                        }, e -> {});
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

            setupImagesDynamic(post);

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
                postRepository.isPostLiked(post.groupId, post.postId, currentUserId, liked -> {
                    if (liked) {
                        postRepository.unlikePost(post.groupId, post.postId, currentUserId, aVoid -> {
                            if (post.likeCount > 0) post.likeCount -= 1;
                            tvLikeCount.setText(String.valueOf(post.likeCount));
                            btnLike.setImageResource(R.drawable.heart0);
                        }, e -> {});
                    } else {
                        postRepository.likePost(post.groupId, post.postId, currentUserId, aVoid -> {
                            post.likeCount += 1;
                            tvLikeCount.setText(String.valueOf(post.likeCount));
                            btnLike.setImageResource(R.drawable.heart1);
                        }, e -> {});
                    }
                }, e -> {});
                if (actionListener != null) actionListener.onLike(post);
            });
            btnComment.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onComment(post);
            });

            btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu menu = new android.widget.PopupMenu(itemView.getContext(), btnMore);
                if (currentUserId != null && currentUserId.equals(post.authorId)) {
                    menu.getMenu().add("Xóa bài đăng").setOnMenuItemClickListener(item -> {
                        if (actionListener != null) actionListener.onDelete(post);
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

        void setupImagesDynamic(Post post) {
            imageContainer.removeAllViews();
            List<String> urls = post.imageUrls == null ? new ArrayList<>() : post.imageUrls;
            if (urls.isEmpty()) return;

            // Tính kích thước màn hình và max heights
            android.util.DisplayMetrics metrics = itemView.getResources().getDisplayMetrics();
            int screenHeight = metrics.heightPixels;
            int maxImageHeight = (int) (screenHeight * 0.4f);

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
                    iv.setLayoutParams(p);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
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
                left.setLayoutParams(lpLeft);
                commonCenterCrop.accept(left);
                loadInto.accept(urls.get(0), left);
                row.addView(left);

                // Right column two small
                LinearLayout col = new LinearLayout(itemView.getContext());
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, maxImageHeight);
                lpCol.weight = 1f;
                col.setLayoutParams(lpCol);
                for (int i = 1; i < 3; i++) {
                    ImageView iv = new ImageView(itemView.getContext());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxImageHeight / 2);
                    iv.setLayoutParams(lp);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
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
                if (i < 3 || count == 4) {
                    ImageView iv = new ImageView(itemView.getContext());
                    android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                    params.width = 0;
                    params.height = maxImageHeight / 2;
                    params.columnSpec = android.widget.GridLayout.spec(i % 2, 1f);
                    params.rowSpec = android.widget.GridLayout.spec(i / 2, 1f);
                    iv.setLayoutParams(params);
                    commonCenterCrop.accept(iv);
                    loadInto.accept(urls.get(i), iv);
                    grid.addView(iv);
                } else {
                    // overlay cell
                    FrameLayout overlay = new FrameLayout(itemView.getContext());
                    android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                    params.width = 0;
                    params.height = maxImageHeight / 2;
                    params.columnSpec = android.widget.GridLayout.spec(i % 2, 1f);
                    params.rowSpec = android.widget.GridLayout.spec(i / 2, 1f);
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
                    grid.addView(overlay);
                }
            }
            imageContainer.addView(grid);
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
    }
}
