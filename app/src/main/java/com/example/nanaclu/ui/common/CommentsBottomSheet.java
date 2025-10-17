package com.example.nanaclu.ui.common;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Comment;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.CommentRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.ui.adapter.CommentAdapter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TikTok-style Comments Dialog
 * Sử dụng custom Dialog với overlay mờ và animation slide up/down
 */
public class CommentsBottomSheet {
    
    /**
     * Show comments dialog từ Activity
     */
    public static void show(android.app.Activity activity, Post post) {
        if (activity == null || activity.isFinishing()) return;
        
        // Tạo dialog fullscreen với overlay
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_comments, null, false);
        
        setupCommentsDialog(dialog, content, post, activity);
    }
    
    /**
     * Show comments dialog từ Fragment
     */
    public static void show(Fragment fragment, Post post) {
        if (fragment.getContext() == null) return;
        
        // Tạo dialog fullscreen với overlay
        Dialog dialog = new Dialog(fragment.requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        View content = LayoutInflater.from(fragment.getContext())
                .inflate(R.layout.dialog_comments, null, false);
        
        setupCommentsDialog(dialog, content, post, fragment);
    }
    
    /**
     * Helper method để lấy Context từ Activity hoặc Fragment
     */
    private static Context getContextFromObject(Object obj) {
        if (obj instanceof android.app.Activity) {
            return (android.app.Activity) obj;
        } else if (obj instanceof Fragment) {
            return ((Fragment) obj).getContext();
        }
        return null;
    }
    
    /**
     * Setup comments dialog với logic chung
     */
    private static void setupCommentsDialog(Dialog dialog, View content, Post post, Object context) {
        
        // Xử lý click overlay để đóng dialog với animation
        View overlay = content.findViewById(R.id.overlayView);
        View commentBox = content.findViewById(R.id.commentBox);
        overlay.setOnClickListener(v -> {
            // Animation slide down trước khi đóng
            commentBox.animate()
                    .translationY(commentBox.getHeight())
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> dialog.dismiss())
                    .start();
        });
        
        // Setup repositories
        CommentRepository commentRepo = new CommentRepository(FirebaseFirestore.getInstance());
        UserRepository userRepo = new UserRepository(FirebaseFirestore.getInstance());

        // Setup RecyclerView với real data
        RecyclerView rv = content.findViewById(R.id.rvComments);
        rv.setLayoutManager(new LinearLayoutManager(getContextFromObject(context)));

        List<Comment> comments = new ArrayList<>();
        CommentAdapter adapter = new CommentAdapter(comments, new CommentAdapter.OnCommentActionListener() {
            @Override
            public void onDeleteComment(Comment comment) {
                // Xóa comment của chính mình
                String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                        ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                if (currentUserId != null && currentUserId.equals(comment.authorId)) {
                    new androidx.appcompat.app.AlertDialog.Builder(getContextFromObject(context))
                            .setTitle("Xóa bình luận")
                            .setMessage("Bạn có chắc muốn xóa bình luận này?")
                            .setPositiveButton("Xóa", (dialog, which) -> {
                                commentRepo.deleteComment(post.groupId, post.postId, comment.commentId)
                                        .addOnSuccessListener(aVoid -> {
                                            Context ctx = getContextFromObject(context);
                                            if (ctx != null) {
                                                Toast.makeText(ctx, "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Context ctx = getContextFromObject(context);
                                            if (ctx != null) {
                                                Toast.makeText(ctx, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                }
            }
        }, post.groupId, post.postId);
        rv.setAdapter(adapter);
        
        // Load comments from database
        android.util.Log.d("CommentsBottomSheet", "Loading comments for post: " + post.postId + " in group: " + post.groupId);
        commentRepo.getComments(post.groupId, post.postId, new CommentRepository.CommentsCallback() {
            @Override
            public void onSuccess(List<Comment> loadedComments) {
                android.util.Log.d("CommentsBottomSheet", "Loaded " + (loadedComments != null ? loadedComments.size() : 0) + " comments");
                // Load user info for each comment
                loadUserInfoForComments(loadedComments, userRepo, adapter);
            }

            @Override
            public void onError(Exception e) {
                // Kiểm tra context trước khi hiển thị Toast
                Context ctx = getContextFromObject(context);
                if (ctx != null) {
                    Toast.makeText(ctx, "Lỗi load comments: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Xử lý input comment
        EditText edtComment = content.findViewById(R.id.edtComment);
        View btnSend = content.findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (!text.isEmpty()) {
                // Xóa text ngay lập tức để tránh spam
                edtComment.setText("");
                // Disable button tạm thời
                btnSend.setEnabled(false);

                // Thêm comment vào database
                commentRepo.addComment(post.groupId, post.postId, text, null)
                        .addOnSuccessListener(commentId -> {
                            Context ctx = getContextFromObject(context);
                            if (ctx != null) {
                                Toast.makeText(ctx, "Đã gửi bình luận", Toast.LENGTH_SHORT).show();
                            }
                            btnSend.setEnabled(true);
                        })
                        .addOnFailureListener(e -> {
                            Context ctx = getContextFromObject(context);
                            if (ctx != null) {
                                Toast.makeText(ctx, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                            // Khôi phục text nếu gửi thất bại
                            edtComment.setText(text);
                            btnSend.setEnabled(true);
                        });
            }
        });
        
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.show();
        
        // Thêm animation slide up
        commentBox.setTranslationY(commentBox.getHeight());
        commentBox.post(() -> {
            commentBox.animate()
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        });
    }

    private static void loadUserInfoForComments(List<Comment> comments, UserRepository userRepo, CommentAdapter adapter) {
        android.util.Log.d("CommentsBottomSheet", "Loading user info for " + (comments != null ? comments.size() : 0) + " comments");
        if (comments == null || comments.isEmpty()) {
            android.util.Log.d("CommentsBottomSheet", "No comments to load user info for");
            adapter.updateComments(comments);
            return;
        }

        int[] loadedCount = {0};
        for (Comment comment : comments) {
            userRepo.getUserById(comment.authorId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(com.example.nanaclu.data.model.User user) {
                    comment.authorName = user.displayName;
                    comment.authorAvatar = user.photoUrl;
                    loadedCount[0]++;
                    android.util.Log.d("CommentsBottomSheet", "Loaded user info " + loadedCount[0] + "/" + comments.size());
                    if (loadedCount[0] == comments.size()) {
                        android.util.Log.d("CommentsBottomSheet", "All user info loaded, updating adapter");
                        adapter.updateComments(comments);
                    }
                }

                @Override
                public void onError(Exception e) {
                    comment.authorName = "Unknown User";
                    comment.authorAvatar = null;
                    loadedCount[0]++;
                    if (loadedCount[0] == comments.size()) {
                        adapter.updateComments(comments);
                    }
                }
            });
        }
    }
}
