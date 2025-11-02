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
 * Adapter cho RecyclerView hiển thị danh sách người dùng khi tìm kiếm
 * 
 * Chức năng chính:
 * - Hiển thị danh sách người dùng dạng danh sách cuộn
 * - Hỗ trợ tải và hiển thị ảnh đại diện
 * - Xử lý sự kiện click vào từng người dùng
 * - Hỗ trợ cập nhật danh sách động
 */
public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {
    // Danh sách người dùng để hiển thị
    private List<User> users;
    
    // Listener để xử lý sự kiện click vào người dùng
    private OnUserClickListener listener;

    /**
     * Interface định nghĩa callback khi người dùng click vào một item
     */
    public interface OnUserClickListener {
        /**
         * Được gọi khi người dùng click vào một user
         * @param userId ID của user được chọn
         */
        void onUserClick(String userId);
    }

    /**
     * Khởi tạo adapter với danh sách người dùng
     * @param users Danh sách người dùng cần hiển thị
     */
    public UserSearchAdapter(List<User> users) {
        this.users = users;
    }

    /**
     * Khởi tạo adapter với danh sách người dùng và listener
     * @param users Danh sách người dùng cần hiển thị
     * @param listener Listener xử lý sự kiện click
     */
    public UserSearchAdapter(List<User> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    /**
     * Cập nhật toàn bộ danh sách người dùng mới
     * @param newUsers Danh sách người dùng mới
     */
    public void setUsers(List<User> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    /**
     * Thêm danh sách người dùng mới vào cuối danh sách hiện có
     * @param newUsers Danh sách người dùng cần thêm
     */
    public void addUsers(List<User> newUsers) {
        int oldSize = this.users.size();
        this.users.addAll(newUsers);
        notifyItemRangeInserted(oldSize, newUsers.size());
    }

    /**
     * Tạo ViewHolder mới khi cần thiết
     */
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tạo view từ layout item_user_search.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }

    /**
     * Gắn dữ liệu vào ViewHolder
     */
    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        // Lấy dữ liệu người dùng tại vị trí position
        User user = users.get(position);
        // Gắn dữ liệu vào view
        holder.bind(user, listener);
    }

    /**
     * Trả về tổng số item trong danh sách
     */
    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * Lớp ViewHolder chứa các view hiển thị thông tin một người dùng
     */
    static class UserViewHolder extends RecyclerView.ViewHolder {
        // Các view trong layout item
        private final ImageView ivUserAvatar;
        private final TextView tvUserName;
        private final TextView tvUserEmail;

        /**
         * Khởi tạo ViewHolder
         * @param itemView View gốc của item
         */
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các view từ layout
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
        }

        /**
         * Gắn dữ liệu người dùng vào các view
         * @param user Đối tượng User chứa thông tin cần hiển thị
         * @param listener Listener xử lý sự kiện click
         */
        public void bind(User user, OnUserClickListener listener) {
            if (user == null) return;

            // Hiển thị tên người dùng, mặc định là "User" nếu null
            String displayName = user.displayName != null ? user.displayName : "User";
            tvUserName.setText(displayName);

            // Hiển thị email, để trống nếu null
            String email = user.email != null ? user.email : "";
            tvUserEmail.setText(email);

            // Xử lý hiển thị ảnh đại diện
            if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                // Tối ưu kích thước ảnh cho Google profile images
                String url = user.photoUrl;
                if (url.contains("googleusercontent.com") && !url.contains("sz=")) {
                    url += (url.contains("?") ? "&" : "?") + "sz=256";
                }
                
                // Sử dụng Glide để tải và hiển thị ảnh
                Glide.with(itemView.getContext())
                        .load(url)
                        .placeholder(R.drawable.ic_account_circle_24) // Ảnh mặc định khi đang tải
                        .error(R.drawable.ic_account_circle_24) // Ảnh mặc định nếu lỗi
                        .circleCrop() // Cắt ảnh thành hình tròn
                        .into(ivUserAvatar);
            } else {
                // Nếu không có ảnh, hiển thị ảnh mặc định
                ivUserAvatar.setImageResource(R.drawable.ic_account_circle_24);
            }

            // Xử lý sự kiện click vào item
            itemView.setOnClickListener(v -> {
                // Gọi callback nếu có listener và userId hợp lệ
                if (listener != null && user.userId != null) {
                    listener.onUserClick(user.userId);
                }
            });
        }
    }
}
