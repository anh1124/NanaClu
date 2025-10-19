package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị friend requests với action buttons
 */
public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendRequestViewHolder> {

    public enum RequestType {
        INCOMING,  // Người khác gửi cho mình
        OUTGOING   // Mình gửi cho người khác
    }

    private List<User> users;
    private List<RequestType> requestTypes;
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAcceptFriendRequest(String userId);
        void onDeclineFriendRequest(String userId);
        void onCancelFriendRequest(String userId);
        void onUserClick(String userId);
    }

    public FriendAdapter(List<User> users, List<RequestType> requestTypes) {
        this.users = users;
        this.requestTypes = requestTypes;
    }

    public FriendAdapter(List<User> users, List<RequestType> requestTypes, OnFriendActionListener listener) {
        this.users = users;
        this.requestTypes = requestTypes;
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers, List<RequestType> newRequestTypes) {
        this.users = newUsers;
        this.requestTypes = newRequestTypes;
        notifyDataSetChanged();
    }

    public void removeUser(String userId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).userId != null && users.get(i).userId.equals(userId)) {
                users.remove(i);
                if (i < requestTypes.size()) {
                    requestTypes.remove(i);
                }
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public FriendRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request, parent, false);
        return new FriendRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendRequestViewHolder holder, int position) {
        User user = users.get(position);
        RequestType requestType = position < requestTypes.size() ? requestTypes.get(position) : RequestType.INCOMING;
        holder.bind(user, requestType, listener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class FriendRequestViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivUserAvatar;
        private TextView tvUserName;
        private TextView tvUserEmail;
        private TextView tvRequestType;
        private MaterialButton btnAccept;
        private MaterialButton btnDecline;
        private MaterialButton btnCancel;

        public FriendRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
            tvRequestType = itemView.findViewById(R.id.tvRequestType);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnDecline = itemView.findViewById(R.id.btnDecline);
            btnCancel = itemView.findViewById(R.id.btnCancel);
        }

        public void bind(User user, RequestType requestType, OnFriendActionListener listener) {
            if (user == null) return;

            // Hiển thị thông tin user
            String displayName = user.displayName != null ? user.displayName : "User";
            tvUserName.setText(displayName);

            String email = user.email != null ? user.email : "";
            tvUserEmail.setText(email);

            // Hiển thị avatar
            if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                String url = user.photoUrl;
                if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                    url += (url.contains("?") ? "&" : "?") + "sz=256";
                }
                Glide.with(itemView.getContext())
                        .load(url)
                        .placeholder(R.drawable.ic_account_circle_24)
                        .error(R.drawable.ic_account_circle_24)
                        .circleCrop()
                        .into(ivUserAvatar);
            } else {
                ivUserAvatar.setImageResource(R.drawable.ic_account_circle_24);
            }

            // Setup buttons based on request type
            btnAccept.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);

            switch (requestType) {
                case INCOMING:
                    tvRequestType.setText("Đã gửi lời mời kết bạn");
                    btnAccept.setVisibility(View.VISIBLE);
                    btnDecline.setVisibility(View.VISIBLE);
                    btnAccept.setOnClickListener(v -> {
                        if (listener != null && user.userId != null) {
                            listener.onAcceptFriendRequest(user.userId);
                        }
                    });
                    btnDecline.setOnClickListener(v -> {
                        if (listener != null && user.userId != null) {
                            listener.onDeclineFriendRequest(user.userId);
                        }
                    });
                    break;
                case OUTGOING:
                    tvRequestType.setText("Đang chờ phản hồi");
                    btnCancel.setVisibility(View.VISIBLE);
                    btnCancel.setOnClickListener(v -> {
                        if (listener != null && user.userId != null) {
                            listener.onCancelFriendRequest(user.userId);
                        }
                    });
                    break;
            }

            // Handle click on user info
            itemView.setOnClickListener(v -> {
                if (listener != null && user.userId != null) {
                    listener.onUserClick(user.userId);
                }
            });
        }
    }
}
