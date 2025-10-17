package com.example.nanaclu.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.nanaclu.ui.BaseFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Chat;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.viewmodel.ChatListViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends BaseFragment {

    private RecyclerView rv;
    private ChatThreadAdapter adapter;
    private final List<ChatThread> allThreads = new ArrayList<>();
    private ChatListViewModel viewModel;
    private UserRepository userRepository;
    private GroupRepository groupRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        // Apply theme color
        int themeColor = com.example.nanaclu.utils.ThemeUtils.getThemeColor(requireContext());
        toolbar.setBackgroundColor(themeColor);
        
        // Bold white title
        android.text.SpannableString s = new android.text.SpannableString("Chats");
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, s.length(), 0);
        toolbar.setTitle(s);
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);

        toolbar.inflateMenu(R.menu.menu_chat);
        // Replace menu item with custom action view to control icon size
        android.view.MenuItem item = toolbar.getMenu().findItem(R.id.action_new_chat);
        android.view.LayoutInflater.from(getContext());
        android.view.View action = android.view.LayoutInflater.from(getContext()).inflate(R.layout.menu_action_new_chat, toolbar, false);
        item.setActionView(action);
        android.view.View btn = action.findViewById(R.id.btnNewChat);
        btn.setOnClickListener(v -> android.widget.Toast.makeText(getContext(), "New chat", android.widget.Toast.LENGTH_SHORT).show());

        EditText edtSearch = root.findViewById(R.id.edtSearch);
        rv = root.findViewById(R.id.rvChatThreads);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatThreadAdapter(new ArrayList<>(), this::showThreadActions);
        rv.setAdapter(adapter);

        // Initialize repositories
        userRepository = new UserRepository(FirebaseFirestore.getInstance());
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        setupViewModel();

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });
        return root;
    }

    @Override
    protected void onThemeChanged() {
        // Reapply theme color to toolbar
        if (getView() != null) {
            MaterialToolbar toolbar = getView().findViewById(R.id.toolbar);
            if (toolbar != null) {
                int themeColor = com.example.nanaclu.utils.ThemeUtils.getThemeColor(requireContext());
                toolbar.setBackgroundColor(themeColor);
                toolbar.setTitleTextColor(android.graphics.Color.WHITE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh chat list to update any changes (like group avatars)
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ChatListViewModel.class);

        viewModel.threads.observe(getViewLifecycleOwner(), chats -> {
            if (chats != null) {
                allThreads.clear();
                String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                        ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                for (Chat chat : chats) {
                    android.util.Log.d("ChatFragment", "Chat: " + chat.chatId + ", type: " + chat.type + ", pairKey: " + chat.pairKey);
                    // Filter out invalid private chat placeholders that would show as "Private Chat"
                    if ("private".equals(chat.type)) {
                        if (chat.pairKey == null || currentUserId == null) {
                            android.util.Log.d("ChatFragment", "Skipping private chat with null pairKey or currentUserId");
                            continue;
                        }
                        String[] userIds = chat.pairKey.split("_");
                        String otherUserId = null;
                        for (String userId : userIds) {
                            if (!userId.equals(currentUserId)) { otherUserId = userId; break; }
                        }
                        if (otherUserId == null || otherUserId.isEmpty()) {
                            android.util.Log.d("ChatFragment", "Skipping private chat with invalid pairKey (no other user)");
                            continue;
                        }
                    }

                    ChatThread thread = new ChatThread();
                    thread.chatId = chat.chatId;
                    thread.name = ""; // will set after resolving
                    thread.lastMessage = chat.lastMessage != null ? chat.lastMessage : "No messages yet";
                    thread.time = chat.lastMessageAt != null ? chat.lastMessageAt : chat.createdAt;
                    thread.chat = chat; // Store chat reference

                    // Fallback: nếu chat nhóm chưa có lastMessage (tài liệu cũ), lấy 1 message mới nhất để hiển thị preview
                    if ("group".equals(chat.type) && (thread.lastMessage == null || "No messages yet".equals(thread.lastMessage))) {
                        if (chat.groupId != null) {
                            new com.example.nanaclu.data.repository.MessageRepository(
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                                    com.google.firebase.storage.FirebaseStorage.getInstance()
                            ).listMessages(chat.chatId, null, 1, "group", chat.groupId)
                             .addOnSuccessListener(list -> {
                                 if (list != null && !list.isEmpty()) {
                                     com.example.nanaclu.data.model.Message m = list.get(list.size() - 1);
                                     thread.lastMessage = "image".equals(m.type) ? "📷 Image" : (m.content != null ? m.content : "");
                                     thread.time = m.createdAt;
                                     adapter.notifyDataSetChanged();
                                 }
                             });
                        }
                    }

                    // Load display name asynchronously for all chat types
                    loadChatDisplayName(chat, thread);
                }
                adapter.setItems(new ArrayList<>(allThreads));
            }
        });

        // Load chats
        viewModel.refresh();
    }

    private void loadChatDisplayName(Chat chat, ChatThread thread) {
        if ("group".equals(chat.type)) {
            // Load group name and avatar
            if (chat.groupId != null) {
                groupRepository.getGroupById(chat.groupId, new GroupRepository.GroupCallback() {
                    @Override
                    public void onSuccess(Group group) {
                        if (group != null) {
                            thread.name = group.name != null ? group.name : "Group Chat";
                            thread.avatarUrl = group.avatarImageId; // Use avatar image
                        } else {
                            thread.name = "Group Chat";
                        }
                        addThreadToListIfNotExists(thread);
                    }

                    @Override
                    public void onError(Exception e) {
                        thread.name = "Group Chat";
                        addThreadToListIfNotExists(thread);
                    }
                });
            } else {
                thread.name = "Group Chat";
                addThreadToListIfNotExists(thread);
            }
        } else {
            // For private chat, find the other user
            String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            if (currentUserId == null || chat.pairKey == null) {
                return; // skip adding placeholder
            }

            // Extract other user ID from pairKey
            String[] userIds = chat.pairKey.split("_");
            String otherUserId = null;
            for (String userId : userIds) {
                if (!userId.equals(currentUserId)) { otherUserId = userId; break; }
            }
            if (otherUserId == null || otherUserId.isEmpty()) return;

            userRepository.getUserById(otherUserId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        thread.name = (user.displayName != null ? user.displayName : "");
                        thread.avatarUrl = user.photoUrl; // Use user's photo
                        addThreadToListIfNotExists(thread);
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Do not add placeholder rows like "Đang trò chuyện"
                }
            });
        }
    }

    private void addThreadToListIfNotExists(ChatThread thread) {
        if (!allThreads.contains(thread)) {
            allThreads.add(thread);
            adapter.setItems(new ArrayList<>(allThreads));
        } else {
            // Update existing thread data
            for (int i = 0; i < allThreads.size(); i++) {
                if (allThreads.get(i).equals(thread)) {
                    allThreads.set(i, thread);
                    break;
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void filter(String q) {
        if (q == null || q.trim().isEmpty()) {
            adapter.setItems(new ArrayList<>(allThreads));
            return;
        }

        List<ChatThread> filtered = new ArrayList<>();
        String query = q.toLowerCase().trim();
        for (ChatThread t : allThreads) {
            if (t.name != null && t.name.toLowerCase().contains(query)) {
                filtered.add(t);
            } else if (t.lastMessage != null && t.lastMessage.toLowerCase().contains(query)) {
                filtered.add(t);
            }
        }
        adapter.setItems(filtered);
    }

    private void showThreadActions(ChatThread thread) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View v = View.inflate(getContext(), R.layout.bottomsheet_chat_actions, null);
        v.findViewById(R.id.btnDelete).setOnClickListener(x -> {
            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            if (uid == null) { dialog.dismiss(); return; }
            com.example.nanaclu.data.repository.ChatRepository repo = new com.example.nanaclu.data.repository.ChatRepository(FirebaseFirestore.getInstance());
            repo.hideChatForUser(thread.chatId, uid)
                    .addOnSuccessListener(aVoid -> {
                        allThreads.remove(thread);
                        adapter.setItems(new ArrayList<>(allThreads));
                        Toast.makeText(getContext(), "Đoạn chat đã được ẩn", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Lỗi ẩn đoạn chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });
        v.findViewById(R.id.btnMute).setOnClickListener(x -> { dialog.dismiss(); });
        dialog.setContentView(v);
        dialog.show();
    }

    // ===== Models & Adapter =====
    static class ChatThread {
        String chatId;
        String name;
        String lastMessage;
        long time;
        String avatarUrl; // optional
        Chat chat; // Store chat reference for navigation
        ChatThread() {}
        ChatThread(String n, String lm, long t) { name=n; lastMessage=lm; time=t; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ChatThread that = (ChatThread) obj;
            return chatId != null ? chatId.equals(that.chatId) : that.chatId == null;
        }

        @Override
        public int hashCode() {
            return chatId != null ? chatId.hashCode() : 0;
        }
    }

    static class ChatThreadAdapter extends RecyclerView.Adapter<ChatThreadAdapter.VH> {
        interface ThreadListener { void onLongPress(ChatThread t); }
        private final List<ChatThread> items;
        private final ThreadListener listener;
        ChatThreadAdapter(List<ChatThread> items, ThreadListener l) { this.items = items; this.listener = l; }
        void setItems(List<ChatThread> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_chat_thread, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos), listener); }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView avatar; TextView name; TextView sub; TextView time;
            VH(@NonNull View itemView) { super(itemView);
                avatar = itemView.findViewById(R.id.imgAvatar);
                name = itemView.findViewById(R.id.tvName);
                sub = itemView.findViewById(R.id.tvSub);
                time = itemView.findViewById(R.id.tvTime);
            }
            void bind(ChatThread t, ThreadListener l) {
                name.setText(t.name);
                sub.setText(t.lastMessage);
                time.setText(android.text.format.DateUtils.getRelativeTimeSpanString(t.time));

                // Load avatar
                if (t.avatarUrl != null && !t.avatarUrl.isEmpty()) {
                    if (t.chat != null && "group".equals(t.chat.type)) {
                        // Group avatar - load from storage
                        loadGroupAvatar(t.avatarUrl);
                    } else {
                        // User avatar - load from URL
                        loadUserAvatar(t.avatarUrl);
                    }
                } else {
                    // Default avatar
                    setDefaultAvatar(t);
                }

                itemView.setOnLongClickListener(v -> { if (l != null) l.onLongPress(t); return true; });

                // Add click listener to open chat room
                itemView.setOnClickListener(v -> {
                    if (t.chatId != null && t.chat != null) {
                        Intent intent = new Intent(itemView.getContext(), ChatRoomActivity.class);
                        intent.putExtra("chatId", t.chatId);
                        intent.putExtra("chatTitle", t.name);
                        // Pass additional info for proper message loading
                        if ("group".equals(t.chat.type)) {
                            intent.putExtra("chatType", "group");
                            intent.putExtra("groupId", t.chat.groupId);
                        } else {
                            intent.putExtra("chatType", "private");
                        }
                        itemView.getContext().startActivity(intent);
                    }
                });
            }

            private void loadGroupAvatar(String imageUrl) {
                if (imageUrl == null || imageUrl.isEmpty()) {
                    avatar.setImageResource(R.mipmap.ic_launcher_round);
                    return;
                }

                try {
                    // Check if it's a Firebase Storage URL (starts with https://)
                    if (imageUrl.startsWith("https://")) {
                        com.bumptech.glide.Glide.with(itemView.getContext())
                                .load(imageUrl)
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .circleCrop()
                                .into(avatar);
                    } else {
                        // Fallback to default avatar for invalid URLs
                        avatar.setImageResource(R.mipmap.ic_launcher_round);
                    }
                } catch (Exception e) {
                    avatar.setImageResource(R.mipmap.ic_launcher_round);
                }
            }

            private void loadUserAvatar(String photoUrl) {
                if (photoUrl.contains("googleusercontent.com") && !photoUrl.contains("sz=")) {
                    photoUrl += (photoUrl.contains("?")?"&":"?") + "sz=128";
                }
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(photoUrl)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(avatar);
            }

            private void setDefaultAvatar(ChatThread t) {
                // Create text avatar
                String text = t.name != null && !t.name.isEmpty() ? t.name.substring(0,1).toUpperCase() : "C";
                try {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setAntiAlias(true);
                    paint.setColor(0xFF6200EA);
                    canvas.drawCircle(100, 100, 100, paint);
                    paint.setColor(0xFFFFFFFF);
                    paint.setTextSize(80f);
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    paint.getTextBounds(text, 0, text.length(), bounds);
                    canvas.drawText(text, 100, 100 + bounds.height()/2f, paint);
                    avatar.setImageBitmap(bitmap);
                } catch (Exception ignored) {
                    avatar.setImageResource(R.mipmap.ic_launcher_round);
                }
            }
        }
    }
}

