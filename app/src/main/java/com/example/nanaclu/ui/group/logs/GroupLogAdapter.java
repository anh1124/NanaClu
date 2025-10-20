package com.example.nanaclu.ui.group.logs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.GroupLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupLogAdapter extends RecyclerView.Adapter<GroupLogAdapter.LogViewHolder> {

    private List<GroupLog> logs;
    private OnLogClickListener clickListener;
    private Context context;

    public interface OnLogClickListener {
        void onLogClick(GroupLog log);
    }

    public GroupLogAdapter(List<GroupLog> logs, OnLogClickListener clickListener, Context context) {
        this.logs = logs;
        this.clickListener = clickListener;
        this.context = context;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        GroupLog log = logs.get(position);
        holder.bind(log);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void updateLogs(List<GroupLog> newLogs) {
        this.logs = newLogs;
        notifyDataSetChanged();
    }

    public void addLogs(List<GroupLog> newLogs) {
        int startPosition = logs.size();
        logs.addAll(newLogs);
        notifyItemRangeInserted(startPosition, newLogs.size());
    }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private ImageView imgActionIcon;
        private TextView tvMessage;
        private TextView tvActor;
        private TextView tvTime;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            imgActionIcon = itemView.findViewById(R.id.imgActionIcon);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvActor = itemView.findViewById(R.id.tvActor);
            tvTime = itemView.findViewById(R.id.tvTime);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onLogClick(logs.get(position));
                }
            });
        }

        public void bind(GroupLog log) {
            // Set message
            tvMessage.setText(log.message);

            // Set actor name
            tvActor.setText(log.actorName != null ? log.actorName : "Unknown User");

            // Set time
            tvTime.setText(formatTime(log.createdAt));

            // Set action icon based on type
            setActionIcon(log.type);
        }

        private void setActionIcon(String type) {
            int iconRes = R.drawable.ic_post; // default

            switch (type) {
                case GroupLog.TYPE_POST_CREATED:
                case GroupLog.TYPE_POST_DELETED:
                    iconRes = R.drawable.ic_post;
                    break;
                case GroupLog.TYPE_COMMENT_ADDED:
                case GroupLog.TYPE_COMMENT_DELETED:
                    iconRes = R.drawable.ic_comment;
                    break;
                case GroupLog.TYPE_EVENT_CREATED:
                case GroupLog.TYPE_EVENT_CANCELLED:
                case GroupLog.TYPE_EVENT_RSVP:
                    iconRes = R.drawable.ic_event;
                    break;
                case GroupLog.TYPE_GROUP_UPDATED:
                case GroupLog.TYPE_GROUP_IMAGE_UPDATED:
                case GroupLog.TYPE_GROUP_DELETED:
                    iconRes = R.drawable.ic_group;
                    break;
                case GroupLog.TYPE_MEMBER_APPROVED:
                case GroupLog.TYPE_MEMBER_REJECTED:
                case GroupLog.TYPE_MEMBER_REMOVED:
                case GroupLog.TYPE_MEMBER_BLOCKED:
                case GroupLog.TYPE_MEMBER_UNBLOCKED:
                case GroupLog.TYPE_ROLE_CHANGED:
                    iconRes = R.drawable.ic_person;
                    break;
                case GroupLog.TYPE_OWNERSHIP_TRANSFERRED:
                    iconRes = R.drawable.ic_admin_panel_settings;
                    break;
                case GroupLog.TYPE_POLICY_CHANGED:
                    iconRes = R.drawable.ic_settings;
                    break;
            }

            imgActionIcon.setImageResource(iconRes);
        }

        private String formatTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 60000) { // Less than 1 minute
                return "Vừa xong";
            } else if (diff < 3600000) { // Less than 1 hour
                long minutes = diff / 60000;
                return minutes + " phút trước";
            } else if (diff < 86400000) { // Less than 1 day
                long hours = diff / 3600000;
                return hours + " giờ trước";
            } else if (diff < 604800000) { // Less than 1 week
                long days = diff / 86400000;
                return days + " ngày trước";
            } else {
                // Show date for older logs
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }
    }
}
