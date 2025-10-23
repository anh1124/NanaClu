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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        // Group similar notifications
        List<Notice> groupedNotices = groupNotifications(notices);
        submitList(groupedNotices);
    }
    
    /**
     * Nhóm các thông báo cùng loại, cùng người gửi và cùng thời gian
     */
    private List<Notice> groupNotifications(List<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            return new ArrayList<>();
        }

        List<Notice> grouped = new ArrayList<>();
        Map<String, List<Notice>> groups = new HashMap<>();

        // Nhóm theo type + actorId và thời gian (trong vòng 1 giờ)
        for (Notice notice : notices) {
            String key = notice.getType() + "_" + notice.getActorId();

            // Kiểm tra xem có thể nhóm với thông báo cuối cùng không
            boolean canGroup = false;
            if (!groups.isEmpty()) {
                List<Notice> lastGroup = groups.get(key);
                if (lastGroup != null && !lastGroup.isEmpty()) {
                    Notice lastNotice = lastGroup.get(lastGroup.size() - 1);
                    long timeDiff = Math.abs(notice.getCreatedAt() - lastNotice.getCreatedAt());
                    // Nhóm nếu cùng loại, cùng người gửi và trong vòng 1 giờ
                    if (timeDiff <= 3600000) { // 1 giờ = 3600000ms
                        canGroup = true;
                    }
                }
            }

            if (canGroup && groups.containsKey(key)) {
                groups.get(key).add(notice);
            } else {
                List<Notice> newGroup = new ArrayList<>();
                newGroup.add(notice);
                groups.put(key, newGroup);
            }
        }

        // Tạo thông báo nhóm
        for (Map.Entry<String, List<Notice>> entry : groups.entrySet()) {
            List<Notice> group = entry.getValue();
            if (group.size() == 1) {
                // Chỉ có 1 thông báo, thêm bình thường
                grouped.add(group.get(0));
            } else {
                // Có nhiều thông báo, tạo thông báo nhóm
                Notice groupedNotice = createGroupedNotice(group);
                grouped.add(groupedNotice);
            }
        }

        // Sắp xếp theo thời gian
        grouped.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        return grouped;
    }
    
    /**
     * Tạo thông báo nhóm từ danh sách thông báo cùng loại và cùng người gửi
     */
    private Notice createGroupedNotice(List<Notice> notices) {
        if (notices.isEmpty()) return null;

        Notice firstNotice = notices.get(0);
        String type = firstNotice.getType();
        String actorName = firstNotice.getActorName();
        int count = notices.size();

        // Tạo thông báo nhóm
        Notice groupedNotice = new Notice();
        groupedNotice.setId("grouped_" + firstNotice.getId());
        groupedNotice.setType(type);
        groupedNotice.setCreatedAt(firstNotice.getCreatedAt()); // Dùng thời gian của thông báo đầu tiên
        groupedNotice.setSeen(firstNotice.isSeen());
        groupedNotice.setGroupId(firstNotice.getGroupId());
        groupedNotice.setActorId(firstNotice.getActorId());
        groupedNotice.setActorName(actorName);
        
        // Lưu thông tin để có thể click mở nhanh
        groupedNotice.setObjectId(firstNotice.getObjectId());
        groupedNotice.setObjectType(firstNotice.getObjectType());
        
        // Lưu danh sách ID của các thông báo gốc để có thể mark seen
        StringBuilder originalIds = new StringBuilder();
        for (int i = 0; i < notices.size(); i++) {
            if (i > 0) originalIds.append(",");
            originalIds.append(notices.get(i).getId());
        }
        groupedNotice.setTargetUserId(originalIds.toString()); // Tạm dùng field này để lưu original IDs

        // Tạo title và message dựa trên loại và người gửi
        switch (type) {
            case "message":
                groupedNotice.setTitle("Tin nhắn mới từ " + actorName);
                groupedNotice.setMessage("Có " + count + " tin nhắn mới");
                break;
            case "like":
                groupedNotice.setTitle("Bài viết được thích");
                groupedNotice.setMessage(actorName + " và " + (count - 1) + " người khác đã thích bài viết của bạn");
                break;
            case "comment":
                groupedNotice.setTitle("Bài viết có bình luận");
                groupedNotice.setMessage(actorName + " và " + (count - 1) + " người khác đã bình luận");
                break;
            default:
                groupedNotice.setTitle("Thông báo từ " + actorName);
                groupedNotice.setMessage("Có " + count + " thông báo mới");
                break;
        }

        return groupedNotice;
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
