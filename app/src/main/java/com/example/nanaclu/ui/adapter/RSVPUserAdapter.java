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
import com.example.nanaclu.data.model.EventRSVP;

import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị danh sách người dùng tham gia sự kiện
 * 
 * Chức năng chính:
 * - Hiển thị danh sách người dùng theo trạng thái tham gia (đồng ý, có thể, từ chối)
 * - Hiển thị ảnh đại diện và tên người dùng
 * - Hỗ trợ click vào người dùng để xem thông tin chi tiết
 * - Tích hợp với Glide để tải ảnh đại diện
 */
public class RSVPUserAdapter extends RecyclerView.Adapter<RSVPUserAdapter.RSVPUserViewHolder> {

    private List<EventRSVP> rsvps;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public RSVPUserAdapter(List<EventRSVP> rsvps) {
        this.rsvps = rsvps;
    }

    public RSVPUserAdapter(List<EventRSVP> rsvps, OnUserClickListener listener) {
        this.rsvps = rsvps;
        this.listener = listener;
    }
    
    public void updateRSVPs(List<EventRSVP> newRSVPs) {
        this.rsvps = newRSVPs;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public RSVPUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rsvp_user, parent, false);
        return new RSVPUserViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull RSVPUserViewHolder holder, int position) {
        EventRSVP rsvp = rsvps.get(position);
        holder.bind(rsvp, listener);
    }
    
    @Override
    public int getItemCount() {
        return rsvps.size();
    }
    
    static class RSVPUserViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivUserAvatar;
        private TextView tvUserName;
        private TextView tvRSVPTime;
        
        public RSVPUserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvRSVPTime = itemView.findViewById(R.id.tvRSVPTime);
        }
        
        public void bind(EventRSVP rsvp, OnUserClickListener listener) {
            tvUserName.setText(rsvp.userName != null ? rsvp.userName : "Unknown User");

            // Format RSVP time
            long timeToUse = rsvp.responseTime > 0 ? rsvp.responseTime : rsvp.rsvpTime; // Backward compatibility
            if (timeToUse > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault());
                tvRSVPTime.setText(sdf.format(new java.util.Date(timeToUse)));
            } else {
                tvRSVPTime.setText("");
            }

            // Load user avatar
            if (rsvp.userAvatarUrl != null && !rsvp.userAvatarUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(rsvp.userAvatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .into(ivUserAvatar);
            } else {
                ivUserAvatar.setImageResource(R.drawable.ic_person);
            }

            // Set click listeners for avatar and name
            View.OnClickListener clickListener = v -> {
                if (listener != null && rsvp.userId != null) {
                    listener.onUserClick(rsvp.userId);
                }
            };

            ivUserAvatar.setOnClickListener(clickListener);
            tvUserName.setOnClickListener(clickListener);
            itemView.setOnClickListener(clickListener);
        }
    }
}
