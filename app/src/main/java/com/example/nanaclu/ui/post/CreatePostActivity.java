package com.example.nanaclu.ui.post;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.FrameLayout;

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
    private static final int PICK_VIDEO_REQUEST = 1004;
    private static final int PERMISSION_VIDEO = 1003;

    private MaterialToolbar toolbar;
    private TextInputLayout tilContent;
    private TextInputEditText etContent;
    private MaterialCardView cardAddImage;
    private MaterialCardView cardAddVideo;
    private RecyclerView rvImages;
    private MaterialButton btnPost;
    private ProgressBar progressBar;
    
    // Video UI components
    private FrameLayout videoPreviewContainer;
    private ImageView ivVideoThumb;
    private ImageView ivPlayIcon;
    private TextView tvVideoDuration;
    private ImageButton btnRemoveVideo;

    private PostRepository postRepository;
    private SelectedImageAdapter imageAdapter;
    private List<String> selectedImagePaths = new ArrayList<>();
    private Uri selectedVideoUri;
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
        cardAddVideo = findViewById(R.id.cardAddVideo);
        rvImages = findViewById(R.id.rvImages);
        btnPost = findViewById(R.id.btnPost);
        progressBar = findViewById(R.id.progressBar);
        
        // Video UI components
        videoPreviewContainer = findViewById(R.id.videoPreviewContainer);
        ivVideoThumb = findViewById(R.id.ivVideoThumb);
        ivPlayIcon = findViewById(R.id.ivPlayIcon);
        tvVideoDuration = findViewById(R.id.tvVideoDuration);
        btnRemoveVideo = findViewById(R.id.btnRemoveVideo);
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

        cardAddVideo.setOnClickListener(v -> {
            if (checkVideoPermission()) {
                pickVideo();
            } else {
                requestVideoPermission();
            }
        });

        btnRemoveVideo.setOnClickListener(v -> removeVideo());

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

    private boolean checkVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - use READ_MEDIA_VIDEO
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 and below - use READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - request READ_MEDIA_VIDEO
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_MEDIA_VIDEO}, 
                    PERMISSION_VIDEO);
        } else {
            // Android 12 and below - request READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    PERMISSION_VIDEO);
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(Intent.createChooser(intent, "Select Video"), PICK_VIDEO_REQUEST);
    }

    private boolean validateVideoSize(Uri videoUri) {
        try {
            long sizeBytes = getVideoFileSizeBytes(videoUri);
            long sizeMB = sizeBytes / (1024 * 1024);
            if (sizeMB > 20) {
                Toast.makeText(this, "Video quá lớn (>20MB)", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (sizeMB > 10) {
                // Show warning but allow
                new AlertDialog.Builder(this)
                    .setMessage("Video >10MB. Tải lên có thể lâu. Tiếp tục?")
                    .setPositiveButton("OK", (d, w) -> processVideo(videoUri))
                    .setNegativeButton("Hủy", null)
                    .show();
                return false; // Will handle in dialog
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private long getVideoFileSizeBytes(Uri uri) {
        long size = 0L;
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    Long value = cursor.getLong(sizeIndex);
                    if (value != null) size = value;
                }
                cursor.close();
            }
            if (size <= 0) {
                android.content.res.AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r");
                if (afd != null) {
                    size = afd.getLength();
                    afd.close();
                }
            }
        } catch (Exception ignored) { }
        return Math.max(size, 0L);
    }

    private void processVideo(Uri videoUri) {
        selectedVideoUri = videoUri;
        showVideoPreview(videoUri);
    }

    private void showVideoPreview(Uri videoUri) {
        // Generate thumbnail
        Bitmap thumbnail = PostRepository.generateVideoThumbnail(this, videoUri);
        if (thumbnail != null) {
            ivVideoThumb.setImageBitmap(thumbnail);
        }

        // Get video metadata
        PostRepository.VideoMetadata metadata = PostRepository.getVideoMetadata(this, videoUri);
        long sizeBytes = getVideoFileSizeBytes(videoUri);
        String info = formatDuration(metadata.duration);
        if (sizeBytes > 0) info += " · " + formatSize(sizeBytes);
        tvVideoDuration.setText(info);

        // Show video preview
        videoPreviewContainer.setVisibility(View.VISIBLE);
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double value = (double) bytes;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (unitIndex <= 1) {
            return String.format(java.util.Locale.getDefault(), "%.0f %s", value, units[unitIndex]);
        }
        return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unitIndex]);
    }

    private void removeVideo() {
        selectedVideoUri = null;
        videoPreviewContainer.setVisibility(View.GONE);
        cardAddImage.setEnabled(true); // Re-enable image picker
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
        } else if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri videoUri = data.getData();
                if (validateVideoSize(videoUri)) {
                    processVideo(videoUri);
                }
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
        
        if (content.isEmpty() && selectedImagePaths.isEmpty() && selectedVideoUri == null) {
            Toast.makeText(this, "Vui lòng nhập nội dung hoặc chọn hình ảnh/video", Toast.LENGTH_SHORT).show();
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

        boolean hasVideo = selectedVideoUri != null;
        boolean hasImages = !selectedImagePaths.isEmpty();

        if (hasVideo && hasImages) {
            // Post có cả ảnh và video: upload ảnh trước rồi tiếp tục với upload video
            createPostWithImagesAndVideo(post);
        } else if (hasVideo) {
            // Chỉ có video
            createPostWithVideo(post);
        } else if (hasImages) {
            // Chỉ có ảnh
            processImagesWithStorage(post);
        } else {
            // Không có media, tạo post trực tiếp
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
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                        String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                        if (errorMessage != null) {
                            Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CreatePostActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    private void createPostWithImagesAndVideo(Post post) {
        List<byte[]> imageDataList = new ArrayList<>();

        for (String imagePath : selectedImagePaths) {
            byte[] imageData = convertImageToByteArray(imagePath);
            if (imageData != null) {
                imageDataList.add(imageData);
            }
        }

        if (imageDataList.isEmpty()) {
            // Không có ảnh hợp lệ, fallback về luồng chỉ video
            createPostWithVideo(post);
            return;
        }

        postRepository.uploadMultipleImages(imageDataList,
            urls -> {
                // Upload ảnh thành công, set URLs vào post rồi tiếp tục upload video
                post.imageUrls = urls;
                createPostWithVideo(post);
            },
            e -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CreatePostActivity.this, "Lỗi upload ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        );
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
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                        String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                        if (errorMessage != null) {
                            Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CreatePostActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
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
                            com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                            String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                            if (errorMessage != null) {
                                Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(CreatePostActivity.this, "Lỗi tạo bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            },
            e -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (errorMessage != null) {
                        Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CreatePostActivity.this, "Lỗi upload ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        );
    }

    private void createPostWithVideo(Post post) {
        // 1. Generate thumbnail
        Bitmap thumb = PostRepository.generateVideoThumbnail(this, selectedVideoUri);
        if (thumb == null) {
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(this, "Không thể tạo thumbnail cho video", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        byte[] thumbBytes = PostRepository.compressBitmapToJpeg(thumb);
        
        // 2. Get metadata
        PostRepository.VideoMetadata meta = PostRepository.getVideoMetadata(this, selectedVideoUri);
        
        // 3. Upload thumbnail
        postRepository.uploadVideoThumbnail(thumbBytes, groupId, post.postId,
            thumbUrl -> {
                // 4. Upload video with progress
                postRepository.uploadVideoToStorage(selectedVideoUri, groupId, post.postId,
                    progress -> {
                        // Update progress on UI thread
                        runOnUiThread(() -> {
                            int progressPercent = (int) (100.0 * progress.getBytesTransferred() / progress.getTotalByteCount());
                            // Could show progress dialog here
                        });
                    },
                    videoUrl -> {
                        // 5. Create post with video fields
                        post.hasVideo = true;
                        post.videoUrl = videoUrl;
                        post.videoThumbUrl = thumbUrl;
                        post.videoDurationMs = meta.duration;
                        post.videoWidth = meta.width;
                        post.videoHeight = meta.height;
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
                                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", e);
                                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                                    if (errorMessage != null) {
                                        Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(CreatePostActivity.this, "Lỗi tạo bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    },
                    error -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", error);
                            String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(error);
                            if (errorMessage != null) {
                                Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(CreatePostActivity.this, "Lỗi upload video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
            },
            error -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("CreatePostActivity", error);
                    String errorMessage = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(error);
                    if (errorMessage != null) {
                        Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CreatePostActivity.this, "Lỗi upload thumbnail: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
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
        cardAddVideo.setEnabled(!show);
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
        } else if (requestCode == PERMISSION_VIDEO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickVideo();
            } else {
                Toast.makeText(this, "Permission denied. Cannot access videos.", Toast.LENGTH_LONG).show();
            }
        }
    }
}


