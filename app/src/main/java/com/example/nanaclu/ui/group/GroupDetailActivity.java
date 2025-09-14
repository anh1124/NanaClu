package com.example.nanaclu.ui.group;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.repository.GroupRepository;
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
    
    // Backup groupId for debugging
    private String backupGroupId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        // Get group ID from intent
        groupId = getIntent().getStringExtra("group_id");
        backupGroupId = groupId; // Store backup
        System.out.println("GroupDetailActivity: Received groupId = " + groupId);
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

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(this));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_group_detail);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        // Load group data
        loadGroupData();

        // Load current user's member info
        loadCurrentUserMember();

        // Setup user avatar
        setupUserAvatar();

        // Setup click listeners
        findViewById(R.id.btnInvite).setOnClickListener(v -> {});
        
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
                // TODO: implement comment later
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
                android.widget.Toast.makeText(GroupDetailActivity.this, "Đã gửi báo cáo", android.widget.Toast.LENGTH_SHORT).show();
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
        MenuItem settingsItem = toolbar.getMenu().findItem(R.id.action_group_settings);
        
        if (settingsItem != null) {
            boolean isAdminOrOwner = currentUserMember != null && 
                ("admin".equals(currentUserMember.role) || "owner".equals(currentUserMember.role));
            settingsItem.setVisible(isAdminOrOwner);
        }
    }

    private void updateUI(Group group) {
        TextView tvGroupName = findViewById(R.id.tvGroupName);
        TextView tvMeta = findViewById(R.id.tvMeta);
        
        tvGroupName.setText(group.name);
        
        String privacy = group.isPublic ? "Public" : "Private";
        String memberText = group.memberCount + " thành viên";
        tvMeta.setText(privacy + " • " + memberText);
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
            } else {
                postAdapter.addItems(posts);
                lastVisible = last;
            }
            isLoadingMore = false;
        }, e -> {
            isLoadingMore = false;
        });
    }

    private void setupUserAvatar() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        ImageView imgAvatar = findViewById(R.id.imgAvatar);
        
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            
            System.out.println("GroupDetailActivity: Current user - name: " + displayName + ", email: " + email);
            
            if (currentUser.getPhotoUrl() != null) {
                // User has a profile photo - try to load it
                System.out.println("GroupDetailActivity: Loading user avatar from: " + currentUser.getPhotoUrl());
                try {
                    // Try to load the image using a more reliable method
                    loadImageFromUrl(imgAvatar, currentUser.getPhotoUrl().toString());
                } catch (Exception e) {
                    System.out.println("GroupDetailActivity: Failed to load avatar from URI: " + e.getMessage());
                    // Fallback to text avatar
                    showTextAvatar(imgAvatar, displayName, email);
                }
            } else {
                // User doesn't have a profile photo, show text avatar
                System.out.println("GroupDetailActivity: No profile photo, showing text avatar");
                showTextAvatar(imgAvatar, displayName, email);
            }
        } else {
            // No user logged in, show default avatar
            System.out.println("GroupDetailActivity: No user logged in, showing default avatar");
            imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }
        System.out.println("GroupDetailActivity: === AVATAR SETUP END ===");
    }
    
    private void loadImageFromUrl(ImageView imgAvatar, String imageUrl) {
        System.out.println("GroupDetailActivity: === LOAD IMAGE FROM URL START ===");
        System.out.println("GroupDetailActivity: Loading image from: " + imageUrl);
        
        try {
            // Method 1: Try using setImageURI with a delay to check if it works
            imgAvatar.setImageURI(android.net.Uri.parse(imageUrl));
            System.out.println("GroupDetailActivity: setImageURI called with: " + imageUrl);
            
            // Check if the drawable was set after a short delay
            imgAvatar.postDelayed(() -> {
                if (imgAvatar.getDrawable() != null) {
                    System.out.println("GroupDetailActivity: ✅ Image loaded successfully via setImageURI");
                } else {
                    System.out.println("GroupDetailActivity: ❌ setImageURI failed, trying alternative method");
                    // Try alternative method - create a simple colored avatar with user's first letter
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String displayName = currentUser.getDisplayName();
                        String email = currentUser.getEmail();
                        showTextAvatar(imgAvatar, displayName, email);
                    }
                }
            }, 1000); // Wait 1 second to see if image loads
            
        } catch (Exception e) {
            System.out.println("GroupDetailActivity: ❌ Error in loadImageFromUrl: " + e.getMessage());
            // Fallback to text avatar
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String displayName = currentUser.getDisplayName();
                String email = currentUser.getEmail();
                showTextAvatar(imgAvatar, displayName, email);
            }
        }
        
        System.out.println("GroupDetailActivity: === LOAD IMAGE FROM URL END ===");
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        System.out.println("GroupDetailActivity: onMenuItemClick - groupId = " + groupId);
        
        if (id == R.id.action_leave_group) {
            showLeaveGroupDialog();
            return true;
        } else if (id == R.id.action_share_group) {
            // TODO: Implement share group functionality
            return true;
        } else if (id == R.id.action_view_members) {
            // Open GroupMembersActivity
            Intent intent = new Intent(this, GroupMembersActivity.class);
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
        } else if (id == R.id.action_create_post) {
            // Open CreatePostActivity
            System.out.println("GroupDetailActivity: action_create_post clicked - groupId = " + groupId);
            
            // Use backupGroupId if groupId is null
            if (groupId == null && backupGroupId != null) {
                groupId = backupGroupId;
                System.out.println("GroupDetailActivity: Restored groupId from backup = " + groupId);
            }
            
            // Double check groupId is not null
            if (groupId == null) {
                Toast.makeText(this, "ERROR: groupId is null in action_create_post!", Toast.LENGTH_LONG).show();
                return true;
            }
            
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            intent.putExtra("group_id", groupId);
            startActivityForResult(intent, 400);
            return true;
        }
        return false;
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
}
