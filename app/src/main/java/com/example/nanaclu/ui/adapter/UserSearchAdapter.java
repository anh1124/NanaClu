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

import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị danh sách users trong search
 */
public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    private List<User> users;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public UserSearchAdapter(List<User> users) {
        this.users = users;
    }

    public UserSearchAdapter(List<User> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    public void addUsers(List<User> newUsers) {
        int oldSize = this.users.size();
        this.users.addAll(newUsers);
        notifyItemRangeInserted(oldSize, newUsers.size());
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user, listener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivUserAvatar;
        private TextView tvUserName;
        private TextView tvUserEmail;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
        }

        public void bind(User user, OnUserClickListener listener) {
            if (user == null) return;

            // Hiển thị tên
            String displayName = user.displayName != null ? user.displayName : "User";
            tvUserName.setText(displayName);

            // Hiển thị email
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

            // Handle click
            itemView.setOnClickListener(v -> {
                if (listener != null && user.userId != null) {
                    listener.onUserClick(user.userId);
                }
            });
        }
    }
}
