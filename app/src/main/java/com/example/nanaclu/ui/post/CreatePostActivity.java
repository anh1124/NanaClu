package com.example.nanaclu.ui.post;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.PostRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreatePostActivity extends AppCompatActivity {
    private static final int PICK_IMAGES_REQUEST = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;

    private MaterialToolbar toolbar;
    private TextInputLayout tilContent;
    private TextInputEditText etContent;
    private MaterialCardView cardAddImage;
    private RecyclerView rvImages;
    private MaterialButton btnPost;
    private ProgressBar progressBar;

    private PostRepository postRepository;
    private SelectedImageAdapter imageAdapter;
    private List<String> selectedImagePaths = new ArrayList<>();
    private String groupId;
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        // Get groupId from intent
        groupId = getIntent().getStringExtra("group_id");
        System.out.println("CreatePostActivity: Received groupId = " + groupId);
        if (groupId == null) {
            Toast.makeText(this, "Group ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get current user ID
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        initRepository();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilContent = findViewById(R.id.tilContent);
        etContent = findViewById(R.id.etContent);
        cardAddImage = findViewById(R.id.cardAddImage);
        rvImages = findViewById(R.id.rvImages);
        btnPost = findViewById(R.id.btnPost);
        progressBar = findViewById(R.id.progressBar);
    }
    
    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        imageAdapter = new SelectedImageAdapter();
        imageAdapter.setOnImageRemoveListener(position -> {
            selectedImagePaths.remove(position);
            imageAdapter.removeImage(position);
        });
        
        rvImages.setLayoutManager(new GridLayoutManager(this, 3));
        rvImages.setAdapter(imageAdapter);
    }
    
    private void setupClickListeners() {
        cardAddImage.setOnClickListener(v -> {
            if (checkPermission()) {
                pickImages();
            } else {
                requestPermission();
            }
        });

        btnPost.setOnClickListener(v -> createPost());
    }

    private void initRepository() {
        postRepository = new PostRepository(FirebaseFirestore.getInstance());
        
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - use READ_MEDIA_IMAGES
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 and below - use READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - request READ_MEDIA_IMAGES
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
                    PERMISSION_REQUEST_CODE);
        } else {
            // Android 12 and below - request READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGES_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                if (data.getClipData() != null) {
                    // Multiple images selected
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        String imagePath = getImagePathFromUri(imageUri);
                        if (imagePath != null) {
                            selectedImagePaths.add(imagePath);
                        }
                    }
                } else if (data.getData() != null) {
                    // Single image selected
                    Uri imageUri = data.getData();
                    String imagePath = getImagePathFromUri(imageUri);
                    if (imagePath != null) {
                        selectedImagePaths.add(imagePath);
                    }
                }
                
                imageAdapter.setImages(selectedImagePaths);
            }
        }
    }

    private String getImagePathFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            
            // Save to temporary file
            File tempFile = new File(getCacheDir(), "temp_image_" + UUID.randomUUID().toString() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            outputStream.close();
            
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void createPost() {
        String content = etContent.getText().toString().trim();
        
        if (content.isEmpty() && selectedImagePaths.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nội dung hoặc chọn hình ảnh", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);

        // Create Post object
        Post post = new Post();
        post.postId = UUID.randomUUID().toString();
        post.authorId = currentUserId;
        post.groupId = groupId;
        post.content = content;
        post.imageIds = new ArrayList<>();

        // Process images
        if (!selectedImagePaths.isEmpty()) {
            processImages(post, 0);
        } else {
            // No images, create post directly
            postRepository.createPost(post, new PostRepository.PostCallback() {
                @Override
                public void onSuccess(Post createdPost) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Đăng bài thành công!", Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_OK);
        finish();
                    });
    }
    
    @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void processImages(Post post, int index) {
        if (index >= selectedImagePaths.size()) {
            // All images processed, create post
            postRepository.createPost(post, new PostRepository.PostCallback() {
                @Override
                public void onSuccess(Post createdPost) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Đăng bài thành công!", Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_OK);
                        finish();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
            return;
        }

        String imagePath = selectedImagePaths.get(index);
        String imageId = UUID.randomUUID().toString();
        post.imageIds.add(imageId);

        // Convert image to base64
        System.out.println("CreatePostActivity: About to call convertImageToBase64 for imagePath = " + imagePath);
        String base64Data = convertImageToBase64(imagePath);
        System.out.println("CreatePostActivity: convertImageToBase64 returned, base64Data = " + (base64Data != null ? "NOT NULL" : "NULL"));
        System.out.println("CreatePostActivity: Converting image " + imagePath + " to base64");
        System.out.println("CreatePostActivity: Base64 data length = " + (base64Data != null ? base64Data.length() : "null"));
        if (base64Data != null) {
            // Save image data
            System.out.println("CreatePostActivity: Saving image data for imageId = " + imageId);
            postRepository.saveImageData(currentUserId, imageId, base64Data, new PostRepository.PostCallback() {
                @Override
                public void onSuccess(Post savedImage) {
                    System.out.println("CreatePostActivity: saveImageData SUCCESS for imageId = " + imageId);
                    // Process next image
                    processImages(post, index + 1);
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("CreatePostActivity: saveImageData ERROR for imageId = " + imageId + ", error = " + e.getMessage());
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Lỗi lưu hình ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            // Skip this image and continue
            processImages(post, index + 1);
        }
    }

    private String convertImageToBase64(String imagePath) {
        try {
            System.out.println("CreatePostActivity: convertImageToBase64 - imagePath = " + imagePath);
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                System.out.println("CreatePostActivity: convertImageToBase64 - bitmap is null!");
                return null;
            }
            System.out.println("CreatePostActivity: convertImageToBase64 - original bitmap size = " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // Resize bitmap to 500x500
            int targetSize = 500;
            int newWidth = targetSize;
            int newHeight = targetSize;
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            System.out.println("CreatePostActivity: convertImageToBase64 - resized bitmap size = " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            String base64 = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            
            // Log first 50 characters of base64
            System.out.println("CreatePostActivity: convertImageToBase64 - base64 first 50 chars = " + base64.substring(0, Math.min(50, base64.length())));
            System.out.println("CreatePostActivity: convertImageToBase64 - base64 length = " + base64.length());
            
            // Create temp variable and log it
            String temp = base64;
            System.out.println("CreatePostActivity: convertImageToBase64 - temp variable = " + temp.substring(0, Math.min(50, temp.length())));
            System.out.println("CreatePostActivity: convertImageToBase64 - temp length = " + temp.length());
            
            return temp;
        } catch (Exception e) {
            System.out.println("CreatePostActivity: convertImageToBase64 - error = " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnPost.setEnabled(!show);
        etContent.setEnabled(!show);
        cardAddImage.setEnabled(!show);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImages();
            } else {
                Toast.makeText(this, "Permission denied. Cannot access images.", Toast.LENGTH_LONG).show();
            }
        }
    }
}