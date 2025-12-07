package com.example.nanaclu.ui.report;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;

/**
 * ViewHolder cho ReportListAdapter
 * Hiển thị thông tin cơ bản của một report trong danh sách
 */
public class ReportViewHolder extends RecyclerView.ViewHolder {

    private TextView tvReason;
    private TextView tvPostId;
    private TextView tvTimestamp;
    private TextView tvStatus;
    private TextView tvReporter;
    private TextView tvPriority;

    public ReportViewHolder(View itemView) {
        super(itemView);

        tvReason = itemView.findViewById(R.id.tvReason);
        tvPostId = itemView.findViewById(R.id.tvPostId);
        tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        tvStatus = itemView.findViewById(R.id.tvStatus);
        tvReporter = itemView.findViewById(R.id.tvReporter);
        tvPriority = itemView.findViewById(R.id.tvPriority);
    }

    /**
     * Bind data to the view
     */
    public void bind(com.example.nanaclu.data.model.ReportModel report, ReportListAdapter.ReportClickListener clickListener) {
        // Hiển thị lý do báo cáo
        String reasonText = getReasonDisplayText(report.reason);
        tvReason.setText(reasonText);

        // Hiển thị Post ID (rút gọn)
        String postIdShort = report.postId != null && report.postId.length() > 8
            ? report.postId.substring(0, 8) + "..."
            : report.postId;
        tvPostId.setText("Post: " + postIdShort);

        // Hiển thị thời gian
        tvTimestamp.setText(formatTimestamp(report.timestamp));

        // Hiển thị trạng thái với màu sắc
        tvStatus.setText(getStatusDisplayText(report.status));
        tvStatus.setTextColor(0xFFFFFFFF); // Màu trắng cho text
        // Set màu cho background
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(getStatusColor(report.status));
        drawable.setCornerRadius(12f);
        tvStatus.setBackground(drawable);

        // Hiển thị người báo cáo (với tên thay vì ID)
        displayReporterName(report);

        // Hiển thị độ ưu tiên (nếu có)
        if (report.priority != null && !report.priority.isEmpty()) {
            tvPriority.setText("Ưu tiên: " + getPriorityDisplayText(report.priority));
            tvPriority.setVisibility(View.VISIBLE);
        } else {
            tvPriority.setVisibility(View.GONE);
        }

        // Set click listener
        itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onReportClick(report);
            }
        });
    }

    private void displayReporterName(com.example.nanaclu.data.model.ReportModel report) {
        if (report.reporterUserId != null) {
            // For now, just show the ID - the adapter handles name fetching
            String reporterShort = report.reporterUserId.length() > 8
                ? report.reporterUserId.substring(0, 8) + "..."
                : report.reporterUserId;
            tvReporter.setText("Bởi: " + reporterShort);
        } else {
            tvReporter.setText("Báo cáo ẩn danh");
        }
    }

    /**
     * Chuyển đổi reason code thành text hiển thị
     */
    private String getReasonDisplayText(String reason) {
        if (reason == null) return "Không xác định";

        switch (reason) {
            case "spam": return "Spam/Quảng cáo";
            case "inappropriate": return "Nội dung không phù hợp";
            case "harassment": return "Quấy rối/Bắt nạt";
            case "violence": return "Bạo lực/Đe dọa";
            case "misinformation": return "Thông tin sai lệch";
            case "copyright": return "Vi phạm bản quyền";
            case "other": return "Khác";
            default: return reason;
        }
    }

    /**
     * Chuyển đổi status code thành text hiển thị
     */
    private String getStatusDisplayText(String status) {
        if (status == null) return "Không xác định";

        switch (status) {
            case "pending": return "Chờ xử lý";
            case "reviewed": return "Đã xem xét";
            case "dismissed": return "Đã bỏ qua";
            case "action_taken": return "Đã xử lý";
            default: return status;
        }
    }

    /**
     * Lấy màu sắc cho status
     */
    private int getStatusColor(String status) {
        if (status == null) return 0xFF666666;

        switch (status) {
            case "pending": return 0xFFFF9800; // Orange
            case "reviewed": return 0xFF2196F3; // Blue
            case "dismissed": return 0xFF9E9E9E; // Gray
            case "action_taken": return 0xFF4CAF50; // Green
            default: return 0xFF666666; // Dark gray
        }
    }

    /**
     * Chuyển đổi priority code thành text hiển thị
     */
    private String getPriorityDisplayText(String priority) {
        if (priority == null) return "";

        switch (priority) {
            case "low": return "Thấp";
            case "normal": return "Bình thường";
            case "high": return "Cao";
            default: return priority;
        }
    }

    /**
     * Format timestamp thành text dễ đọc
     */
    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60000) { // < 1 minute
            return "Vừa xong";
        } else if (diff < 3600000) { // < 1 hour
            return (diff / 60000) + " phút trước";
        } else if (diff < 86400000) { // < 1 day
            return (diff / 3600000) + " giờ trước";
        } else if (diff < 604800000) { // < 1 week
            return (diff / 86400000) + " ngày trước";
        } else {
            return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp));
        }
    }
}
