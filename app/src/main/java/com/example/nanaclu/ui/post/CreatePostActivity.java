package com.example.nanaclu.ui.post;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.google.android.material.textfield.TextInputEditText;

public class CreatePostActivity extends AppCompatActivity {
    
    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_CAMERA_CAPTURE = 1002;
    private static final int PERMISSION_REQUEST_CAMERA = 1003;
    
    private androidx.appcompat.widget.Toolbar toolbar;
    private TextInputEditText etPostContent;
    private FrameLayout imagePreviewContainer;
    private ImageView ivImagePreview;
    private ImageButton btnRemoveImage;
    private ImageButton btnAddImage;
    private ImageButton btnCamera;
    private Button btnPost;
    
    private Uri selectedImageUri;
    private Bitmap selectedImageBitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        
        initViews();
        setupToolbar();
        setupClickListeners();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        etPostContent = findViewById(R.id.etPostContent);
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        ivImagePreview = findViewById(R.id.ivImagePreview);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);
        btnAddImage = findViewById(R.id.btnAddImage);
        btnCamera = findViewById(R.id.btnCamera);
        btnPost = findViewById(R.id.btnPost);
    }
    
    private void setupToolbar() {
        // Apply toolbar color from theme settings
        toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(this));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        
        // Setup navigation icon
        toolbar.setNavigationOnClickListener(v -> {
            // Return to previous screen
            finish();
        });
    }
    
    private void setupClickListeners() {
        btnAddImage.setOnClickListener(v -> openImagePicker());
        btnCamera.setOnClickListener(v -> openCamera());
        btnRemoveImage.setOnClickListener(v -> removeImage());
        btnPost.setOnClickListener(v -> createPost());
        
        // Make image preview clickable to change image
        ivImagePreview.setOnClickListener(v -> showImageOptionsDialog());
    }
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }
    
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 
                    PERMISSION_REQUEST_CAMERA);
        } else {
            launchCamera();
        }
    }
    
    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
    }
    
    private void removeImage() {
        selectedImageUri = null;
        selectedImageBitmap = null;
        imagePreviewContainer.setVisibility(View.GONE);
        ivImagePreview.setImageDrawable(null);
    }
    
    private void showImageOptionsDialog() {
        if (selectedImageUri != null || selectedImageBitmap != null) {
            // Show dialog to choose between gallery and camera
            String[] options = {"Chọn từ thư viện", "Chụp ảnh mới"};
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Thay đổi ảnh")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            openImagePicker();
                        } else {
                            openCamera();
                        }
                    })
                    .show();
        }
    }
    
    private void createPost() {
        String content = etPostContent.getText().toString().trim();
        
        if (content.isEmpty() && selectedImageUri == null && selectedImageBitmap == null) {
            Toast.makeText(this, "Vui lòng nhập nội dung hoặc chọn ảnh", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // TODO: Implement post creation logic
        Toast.makeText(this, "Đang tạo post...", Toast.LENGTH_SHORT).show();
        
        // For now, just show success and finish
        Toast.makeText(this, "Tạo post thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_IMAGE_PICK) {
                // Handle image pick result
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    setSelectedImage(imageUri, null);
                }
            } else if (requestCode == REQUEST_CAMERA_CAPTURE) {
                // Handle camera capture result
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        setSelectedImage(null, imageBitmap);
                    }
                }
            }
        }
    }
    
    private void setSelectedImage(Uri imageUri, Bitmap imageBitmap) {
        if (imageUri != null) {
            selectedImageUri = imageUri;
            selectedImageBitmap = null;
            ivImagePreview.setImageURI(imageUri);
        } else if (imageBitmap != null) {
            selectedImageUri = null;
            selectedImageBitmap = imageBitmap;
            ivImagePreview.setImageBitmap(imageBitmap);
        }
        
        // Show image preview
        imagePreviewContainer.setVisibility(View.VISIBLE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                        @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Cần quyền camera để chụp ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }
}


