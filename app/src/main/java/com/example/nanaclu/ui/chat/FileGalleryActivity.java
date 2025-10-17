package com.example.nanaclu.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.FileAttachment;
import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.data.repository.FileRepository;
import com.example.nanaclu.ui.adapter.FileAttachmentAdapter;
import com.example.nanaclu.utils.FileActionsUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileGalleryActivity extends AppCompatActivity {
    private RecyclerView rvFiles;
    private FileAttachmentAdapter adapter;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefresh;
    private FirebaseFirestore db;
    private FileRepository fileRepository;
    private FileActionsUtil fileActionsUtil;

    private String chatId;
    private String chatType;
    private String groupId;
    private List<FileAttachment> allFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_gallery);
        
        initViews();
        getIntentData();
        setupToolbar();
        setupRecyclerView();
        loadFiles();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvFiles = findViewById(R.id.rvFiles);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        db = FirebaseFirestore.getInstance();
        fileRepository = new FileRepository(this);
        // Helper xử lý file dùng chung
        fileActionsUtil = new FileActionsUtil(this, fileRepository);
    }

    private void getIntentData() {
        chatId = getIntent().getStringExtra("chatId");
        chatType = getIntent().getStringExtra("chatType");
        groupId = getIntent().getStringExtra("groupId");
        
        String chatTitle = getIntent().getStringExtra("chatTitle");
        if (chatTitle != null) {
            toolbar.setTitle("Files - " + chatTitle);
        } else {
            toolbar.setTitle("Files đã gửi");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(com.example.nanaclu.utils.ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_gallery_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open_file_location) {
            // Sử dụng FileActionsUtil để mở thư mục Downloads của app
            fileActionsUtil.openDownloadFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new FileAttachmentAdapter(new ArrayList<>(), 
            new FileAttachmentAdapter.OnFileActionListener() {
                @Override
                public void onFileClick(FileAttachment file) {
                    fileActionsUtil.handleFileClick(file);
                }
                @Override
                public void onDownloadClick(FileAttachment file) {
                    fileActionsUtil.handleFileClick(file);
                }
                @Override
                public void onDeleteClick(FileAttachment file) { }
            }, this, false);
        
        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        rvFiles.setAdapter(adapter);
        
        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener(this::loadFiles);
    }

    private void loadFiles() {
        swipeRefresh.setRefreshing(true);
        
        Query query;
        if ("group".equals(chatType) && groupId != null) {
            // Group chat: groups/{groupId}/chats/{chatId}/messages
            query = db.collection("groups")
                    .document(groupId)
                    .collection("chats")
                    .document(chatId)
                    .collection("messages");
        } else {
            // Private chat: chats/{chatId}/messages
            query = db.collection("chats")
                    .document(chatId)
                    .collection("messages");
        }

        query
                .whereIn("type", java.util.Arrays.asList("file", "mixed"))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allFiles.clear();
                    queryDocumentSnapshots.forEach(doc -> {
                        Message message = doc.toObject(Message.class);
                        if (message != null && message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                            message.messageId = doc.getId();
                            for (FileAttachment file : message.fileAttachments) {
                                file.senderName = message.authorName; // Gán tên người gửi
                                allFiles.add(file);
                            }
                        }
                    });
                    adapter.updateFiles(allFiles);
                    swipeRefresh.setRefreshing(false);
                    
                    if (allFiles.isEmpty()) {
                        Toast.makeText(this, "Chưa có file nào", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Lỗi tải files: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Download file về thiết bị
     */
    private void downloadFile(FileAttachment file) {
        if (file.isDownloading) {
            Toast.makeText(this, "File đang được tải xuống...", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Bắt đầu tải xuống: " + file.fileName, Toast.LENGTH_SHORT).show();
        
        fileRepository.downloadFile(file, new FileRepository.ProgressCallback() {
            @Override
            public void onProgress(int progress) {
                // Progress sẽ được update trong adapter
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(FileGalleryActivity.this, 
                        "Tải xuống thành công: " + file.fileName, Toast.LENGTH_SHORT).show();
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(FileGalleryActivity.this, 
                        "Lỗi tải xuống: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    /**
     * Download file và tự động mở sau khi hoàn thành
     */
    private void downloadAndOpenFile(FileAttachment file) {
        if (file.isDownloading) {
            Toast.makeText(this, "File đang được tải xuống...", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang tải xuống và sẽ mở file: " + file.fileName, Toast.LENGTH_SHORT).show();
        
        fileRepository.downloadFile(file, new FileRepository.ProgressCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(FileGalleryActivity.this, 
                        "Tải xuống thành công, đang mở file...", Toast.LENGTH_SHORT).show();
                    adapter.notifyDataSetChanged();
                    // Tự động mở file sau khi download xong
                    openFileWithExternalApp(file);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(FileGalleryActivity.this, 
                        "Lỗi tải xuống: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    /**
     * Mở file bằng external app
     */
    private void openFileWithExternalApp(FileAttachment file) {
        if (file.localPath == null) {
            Toast.makeText(this, "File chưa được tải xuống", Toast.LENGTH_SHORT).show();
            return;
        }

        File localFile = new File(file.localPath);
        if (!localFile.exists()) {
            Toast.makeText(this, "File không tồn tại trên thiết bị", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Sử dụng FileProvider để share file với external apps
            Uri fileUri = Uri.fromFile(localFile);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, file.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Kiểm tra có app nào có thể mở file này không
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Thử với chooser nếu không tìm thấy app phù hợp
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
}
