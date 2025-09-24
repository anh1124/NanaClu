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
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.UserRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder> {
    
    private List<Member> members;
    private OnMemberClickListener listener;
    private UserRepository userRepository;
    private String currentUserId;

    public interface OnMemberClickListener {
        void onMemberClick(Member member);
    }

    public GroupMembersAdapter(List<Member> members, OnMemberClickListener listener) {
        this(members, listener, null);
    }

    public GroupMembersAdapter(List<Member> members, OnMemberClickListener listener, String currentUserId) {
        this.members = members;
        this.listener = listener;
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());
        this.currentUserId = currentUserId;
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
            // Set default name first
            tvMemberName.setText("Loading...");

            // Load user info asynchronously
            userRepository.getUserById(member.userId, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null && user.displayName != null) {
                        tvMemberName.setText(user.displayName);
                        // Load user avatar
                        loadUserAvatar(user);
                    } else {
                        tvMemberName.setText(member.userId);
                        showTextAvatar(member.userId);
                    }
                }

                @Override
                public void onError(Exception e) {
                    tvMemberName.setText(member.userId);
                    showTextAvatar(member.userId);
                }
            });

            // Hide role text since we have badge
            tvMemberRole.setVisibility(View.GONE);

            // Set role badge
            setRoleBadge(member.role);

            // Set join date
            String joinDate = formatDate(member.joinedAt);
            tvJoinDate.setText("Tham gia: " + joinDate);
        }

        private void loadUserAvatar(User user) {
            if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                // Load user photo
                String photoUrl = user.photoUrl;
                if (photoUrl.contains("googleusercontent.com") && !photoUrl.contains("sz=")) {
                    photoUrl += (photoUrl.contains("?")?"&":"?") + "sz=128";
                }
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(photoUrl)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .circleCrop()
                        .into(imgAvatar);
            } else {
                // Show text avatar
                showTextAvatar(user.displayName != null ? user.displayName : user.userId);
            }
        }

        private void showTextAvatar(String displayName) {
            String firstLetter = "";
            if (displayName != null && !displayName.isEmpty()) {
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
            boolean isSelf = (currentUserId != null && getAdapterPosition() != RecyclerView.NO_POSITION
                    && members.get(getAdapterPosition()).userId.equals(currentUserId));
            String you = isSelf ? itemView.getContext().getString(R.string.label_tag_you) : "";
            switch (role) {
                case "owner":
                    tvRoleBadge.setText("OWNER" + you);
                    tvRoleBadge.setBackgroundResource(R.drawable.role_badge_owner);
                    break;
                case "admin":
                    tvRoleBadge.setText("ADMIN" + you);
                    tvRoleBadge.setBackgroundResource(R.drawable.role_badge_admin);
                    break;
                case "member":
                default:
                    tvRoleBadge.setText("MEMBER" + you);
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
