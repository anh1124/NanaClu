package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.repository.ChatRepository;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.ui.chat.ChatRoomActivity;
import com.example.nanaclu.ui.common.CommentsBottomSheet;
import com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment;
import com.example.nanaclu.utils.ThemeUtils;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.PostRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class GroupDetailActivity extends AppCompatActivity {
    private GroupRepository groupRepository;
    private ChatRepository chatRepository;
    private String groupId;
    private Group currentGroup;
    private String currentUserId;
    private Member currentUserMember;
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private PostRepository postRepository;
    private boolean isLoadingMore = false;
    private DocumentSnapshot lastVisible;
    private boolean reachedEnd = false;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Demo: notification icon state (notification0/notification1)
    private boolean groupHasNotifications = false;

    // Backup groupId for debugging
    private String backupGroupId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        // Get group ID from intent (support both keys: "groupId" and legacy "group_id")
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            groupId = getIntent().getStringExtra("group_id");
        }
        backupGroupId = groupId; // Store backup
        System.out.println("GroupDetailActivity: Received groupId = " + groupId);
        android.util.Log.d("GroupDetailActivity", "onCreate: groupId=" + groupId + " (from intent)");
        Toast.makeText(this, "onCreate: groupId = " + groupId, Toast.LENGTH_SHORT).show();
        if (groupId == null) {
            Toast.makeText(this, "Group ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get current user ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            finish();
            return;
        }

        // Initialize repository
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
        postRepository = new PostRepository(FirebaseFirestore.getInstance());
        chatRepository = new ChatRepository(FirebaseFirestore.getInstance());

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(this));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_group_detail);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        updateMenuVisibility();
        // Style the "Rời nhóm" menu item title in red
        {
            android.view.Menu m = toolbar.getMenu();
            if (m != null) {
                android.view.MenuItem leave = m.findItem(R.id.action_leave_group);
                if (leave != null) {
                    android.text.SpannableString title = new android.text.SpannableString(leave.getTitle());
                    title.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF3B30), 0, title.length(), 0);
                    leave.setTitle(title);
                }
            }
        }
        // Tint navigation and overflow icons to white
        android.graphics.PorterDuff.Mode mode = android.graphics.PorterDuff.Mode.SRC_ATOP;
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(android.graphics.Color.WHITE, mode);
        }
        toolbar.post(() -> {
            android.graphics.drawable.Drawable ov = toolbar.getOverflowIcon();
            if (ov != null) {
                ov.setColorFilter(android.graphics.Color.WHITE, mode);
            }
        });

        // Hide/show toolbar on scroll direction and enable bottom pull-up to retry load
        androidx.core.widget.NestedScrollView nested = findViewById(R.id.nestedScroll);
        if (nested != null) {
            nested.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > oldScrollY) {
                        // scrolling down, hide
                        toolbar.animate().translationY(-toolbar.getHeight()).setDuration(150).start();
                    } else if (scrollY < oldScrollY) {
                        // scrolling up, show
                        toolbar.animate().translationY(0).setDuration(150).start();
                    }
                });

            nested.setOnTouchListener(new android.view.View.OnTouchListener() {
                float downY;
                final float threshold = getResources().getDisplayMetrics().density * 80; // ~80dp
                @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            downY = e.getY();
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                            float upY = e.getY();
                            boolean atBottom = !v.canScrollVertically(1);
                            if (atBottom && (downY - upY) > threshold && !isLoadingMore && !reachedEnd) {
                                // User swiped up at bottom: try load more manually
                                loadMorePosts();
                                return true;
                            }
                            break;
                    }
                    return false;
                }
            });
        }

        // Swipe-to-refresh: tải 5 bài mới nhất
        swipeRefreshLayout = findViewById(R.id.swipeRefreshGroupDetail);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadInitialPosts();
            });
        }

        // Load group data
        loadGroupData();

        // Load current user's member info
        loadCurrentUserMember();

        // Setup user avatar
        setupUserAvatar();

        // Setup click listeners
        findViewById(R.id.btnInvite).setOnClickListener(v -> showInviteDialog());
        findViewById(R.id.btnChat).setOnClickListener(v -> {
            if (groupId != null) {
                createGroupChatAndOpen(groupId);
            }
        });

        // Make entire post composer area clickable
        View postComposerArea = findViewById(R.id.postComposer);
        postComposerArea.setOnClickListener(v -> {
            // Open CreatePostActivity when clicking anywhere in the post composer area
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        });

        // Also make individual elements clickable for better UX
        View btnAddImage = findViewById(R.id.btnAddImage);
        TextView edtPost = findViewById(R.id.edtPost);

        // These will also open CreatePostActivity
        btnAddImage.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        });

        edtPost.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        });

        // Setup posts RecyclerView
        rvPosts = findViewById(R.id.rvPosts);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvPosts.setLayoutManager(lm);
        postAdapter = new PostAdapter(postRepository, new PostAdapter.PostActionListener() {
            @Override
            public void onLike(Post post) {
                // TODO: implement like later
            }
            @Override
            public void onComment(Post post) {
                // Mở CommentsBottomSheet để hiển thị và thêm comments
                CommentsBottomSheet.show(GroupDetailActivity.this, post);
            }
            @Override
            public void onDelete(Post post) {
                // Xóa post (với Firebase Storage, URLs sẽ tự động không accessible khi post bị xóa)
                // Không cần xóa riêng images vì chúng được lưu trong Storage với URLs
                postRepository.deletePost(post.groupId, post.postId, new com.example.nanaclu.data.repository.PostRepository.PostCallback() {
                    @Override
                    public void onSuccess(Post p) {
                        // refresh list đơn giản: load lại từ đầu
                        loadInitialPosts();
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(GroupDetailActivity.this, "Lỗi xóa bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onReport(Post post) {
                // Mở ReportBottomSheet để user có thể chọn lý do báo cáo
                ReportBottomSheetDialogFragment reportSheet = ReportBottomSheetDialogFragment.newInstance(
                    groupId, post.postId, post.authorId);
                reportSheet.show(getSupportFragmentManager(), "report_bottom_sheet");
            }
        });
        rvPosts.setAdapter(postAdapter);

        rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (!isLoadingMore && !reachedEnd && (visible + first) >= total - 2) {
                    loadMorePosts();
                }
            }
        });

        // Initial load
        loadInitialPosts();
    }

    private void loadGroupData() {
        groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                currentGroup = group;
                updateUI(group);
            }

            @Override
            public void onError(Exception e) {
                // Handle error
            }
        });
    }

    private void loadCurrentUserMember() {
        groupRepository.getMemberById(groupId, currentUserId, new GroupRepository.MemberCallback() {
            @Override
            public void onSuccess(Member member) {
                currentUserMember = member;
                updateMenuVisibility();
            }

            @Override
            public void onError(Exception e) {
                // Handle error - user might not be a member
                currentUserMember = null;
                updateMenuVisibility();
            }
        });
    }

    private void updateMenuVisibility() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        android.view.Menu menu = toolbar.getMenu();
        if (menu == null) return;

        boolean isAdminOrOwner = currentUserMember != null &&
                ("admin".equals(currentUserMember.role) || "owner".equals(currentUserMember.role));

        MenuItem settingsItem = menu.findItem(R.id.action_group_settings);
        if (settingsItem != null) {
            settingsItem.setVisible(isAdminOrOwner);
        }
        MenuItem approveItem = menu.findItem(R.id.action_approve_members);
        if (approveItem != null) {
            approveItem.setVisible(isAdminOrOwner);
        }

        // Update notification icon 0/1
        MenuItem notiItem = menu.findItem(R.id.action_group_notifications);
        if (notiItem != null) {
            notiItem.setIcon(groupHasNotifications ? R.drawable.ic_notifications_active_24 : R.drawable.ic_notifications_none_24);
        }
    }

    private void updateUI(Group group) {
        TextView tvGroupName = findViewById(R.id.tvGroupName);
        TextView tvPrivacy = findViewById(R.id.tvPrivacy);
        TextView tvMemberCount = findViewById(R.id.tvMemberCount);
        ImageView imgCover = findViewById(R.id.imgCover);
        ImageView imgGroupAvatar = findViewById(R.id.imgGroupAvatar);

        // Update toolbar title to group name
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setTitle(group.name != null ? group.name : "Group");

        tvGroupName.setText(group.name != null ? group.name : "");

        // Approval mode display
        boolean requireApproval = group.requireApproval;
        String approvalText = requireApproval ? "Can phaf duyt" : "Kh f4ng can duyt";
        tvPrivacy.setVisibility(View.GONE);

        String memberText = group.memberCount + " thành viên";
        tvMemberCount.setText(memberText);

        // Make member count clickable
        tvMemberCount.setOnClickListener(v -> openMembersActivity());

        if (group.coverImageId != null && !group.coverImageId.isEmpty()) {
            loadImageUrlInto(imgCover, group.coverImageId);
            // Add click listener for fullscreen view
            imgCover.setOnClickListener(v -> showFullscreenImage(group.coverImageId, "Cover Image"));
        }
        if (group.avatarImageId != null && !group.avatarImageId.isEmpty()) {
            loadImageUrlInto(imgGroupAvatar, group.avatarImageId);
            // Add click listener for fullscreen view
            imgGroupAvatar.setOnClickListener(v -> showFullscreenImage(group.avatarImageId, "Group Avatar"));
        }
    }

    /** Lightweight loader for HTTP(S) image URLs without third-party libs */
    private void loadImageUrlInto(ImageView view, String url) {
        new Thread(() -> {
            try {
                java.io.InputStream is = new java.net.URL(url).openStream();
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                view.post(() -> view.setImageBitmap(bmp));
            } catch (Exception ignored) {}
        }).start();
    }

    private void showFullscreenImage(String imageUrl, String title) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        // Create fullscreen dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_image, null);

        ImageView imgFullscreen = dialogView.findViewById(R.id.imgFullscreen);
        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        tvTitle.setText(title);

        // Load image
        loadImageUrlInto(imgFullscreen, imageUrl);

        AlertDialog dialog = builder.setView(dialogView).create();

        // Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Click anywhere to close
        imgFullscreen.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }


    private void loadInitialPosts() {
        isLoadingMore = true;
        lastVisible = null;



        reachedEnd = false;
        postRepository.getGroupPostsPaged(groupId, 5, null, (posts, last) -> {
            postAdapter.setItems(posts);
            lastVisible = last;
            isLoadingMore = false;
            reachedEnd = posts == null || posts.isEmpty();
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }, e -> {
            isLoadingMore = false;
        });
    }

    private void loadMorePosts() {
        if (lastVisible == null) { reachedEnd = true; return; }
        isLoadingMore = true;
        postRepository.getGroupPostsPaged(groupId, 5, lastVisible, (posts, last) -> {
            if (posts == null || posts.isEmpty()) {
                reachedEnd = true;
                android.widget.Toast.makeText(GroupDetailActivity.this, "Bạn đã xem hết bài viết", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                postAdapter.addItems(posts);
                lastVisible = last;
            }
            isLoadingMore = false;
        }, e -> {
            isLoadingMore = false;
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setupUserAvatar() {
        ImageView imgAvatar = findViewById(R.id.imgAvatar);
        android.content.SharedPreferences up = getSharedPreferences("user_profile", MODE_PRIVATE);
        String name = up.getString("displayName", null);
        String email = up.getString("email", null);
        String photo = up.getString("photoUrl", null);
        if (photo != null && !photo.isEmpty()) {
            String url = photo;
            if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                url += (url.contains("?")?"&":"?") + "sz=128";
            }
            try {
                com.bumptech.glide.Glide.with(this)
                        .load(url)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(imgAvatar);
                return;
            } catch (Exception ignored) {}
        }
        showTextAvatar(imgAvatar, name, email);
    }

    private void loadImageFromUrl(ImageView imgAvatar, String imageUrl) {
        try {
            com.bumptech.glide.Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(imgAvatar);
        } catch (Exception e) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) showTextAvatar(imgAvatar, currentUser.getDisplayName(), currentUser.getEmail());
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        System.out.println("GroupDetailActivity: onMenuItemClick - groupId = " + groupId);

        if (id == R.id.action_group_notifications) {
            Intent intent = new Intent(this, GroupNotificationsActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_group_events) {
            // Open GroupEventActivity
            Intent intent = new Intent(this, com.example.nanaclu.ui.event.GroupEventActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("groupName", currentGroup != null ? currentGroup.name : "Nhóm");
            startActivity(intent);
            return true;
        } else if (id == R.id.action_view_members) {
            // Open GroupMembersActivity
            Intent intent = new Intent(this, GroupMembersActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_approve_members) {
            Intent intent = new Intent(this, GroupPendingMembersActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_group_settings) {
            // Open GroupSettingsActivity
            Intent intent = new Intent(this, GroupSettingsActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("currentUserId", currentUserId);
            startActivityForResult(intent, 200);
            return true;
        } else if (id == R.id.action_invite_members) {
            showInviteDialog();
            return true;
        } else if (id == R.id.action_leave_group) {
            showLeaveGroupDialog();
            return true;
        }
        return false;
    }

    private void showInviteDialog() {
        if (currentGroup == null) {
            Toast.makeText(this, "Đang tải...", Toast.LENGTH_SHORT).show();
            return;
        }
        String code = currentGroup.code != null ? currentGroup.code : (groupId != null ? groupId : "");

        // Inflate dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_invite_member, null);

        // Get views from layout
        TextView tvGroupCode = dialogView.findViewById(R.id.tvGroupCode);
        ImageButton btnCopyCode = dialogView.findViewById(R.id.btnCopyCode);

        // Set group code
        tvGroupCode.setText(code);

        // Set copy button click listener
        btnCopyCode.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("group_code", code);
            if (cm != null) cm.setPrimaryClip(clip);
            Toast.makeText(this, "Đã copy mã nhóm", Toast.LENGTH_SHORT).show();
        });

        // Show dialog
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Mời thành viên")
                .setView(dialogView)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showLeaveGroupDialog() {
        if (currentUserMember == null) {
            Toast.makeText(this, "Bạn không phải thành viên của nhóm này", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("owner".equals(currentUserMember.role)) {
            // Owner wants to leave - check if there are other members
            checkOwnerLeaveConditions();
        } else {
            // Member or admin wants to leave
            showMemberLeaveDialog();
        }
    }

    private void checkOwnerLeaveConditions() {
        groupRepository.getGroupMembers(groupId, new GroupRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Member> members) {
                if (members.size() == 1) {
                    // Only owner left - show delete group warning
                    showDeleteGroupWarning();
                } else {
                    // Other members exist - must transfer ownership
                    showTransferOwnershipRequired();
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupDetailActivity.this, "Lỗi khi kiểm tra thành viên", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteGroupWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận rời nhóm")
                .setMessage("Bạn là người sở hữu duy nhất của nhóm. Nếu bạn rời nhóm, nhóm sẽ bị xóa vĩnh viễn. Bạn có chắc chắn muốn tiếp tục?")
                .setPositiveButton("Xóa nhóm và rời", (dialog, which) -> {
                    deleteGroupAndLeave();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showTransferOwnershipRequired() {
        new AlertDialog.Builder(this)
                .setTitle("Chuyển quyền sở hữu")
                .setMessage("Bạn là chủ sở hữu của nhóm. Bạn phải chuyển quyền sở hữu cho thành viên khác trước khi rời nhóm.")
                .setPositiveButton("Chuyển quyền sở hữu", (dialog, which) -> {
                    openTransferOwnershipActivity();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showMemberLeaveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Rời nhóm")
                .setMessage("Bạn có chắc chắn muốn rời khỏi nhóm này không?")
                .setPositiveButton("Rời nhóm", (dialog, which) -> {
                    leaveGroup();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void openMembersActivity() {
        Intent intent = new Intent(this, GroupMembersActivity.class);
        intent.putExtra("groupId", groupId);
        startActivity(intent);
    }

    private void deleteGroupAndLeave() {
        groupRepository.deleteGroup(groupId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(GroupDetailActivity.this, "Đã xóa nhóm và rời khỏi nhóm", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupDetailActivity.this, "Lỗi khi xóa nhóm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveGroup() {
        groupRepository.removeMember(groupId, currentUserId, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(GroupDetailActivity.this, "Đã rời khỏi nhóm", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(GroupDetailActivity.this, "Lỗi khi rời nhóm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openTransferOwnershipActivity() {
        Intent intent = new Intent(this, TransferOwnershipActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("currentUserId", currentUserId);
        startActivityForResult(intent, 300);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 200 && resultCode == RESULT_OK) {
            if (data != null && "group_deleted".equals(data.getStringExtra("action"))) {
                // Group was deleted, finish this activity to go back to My Groups
                setResult(RESULT_OK, data);
                finish();
            }
            // Always reload group data when returning from settings
            loadGroupData();
        } else if (requestCode == 300 && resultCode == RESULT_OK) {
            // Ownership was transferred, reload group data
            loadGroupData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("GroupDetailActivity: onResume() - groupId = " + groupId);
        // Always reload group data when activity resumes
        loadGroupData();
    }

    private void showTextAvatar(ImageView imgAvatar, String displayName, String email) {
        // Create a simple text-based avatar
        String text = "";
        if (displayName != null && !displayName.isEmpty()) {
            text = displayName.substring(0, 1).toUpperCase();
        } else if (email != null && !email.isEmpty()) {
            text = email.substring(0, 1).toUpperCase();
        } else {
            text = "U"; // User
        }

        System.out.println("GroupDetailActivity: Showing text avatar with: " + text);

        // Create a custom drawable with text
        try {
            android.graphics.drawable.Drawable textDrawable = createTextDrawable(text);
            imgAvatar.setImageDrawable(textDrawable);
        } catch (Exception e) {
            System.out.println("GroupDetailActivity: Failed to create text drawable: " + e.getMessage());
            // Fallback to default avatar
            imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }

        // Set content description for accessibility
        imgAvatar.setContentDescription("Avatar for " + (displayName != null ? displayName : "user"));
    }

    private android.graphics.drawable.Drawable createTextDrawable(String text) {
        // Create a simple colored circle with text
        android.graphics.drawable.ShapeDrawable shape = new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape());
        shape.getPaint().setColor(android.graphics.Color.parseColor("#6200EA")); // Purple color

        // Create a bitmap with text
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        // Draw the circle
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.parseColor("#6200EA"));
        paint.setAntiAlias(true);
        canvas.drawCircle(100, 100, 100, paint);

        // Draw the text
        paint.setColor(android.graphics.Color.WHITE);
        paint.setTextSize(80);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setAntiAlias(true);

        // Center the text
        android.graphics.Rect bounds = new android.graphics.Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int x = 100;
        int y = 100 + bounds.height() / 2;

        canvas.drawText(text, x, y, paint);

        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private void createGroupChatAndOpen(String groupId) {
        chatRepository.getOrCreateGroupChat(groupId)
            .addOnSuccessListener(chatId -> {
                Intent intent = new Intent(this, ChatRoomActivity.class);
                intent.putExtra("chatId", chatId);
                intent.putExtra("chatTitle", currentGroup != null ? currentGroup.name : "Group Chat");
                intent.putExtra("chatType", "group");
                intent.putExtra("groupId", groupId);
                startActivity(intent);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to create group chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
}
