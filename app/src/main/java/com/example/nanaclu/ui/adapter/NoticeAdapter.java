package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Notice;
import com.example.nanaclu.utils.ThemeUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoticeAdapter extends ListAdapter<Notice, NoticeAdapter.NoticeViewHolder> {
    private NoticeClickListener clickListener;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());

    public interface NoticeClickListener {
        void onNoticeClick(Notice notice);
    }

    public NoticeAdapter(NoticeClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notice, parent, false);
        return new NoticeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder holder, int position) {
        Notice notice = getItem(position);
        holder.bind(notice);
    }

    public void setNotices(List<Notice> notices) {
        submitList(notices);
    }

    class NoticeViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivIcon;
        private TextView tvTitle;
        private TextView tvMessage;
        private TextView tvTime;
        private View vUnreadIndicator;
        private View cardView;

        public NoticeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            vUnreadIndicator = itemView.findViewById(R.id.vUnreadIndicator);
            cardView = itemView.findViewById(R.id.cardView);
        }

        public void bind(Notice notice) {
            // Set icon based on notice type
            int iconRes = getIconForType(notice.getType());
            ivIcon.setImageResource(iconRes);

            // Set title and message
            tvTitle.setText(notice.getTitle());
            tvMessage.setText(notice.getMessage());

            // Set time
            tvTime.setText(formatTime(notice.getCreatedAt()));

            // Set unread indicator
            if (notice.isSeen()) {
                vUnreadIndicator.setVisibility(View.GONE);
                // Set background to white for read notices
                cardView.setBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.white));
            } else {
                vUnreadIndicator.setVisibility(View.VISIBLE);
                // Set background to very light, pastel theme color for unread notices
                int themeColor = ThemeUtils.getThemeColor(itemView.getContext());
                int lightColor = lightenColor(themeColor, 0.95f);
                cardView.setBackgroundColor(lightColor);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onNoticeClick(notice);
                }
            });
        }

        private int getIconForType(String type) {
            switch (type) {
                case "like":
                    return R.drawable.ic_favorite;
                case "comment":
                    return R.drawable.ic_comment;
                case "message":
                    return R.drawable.ic_message;
                case "post_approved":
                    return R.drawable.ic_check_circle;
                case "event_created":
                    return R.drawable.ic_event;
                case "friend_request":
                case "friend_accepted":
                    return R.drawable.ic_person_add;
                default:
                    return R.drawable.ic_notification;
            }
        }

        private String formatTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 60000) { // Less than 1 minute
                return "Vừa xong";
            } else if (diff < 3600000) { // Less than 1 hour
                return (diff / 60000) + " phút trước";
            } else if (diff < 86400000) { // Less than 1 day
                return (diff / 3600000) + " giờ trước";
            } else if (diff < 604800000) { // Less than 1 week
                return (diff / 86400000) + " ngày trước";
            } else {
                return dateFormat.format(new Date(timestamp));
            }
        }

        private int lightenColor(int color, float factor) {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(color, hsv);
            // Reduce saturation for softer look
            hsv[1] = Math.max(0f, hsv[1] * 0.25f);
            // Increase brightness aggressively
            hsv[2] = Math.min(1.0f, hsv[2] + (1.0f - hsv[2]) * factor);
            return android.graphics.Color.HSVToColor(hsv);
        }
    }

    private static final DiffUtil.ItemCallback<Notice> DIFF_CALLBACK = new DiffUtil.ItemCallback<Notice>() {
        @Override
        public boolean areItemsTheSame(@NonNull Notice oldItem, @NonNull Notice newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Notice oldItem, @NonNull Notice newItem) {
            return oldItem.equals(newItem);
        }
    };
}
