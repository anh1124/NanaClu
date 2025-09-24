package com.example.nanaclu.ui.common;

import android.app.Dialog;
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
import com.example.nanaclu.data.model.Post;

import java.util.Arrays;
import java.util.List;

/**
 * TikTok-style Comments Dialog
 * Sử dụng custom Dialog với overlay mờ và animation slide up/down
 */
public class CommentsBottomSheet {
    
    public static void show(Fragment fragment, Post post) {
        if (fragment.getContext() == null) return;
        
        // Tạo dialog fullscreen với overlay
        Dialog dialog = new Dialog(fragment.requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        View content = LayoutInflater.from(fragment.getContext())
                .inflate(R.layout.dialog_comments, null, false);
        
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
        
        // Setup RecyclerView với sample data
        RecyclerView rv = content.findViewById(R.id.rvComments);
        rv.setLayoutManager(new LinearLayoutManager(fragment.getContext()));
        List<String> samples = Arrays.asList(
                "Rất hay! Cảm ơn bạn đã chia sẻ.",
                "Ảnh đẹp quá!",
                "Mọi người nghĩ sao về chủ đề này?",
                "Tôi cũng có trải nghiệm tương tự!",
                "Bài viết rất bổ ích 👍",
                "Cảm ơn bạn đã chia sẻ kinh nghiệm!",
                "Tôi sẽ thử làm theo 😊"
        );
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            @Override public int getItemCount(){return samples.size();}
            @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
                return new RecyclerView.ViewHolder(v){};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos){
                android.widget.TextView tv = h.itemView.findViewById(R.id.tvCommentText);
                tv.setText(samples.get(pos));
                android.widget.TextView like = h.itemView.findViewById(R.id.tvLikeCount);
                like.setText(String.valueOf((pos+1)*2));
            }
        });
        
        // Xử lý input comment
        EditText edtComment = content.findViewById(R.id.edtComment);
        View btnSend = content.findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (!text.isEmpty()) {
                // TODO: Thêm comment vào database cho post
                Toast.makeText(fragment.getContext(), "Đã gửi: " + text, Toast.LENGTH_SHORT).show();
                edtComment.setText("");
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
}
