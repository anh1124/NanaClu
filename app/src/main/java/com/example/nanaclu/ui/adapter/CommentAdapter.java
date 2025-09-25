package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Comment;
import com.example.nanaclu.data.repository.CommentRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {
    private List<Comment> comments;
    private OnCommentActionListener listener;
    private OnUserClickListener userClickListener;
    private CommentRepository commentRepository;
    private String groupId;
    private String postId;
    private String currentUserId;

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public CommentAdapter(List<Comment> comments, OnCommentActionListener listener, String groupId, String postId) {
        this.comments = comments;
        this.listener = listener;
        this.groupId = groupId;
        this.postId = postId;
        this.commentRepository = new CommentRepository(FirebaseFirestore.getInstance());
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    public CommentAdapter(List<Comment> comments, OnCommentActionListener listener, OnUserClickListener userClickListener, String groupId, String postId) {
        this.comments = comments;
        this.listener = listener;
        this.userClickListener = userClickListener;
        this.groupId = groupId;
        this.postId = postId;
        this.commentRepository = new CommentRepository(FirebaseFirestore.getInstance());
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment_enhanced, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment, userClickListener);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void updateComments(List<Comment> newComments) {
        this.comments = newComments;
        notifyDataSetChanged();
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivAvatar;
        private TextView tvAuthorName;
        private TextView tvCommentText;
        private TextView tvTimeAgo;
        private ImageView ivLike;
        private TextView tvLikeCount;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            tvAuthorName = itemView.findViewById(R.id.tvCommentAuthor);
            tvCommentText = itemView.findViewById(R.id.tvCommentText);
            tvTimeAgo = itemView.findViewById(R.id.tvCommentTime);
            ivLike = itemView.findViewById(R.id.ivCommentLike);
            tvLikeCount = itemView.findViewById(R.id.tvCommentLikeCount);
        }

        public void bind(Comment comment, OnUserClickListener userClickListener) {
            // Set author name
            tvAuthorName.setText(comment.authorName != null ? comment.authorName : "Unknown User");
            
            // Set comment text
            tvCommentText.setText(comment.content);
            
            // Set time ago
            long createdAtLong = comment.getCreatedAtLong();
            if (createdAtLong > 0) {
                String timeAgo = getTimeAgo(createdAtLong);
                tvTimeAgo.setText(timeAgo);
            } else {
                tvTimeAgo.setText("Vừa xong");
            }
            
            // Set avatar
            if (comment.authorAvatar != null && !comment.authorAvatar.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(comment.authorAvatar)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_person);
            }

            // Set click listeners for avatar and name to open profile
            if (userClickListener != null && comment.authorId != null) {
                View.OnClickListener profileClickListener = v -> userClickListener.onUserClick(comment.authorId);
                ivAvatar.setOnClickListener(profileClickListener);
                tvAuthorName.setOnClickListener(profileClickListener);
            }

            // Set like count
            tvLikeCount.setText(String.valueOf(comment.likeCount));

            // Check if current user liked this comment (similar to PostAdapter)
            if (currentUserId != null && groupId != null && postId != null) {
                commentRepository.isCommentLiked(groupId, postId, comment.commentId, currentUserId,
                        liked -> {
                            ivLike.setImageResource(liked ? R.drawable.heart1 : R.drawable.heart0);
                            ivLike.setTag(liked ? "liked" : "not_liked");
                        },
                        e -> {
                            ivLike.setImageResource(R.drawable.heart0);
                            ivLike.setTag("not_liked");
                        });
            } else {
                ivLike.setImageResource(R.drawable.heart0);
                ivLike.setTag("not_liked");
            }

            // Like button click
            ivLike.setOnClickListener(v -> {
                if (currentUserId == null || groupId == null || postId == null) return;

                // Toggle like in database (similar to PostAdapter)
                commentRepository.isCommentLiked(groupId, postId, comment.commentId, currentUserId, liked -> {
                    if (liked) {
                        // Unlike
                        commentRepository.toggleLikeComment(groupId, postId, comment.commentId)
                                .addOnSuccessListener(aVoid -> {
                                    ivLike.setImageResource(R.drawable.heart0);
                                    ivLike.setTag("not_liked");
                                })
                                .addOnFailureListener(e -> {});
                    } else {
                        // Like
                        commentRepository.toggleLikeComment(groupId, postId, comment.commentId)
                                .addOnSuccessListener(aVoid -> {
                                    ivLike.setImageResource(R.drawable.heart1);
                                    ivLike.setTag("liked");
                                })
                                .addOnFailureListener(e -> {});
                    }
                }, e -> {});

                // Also call listener for any additional handling
                if (listener != null) {
                    listener.onLikeComment(comment);
                }
            });

            // Long press to delete own comment
            itemView.setOnLongClickListener(v -> {
                String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                        ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                if (currentUserId != null && currentUserId.equals(comment.authorId) && listener != null) {
                    listener.onDeleteComment(comment);
                    return true;
                }
                return false;
            });
        }
        
        private String getTimeAgo(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            
            if (diff < 60000) { // < 1 minute
                return "Vừa xong";
            } else if (diff < 3600000) { // < 1 hour
                return (diff / 60000) + " phút trước";
            } else if (diff < 86400000) { // < 1 day
                return (diff / 3600000) + " giờ trước";
            } else if (diff < 604800000) { // < 1 week
                return (diff / 86400000) + " ngày trước";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }
    }

    public interface OnCommentActionListener {
        void onLikeComment(Comment comment);
        void onDeleteComment(Comment comment);
    }
}
