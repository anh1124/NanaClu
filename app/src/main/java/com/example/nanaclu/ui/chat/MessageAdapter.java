package com.example.nanaclu.ui.chat;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.FileAttachment;
import com.example.nanaclu.data.model.Message;
import com.example.nanaclu.ui.adapter.FileAttachmentAdapter;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.FirebaseFirestore;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.model.User;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_MESSAGE_SENT = 1;
    private static final int TYPE_MESSAGE_RECEIVED = 2;
    private static final int TYPE_FILE_ATTACHMENT = 3;
    // Simple in-memory caches to minimize Firestore calls
    private static final ConcurrentHashMap<String, String> NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> PHOTO_CACHE = new ConcurrentHashMap<>();


    private List<Message> messages;
    private OnMessageClickListener listener;
    private String currentUserId;

    public interface OnMessageClickListener {
        void onMessageLongClick(Message message);
        void onDeleteMessage(Message message);
        void onImageClick(Message message);
        void onFileClick(FileAttachment file);
        void onFileDownload(FileAttachment file);
    }

    public MessageAdapter(List<Message> messages, OnMessageClickListener listener) {
        this.messages = messages;
        this.listener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        setHasStableIds(true);
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }
    @Override
    public long getItemId(int position) {
        Message m = messages.get(position);
        return (m != null && m.messageId != null) ? (m.messageId.hashCode() & 0xffffffffL) : position;
    }


    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if ("file".equals(message.type)) {
            return TYPE_FILE_ATTACHMENT;
        } else if (message.authorId != null && message.authorId.equals(currentUserId)) {
            return TYPE_MESSAGE_SENT;
        } else {
            return TYPE_MESSAGE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FILE_ATTACHMENT) {
            View view = inflater.inflate(R.layout.item_file_attachment, parent, false);
            return new FileAttachmentViewHolder(view);
        } else if (viewType == TYPE_MESSAGE_SENT) {
            View view = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder instanceof FileAttachmentViewHolder) {
            ((FileAttachmentViewHolder) holder).bind(message, listener);
        } else if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, listener);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, listener);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ViewHolder for sent messages (right side)
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvEdited;
        ImageView ivImage;
        View messageContainer;
        LinearLayout fileAttachmentsContainer;
        RecyclerView rvFileAttachments;

        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvEdited = itemView.findViewById(R.id.tvEdited);
            ivImage = itemView.findViewById(R.id.ivImage);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            fileAttachmentsContainer = itemView.findViewById(R.id.fileAttachmentsContainer);
            rvFileAttachments = itemView.findViewById(R.id.rvFileAttachments);
        }

        void bind(Message message, OnMessageClickListener listener) {
            // Log debug để kiểm tra dữ liệu bind
            android.util.Log.d("MessageAdapter_BIND", "ID: " + message.messageId + ", type: " + message.type + ", content: " + message.content + ", deletedAt: " + message.deletedAt + ", fileAttachments: " + (message.fileAttachments != null ? message.fileAttachments.size() : 0));

            // Reset visibility
            tvMessage.setVisibility(View.VISIBLE);
            if (ivImage != null) ivImage.setVisibility(View.GONE);
            if (fileAttachmentsContainer != null) fileAttachmentsContainer.setVisibility(View.GONE);

            // Nếu là tin nhắn đã thu hồi (dù type gì)
            if (message.deletedAt != null && message.deletedAt > 0 || "Tin nhắn đã được thu hồi".equals(message.content)) {
                tvMessage.setText("Tin nhắn đã được thu hồi");
                tvMessage.setTextColor(0xFF999999);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                return;
            }

            // Xử lý theo type
            if ("text".equals(message.type)) {
                tvMessage.setText(message.content);
                tvMessage.setTextColor(0xFF000000);
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else if ("image".equals(message.type)) {
                tvMessage.setVisibility(View.GONE);
                if (ivImage != null) {
                    ivImage.setVisibility(View.VISIBLE);
                    Glide.with(itemView.getContext())
                        .load(message.content)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .into(ivImage);
                    ivImage.setOnClickListener(v -> {
                        if (listener != null) listener.onImageClick(message);
                    });
                }
            } else if ("file".equals(message.type) || "mixed".equals(message.type)) {
                // Hiển thị file nếu có fileAttachments
                if (message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                    if (fileAttachmentsContainer != null) {
                        fileAttachmentsContainer.setVisibility(View.VISIBLE);
                        setupFileAttachments(message.fileAttachments, listener);
                    }
                }
                // Nếu là mixed và có content, hiển thị text
                if ("mixed".equals(message.type) && message.content != null && !message.content.trim().isEmpty()) {
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(message.content);
                    tvMessage.setTextColor(0xFF000000);
                    tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                } else {
                    tvMessage.setVisibility(View.GONE);
                }
            }

            // Show time
            if (tvTime != null) {
                tvTime.setText(DateUtils.getRelativeTimeSpanString(message.createdAt));
            }

            // Show edited indicator
            if (tvEdited != null) {
                if (message.editedAt != null && message.editedAt > 0) {
                    tvEdited.setVisibility(View.VISIBLE);
                } else {
                    tvEdited.setVisibility(View.GONE);
                }
            }

            // Long click listener for own messages
            if (messageContainer != null && listener != null) {
                messageContainer.setOnLongClickListener(v -> {
                    // Check if this is user's own message
                    String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                    if (currentUserId != null && currentUserId.equals(message.authorId)) {
                        listener.onDeleteMessage(message);
                        return true;
                    }
                    return false;
                });
            }
        }

        private void setupFileAttachments(List<FileAttachment> attachments, OnMessageClickListener listener) {
            if (rvFileAttachments != null && attachments != null && !attachments.isEmpty()) {
                FileAttachmentAdapter adapter = new FileAttachmentAdapter(attachments,
                    new FileAttachmentAdapter.OnFileActionListener() {
                        @Override
                        public void onFileClick(FileAttachment file) {
                            if (listener != null) listener.onFileClick(file);
                        }
                        @Override
                        public void onDownloadClick(FileAttachment file) {
                            if (listener != null) listener.onFileDownload(file);
                        }
                        @Override
                        public void onDeleteClick(FileAttachment file) {}
                    }, itemView.getContext(), false);
                LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext());
                layoutManager.setAutoMeasureEnabled(true); // QUAN TRỌNG
                rvFileAttachments.setLayoutManager(layoutManager);
                rvFileAttachments.setHasFixedSize(false); // QUAN TRỌNG
                rvFileAttachments.setAdapter(adapter);
            }
        }
    }

    // ViewHolder for received messages (left side)
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvEdited, tvAuthorName;
        ImageView ivImage, ivAvatarLeft;
        View messageContainer;
        LinearLayout fileAttachmentsContainer;
        RecyclerView rvFileAttachments;

        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvEdited = itemView.findViewById(R.id.tvEdited);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            ivImage = itemView.findViewById(R.id.ivImage);
            ivAvatarLeft = itemView.findViewById(R.id.ivAvatarLeft);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            fileAttachmentsContainer = itemView.findViewById(R.id.fileAttachmentsContainer);
            rvFileAttachments = itemView.findViewById(R.id.rvFileAttachments);
        }

        private void loadAvatar(Message message) {
            if (ivAvatarLeft == null) return;
            String url = (message.authorId != null) ? PHOTO_CACHE.get(message.authorId) : null;
            if (url != null && !url.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(url)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(ivAvatarLeft);
            } else if (message.authorId != null) {
                new UserRepository(FirebaseFirestore.getInstance()).getUserById(message.authorId, new UserRepository.UserCallback() {
                    @Override public void onSuccess(User user) {
                        if (user != null && user.photoUrl != null) {
                            PHOTO_CACHE.put(message.authorId, user.photoUrl);
                            Glide.with(itemView.getContext())
                                    .load(user.photoUrl)
                                    .placeholder(R.mipmap.ic_launcher_round)
                                    .error(R.mipmap.ic_launcher_round)
                                    .circleCrop()
                                    .into(ivAvatarLeft);
                        }
                    }
                    @Override public void onError(Exception e) { /* ignore */ }
                });
            }
        }

        void bind(Message message, OnMessageClickListener listener) {
            // Author name with cache fallback
            if (tvAuthorName != null) {
                String name = (message.authorName != null && !message.authorName.isEmpty()) ? message.authorName : NAME_CACHE.get(message.authorId);
                if (name != null && !name.isEmpty()) {
                    tvAuthorName.setText(name);
                    tvAuthorName.setVisibility(View.VISIBLE);
                } else {
                    tvAuthorName.setText("Unknown");
                    tvAuthorName.setVisibility(View.VISIBLE);
                    if (message.authorId != null) {
                        new UserRepository(FirebaseFirestore.getInstance()).getUserById(message.authorId, new UserRepository.UserCallback() {
                            @Override public void onSuccess(User user) {
                                if (user != null) {
                                    NAME_CACHE.put(message.authorId, user.displayName);
                                    if (user.displayName != null) tvAuthorName.setText(user.displayName);
                                    // also cache photo
                                    if (user.photoUrl != null) PHOTO_CACHE.put(message.authorId, user.photoUrl);
                                    // load avatar if available
                                    loadAvatar(message);
                                }
                            }
                            @Override public void onError(Exception e) { /* ignore */ }
                        });
                    }
                }
            }

            // Load avatar (left) optimized with cache
            loadAvatar(message);

            // Handle deleted messages
            if (message.deletedAt != null && message.deletedAt > 0) {
                tvMessage.setText("Tin nhắn đã được thu hồi");
                tvMessage.setTextColor(0xFF999999);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                if (ivImage != null) ivImage.setVisibility(View.GONE);
            } else {
                // Log debug để kiểm tra dữ liệu bind
                android.util.Log.d("MessageAdapter_BIND", "ID: " + message.messageId + ", type: " + message.type + ", content: " + message.content + ", deletedAt: " + message.deletedAt + ", fileAttachments: " + (message.fileAttachments != null ? message.fileAttachments.size() : 0));

                // Reset visibility
                tvMessage.setVisibility(View.VISIBLE);
                if (ivImage != null) ivImage.setVisibility(View.GONE);
                if (fileAttachmentsContainer != null) fileAttachmentsContainer.setVisibility(View.GONE);

                // Nếu là tin nhắn đã thu hồi (dù type gì)
                if (message.deletedAt != null && message.deletedAt > 0 || "Tin nhắn đã được thu hồi".equals(message.content)) {
                    tvMessage.setText("Tin nhắn đã được thu hồi");
                    tvMessage.setTextColor(0xFF999999);
                    tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                    return;
                }

                // Xử lý theo type
                if ("text".equals(message.type)) {
                    tvMessage.setText(message.content);
                    tvMessage.setTextColor(0xFF000000);
                    tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                } else if ("image".equals(message.type)) {
                    tvMessage.setVisibility(View.GONE);
                    if (ivImage != null) {
                        ivImage.setVisibility(View.VISIBLE);
                        Glide.with(itemView.getContext())
                            .load(message.content)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(ivImage);

                        // Add click listener for image
                        ivImage.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onImageClick(message);
                            }
                        });
                    }
                } else if ("file".equals(message.type) || "mixed".equals(message.type)) {
                    // Hiển thị file attachments nếu có
                    if (message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                        if (fileAttachmentsContainer != null) {
                            fileAttachmentsContainer.setVisibility(View.VISIBLE);
                            setupFileAttachments(message.fileAttachments, listener);
                        }
                    } else {
                        if (fileAttachmentsContainer != null) fileAttachmentsContainer.setVisibility(View.GONE);
                    }

                    // Hiển thị text content nếu là mixed type và có nội dung
                    if ("mixed".equals(message.type) && message.content != null && !message.content.trim().isEmpty()) {
                        tvMessage.setVisibility(View.VISIBLE);
                        tvMessage.setText(message.content);
                        tvMessage.setTextColor(0xFF000000);
                        tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                    } else {
                        tvMessage.setVisibility(View.GONE);
                    }

                    // Ẩn image view cho file messages
                    if (ivImage != null) ivImage.setVisibility(View.GONE);
                }
            }

            // Show time
            if (tvTime != null) {
                tvTime.setText(DateUtils.getRelativeTimeSpanString(message.createdAt));
            }

            // Show edited indicator
            if (tvEdited != null) {
                if (message.editedAt != null && message.editedAt > 0) {
                    tvEdited.setVisibility(View.VISIBLE);
                } else {
                    tvEdited.setVisibility(View.GONE);
                }
            }

            // Long click listener for own messages
            if (messageContainer != null && listener != null) {
                messageContainer.setOnLongClickListener(v -> {
                    // Check if this is user's own message
                    String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                    if (currentUserId != null && currentUserId.equals(message.authorId)) {
                        listener.onDeleteMessage(message);
                        return true;
                    }
                    return false;
                });
            }
        }

        private void setupFileAttachments(List<FileAttachment> attachments, OnMessageClickListener listener) {
            if (rvFileAttachments != null && attachments != null && !attachments.isEmpty()) {
                FileAttachmentAdapter adapter = new FileAttachmentAdapter(attachments,
                    new FileAttachmentAdapter.OnFileActionListener() {
                        @Override
                        public void onFileClick(FileAttachment file) {
                            if (listener != null) listener.onFileClick(file);
                        }
                        @Override
                        public void onDownloadClick(FileAttachment file) {
                            if (listener != null) listener.onFileDownload(file);
                        }
                        @Override
                        public void onDeleteClick(FileAttachment file) {}
                    }, itemView.getContext(), false);
                LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext());
                layoutManager.setAutoMeasureEnabled(true); // QUAN TRỌNG
                rvFileAttachments.setLayoutManager(layoutManager);
                rvFileAttachments.setHasFixedSize(false); // QUAN TRỌNG
                rvFileAttachments.setAdapter(adapter);
            }
        }
    }

    static class FileAttachmentViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvFileName, tvFileSize, tvFileStatus;
        ImageView ivFileIcon;
        ProgressBar pbProgress;
        ImageButton btnFileAction;
        public FileAttachmentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            tvFileStatus = itemView.findViewById(R.id.tvFileStatus);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            pbProgress = itemView.findViewById(R.id.pbProgress);
            btnFileAction = itemView.findViewById(R.id.btnFileAction);
        }
        public void bind(Message message, OnMessageClickListener listener) {
            // Hiển thị tên người gửi
            tvSenderName.setText(message.authorName != null ? message.authorName : "Unknown");
            // Hiển thị file đầu tiên (nếu có)
            if (message.fileAttachments != null && !message.fileAttachments.isEmpty()) {
                FileAttachment file = message.fileAttachments.get(0);
                tvFileName.setText(file.fileName);
                tvFileSize.setText(file.getFormattedFileSize());
                // Set icon theo fileType nếu muốn
                // ...
                // Set status/progress nếu muốn
                // ...
                // Set click listeners nếu muốn
                btnFileAction.setOnClickListener(v -> {
                    if (listener != null) listener.onFileDownload(file);
                });
            } else {
                tvFileName.setText("<Không có file>");
                tvFileSize.setText("");
            }
        }
    }
}
