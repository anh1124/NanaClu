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
            if (position >= 0 && position < selectedImagePaths.size()) {
                selectedImagePaths.remove(position);
                imageAdapter.notifyItemRemoved(position);
                imageAdapter.notifyItemRangeChanged(position, selectedImagePaths.size() - position);
            }
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
            if (inputStream == null) return null;
            // Giữ nguyên dữ liệu gốc, không nén lại để không thay đổi kích thước
            File tempFile = new File(getCacheDir(), "temp_image_" + UUID.randomUUID().toString());
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
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
        post.imageUrls = new ArrayList<>();

        // Process images
        if (!selectedImagePaths.isEmpty()) {
            processImagesWithStorage(post);
        } else {
            // No images, create post directly
            routePostCreation(post, new PostRepository.PostCallback() {
                @Override
                public void onSuccess(Post createdPost) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Đã gửi bài đăng", Toast.LENGTH_SHORT).show();
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

    private void processImagesWithStorage(Post post) {
        // Convert image paths to byte arrays
        List<byte[]> imageDataList = new ArrayList<>();
        
        for (String imagePath : selectedImagePaths) {
            byte[] imageData = convertImageToByteArray(imagePath);
            if (imageData != null) {
                imageDataList.add(imageData);
            }
        }
        
        if (imageDataList.isEmpty()) {
            // No valid images, create post without images
            routePostCreation(post, new PostRepository.PostCallback() {
                @Override
                public void onSuccess(Post createdPost) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "Đã gửi bài đăng", Toast.LENGTH_SHORT).show();
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
        
        // Upload images to Firebase Storage
        postRepository.uploadMultipleImages(imageDataList, 
            urls -> {
                // Upload thành công, set URLs vào post
                post.imageUrls = urls;
                
                // Route post creation (check approval requirement)
                routePostCreation(post, new PostRepository.PostCallback() {
                    @Override
                    public void onSuccess(Post createdPost) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(CreatePostActivity.this, "Đã gửi bài đăng", Toast.LENGTH_SHORT).show();
                            setResult(Activity.RESULT_OK);
                            finish();
                        });
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(CreatePostActivity.this, "Lỗi tạo bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            },
            e -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(CreatePostActivity.this, "Lỗi upload ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        );
    }

    /** Decide whether to create immediate post or pending post based on group setting and role */
    private void routePostCreation(Post post, PostRepository.PostCallback callback) {
        // Load group setting to check requirePostApproval
        com.example.nanaclu.data.repository.GroupRepository gRepo = new com.example.nanaclu.data.repository.GroupRepository(FirebaseFirestore.getInstance());
        gRepo.getGroupById(post.groupId, new com.example.nanaclu.data.repository.GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(com.example.nanaclu.data.model.Group group) {
                // If approval required and current user is not owner/admin -> pending
                if (group != null && group.requirePostApproval) {
                    gRepo.getMemberById(post.groupId, currentUserId, new com.example.nanaclu.data.repository.GroupRepository.MemberCallback() {
                        @Override
                        public void onSuccess(com.example.nanaclu.data.model.Member member) {
                            boolean elevated = member != null && ("owner".equals(member.role) || "admin".equals(member.role));
                            if (elevated) {
                                postRepository.createPost(post, callback);
                            } else {
                                postRepository.createPendingPost(post, callback);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            // Fallback: treat as normal user
                            postRepository.createPendingPost(post, callback);
                        }
                    });
                } else {
                    postRepository.createPost(post, callback);
                }
            }

            @Override
            public void onError(Exception e) {
                // If cannot load group, fallback to direct create to avoid blocking user
                postRepository.createPost(post, callback);
            }
        });
    }

    private byte[] convertImageToByteArray(String imagePath) {
        try {
            // Tối ưu hóa ảnh trước khi upload
            final int maxDimension = 1600; // giới hạn cạnh dài
            final int targetMaxBytes = 1024 * 1024; // 1MB

            // 1) Decode kích thước ban đầu
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(imagePath, bounds);
            int srcW = bounds.outWidth;
            int srcH = bounds.outHeight;

            // 2) Tính inSampleSize sơ bộ để cạnh dài ~= maxDimension
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = 1;
            int longer = Math.max(srcW, srcH);
            while (longer / opts.inSampleSize > maxDimension) {
                opts.inSampleSize *= 2;
            }

            // 3) Decode bitmap với inSampleSize
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imagePath, opts);
            if (bitmap == null) return null;

            // 4) Scale chính xác về maxDimension nếu vẫn vượt
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            int curLonger = Math.max(bw, bh);
            if (curLonger > maxDimension) {
                float scale = (float) maxDimension / (float) curLonger;
                int newW = Math.round(bw * scale);
                int newH = Math.round(bh * scale);
                bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            }

            // 5) Nén JPEG chất lượng động để đạt dưới targetMaxBytes
            int quality = 90;
            byte[] jpegBytes;
            do {
                ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, jpegOut);
                jpegBytes = jpegOut.toByteArray();
                quality -= 5;
            } while (jpegBytes.length > targetMaxBytes && quality >= 60);

            return jpegBytes;
        } catch (Exception e) {
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


