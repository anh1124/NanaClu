package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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
        View composer = findViewById(R.id.postComposer);
        View btnImage = findViewById(R.id.btnAddImage);
        View edt = findViewById(R.id.edtPost);
        View.OnClickListener openCreatePost = x -> startActivity(new android.content.Intent(this, com.example.nanaclu.ui.post.CreatePostActivity.class));
        composer.setOnClickListener(openCreatePost);
        btnImage.setOnClickListener(openCreatePost);
        edt.setOnClickListener(openCreatePost);
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
            if (currentUser.getPhotoUrl() != null) {
                // User has a profile photo
                System.out.println("GroupDetailActivity: Loading user avatar from: " + currentUser.getPhotoUrl());
                imgAvatar.setImageURI(currentUser.getPhotoUrl());
            } else {
                // User doesn't have a profile photo, show first letter of display name
                System.out.println("GroupDetailActivity: No profile photo, showing first letter");
                String displayName = currentUser.getDisplayName();
                if (displayName != null && !displayName.isEmpty()) {
                    String firstLetter = displayName.substring(0, 1).toUpperCase();
                    // Create a text drawable or use a placeholder
                    imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
                } else {
                    // Fallback to default avatar
                    imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
                }
            }
        } else {
            // No user logged in, show default avatar
            System.out.println("GroupDetailActivity: No user logged in, showing default avatar");
            imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }
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
}


