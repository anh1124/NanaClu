package com.example.nanaclu.data.model;

import java.util.List;
import java.util.ArrayList;

public class Post {
    public String postId;
    public String authorId;
    public String groupId;        // NEW: Liên kết với group
    public String content;
    public List<String> imageUrls; // CHANGED: Từ imageIds thành imageUrls để lưu Firebase Storage URLs
    public long createdAt;
    public Long deletedAt;
    public Long editedAt;
    public int likeCount;
    public int commentCount;
    
    // Video fields
    public boolean hasVideo;
    public String videoUrl;
    public String videoThumbUrl;
    public long videoDurationMs;
    public int videoWidth;
    public int videoHeight;

    // Poll fields
    public String type;                  // null, "text", "media", "poll"
    public String pollTitle;             // Tiêu đề bình chọn
    public String pollDescription;       // Mô tả thêm
    public Boolean pollMultiple;         // Cho phép chọn nhiều
    public Boolean pollAllowAddOption;   // Cho phép thêm lựa chọn mới
    public Boolean pollAnonymous;        // Ẩn danh (UI không hiển thị danh sách người chọn)
    public Boolean pollAllowViewVoters;  // Cho phép xem danh sách người đã chọn
    public Boolean pollHideResult;       // Không công bố kết quả (ẩn số lượng, chỉ hiển thị lựa chọn)
    public Long pollDeadline;            // Thời gian kết thúc (millis), null nếu không giới hạn

    public Post() {
        this.imageUrls = new ArrayList<>(); // Initialize empty list
    }
}
