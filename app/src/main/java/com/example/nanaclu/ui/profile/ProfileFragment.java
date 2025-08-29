package com.example.nanaclu.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nanaclu.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.nanaclu.utils.ThemeUtils;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);
        TextView tvDisplayName = root.findViewById(R.id.tvDisplayName);
        TextView tvEmail = root.findViewById(R.id.tvEmail);
        View btnLogout = root.findViewById(R.id.btnLogout);
        View colorPreview = root.findViewById(R.id.colorPreview);
        View btnPick = root.findViewById(R.id.btnPickColor);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);
        android.widget.ImageView imgAvatar = root.findViewById(R.id.imgAvatar);

        int currentColor = ThemeUtils.getToolbarColor(requireContext());
        colorPreview.setBackgroundColor(currentColor);
        toolbar.setBackgroundColor(currentColor);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");
            
            // Setup user avatar
            setupUserAvatar(imgAvatar, user);
        }

        btnLogout.setOnClickListener(v -> openConfirmLogoutDialog());
        btnPick.setOnClickListener(v -> openColorPicker(toolbar, colorPreview));
        return root;
    }

    private void openColorPicker(androidx.appcompat.widget.Toolbar toolbar, View preview) {
        if (getContext() == null) return;
        // đơn giản: chọn nhanh 3 màu preset
        android.app.Dialog d = new android.app.Dialog(getContext());
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(24,24,24,24);
        int[] colors = new int[]{android.graphics.Color.parseColor("#6200EE"), android.graphics.Color.parseColor("#03DAC5"), android.graphics.Color.parseColor("#FF5722")};
        for (int c : colors) {
            android.view.View v = new android.view.View(getContext());
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(80,80);
            lp.setMargins(16,0,16,0);
            v.setLayoutParams(lp);
            v.setBackgroundColor(c);
            v.setOnClickListener(x -> {
                ThemeUtils.saveToolbarColor(requireContext(), c);
                toolbar.setBackgroundColor(c);
                preview.setBackgroundColor(c);
                d.dismiss();
            });
            layout.addView(v);
        }
        d.setContentView(layout);
        d.show();
    }

    private void openConfirmLogoutDialog() {
        if (getContext() == null) return;
        android.app.Dialog d = new android.app.Dialog(getContext());
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_confirm_logout);
        d.setCancelable(true);

        View btnCancel = d.findViewById(R.id.btnCancel);
        View btnConfirm = d.findViewById(R.id.btnConfirm);
        btnCancel.setOnClickListener(v -> d.dismiss());
        btnConfirm.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            if (getActivity() != null) {
                getActivity().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("remember_me", false).apply();
                startActivity(new android.content.Intent(getActivity(), com.example.nanaclu.ui.auth.LoginActivity.class));
                getActivity().finish();
            }
            d.dismiss();
        });

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
    
    private void setupUserAvatar(android.widget.ImageView imgAvatar, FirebaseUser user) {
        if (user != null) {
            String displayName = user.getDisplayName();
            String email = user.getEmail();
            
            android.util.Log.d("ProfileAvatar", "=== AVATAR SETUP START ===");
            android.util.Log.d("ProfileAvatar", "User ID: " + user.getUid());
            android.util.Log.d("ProfileAvatar", "Display Name: " + displayName);
            android.util.Log.d("ProfileAvatar", "Email: " + email);
            android.util.Log.d("ProfileAvatar", "Photo URL: " + user.getPhotoUrl());
            android.util.Log.d("ProfileAvatar", "Provider Data Count: " + user.getProviderData().size());
            
            // Log all provider data
            for (com.google.firebase.auth.UserInfo profile : user.getProviderData()) {
                android.util.Log.d("ProfileAvatar", "Provider: " + profile.getProviderId() + 
                    ", Photo: " + profile.getPhotoUrl() + 
                    ", Name: " + profile.getDisplayName());
            }
            
            if (user.getPhotoUrl() != null) {
                // User has a profile photo - try to load it
                android.util.Log.d("ProfileAvatar", "Attempting to load photo from: " + user.getPhotoUrl());
                try {
                    // Try to load the image using a more reliable method
                    loadImageFromUrl(imgAvatar, user.getPhotoUrl().toString());
                    
                } catch (Exception e) {
                    android.util.Log.e("ProfileAvatar", "❌ Failed to load avatar from URI: " + e.getMessage(), e);
                    android.util.Log.d("ProfileAvatar", "Stack trace: " + android.util.Log.getStackTraceString(e));
                    // Fallback to text avatar
                    android.util.Log.d("ProfileAvatar", "Falling back to text avatar");
                    showTextAvatar(imgAvatar, displayName, email);
                }
            } else {
                // User doesn't have a profile photo, show text avatar
                android.util.Log.d("ProfileAvatar", "No profile photo available, showing text avatar");
                showTextAvatar(imgAvatar, displayName, email);
            }
        } else {
            // No user logged in, show default avatar
            android.util.Log.w("ProfileAvatar", "No user logged in, showing default avatar");
            imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }
        android.util.Log.d("ProfileAvatar", "=== AVATAR SETUP END ===");
    }
    
    private void loadImageFromUrl(android.widget.ImageView imgAvatar, String imageUrl) {
        android.util.Log.d("ProfileAvatar", "=== LOAD IMAGE FROM URL START ===");
        android.util.Log.d("ProfileAvatar", "Loading image from: " + imageUrl);
        
        try {
            // Method 1: Try using setImageURI with a delay to check if it works
            imgAvatar.setImageURI(android.net.Uri.parse(imageUrl));
            android.util.Log.d("ProfileAvatar", "setImageURI called with: " + imageUrl);
            
            // Check if the drawable was set after a short delay
            imgAvatar.postDelayed(() -> {
                if (imgAvatar.getDrawable() != null) {
                    android.util.Log.d("ProfileAvatar", "✅ Image loaded successfully via setImageURI");
                } else {
                    android.util.Log.d("ProfileAvatar", "❌ setImageURI failed, trying alternative method");
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
            android.util.Log.e("ProfileAvatar", "❌ Error in loadImageFromUrl: " + e.getMessage(), e);
            // Fallback to text avatar
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String displayName = currentUser.getDisplayName();
                String email = currentUser.getEmail();
                showTextAvatar(imgAvatar, displayName, email);
            }
        }
        
        android.util.Log.d("ProfileAvatar", "=== LOAD IMAGE FROM URL END ===");
    }
    
    private void showTextAvatar(android.widget.ImageView imgAvatar, String displayName, String email) {
        android.util.Log.d("ProfileAvatar", "=== TEXT AVATAR CREATION START ===");
        
        // Create a simple text-based avatar
        String text = "";
        if (displayName != null && !displayName.isEmpty()) {
            text = displayName.substring(0, 1).toUpperCase();
            android.util.Log.d("ProfileAvatar", "Using first letter of display name: " + text);
        } else if (email != null && !email.isEmpty()) {
            text = email.substring(0, 1).toUpperCase();
            android.util.Log.d("ProfileAvatar", "Using first letter of email: " + text);
        } else {
            text = "U"; // User
            android.util.Log.d("ProfileAvatar", "Using default letter: " + text);
        }
        
        android.util.Log.d("ProfileAvatar", "Final text for avatar: " + text);
        
        // Create a custom drawable with text
        try {
            android.util.Log.d("ProfileAvatar", "Creating custom text drawable...");
            android.graphics.drawable.Drawable textDrawable = createTextDrawable(text);
            android.util.Log.d("ProfileAvatar", "Text drawable created successfully");
            
            imgAvatar.setImageDrawable(textDrawable);
            android.util.Log.d("ProfileAvatar", "✅ Text drawable set to ImageView");
            
            // Verify the drawable was set
            if (imgAvatar.getDrawable() != null) {
                android.util.Log.d("ProfileAvatar", "✅ ImageView drawable verified after setImageDrawable");
            } else {
                android.util.Log.e("ProfileAvatar", "❌ ImageView drawable is null after setImageDrawable");
            }
            
        } catch (Exception e) {
            android.util.Log.e("ProfileAvatar", "❌ Failed to create text drawable: " + e.getMessage(), e);
            android.util.Log.d("ProfileAvatar", "Stack trace: " + android.util.Log.getStackTraceString(e));
            // Fallback to default avatar
            android.util.Log.d("ProfileAvatar", "Falling back to default avatar resource");
            imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }
        
        // Set content description for accessibility
        imgAvatar.setContentDescription("Avatar for " + (displayName != null ? displayName : "user"));
        android.util.Log.d("ProfileAvatar", "Content description set");
        android.util.Log.d("ProfileAvatar", "=== TEXT AVATAR CREATION END ===");
    }
    
    private android.graphics.drawable.Drawable createTextDrawable(String text) {
        android.util.Log.d("ProfileAvatar", "Creating text drawable with text: " + text);
        
        try {
            // Create a simple colored circle with text
            android.graphics.drawable.ShapeDrawable shape = new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape());
            shape.getPaint().setColor(android.graphics.Color.parseColor("#6200EA")); // Purple color
            android.util.Log.d("ProfileAvatar", "Shape drawable created with purple color");
            
            // Create a bitmap with text
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
            android.util.Log.d("ProfileAvatar", "Bitmap created: 200x200");
            
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            android.util.Log.d("ProfileAvatar", "Canvas created");
            
            // Draw the circle
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.parseColor("#6200EA"));
            paint.setAntiAlias(true);
            canvas.drawCircle(100, 100, 100, paint);
            android.util.Log.d("ProfileAvatar", "Circle drawn on canvas");
            
            // Draw the text
            paint.setColor(android.graphics.Color.WHITE);
            paint.setTextSize(80);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setAntiAlias(true);
            android.util.Log.d("ProfileAvatar", "Text paint configured");
            
            // Center the text
            android.graphics.Rect bounds = new android.graphics.Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            int x = 100;
            int y = 100 + bounds.height() / 2;
            
            canvas.drawText(text, x, y, paint);
            android.util.Log.d("ProfileAvatar", "Text drawn on canvas at position (" + x + ", " + y + ")");
            
            android.graphics.drawable.Drawable result = new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
            android.util.Log.d("ProfileAvatar", "✅ Text drawable created successfully");
            return result;
            
        } catch (Exception e) {
            android.util.Log.e("ProfileAvatar", "❌ Error creating text drawable: " + e.getMessage(), e);
            throw e;
        }
    }
}


