package com.example.nanaclu.data.model;

import java.util.Map;

/**
 * Model cho báo cáo post trong group
 * Lưu trữ thông tin về việc báo cáo nội dung không phù hợp
 */
public class ReportModel {
    public String reportId;       // document id (cho Firestore tự sinh)
    public String postId;         // id của post bị report
    public String reportedUserId; // chủ bài viết (owner)
    public String reporterUserId; // người report (null nếu anonymous)
    public String reason;         // enum string: spam, harassment, other...
    public String reasonDetail;   // optional, text do user nhập
    public long timestamp;        // epoch millis
    public String status;         // pending | reviewed | dismissed | action_taken
    public String moderatorId;    // id moderator xử lý (optional)
    public String moderatorNote;  // ghi chú moderator (optional)
    public String action;         // removed/warn/ban (optional)
    public String priority;       // low|normal|high (optional)

    public ReportModel() {}

    /**
     * Chuyển đổi ReportModel thành Map để lưu vào Firestore
     * Chỉ thêm các field không null để tránh lưu dữ liệu rỗng
     */
    public Map<String,Object> toMap() {
        Map<String,Object> m = new java.util.HashMap<>();
        if (postId != null) m.put("postId", postId);
        if (reportedUserId != null) m.put("reportedUserId", reportedUserId);
        if (reporterUserId != null) m.put("reporterUserId", reporterUserId);
        if (reason != null) m.put("reason", reason);
        if (reasonDetail != null) m.put("reasonDetail", reasonDetail);
        m.put("timestamp", timestamp);
        m.put("status", status == null ? "pending" : status);
        if (moderatorId != null) m.put("moderatorId", moderatorId);
        if (moderatorNote != null) m.put("moderatorNote", moderatorNote);
        if (action != null) m.put("action", action);
        if (priority != null) m.put("priority", priority);
        return m;
    }

    /**
     * Tạo ReportModel từ Map data từ Firestore
     * Xử lý các kiểu dữ liệu khác nhau từ Firestore
     */
    public static ReportModel fromMap(String id, Map<String,Object> map) {
        if (map == null) return null;
        ReportModel r = new ReportModel();
        r.reportId = id;
        r.postId = (String) map.get("postId");
        r.reportedUserId = (String) map.get("reportedUserId");
        r.reporterUserId = (String) map.get("reporterUserId");
        r.reason = (String) map.get("reason");
        r.reasonDetail = (String) map.get("reasonDetail");
        Object ts = map.get("timestamp");
        if (ts instanceof Number) r.timestamp = ((Number) ts).longValue();
        r.status = (String) map.get("status");
        r.moderatorId = (String) map.get("moderatorId");
        r.moderatorNote = (String) map.get("moderatorNote");
        r.action = (String) map.get("action");
        r.priority = (String) map.get("priority");
        return r;
    }
}
