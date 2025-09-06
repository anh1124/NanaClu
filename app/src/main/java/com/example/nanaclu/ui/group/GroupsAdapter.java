package com.example.nanaclu.ui.group;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.model.Member;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.ui.group.GroupDetailActivity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {
    private List<Group> groups = new ArrayList<>();
    private OnGroupClickListener onGroupClickListener;
    private String currentUserId;
    private GroupRepository groupRepository;

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    public GroupsAdapter() {
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        this.groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.onGroupClickListener = listener;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private TextView tvGroupName;
        private TextView tvMemberCount;
        private TextView tvUserRole;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onGroupClickListener != null) {
                    onGroupClickListener.onGroupClick(groups.get(position));
                }
            });
        }

        public void bind(Group group) {
            tvGroupName.setText(group.name);
            tvMemberCount.setText(group.memberCount + " thành viên");
            
            // Load user role for this group
            loadUserRole(group);
        }
        
        private void loadUserRole(Group group) {
            if (currentUserId == null) {
                tvUserRole.setVisibility(View.GONE);
                return;
            }
            
            // Check if current user is the owner
            if (currentUserId.equals(group.createdBy)) {
                tvUserRole.setText("Owner");
                tvUserRole.setBackgroundResource(R.drawable.role_badge_owner);
                tvUserRole.setVisibility(View.VISIBLE);
                return;
            }
            
            // Load role from members collection
            groupRepository.getGroupMembers(group.groupId, new GroupRepository.MembersCallback() {
                @Override
                public void onSuccess(List<Member> members) {
                    // Find current user's role in this group
                    for (Member member : members) {
                        if (currentUserId.equals(member.userId)) {
                            String roleText = getRoleText(member.role);
                            int backgroundRes = getRoleBackground(member.role);
                            
                            tvUserRole.setText(roleText);
                            tvUserRole.setBackgroundResource(backgroundRes);
                            tvUserRole.setVisibility(View.VISIBLE);
                            return;
                        }
                    }
                    // User not found in members, hide role
                    tvUserRole.setVisibility(View.GONE);
                }
                
                @Override
                public void onError(Exception e) {
                    // On error, hide role
                    tvUserRole.setVisibility(View.GONE);
                }
            });
        }
        
        private String getRoleText(String role) {
            switch (role) {
                case "owner": return "Owner";
                case "admin": return "Admin";
                case "member": return "Member";
                default: return "Member";
            }
        }
        
        private int getRoleBackground(String role) {
            switch (role) {
                case "owner": return R.drawable.role_badge_owner;
                case "admin": return R.drawable.role_badge_admin;
                case "member": return R.drawable.role_badge_member;
                default: return R.drawable.role_badge_member;
            }
        }
    }
}


