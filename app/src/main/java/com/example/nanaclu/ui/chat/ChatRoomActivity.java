package com.example.nanaclu.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nanaclu.ui.BaseActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.viewmodel.ChatRoomViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import com.example.nanaclu.utils.FileActionsUtil;
import com.example.nanaclu.utils.ActiveScreenTracker;

public class ChatRoomActivity extends BaseActivity {
    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int PICK_FILE_REQUEST = 1002;
    private static final int TAKE_PHOTO_REQUEST = 1003;

    private ChatRoomViewModel viewModel;
    private MessageAdapter adapter;
    private RecyclerView rvMessages;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etMessage;
    private ImageButton btnSend, btnAttach, btnAttachFile;
    private MaterialToolbar toolbar;
    private MaterialButton btnScrollDown;

    // Upload progress views
    private LinearLayout uploadProgressContainer;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressText;

    private String chatId;
    private String chatTitle = "Chat";
    private String chatType;
    private String groupId;

    private boolean canDeleteGroupChat = false;
    // State for bottom/new messages
    private boolean isAtBottom = true;
    private int lastRenderedCount = 0;
    private int pendingNewCount = 0;

    private FileActionsUtil chatFileActions;

    // Image preview
    private LinearLayout imagePreviewContainer;
    private RecyclerView rvImagePreview;
    private android.widget.Button btnSendImages;
    private com.example.nanaclu.ui.adapter.ImagePreviewAdapter imagePreviewAdapter;
    private java.util.List<Uri> selectedImages = new ArrayList<>();
    private Uri photoUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        // Get chat info from intent
        chatId = getIntent().getStringExtra("chatId");
        chatTitle = getIntent().getStringExtra("chatTitle");
        chatType = getIntent().getStringExtra("chatType");
        groupId = getIntent().getStringExtra("groupId");
        if (chatTitle == null) chatTitle = "Chat";
        // Preload permissions for group chat to decide showing delete option
        if ("group".equals(chatType) && groupId != null) {
            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            if (uid != null) {
                new com.example.nanaclu.data.repository.GroupRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance())
                        .getMemberById(groupId, uid, new com.example.nanaclu.data.repository.GroupRepository.MemberCallback() {
                            @Override public void onSuccess(com.example.nanaclu.data.model.Member member) {
                                canDeleteGroupChat = (member != null && ("admin".equals(member.role) || "owner".equals(member.role)));
                            }
                            @Override public void onError(Exception e) { canDeleteGroupChat = false; }
                        });
            }
        }

        if (chatId == null) {
            Toast.makeText(this, "Invalid chat ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupViewModel();
        setupClickListeners();
        
        // Set active chat for notification suppression
        ActiveScreenTracker.setActiveChatId(chatId);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvMessages = findViewById(R.id.rvMessages);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnScrollDown = findViewById(R.id.btnScrollDown);
        if (btnScrollDown != null) btnScrollDown.setVisibility(View.GONE);

        // Upload progress views
        uploadProgressContainer = findViewById(R.id.uploadProgressContainer);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressText = findViewById(R.id.uploadProgressText);

        // Khởi tạo helper xử lý file (gom logic mở/tải)
        chatFileActions = new FileActionsUtil(this, new com.example.nanaclu.data.repository.FileRepository(this));

        // Initialize image preview
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        rvImagePreview = findViewById(R.id.rvImagePreview);
        btnSendImages = findViewById(R.id.btnSendImages);
        
        // Setup image preview RecyclerView
        rvImagePreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        imagePreviewAdapter = new com.example.nanaclu.ui.adapter.ImagePreviewAdapter(position -> {
            // Remove image at position
            selectedImages.remove(position);
            imagePreviewAdapter.setImages(selectedImages);
            if (selectedImages.isEmpty()) {
                imagePreviewContainer.setVisibility(View.GONE);
            }
        });
        rvImagePreview.setAdapter(imagePreviewAdapter);
        
        // Setup send images button
        btnSendImages.setOnClickListener(v -> sendSelectedImages());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(chatTitle);
        }
        
        // Apply theme color
        int themeColor = com.example.nanaclu.utils.ThemeUtils.getThemeColor(this);
        toolbar.setBackgroundColor(themeColor);
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
    }

    @Override
    protected void onThemeChanged() {
        // Reapply theme color to toolbar
        if (toolbar != null) {
            int themeColor = com.example.nanaclu.utils.ThemeUtils.getThemeColor(this);
            toolbar.setBackgroundColor(themeColor);
            toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat_room, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_more) {
            showChatMoreOptions();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // latest at bottom
        rvMessages.setLayoutManager(layoutManager);

        // Adapter của màn chatroom: truyền listener để xử lý các hành động trên item (ảnh, file, xóa...)
        // LƯU Ý: Toàn bộ logic khi ấn vào item_file_attachment ở chatroom được xử lý tại đây (trong Activity),
        // không nằm trong adapter. Adapter chỉ chuyển tiếp sự kiện qua listener.
        adapter = new MessageAdapter(new ArrayList<>(), new MessageAdapter.OnMessageClickListener() {
            @Override
            public void onMessageLongClick(Message message) {
                // TODO: Show message actions (edit, reply, delete)
                Toast.makeText(ChatRoomActivity.this, "Message actions coming soon", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteMessage(Message message) {
                // Show delete confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(ChatRoomActivity.this)
                        .setTitle("Xóa tin nhắn")
                        .setMessage("Bạn có chắc muốn xóa tin nhắn này?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            viewModel.recallMessage(message.messageId);
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }

            @Override
            public void onImageClick(Message message) {
                // Open image viewer for single image
                Intent intent = new Intent(ChatRoomActivity.this, com.example.nanaclu.ui.post.ImageViewerActivity.class);
                java.util.ArrayList<String> imageUrls = new java.util.ArrayList<>();
                imageUrls.add(message.content);
                intent.putStringArrayListExtra(com.example.nanaclu.ui.post.ImageViewerActivity.EXTRA_IMAGES, imageUrls);
                intent.putExtra(com.example.nanaclu.ui.post.ImageViewerActivity.EXTRA_INDEX, 0);
                startActivity(intent);
            }

            @Override
            public void onFileClick(com.example.nanaclu.data.model.FileAttachment file) {
                // Dùng util: nếu có sẵn thì mở, chưa có thì tải rồi mở
                chatFileActions.handleFileClick(file);
            }

            @Override
            public void onFileDownload(com.example.nanaclu.data.model.FileAttachment file) {
                // Dùng util: tải (nếu chưa) rồi mở
                chatFileActions.handleFileClick(file);
            }
        });
        rvMessages.setAdapter(adapter);

        // Track bottom state
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                boolean nowAtBottom = total == 0 || lastVisible >= total - 1;
                if (nowAtBottom != isAtBottom) {
                    isAtBottom = nowAtBottom;
                    if (isAtBottom) {
                        pendingNewCount = 0;
                        updateScrollDownButton();
                    }
                }
            }
        });

        // Pull-to-refresh: load older messages
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadOlderMessages());
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ChatRoomViewModel.class);

        // Initialize file repository
        viewModel.initFileRepository(this);

        viewModel.messages.observe(this, messages -> {
            if (messages == null) return;
            int newCount = messages.size();
            adapter.setMessages(messages);

            if (isAtBottom || lastRenderedCount == 0) {
                rvMessages.scrollToPosition(Math.max(0, newCount - 1));
                pendingNewCount = 0;
            } else if (newCount > lastRenderedCount) {
                pendingNewCount += (newCount - lastRenderedCount);
            }
            lastRenderedCount = newCount;
            updateScrollDownButton();
        });

        viewModel.sending.observe(this, sending -> {
            btnSend.setEnabled(!sending);
            btnAttach.setEnabled(!sending);
        });

        viewModel.loading.observe(this, loading -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });

        viewModel.error.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
            swipeRefresh.setRefreshing(false);
        });

        // File handling observers
        viewModel.uploading.observe(this, uploading -> {
            // Show/hide upload progress UI
            if (uploading != null && uploading) {
                // Show progress container
                uploadProgressContainer.setVisibility(View.VISIBLE);
                uploadProgressBar.setProgress(0);
                uploadProgressText.setText("Đang tải lên...");

                // Disable file attach button during upload
                btnAttachFile.setEnabled(false);
                btnAttachFile.setAlpha(0.5f);
            } else {
                // Hide progress container
                uploadProgressContainer.setVisibility(View.GONE);

                // Re-enable file attach button
                btnAttachFile.setEnabled(true);
                btnAttachFile.setAlpha(1.0f);
            }
        });

        viewModel.uploadProgress.observe(this, progress -> {
            // Update upload progress
            if (progress != null && uploadProgressContainer.getVisibility() == View.VISIBLE) {
                uploadProgressBar.setProgress(progress);
                uploadProgressText.setText("Đang tải lên... " + progress + "%");
            }
        });

        viewModel.fileError.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                // Show error with more context
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Lỗi tải file")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        // Initialize chat room
        viewModel.init(chatId, chatType, groupId);
        viewModel.markRead();
    }

    private void updateScrollDownButton() {
        if (btnScrollDown == null) return;
        if (isAtBottom || pendingNewCount <= 0) {
            btnScrollDown.setVisibility(View.GONE);
        } else {
            btnScrollDown.setText(pendingNewCount > 0 ? ("Tin nhắn mới " + pendingNewCount) : "Xuống");
            btnScrollDown.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> openImagePicker());
        btnAttachFile.setOnClickListener(v -> openFilePicker());
        if (btnScrollDown != null) {
            btnScrollDown.setOnClickListener(v -> {
                rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                pendingNewCount = 0;
                isAtBottom = true;
                updateScrollDownButton();
            });
        }
    }

    private void sendTextMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        viewModel.sendText(text);
        etMessage.setText("");
    }

    private void openImagePicker() {
        // Show dialog to choose between camera and gallery
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_image_source, null);
        dialog.setView(view);
        
        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            openCamera();
        });
        
        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            openGallery();
        });
        
        dialog.show();
    }

    private void openCamera() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Create file to save photo
            try {
                java.io.File photoFile = createImageFile();
                if (photoFile != null) {
                    photoUri = androidx.core.content.FileProvider.getUriForFile(this,
                            getApplicationContext().getPackageName() + ".fileprovider",
                            photoFile);
                    intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, TAKE_PHOTO_REQUEST);
                }
            } catch (java.io.IOException ex) {
                Toast.makeText(this, "Lỗi tạo file ảnh", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Không tìm thấy ứng dụng camera", Toast.LENGTH_SHORT).show();
        }
    }

    private java.io.File createImageFile() throws java.io.IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        java.io.File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        return java.io.File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh"), PICK_IMAGE_REQUEST);
    }

    private void sendSelectedImages() {
        if (selectedImages.isEmpty()) return;
        
        // Hide preview
        imagePreviewContainer.setVisibility(View.GONE);
        
        // Send images one by one
        for (int i = 0; i < selectedImages.size(); i++) {
            Uri imageUri = selectedImages.get(i);
            final int imageNumber = i + 1;
            final int totalImages = selectedImages.size();
            
            // Add a small delay between each image to ensure order
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                viewModel.sendImage(imageUri);
                if (imageNumber == totalImages) {
                    Toast.makeText(this, "Đã gửi " + totalImages + " ảnh", Toast.LENGTH_SHORT).show();
                }
            }, i * 500); // 500ms delay between each image
        }
        
        // Clear selected images
        selectedImages.clear();
        imagePreviewAdapter.setImages(selectedImages);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        String[] mimeTypes = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select files"), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            // Handle multiple images from gallery
            if (data.getClipData() != null) {
                // Multiple images selected
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    if (imageUri != null && !selectedImages.contains(imageUri)) {
                        selectedImages.add(imageUri);
                    }
                }
            } else if (data.getData() != null) {
                // Single image selected
                Uri imageUri = data.getData();
                if (!selectedImages.contains(imageUri)) {
                    selectedImages.add(imageUri);
                }
            }
            
            // Show preview
            if (!selectedImages.isEmpty()) {
                imagePreviewAdapter.setImages(selectedImages);
                imagePreviewContainer.setVisibility(View.VISIBLE);
            }
        } else if (requestCode == TAKE_PHOTO_REQUEST && resultCode == RESULT_OK) {
            // Handle photo from camera
            if (photoUri != null && !selectedImages.contains(photoUri)) {
                selectedImages.add(photoUri);
                imagePreviewAdapter.setImages(selectedImages);
                imagePreviewContainer.setVisibility(View.VISIBLE);
            }
        } else if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            java.util.List<Uri> fileUris = new java.util.ArrayList<>();

            if (data.getClipData() != null) {
                // Multiple files selected
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    if (fileUri != null) {
                        fileUris.add(fileUri);
                    }
                }
            } else if (data.getData() != null) {
                // Single file selected
                fileUris.add(data.getData());
            }

            if (!fileUris.isEmpty()) {
                // Show confirmation for multiple files
                if (fileUris.size() > 1) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Gửi files")
                            .setMessage("Bạn có muốn gửi " + fileUris.size() + " files?")
                            .setPositiveButton("Gửi", (dialog, which) -> {
                                viewModel.uploadFiles(fileUris);
                                Toast.makeText(this, "Đang tải lên " + fileUris.size() + " files...", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                } else {
                    viewModel.uploadFiles(fileUris);
                    Toast.makeText(this, "Đang tải lên file...", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }



    private void showChatMoreOptions() {
        // Simple menu via AlertDialog; can be upgraded to a bottom sheet with avatar preview
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Tùy chỉnh chủ đề");
        items.add("Tìm kiếm trong cuộc trò chuyện");
        if ("group".equals(chatType)) {
            items.add("Xem thành viên");
            items.add("Xem ảnh");
            items.add("Xem files");
            items.add("Tùy chỉnh chủ đề");
            items.add("Rời khỏi đoạn chat");
            if (canDeleteGroupChat) items.add("Xóa đoạn chat");
        } else {
            items.add("Xem ảnh");
            items.add("Xem files");
            items.add("Tùy chỉnh chủ đề");
            items.add("Xóa đoạn chat");
        }
        String[] arr = items.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(chatTitle)
                .setItems(arr, (d, which) -> {
                    String sel = arr[which];
                    if (sel.startsWith("Tùy chỉnh")) {
                        Toast.makeText(this, "Đổi chủ đề (WIP)", Toast.LENGTH_SHORT).show();
                    } else if (sel.startsWith("Tìm kiếm")) {
                        Toast.makeText(this, "Tìm kiếm (WIP)", Toast.LENGTH_SHORT).show();
                    } else if (sel.startsWith("Xem thành viên")) {
                        // Open members activity
                        Intent membersIntent = new Intent(this, MembersActivity.class);
                        membersIntent.putExtra("chatId", chatId);
                        membersIntent.putExtra("chatType", chatType);
                        membersIntent.putExtra("groupId", groupId);
                        membersIntent.putExtra("chatTitle", chatTitle);
                        startActivity(membersIntent);
                    } else if (sel.startsWith("Xem ảnh")) {
                        // Open photo gallery
                        Intent galleryIntent = new Intent(this, PhotoGalleryActivity.class);
                        galleryIntent.putExtra("chatId", chatId);
                        galleryIntent.putExtra("chatType", chatType);
                        galleryIntent.putExtra("groupId", groupId);
                        galleryIntent.putExtra("chatTitle", chatTitle);
                        startActivity(galleryIntent);
                    } else if (sel.startsWith("Xem files")) {
                        // Open file gallery
                        Intent fileGalleryIntent = new Intent(this, FileGalleryActivity.class);
                        fileGalleryIntent.putExtra("chatId", chatId);
                        fileGalleryIntent.putExtra("chatType", chatType);
                        fileGalleryIntent.putExtra("groupId", groupId);
                        fileGalleryIntent.putExtra("chatTitle", chatTitle);
                        startActivity(fileGalleryIntent);
                    } else if (sel.startsWith("Tùy chỉnh chủ đề")) {
                        // TODO: Open theme customization
                        Toast.makeText(this, "Tùy chỉnh chủ đề sẽ có sớm", Toast.LENGTH_SHORT).show();
                    } else if (sel.startsWith("Rời")) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Rời đoạn chat")
                                .setMessage("Bạn có chắc muốn rời nhóm này?")
                                .setPositiveButton("Rời", (dd, w) -> Toast.makeText(this, "Đã rời nhóm", Toast.LENGTH_SHORT).show())
                                .setNegativeButton("Hủy", null)
                                .show();
                    } else if (sel.startsWith("Xóa")) {
                        if ("group".equals(chatType)) {
                            // For group chat, show final warning and only allow admin/owner
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Cảnh báo lần cuối!")
                                    .setMessage("Bạn có chắc chắn muốn xóa đoạn chat này không? Hành động này sẽ xóa đoạn chat cho tất cả thành viên và không thể hoàn tác!")
                                    .setPositiveButton("Xóa", (dd, w) -> {
                                        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                                                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                                        if (uid == null) { Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show(); return; }
                                        new com.example.nanaclu.data.repository.ChatRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance())
                                                .deleteGroupChat(chatId, groupId)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(this, "Đã xóa đoạn chat cho tất cả thành viên", Toast.LENGTH_SHORT).show();
                                                    finish();
                                                })
                                                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    })
                                    .setNegativeButton("Hủy", null)
                                    .show();
                        } else {
                            // For private chat, use original logic
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Xóa đoạn chat")
                                    .setMessage("Bạn chỉ xóa ở phía bạn. Khi cả hai cùng xóa, đoạn chat mới bị xóa khỏi máy chủ. Tiếp tục?")
                                    .setPositiveButton("Xóa", (dd, w) -> {
                                        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                                                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                                        if (uid == null) { Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show(); return; }
                                        new com.example.nanaclu.data.repository.ChatRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance())
                                                .hideChatForUser(chatId, uid)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(this, "Đã ẩn đoạn chat", Toast.LENGTH_SHORT).show();
                                                    finish();
                                                })
                                                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    })
                                    .setNegativeButton("Hủy", null)
                                    .show();
                        }
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.markRead();
    }

    /**
     * Mở file bằng ứng dụng ngoài thông qua FileProvider.
     * (Đã chuyển vào ChatFileActions; giữ lại để tham chiếu nếu cần)
     */
    private void openFileWithExternalApp(com.example.nanaclu.data.model.FileAttachment file) {
        if (file.localPath == null) {
            Toast.makeText(this, "File chưa được tải xuống", Toast.LENGTH_SHORT).show();
            return;
        }
        java.io.File localFile = new java.io.File(file.localPath);
        if (!localFile.exists()) {
            Toast.makeText(this, "File không tồn tại trên thiết bị", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", localFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, file.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent chooser = Intent.createChooser(intent, "Mở file với ứng dụng nào?");
                if (chooser.resolveActivity(getPackageManager()) != null) {
                    startActivity(chooser);
                } else {
                    Toast.makeText(this, "Không có ứng dụng nào có thể mở file này", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi mở file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear active chat
        ActiveScreenTracker.setActiveChatId(null);
    }
}
