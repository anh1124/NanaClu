package com.example.nanaclu.ui.group;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Member;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder> {
    
    private List<Member> members;
    private OnMemberClickListener listener;

    public interface OnMemberClickListener {
        void onMemberClick(Member member);
    }

    public GroupMembersAdapter(List<Member> members, OnMemberClickListener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Member member = members.get(position);
        holder.bind(member);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    public void updateMembers(List<Member> newMembers) {
        this.members = newMembers;
        notifyDataSetChanged();
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private ImageView imgAvatar;
        private TextView tvMemberName;
        private TextView tvMemberRole;
        private TextView tvJoinDate;
        private TextView tvRoleBadge;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            tvMemberName = itemView.findViewById(R.id.tvMemberName);
            tvMemberRole = itemView.findViewById(R.id.tvMemberRole);
            tvJoinDate = itemView.findViewById(R.id.tvJoinDate);
            tvRoleBadge = itemView.findViewById(R.id.tvRoleBadge);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMemberClick(members.get(position));
                }
            });
        }

        public void bind(Member member) {
            // Set member name (truncated if too long)
            String displayName = member.userName != null && !member.userName.isEmpty() 
                ? member.userName 
                : member.userId;
            tvMemberName.setText(displayName);
            
            // Set role
            String roleText = getRoleText(member.role);
            tvMemberRole.setText(roleText);
            
            // Set role badge
            setRoleBadge(member.role);
            
            // Set join date
            String joinDate = formatDate(member.joinedAt);
            tvJoinDate.setText("Tham gia: " + joinDate);
            
            // Set avatar
            loadAvatar(member);
        }

        private void loadAvatar(Member member) {
            if (member.avatarImageId != null && !member.avatarImageId.isEmpty()) {
                // TODO: Load avatar from imageId
                // For now, show text avatar
                showTextAvatar(member);
            } else {
                // Show text avatar with first letter of name
                showTextAvatar(member);
            }
        }

        private void showTextAvatar(Member member) {
            String displayName = member.userName != null && !member.userName.isEmpty() 
                ? member.userName 
                : member.userId;
            
            String firstLetter = "";
            if (!displayName.isEmpty()) {
                firstLetter = displayName.substring(0, 1).toUpperCase();
            } else {
                firstLetter = "U";
            }
            
            // Create text avatar
            try {
                android.graphics.drawable.Drawable textDrawable = createTextDrawable(firstLetter);
                imgAvatar.setImageDrawable(textDrawable);
            } catch (Exception e) {
                // Fallback to default avatar
                imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
            }
        }

        private android.graphics.drawable.Drawable createTextDrawable(String text) {
            // Create a simple colored circle with text
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // Draw the circle
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.parseColor("#6200EA"));
            paint.setAntiAlias(true);
            canvas.drawCircle(100, 100, 100, paint);
            
            // Draw the text
            paint.setColor(android.graphics.Color.WHITE);
            paint.setTextSize(80);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setAntiAlias(true);
            
            // Center the text
            android.graphics.Rect bounds = new android.graphics.Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            int x = 100;
            int y = 100 + bounds.height() / 2;
            
            canvas.drawText(text, x, y, paint);
            
            return new android.graphics.drawable.BitmapDrawable(itemView.getResources(), bitmap);
        }

        private String getRoleText(String role) {
            switch (role) {
                case "owner":
                    return "Chủ sở hữu";
                case "admin":
                    return "Quản trị viên";
                case "member":
                default:
                    return "Thành viên";
            }
        }

        private void setRoleBadge(String role) {
            switch (role) {
                case "owner":
                    tvRoleBadge.setText("OWNER");
                    tvRoleBadge.setBackgroundResource(R.drawable.role_badge_owner);
                    break;
                case "admin":
                    tvRoleBadge.setText("ADMIN");
                    tvRoleBadge.setBackgroundResource(R.drawable.role_badge_admin);
                    break;
                case "member":
                default:
                    tvRoleBadge.setText("MEMBER");
                    tvRoleBadge.setBackgroundResource(R.drawable.role_badge_member);
                    break;
            }
        }

        private String formatDate(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}
