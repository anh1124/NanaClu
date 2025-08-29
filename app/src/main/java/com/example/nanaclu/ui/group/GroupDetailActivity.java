package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.utils.ThemeUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class GroupDetailActivity extends AppCompatActivity {
    private GroupRepository groupRepository;
    private String groupId;
    private Group currentGroup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        // Get group ID from intent
        groupId = getIntent().getStringExtra("group_id");
        if (groupId == null) {
            finish();
            return;
        }

        // Initialize repository
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

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

        // Setup user avatar
        setupUserAvatar();

        // Setup click listeners
        findViewById(R.id.btnInvite).setOnClickListener(v -> {});
        
        // Make entire post composer area clickable
        View postComposerArea = findViewById(R.id.postComposer);
        postComposerArea.setOnClickListener(v -> {
            // Open CreatePostActivity when clicking anywhere in the post composer area
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            startActivity(intent);
        });
        
        // Also make individual elements clickable for better UX
        View btnAddImage = findViewById(R.id.btnAddImage);
        TextView edtPost = findViewById(R.id.edtPost);
        
        // These will also open CreatePostActivity
        btnAddImage.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            startActivity(intent);
        });
        
        edtPost.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class);
            startActivity(intent);
        });
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

    private void updateUI(Group group) {
        TextView tvGroupName = findViewById(R.id.tvGroupName);
        TextView tvMeta = findViewById(R.id.tvMeta);
        
        tvGroupName.setText(group.name);
        
        String privacy = group.isPublic ? "Public" : "Private";
        String memberText = group.memberCount + " thành viên";
        tvMeta.setText(privacy + " • " + memberText);
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
        if (id == R.id.action_leave_group) {
            return true;
        } else if (id == R.id.action_share_group) {
            return true;
        } else if (id == R.id.action_view_members) {
            return true;
        } else if (id == R.id.action_manage_members) {
            return true;
        }
        return false;
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


