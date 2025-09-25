package com.example.nanaclu.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

public class ChatRoomActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1001;

    private ChatRoomViewModel viewModel;
    private MessageAdapter adapter;
    private RecyclerView rvMessages;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etMessage;
    private ImageButton btnSend, btnAttach;
    private MaterialToolbar toolbar;
    private MaterialButton btnScrollDown;

    private String chatId;
    private String chatTitle = "Chat";
    private String chatType;
    private String groupId;

    private boolean canDeleteGroupChat = false;
    // State for bottom/new messages
    private boolean isAtBottom = true;
    private int lastRenderedCount = 0;
    private int pendingNewCount = 0;

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
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvMessages = findViewById(R.id.rvMessages);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        btnScrollDown = findViewById(R.id.btnScrollDown);
        if (btnScrollDown != null) btnScrollDown.setVisibility(View.GONE);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(chatTitle);
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                viewModel.sendImage(imageUri);
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
            items.add("Tùy chỉnh chủ đề");
            items.add("Rời khỏi đoạn chat");
            if (canDeleteGroupChat) items.add("Xóa đoạn chat");
        } else {
            items.add("Xem ảnh");
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
}
