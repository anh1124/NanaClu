package com.example.nanaclu.ui.chat;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Message;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.FirebaseFirestore;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.model.User;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_MESSAGE_SENT = 1;
    private static final int TYPE_MESSAGE_RECEIVED = 2;
    // Simple in-memory caches to minimize Firestore calls
    private static final ConcurrentHashMap<String, String> NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> PHOTO_CACHE = new ConcurrentHashMap<>();


    private List<Message> messages;
    private OnMessageClickListener listener;
    private String currentUserId;

    public interface OnMessageClickListener {
        void onMessageLongClick(Message message);
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
        if (message.authorId != null && message.authorId.equals(currentUserId)) {
            return TYPE_MESSAGE_SENT;
        } else {
            return TYPE_MESSAGE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MESSAGE_SENT) {
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

        if (holder instanceof SentMessageViewHolder) {
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

        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvEdited = itemView.findViewById(R.id.tvEdited);
            ivImage = itemView.findViewById(R.id.ivImage);
            messageContainer = itemView.findViewById(R.id.messageContainer);
        }

        void bind(Message message, OnMessageClickListener listener) {
            // Handle deleted messages
            if (message.deletedAt != null && message.deletedAt > 0) {
                tvMessage.setText("Tin nhắn đã được thu hồi");
                tvMessage.setTextColor(0xFF999999);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                if (ivImage != null) ivImage.setVisibility(View.GONE);
            } else {
                // Handle different message types
                if ("text".equals(message.type)) {
                    tvMessage.setText(message.content);
                    tvMessage.setTextColor(0xFF000000);
                    tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                    if (ivImage != null) ivImage.setVisibility(View.GONE);
                } else if ("image".equals(message.type)) {
                    tvMessage.setVisibility(View.GONE);
                    if (ivImage != null) {
                        ivImage.setVisibility(View.VISIBLE);
                        Glide.with(itemView.getContext())
                            .load(message.content)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(ivImage);
                    }
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

            // Long click listener
            if (messageContainer != null && listener != null) {
                messageContainer.setOnLongClickListener(v -> {
                    listener.onMessageLongClick(message);
                    return true;
                });
            }
        }
    }

    // ViewHolder for received messages (left side)
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvEdited, tvAuthorName;
        ImageView ivImage, ivAvatarLeft;
        View messageContainer;

        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvEdited = itemView.findViewById(R.id.tvEdited);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            ivImage = itemView.findViewById(R.id.ivImage);
            ivAvatarLeft = itemView.findViewById(R.id.ivAvatarLeft);
            messageContainer = itemView.findViewById(R.id.messageContainer);
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
                // Handle different message types
                if ("text".equals(message.type)) {
                    tvMessage.setText(message.content);
                    tvMessage.setTextColor(0xFF000000);
                    tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                    if (ivImage != null) ivImage.setVisibility(View.GONE);
                } else if ("image".equals(message.type)) {
                    tvMessage.setVisibility(View.GONE);
                    if (ivImage != null) {
                        ivImage.setVisibility(View.VISIBLE);
                        Glide.with(itemView.getContext())
                            .load(message.content)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(ivImage);
                    }
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

            // Long click listener
            if (messageContainer != null && listener != null) {
                messageContainer.setOnLongClickListener(v -> {
                    listener.onMessageLongClick(message);
                    return true;
                });
            }
        }
    }
}
