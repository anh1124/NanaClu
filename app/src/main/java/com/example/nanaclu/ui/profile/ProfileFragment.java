package com.example.nanaclu.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.nanaclu.ui.BaseFragment;

import com.example.nanaclu.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.nanaclu.utils.ThemeUtils;

public class ProfileFragment extends BaseFragment {
    // Field đếm số lần click avatar để mở dashboard admin
    private int avatarClickCount = 0;
    private long lastAvatarClickTime = 0;
    private static final long CLICK_RESET_TIMEOUT_MS = 10000; // 10s

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);
        TextView tvDisplayName = root.findViewById(R.id.tvDisplayName);
        TextView tvEmail = root.findViewById(R.id.tvEmail);
        View btnLogout = root.findViewById(R.id.btnLogout);
        View colorPreview = root.findViewById(R.id.colorPreview);
        View btnPick = root.findViewById(R.id.btnPickColor);
        View friendsRow = root.findViewById(R.id.friendsRow);
        View securityRow = root.findViewById(R.id.securityRow);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);
        android.widget.ImageView imgAvatar = root.findViewById(R.id.imgAvatar);
        com.google.android.material.materialswitch.MaterialSwitch switchAuto = root.findViewById(R.id.switchAutoLogin);

        int currentColor = ThemeUtils.getThemeColor(requireContext());
        colorPreview.setBackgroundColor(currentColor);
        toolbar.setBackgroundColor(currentColor);

        // Bind auto-login switch
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE);
        boolean auto = prefs.getBoolean("auto_login", true);
        switchAuto.setChecked(auto);
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("auto_login", isChecked).apply());

        // Use cached profile first to avoid DB reads; update only on login
        android.content.SharedPreferences up = requireContext().getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE);
        String cachedName = up.getString("displayName", null);
        String cachedEmail = up.getString("email", null);
        String cachedPhoto = up.getString("photoUrl", null);
        if (cachedName != null) tvDisplayName.setText(cachedName);
        if (cachedEmail != null) tvEmail.setText(cachedEmail);
        if (cachedPhoto != null) {
            setupUserAvatar(imgAvatar, cachedName, cachedEmail, cachedPhoto);
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String dn = user.getDisplayName();
                String em = user.getEmail();
                String pu = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                tvDisplayName.setText(dn != null ? dn : tvDisplayName.getText());
                tvEmail.setText(em != null ? em : tvEmail.getText());
                setupUserAvatar(imgAvatar, dn, em, pu);
            }
        }

        // Load additional profile info
        TextView tvCreatedAt = root.findViewById(R.id.tvCreatedAt);
        TextView tvLastLogin = root.findViewById(R.id.tvLastLogin);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long createdAt = doc.getLong("createdAt");
                            Long lastLoginAt = doc.getLong("lastLoginAt");

                            if (createdAt != null) {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                                tvCreatedAt.setText("Tham gia: " + sdf.format(new java.util.Date(createdAt)));
                            }

                            if (lastLoginAt != null) {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
                                tvLastLogin.setText("Đăng nhập gần nhất: " + sdf.format(new java.util.Date(lastLoginAt)));
                            }
                        }
                    });
        }

        btnLogout.setOnClickListener(v -> openConfirmLogoutDialog());
        btnPick.setOnClickListener(v -> openColorPicker(toolbar, colorPreview));
        friendsRow.setOnClickListener(v -> openFriendsActivity());
        securityRow.setOnClickListener(v -> openSecurityActivity());
        // Bắt sự kiện click avatar để mở dashboard admin nếu đủ điều kiện
        imgAvatar.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastAvatarClickTime > CLICK_RESET_TIMEOUT_MS) {
                avatarClickCount = 0;
            }
            lastAvatarClickTime = now;
            avatarClickCount++;
            if (avatarClickCount == 5) {
                avatarClickCount = 0; // reset về 0 để tránh double trigger
                // Kiểm tra quyền admin từ Firestore
                com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) return;
                com.example.nanaclu.data.repository.AdminRepository adminRepo =
                        new com.example.nanaclu.data.repository.AdminRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
                adminRepo.checkIsAdmin(user.getUid(), new com.example.nanaclu.data.repository.AdminRepository.AdminCheckCallback() {
                    @Override
                    public void onResult(boolean isAdmin) {
                        if (isAdmin) {
                            // Mở dashboard admin
                            android.content.Intent i = new android.content.Intent(getContext(), com.example.nanaclu.ui.admin.AdminDashboardActivity.class);
                            startActivity(i);
                        } else {
                            android.widget.Toast.makeText(getContext(), "Bạn không có quyền admin", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onError(Exception e) {
                        android.widget.Toast.makeText(getContext(), "Lỗi kiểm tra quyền admin: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        return root;
    }

    @Override
    protected void onThemeChanged() {
        // Reapply theme color to toolbar and preview
        if (getView() != null) {
            androidx.appcompat.widget.Toolbar toolbar = getView().findViewById(R.id.toolbar);
            View colorPreview = getView().findViewById(R.id.colorPreview);
            if (toolbar != null && colorPreview != null) {
                int currentColor = ThemeUtils.getThemeColor(requireContext());
                toolbar.setBackgroundColor(currentColor);
                colorPreview.setBackgroundColor(currentColor);
            }
        }
    }

    private void openFriendsActivity() {
        android.content.Intent intent = new android.content.Intent(getContext(), com.example.nanaclu.ui.friends.FriendsActivity.class);
        startActivity(intent);
    }

    private void openSecurityActivity() {
        android.content.Intent intent = new android.content.Intent(getContext(), com.example.nanaclu.ui.security.SecurityActivity.class);
        startActivity(intent);
    }

    private void openColorPicker(androidx.appcompat.widget.Toolbar toolbar, View preview) {
        if (getContext() == null) return;
        android.app.Dialog d = new android.app.Dialog(getContext());
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout root = new android.widget.LinearLayout(getContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(getResources().getDisplayMetrics().density * 16);
        root.setPadding(pad,pad,pad,pad);

        android.widget.TextView title = new android.widget.TextView(getContext());
        title.setText("Chọn màu chủ đề");
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        android.widget.GridLayout grid = new android.widget.GridLayout(getContext());
        grid.setColumnCount(6);
        grid.setUseDefaultMargins(true);
        int[] swatches = buildColorSwatches();
        for (int c : swatches) {
            android.view.View v = new android.view.View(getContext());
            int size = (int)(getResources().getDisplayMetrics().density * 36);
            android.widget.GridLayout.LayoutParams p = new android.widget.GridLayout.LayoutParams();
            p.width = size; p.height = size; p.setMargins(pad/4,pad/4,pad/4,pad/4);
            v.setLayoutParams(p);
            v.setBackground(createCircleDrawable(c));
            v.setOnClickListener(x -> {
                ThemeUtils.saveThemeColor(requireContext(), c);
                toolbar.setBackgroundColor(c);
                preview.setBackgroundColor(c);
                d.dismiss();
                
                // Show toast to inform user
                android.widget.Toast.makeText(requireContext(), "Màu chủ đề đã được cập nhật!", android.widget.Toast.LENGTH_SHORT).show();
            });
            grid.addView(v);
        }
        scroll.addView(grid);
        root.addView(scroll, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int)(getResources().getDisplayMetrics().density * 320)));

        android.widget.Button btnClose = new android.widget.Button(getContext());
        btnClose.setText("Đóng");
        btnClose.setOnClickListener(v -> d.dismiss());
        root.addView(btnClose);

        d.setContentView(root);
        d.show();
    }

    private int[] buildColorSwatches() {
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        // Purples
        list.add(0xFF6200EE); list.add(0xFF7C4DFF); list.add(0xFF536DFE); list.add(0xFF3F51B5);
        // Blues
        list.add(0xFF2196F3); list.add(0xFF03A9F4); list.add(0xFF00BCD4); list.add(0xFF009688);
        // Greens
        list.add(0xFF4CAF50); list.add(0xFF8BC34A); list.add(0xFFCDDC39); list.add(0xFFFFEB3B);
        // Oranges/Reds
        list.add(0xFFFFC107); list.add(0xFFFF9800); list.add(0xFFFF5722); list.add(0xFFF44336);
        // Greys
        list.add(0xFF9E9E9E); list.add(0xFF607D8B); list.add(0xFF795548); list.add(0xFF000000);
        // Extra hues
        for (int h = 0; h < 360; h += 24) {
            int c = android.graphics.Color.HSVToColor(new float[]{h, 0.7f, 0.9f});
            list.add(c);
        }
        int[] arr = new int[list.size()];
        for (int i=0;i<list.size();i++) arr[i] = list.get(i);
        return arr;
    }

    private android.graphics.drawable.Drawable createCircleDrawable(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        return d;
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
            // Show loading state
            btnConfirm.setEnabled(false);
            btnConfirm.setAlpha(0.5f);
            
            // Use AuthRepository for proper logout with cache clearing
            com.example.nanaclu.data.repository.AuthRepository authRepo = 
                    new com.example.nanaclu.data.repository.AuthRepository(requireContext());
            
            authRepo.logout(requireContext()).addOnCompleteListener(logoutTask -> {
                // Clear Google Sign-In cached account to force account picker next time
                com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                        new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build();
                com.google.android.gms.auth.api.signin.GoogleSignInClient client =
                        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireContext(), gso);
                
                client.signOut().addOnCompleteListener(task1 -> 
                    client.revokeAccess().addOnCompleteListener(task2 -> {
                        if (getActivity() != null) {
                            // Clear remember me setting
                            getActivity().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("remember_me", false).apply();
                            
                            // Navigate to login screen
                            startActivity(new android.content.Intent(getActivity(), com.example.nanaclu.ui.auth.LoginActivity.class));
                            getActivity().finish();
                        }
                        d.dismiss();
                    }));
            });
        });

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
    
    private void setupUserAvatar(android.widget.ImageView imgAvatar, String displayName, String email, String photoUrl) {
        android.util.Log.d("ProfileAvatar", "=== AVATAR SETUP START ===");
        android.util.Log.d("ProfileAvatar", "Display Name: " + displayName);
        android.util.Log.d("ProfileAvatar", "Email: " + email);
        android.util.Log.d("ProfileAvatar", "Photo URL: " + photoUrl);

        if (photoUrl != null && !photoUrl.isEmpty()) {
            String url = photoUrl;
            if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                // Request a reasonable size for Google photos
                url = url + (url.contains("?") ? "&" : "?") + "sz=256";
            }
            try {
                com.bumptech.glide.Glide.with(this)
                        .load(url)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(imgAvatar);
            } catch (Exception e) {
                android.util.Log.e("ProfileAvatar", "Glide load error: " + e.getMessage());
                showTextAvatar(imgAvatar, displayName, email);
            }
        } else {
            showTextAvatar(imgAvatar, displayName, email);
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


