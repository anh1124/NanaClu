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
import com.example.nanaclu.data.repository.FriendshipRepository;
import com.example.nanaclu.ui.chat.ChatRoomActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {
    private String userId;
    private String currentUserId;
    private UserRepository userRepository;
    private ChatRepository chatRepository;
    private FriendshipRepository friendshipRepository;
    private User currentUser; // Store user info for chat title
    private String friendshipStatus = "none";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userId = getIntent().getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            finish();
            return;
        }

        // Get current user ID
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            finish();
            return;
        }

        userRepository = new UserRepository(FirebaseFirestore.getInstance());
        chatRepository = new ChatRepository(FirebaseFirestore.getInstance());
        friendshipRepository = new FriendshipRepository(FirebaseFirestore.getInstance());

        ImageView imgAvatar = findViewById(R.id.imgAvatar);
        TextView tvDisplayName = findViewById(R.id.tvDisplayName);
        TextView tvEmail = findViewById(R.id.tvEmail);
        View btnChat = findViewById(R.id.btnChat);
        com.google.android.material.button.MaterialButton btnFriendAction = findViewById(R.id.btnFriendAction);

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Show "(Bạn)" if viewing own profile
            if (userId.equals(currentUserId)) {
                getSupportActionBar().setTitle("Profile (Bạn)");
            } else {
                getSupportActionBar().setTitle("Profile");
            }
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

                // Load friendship status nếu không phải chính mình
                if (!userId.equals(currentUserId)) {
                    loadFriendshipStatus(btnFriendAction);
                } else {
                    // Ẩn friend action button nếu là profile của chính mình
                    btnFriendAction.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                finish();
            }
        });

        // Chat button → check permission then create private chat and open ChatRoomActivity
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (currentUid != null && userId != null && !currentUid.equals(userId)) {
                    checkChatPermissionAndOpen(currentUid, userId);
                } else {
                    Toast.makeText(this, "Không thể nhắn tin với chính mình", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    private void checkChatPermissionAndOpen(String currentUid, String otherUid) {
        // First check if they are friends
        friendshipRepository.getStatus(currentUid, otherUid)
            .addOnSuccessListener(status -> {
                if ("accepted".equals(status)) {
                    // They are friends, allow chat
                    createPrivateChatAndOpen(currentUid, otherUid);
                } else {
                    // Not friends, check if the other user allows stranger messages
                    userRepository.getUserById(otherUid, new UserRepository.UserCallback() {
                        @Override
                        public void onSuccess(User user) {
                            if (user != null) {
                                Boolean allowStrangerMessages = user.allowStrangerMessages;
                                // Default to true if not set
                                boolean allowsStrangers = allowStrangerMessages != null ? allowStrangerMessages : true;

                                if (allowsStrangers) {
                                    // User allows stranger messages, create chat
                                    createPrivateChatAndOpen(currentUid, otherUid);
                                } else {
                                    // User doesn't allow stranger messages, block chat
                                    String displayName = user.displayName != null ? user.displayName : "người dùng này";
                                    Toast.makeText(ProfileActivity.this,
                                        displayName + " không cho phép nhận tin nhắn từ người lạ",
                                        Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(ProfileActivity.this, "Không thể tải thông tin người dùng", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            android.util.Log.e("ProfileActivity", "Error loading user for chat permission check", e);
                            Toast.makeText(ProfileActivity.this, "Lỗi khi kiểm tra quyền nhắn tin", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ProfileActivity", "Error checking friendship status for chat", e);
                Toast.makeText(this, "Lỗi khi kiểm tra trạng thái kết bạn", Toast.LENGTH_SHORT).show();
            });
    }

    private void createPrivateChatAndOpen(String currentUid, String otherUid) {
        chatRepository.getOrCreatePrivateChat(currentUid, otherUid)
            .addOnSuccessListener(chatId -> {
                // If this user had archived the chat earlier, unhide it on open (keep clearedAt)
                java.util.List<String> self = new java.util.ArrayList<>();
                self.add(currentUid);
                chatRepository.unarchiveForUsers(chatId, self);
                Intent intent = new Intent(this, ChatRoomActivity.class);
                intent.putExtra("chatId", chatId);
                // Use the loaded user's name for chat title
                String chatTitle = currentUser != null && currentUser.displayName != null
                    ? currentUser.displayName : "Private Chat";
                intent.putExtra("chatTitle", chatTitle);
                startActivity(intent);
            })
            .addOnFailureListener(e -> {
                com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                if (errorMessage != null) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to create chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
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

    /**
     * Load friendship status và update UI
     */
    private void loadFriendshipStatus(com.google.android.material.button.MaterialButton btnFriendAction) {
        friendshipRepository.getStatus(currentUserId, userId)
                .addOnSuccessListener(status -> {
                    friendshipStatus = status;
                    updateFriendActionButton(btnFriendAction);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ProfileActivity", "Failed to load friendship status", e);
                    friendshipStatus = "none";
                    updateFriendActionButton(btnFriendAction);
                });
    }

    /**
     * Update friend action button dựa trên friendship status
     */
    private void updateFriendActionButton(com.google.android.material.button.MaterialButton btnFriendAction) {
        if (btnFriendAction == null) return;

        switch (friendshipStatus) {
            case "none":
                btnFriendAction.setText("Add Friend");
                btnFriendAction.setOnClickListener(v -> sendFriendRequest());
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            case "pending_sent":
                btnFriendAction.setText("Pending");
                btnFriendAction.setOnClickListener(v -> cancelFriendRequest());
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            case "pending_incoming":
                btnFriendAction.setText("Accept");
                btnFriendAction.setOnClickListener(v -> showAcceptDeclineDialog());
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            case "accepted":
                btnFriendAction.setText("Friend");
                btnFriendAction.setOnClickListener(v -> showFriendActionDialog());
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            case "blocked_by_me":
                btnFriendAction.setText("Unblock");
                btnFriendAction.setOnClickListener(v -> unblockUser());
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            case "blocked_by_them":
                btnFriendAction.setText("Blocked");
                btnFriendAction.setOnClickListener(null);
                btnFriendAction.setVisibility(View.VISIBLE);
                break;
            default:
                btnFriendAction.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * Gửi lời mời kết bạn
     */
    private void sendFriendRequest() {
        showLoading(true);
        friendshipRepository.sendFriendRequest(currentUserId, userId)
                .addOnSuccessListener(result -> {
                    showLoading(false);
                    android.util.Log.d("ProfileActivity", "Friend request result: " + result);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Lời mời kết bạn đã được gửi", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi gửi lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Cancel lời mời kết bạn đã gửi
     */
    private void cancelFriendRequest() {
        showLoading(true);
        friendshipRepository.cancelFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã hủy lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi hủy lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show dialog Accept/Decline cho incoming request
     */
    private void showAcceptDeclineDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Lời mời kết bạn")
                .setMessage("Bạn có muốn chấp nhận lời mời kết bạn từ " + 
                           (currentUser != null && currentUser.displayName != null ? currentUser.displayName : "người này") + "?")
                .setPositiveButton("Chấp nhận", (dialog, which) -> acceptFriendRequest())
                .setNegativeButton("Từ chối", (dialog, which) -> declineFriendRequest())
                .setNeutralButton("Hủy", null)
                .show();
    }

    /**
     * Accept lời mời kết bạn
     */
    private void acceptFriendRequest() {
        showLoading(true);
        friendshipRepository.acceptFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã chấp nhận lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi chấp nhận lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Decline lời mời kết bạn
     */
    private void declineFriendRequest() {
        showLoading(true);
        friendshipRepository.declineFriendRequest(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi từ chối lời mời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show friend action dialog (Unfriend/Block)
     */
    private void showFriendActionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Quản lý bạn bè")
                .setMessage("Bạn muốn làm gì?")
                .setPositiveButton("Hủy kết bạn", (dialog, which) -> showUnfriendConfirmDialog())
                .setNegativeButton("Chặn", (dialog, which) -> showBlockConfirmDialog())
                .setNeutralButton("Hủy", null)
                .show();
    }

    /**
     * Show confirm dialog cho unfriend
     */
    private void showUnfriendConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hủy kết bạn")
                .setMessage("Bạn có chắc muốn hủy kết bạn với " + 
                           (currentUser != null && currentUser.displayName != null ? currentUser.displayName : "người này") + "?")
                .setPositiveButton("Hủy kết bạn", (dialog, which) -> unfriendUser())
                .setNegativeButton("Không", null)
                .show();
    }

    /**
     * Unfriend user
     */
    private void unfriendUser() {
        showLoading(true);
        friendshipRepository.unfriend(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã hủy kết bạn", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi hủy kết bạn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show confirm dialog cho block
     */
    private void showBlockConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chặn người dùng")
                .setMessage("Bạn có chắc muốn chặn " + 
                           (currentUser != null && currentUser.displayName != null ? currentUser.displayName : "người này") + "?")
                .setPositiveButton("Chặn", (dialog, which) -> blockUser())
                .setNegativeButton("Không", null)
                .show();
    }

    /**
     * Block user
     */
    private void blockUser() {
        showLoading(true);
        friendshipRepository.block(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi chặn người dùng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Unblock user
     */
    private void unblockUser() {
        showLoading(true);
        friendshipRepository.unblock(currentUserId, userId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    loadFriendshipStatus(findViewById(R.id.btnFriendAction));
                    Toast.makeText(this, "Đã bỏ chặn người dùng", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("ProfileActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi bỏ chặn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
