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
 * Adapter cho RecyclerView hiển thị danh sách thành viên nhóm
 * 
 * Chức năng chính:
 * - Hiển thị thông tin thành viên với ảnh đại diện và tên
 * - Hỗ trợ nút hành động tùy chỉnh (thêm bạn, xóa thành viên, v.v.)
 * - Tích hợp với Glide để tải ảnh đại diện
 * - Hỗ trợ xử lý sự kiện click vào thành viên
 */
public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {
    private List<User> members;

    public MemberAdapter(List<User> members) {
        this.members = members;
    }

    public void updateMembers(List<User> newMembers) {
        this.members = newMembers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        User member = members.get(position);
        holder.bind(member);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivAvatar;
        private TextView tvName;
        private TextView tvEmail;
        private TextView tvRole;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
            tvName = itemView.findViewById(R.id.tvMemberName);
            tvEmail = itemView.findViewById(R.id.tvMemberEmail);
            tvRole = itemView.findViewById(R.id.tvMemberRole);
        }

        void bind(User member) {
            // Set name
            if (member.displayName != null && !member.displayName.isEmpty()) {
                tvName.setText(member.displayName);
            } else {
                tvName.setText("Người dùng");
            }

            // Set email
            if (member.email != null && !member.email.isEmpty()) {
                tvEmail.setText(member.email);
                tvEmail.setVisibility(View.VISIBLE);
            } else {
                tvEmail.setVisibility(View.GONE);
            }

            // Hide role for now (User model doesn't have role field)
            tvRole.setVisibility(View.GONE);

            // Load avatar
            if (member.photoUrl != null && !member.photoUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(member.photoUrl)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_person);
            }
        }
    }
}
