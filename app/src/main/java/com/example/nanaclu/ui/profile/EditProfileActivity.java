package com.example.nanaclu.ui.profile;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EditProfileActivity extends AppCompatActivity {
    private static final String TAG = "EditProfileActivity";
    private static final int MAX_IMAGE_SIZE = 512;
    private static final int COMPRESSION_QUALITY = 80;

    // UI Components
    private MaterialToolbar toolbar;
    private ImageView imgAvatar;
    private MaterialButton btnChangeAvatar;
    private TextInputEditText etDisplayName;
    private TextInputLayout textInputLayoutDisplayName;
    private ProgressBar progressBar;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;

    // Data
    private UserRepository userRepository;
    private String currentUserId;
    private User currentUser;
    private Uri currentImageUri;
    private String currentDisplayName;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    // Permission constants
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initViews();
        setupToolbar();
        setupActivityResultLaunchers();
        loadCurrentUserData();
        setupClickListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imgAvatar = findViewById(R.id.imgAvatar);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        etDisplayName = findViewById(R.id.etDisplayName);
        textInputLayoutDisplayName = findViewById(R.id.textInputLayoutDisplayName);
        progressBar = findViewById(R.id.progressBar);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);

        userRepository = new UserRepository(FirebaseFirestore.getInstance());
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chỉnh sửa hồ sơ");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupActivityResultLaunchers() {
        // Camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            processSelectedImage(imageUri);
                        }
                    }
                }
        );

        // Gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            processSelectedImage(imageUri);
                        }
                    }
                }
        );

        // Permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    
                    if (allGranted) {
                        showImageSourceDialog();
                    } else {
                        Toast.makeText(this, "Cần cấp quyền để thay đổi ảnh đại diện", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void loadCurrentUserData() {
        if (currentUserId == null) {
            Toast.makeText(this, "Không thể tải thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userRepository.getUserById(currentUserId, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                currentDisplayName = user.displayName;
                
                // Update UI with current data
                etDisplayName.setText(user.displayName);
                
                // Load avatar
                if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                    String url = user.photoUrl;
                    if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                        url += (url.contains("?") ? "&" : "?") + "sz=256";
                    }
                    Glide.with(EditProfileActivity.this)
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
                Log.e(TAG, "Error loading user data", e);
                Toast.makeText(EditProfileActivity.this, "Không thể tải thông tin người dùng", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupClickListeners() {
        btnChangeAvatar.setOnClickListener(v -> {
            if (checkPermissions()) {
                showImageSourceDialog();
            } else {
                requestPermissions();
            }
        });

        btnSave.setOnClickListener(v -> saveProfile());

        btnCancel.setOnClickListener(v -> finish());
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        permissionLauncher.launch(REQUIRED_PERMISSIONS);
    }

    private void showImageSourceDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_choose_image_source);
        dialog.setCancelable(true);

        MaterialButton btnCamera = dialog.findViewById(R.id.btnCamera);
        MaterialButton btnGallery = dialog.findViewById(R.id.btnGallery);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCamera.setOnClickListener(v -> {
            dialog.dismiss();
            openCamera();
        });

        btnGallery.setOnClickListener(v -> {
            dialog.dismiss();
            openGallery();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void openCamera() {
        try {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoURI = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraLauncher.launch(cameraIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Không thể mở camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        galleryLauncher.launch(galleryIntent);
    }

    private File createImageFile() throws IOException {
        String imageFileName = "TEMP_" + System.currentTimeMillis();
        File storageDir = getExternalFilesDir("Pictures");
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        return image;
    }

    private void processSelectedImage(Uri imageUri) {
        currentImageUri = imageUri;
        
        try {
            // Compress and display the image
            Bitmap bitmap = compressImage(imageUri);
            imgAvatar.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            Toast.makeText(this, "Không thể xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap compressImage(Uri imageUri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
        
        if (originalBitmap == null) {
            throw new IOException("Không thể đọc ảnh");
        }

        // Calculate new dimensions
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        float ratio = Math.min((float) MAX_IMAGE_SIZE / width, (float) MAX_IMAGE_SIZE / height);
        
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        // Create compressed bitmap
        Bitmap compressedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
        originalBitmap.recycle();
        
        return compressedBitmap;
    }

    private void saveProfile() {
        String newDisplayName = etDisplayName.getText().toString().trim();
        
        // Validate display name
        if (newDisplayName.isEmpty()) {
            textInputLayoutDisplayName.setError("Tên hiển thị không được để trống");
            return;
        }
        
        if (newDisplayName.length() < 3 || newDisplayName.length() > 50) {
            textInputLayoutDisplayName.setError("Tên hiển thị phải từ 3-50 ký tự");
            return;
        }
        
        textInputLayoutDisplayName.setError(null);
        
        // Check if anything changed
        boolean displayNameChanged = !newDisplayName.equals(currentDisplayName);
        boolean avatarChanged = currentImageUri != null;
        
        if (!displayNameChanged && !avatarChanged) {
            Toast.makeText(this, "Không có thay đổi nào", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        if (avatarChanged) {
            uploadAvatarAndSaveProfile(newDisplayName);
        } else {
            saveDisplayNameOnly(newDisplayName);
        }
    }

    private void uploadAvatarAndSaveProfile(String displayName) {
        try {
            Bitmap bitmap = compressImage(currentImageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
            byte[] imageData = baos.toByteArray();
            
            // Upload to Firebase Storage
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            String fileName = "avatar_" + currentUserId + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference avatarRef = storageRef.child("user_avatars").child(currentUserId).child(fileName);
            
            UploadTask uploadTask = avatarRef.putBytes(imageData);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                avatarRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String photoUrl = uri.toString();
                    userRepository.updateUserProfile(currentUserId, displayName, photoUrl, new UserRepository.UserUpdateCallback() {
                        @Override
                        public void onSuccess() {
                            updateLocalCache(displayName, photoUrl);
                            showLoading(false);
                            Toast.makeText(EditProfileActivity.this, "Cập nhật hồ sơ thành công", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "Error updating profile", e);
                            showLoading(false);
                            Toast.makeText(EditProfileActivity.this, "Lỗi cập nhật hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting download URL", e);
                    showLoading(false);
                    Toast.makeText(EditProfileActivity.this, "Lỗi tải ảnh lên", Toast.LENGTH_SHORT).show();
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error uploading image", e);
                showLoading(false);
                Toast.makeText(EditProfileActivity.this, "Lỗi tải ảnh lên", Toast.LENGTH_SHORT).show();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image for upload", e);
            showLoading(false);
            Toast.makeText(this, "Lỗi xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveDisplayNameOnly(String displayName) {
        userRepository.updateUserDisplayName(currentUserId, displayName, new UserRepository.UserUpdateCallback() {
            @Override
            public void onSuccess() {
                updateLocalCache(displayName, currentUser.photoUrl);
                showLoading(false);
                Toast.makeText(EditProfileActivity.this, "Cập nhật tên thành công", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error updating display name", e);
                showLoading(false);
                Toast.makeText(EditProfileActivity.this, "Lỗi cập nhật tên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLocalCache(String displayName, String photoUrl) {
        // Update SharedPreferences cache
        getSharedPreferences("user_profile", MODE_PRIVATE)
                .edit()
                .putString("displayName", displayName)
                .putString("photoUrl", photoUrl)
                .apply();
        
        // Update Firebase Auth profile if needed
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            com.google.firebase.auth.UserProfileChangeRequest profileUpdates = 
                    new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .setPhotoUri(photoUrl != null ? Uri.parse(photoUrl) : null)
                            .build();
            
            firebaseUser.updateProfile(profileUpdates);
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
        btnChangeAvatar.setEnabled(!show);
    }
}
