package com.example.nanaclu.data.model;

/**
 * Model cho thống kê hoạt động nhóm theo tháng/năm
 */
public class GroupStatistics {
    public String periodKey;      // "2025-01" hoặc "2025"
    public int postCount;         // Số bài đăng
    public int eventCount;        // Số sự kiện
    public int newMemberCount;    // Số thành viên mới
    public long timestamp;        // Thời điểm tính toán

    public GroupStatistics() {
        this.postCount = 0;
        this.eventCount = 0;
        this.newMemberCount = 0;
        this.timestamp = System.currentTimeMillis();
    }

    public GroupStatistics(String periodKey) {
        this();
        this.periodKey = periodKey;
    }

    /**
     * Lấy tổng số hoạt động
     */
    public int getTotalActivity() {
        return postCount + eventCount + newMemberCount;
    }

    /**
     * Lấy giá trị theo loại metric
     */
    public int getValueByMetric(String metric) {
        switch (metric) {
            case "posts":
                return postCount;
            case "events":
                return eventCount;
            case "members":
                return newMemberCount;
            default:
                return 0;
        }
    }
}
