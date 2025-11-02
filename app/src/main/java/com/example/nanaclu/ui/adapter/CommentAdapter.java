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

/**
 * Adapter cho RecyclerView hiển thị danh sách bình luận
 * 
 * Chức năng chính:
 * - Hiển thị danh sách bình luận với thông tin người đăng và thời gian
 * - Hỗ trợ tương tác: xóa, chỉnh sửa bình luận
 * - Tự động cập nhật khi có bình luận mới
 * - Tích hợp với Firebase để đồng bộ dữ liệu thời gian thực
 */
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

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            tvAuthorName = itemView.findViewById(R.id.tvCommentAuthor);
            tvCommentText = itemView.findViewById(R.id.tvCommentText);
            tvTimeAgo = itemView.findViewById(R.id.tvCommentTime);
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
        void onDeleteComment(Comment comment);
    }
}
