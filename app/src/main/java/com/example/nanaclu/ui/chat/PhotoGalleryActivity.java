package com.example.nanaclu.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.ui.adapter.PhotoGalleryAdapter;
import com.example.nanaclu.ui.post.ImageViewerActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryActivity extends AppCompatActivity {
    private RecyclerView rvPhotos;
    private PhotoGalleryAdapter adapter;
    private MaterialToolbar toolbar;
    private FirebaseFirestore db;

    private String chatId;
    private String chatType;
    private String groupId;
    private List<Message> allPhotoMessages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_gallery);
        
        initViews();
        getIntentData();
        setupToolbar();
        setupRecyclerView();
        loadPhotos();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvPhotos = findViewById(R.id.rvPhotos);
        db = FirebaseFirestore.getInstance();
    }

    private void getIntentData() {
        chatId = getIntent().getStringExtra("chatId");
        chatType = getIntent().getStringExtra("chatType");
        groupId = getIntent().getStringExtra("groupId");
        
        String chatTitle = getIntent().getStringExtra("chatTitle");
        if (chatTitle != null) {
            toolbar.setTitle("Ảnh - " + chatTitle);
        } else {
            toolbar.setTitle("Ảnh đã gửi");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new PhotoGalleryAdapter(new ArrayList<>(), this::openPhotoViewer);
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3)); // 3 columns like Google Photos
        rvPhotos.setAdapter(adapter);
    }

    private void loadPhotos() {
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
                .whereEqualTo("type", "image")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allPhotoMessages.clear();
                    queryDocumentSnapshots.forEach(doc -> {
                        Message message = doc.toObject(Message.class);
                        if (message != null && message.content != null && !message.content.isEmpty()) {
                            message.messageId = doc.getId();
                            allPhotoMessages.add(message);
                        }
                    });
                    adapter.updatePhotos(allPhotoMessages);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openPhotoViewer(Message photoMessage) {
        // Create list of image URLs
        ArrayList<String> imageUrls = new ArrayList<>();
        for (Message msg : allPhotoMessages) {
            if (msg.content != null && !msg.content.isEmpty()) {
                imageUrls.add(msg.content);
            }
        }

        // Find current photo index
        int currentIndex = 0;
        for (int i = 0; i < allPhotoMessages.size(); i++) {
            if (allPhotoMessages.get(i).messageId != null &&
                allPhotoMessages.get(i).messageId.equals(photoMessage.messageId)) {
                currentIndex = i;
                break;
            }
        }

        // Open ImageViewerActivity
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGES, imageUrls);
        intent.putExtra(ImageViewerActivity.EXTRA_INDEX, currentIndex);
        startActivity(intent);
    }
}
