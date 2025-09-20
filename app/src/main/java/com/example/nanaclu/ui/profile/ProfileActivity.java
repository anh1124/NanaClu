package com.example.nanaclu.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.ui.chat.ChatRoomActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {
    private String userId;
    private UserRepository userRepository;
    private ChatRepository chatRepository;
    private User currentUser; // Store user info for chat title

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userId = getIntent().getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            finish();
            return;
        }

        userRepository = new UserRepository(FirebaseFirestore.getInstance());
        chatRepository = new ChatRepository(FirebaseFirestore.getInstance());

        ImageView imgAvatar = findViewById(R.id.imgAvatar);
        TextView tvDisplayName = findViewById(R.id.tvDisplayName);
        TextView tvEmail = findViewById(R.id.tvEmail);
        View btnChat = findViewById(R.id.btnChat);

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Profile");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Load user
        showLoading(true);
        userRepository.getUserById(userId, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                showLoading(false);
                if (user == null) { finish(); return; }

                // Store user info for chat title
                currentUser = user;

                tvDisplayName.setText(user.displayName != null ? user.displayName : "User");
                tvEmail.setText(user.email != null ? user.email : "");
                if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                    String url = user.photoUrl;
                    if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                        url += (url.contains("?")?"&":"?") + "sz=256";
                    }
                    Glide.with(ProfileActivity.this)
                            .load(url)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .circleCrop()
                            .into(imgAvatar);
                } else {
                    imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                finish();
            }
        });

        // Chat button → create private chat and open ChatRoomActivity
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (currentUid != null && userId != null && !currentUid.equals(userId)) {
                    createPrivateChatAndOpen(currentUid, userId);
                } else {
                    Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void createPrivateChatAndOpen(String currentUid, String otherUid) {
        chatRepository.getOrCreatePrivateChat(currentUid, otherUid)
            .addOnSuccessListener(chatId -> {
                Intent intent = new Intent(this, ChatRoomActivity.class);
                intent.putExtra("chatId", chatId);
                // Use the loaded user's name for chat title
                String chatTitle = currentUser != null && currentUser.displayName != null
                    ? currentUser.displayName : "Private Chat";
                intent.putExtra("chatTitle", chatTitle);
                startActivity(intent);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to create chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private android.app.Dialog loadingDialog;
    private void showLoading(boolean show) {
        if (show) {
            if (loadingDialog == null) {
                loadingDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
                FrameLayout root = new FrameLayout(this);
                root.setBackgroundColor(0x88000000);
                root.setClickable(true);
                ProgressBar pb = new ProgressBar(this);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER;
                root.addView(pb, lp);
                loadingDialog.setContentView(root);
                loadingDialog.setCancelable(false);
            }
            if (!loadingDialog.isShowing()) loadingDialog.show();
        } else if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}

