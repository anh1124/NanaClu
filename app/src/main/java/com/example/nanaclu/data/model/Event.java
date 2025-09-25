package com.example.nanaclu.data.model;

import com.google.firebase.Timestamp;
import java.util.List;

public class Event {
    public String eventId;
    public String groupId;
    public String title; // Tên sự kiện
    public String description; // Mô tả chi tiết
    public String creatorId; // ID người tạo
    public String creatorName; // Tên người tạo (cache)

    // Thời gian
    public long startTime; // Timestamp bắt đầu
    public long endTime; // Timestamp kết thúc
    public long createdAt; // Timestamp tạo event

    // Địa điểm
    public String location; // Địa điểm đơn giản
    public String locationType; // "link" | "location" | "none"
    public String locationData; // URL hoặc địa chỉ
    public Double latitude; // Tọa độ (optional)
    public Double longitude; // Tọa độ (optional)

    // Hình ảnh
    public String imageUrl; // URL hình minh họa (optional)

    // Trạng thái
    public String status; // "active" | "cancelled" | "completed"

    // Thống kê RSVP
    public int goingCount; // Số người tham gia
    public int notGoingCount; // Số người không tham gia
    public int maybeCount; // Số người quan tâm

    // Reminder settings
    public List<Integer> reminderMinutes; // [30, 60, 1440] = 30min, 1h, 1day trước

    public Event() {
        this.status = "active";
        this.goingCount = 0;
        this.notGoingCount = 0;
        this.maybeCount = 0;
    }

    // Custom setters for Firestore Timestamp handling
    public void setStartTime(Object startTime) {
        if (startTime instanceof Timestamp) {
            this.startTime = ((Timestamp) startTime).toDate().getTime();
        } else if (startTime instanceof Long) {
            this.startTime = (Long) startTime;
        } else if (startTime instanceof Number) {
            this.startTime = ((Number) startTime).longValue();
        }
    }

    public void setEndTime(Object endTime) {
        if (endTime instanceof Timestamp) {
            this.endTime = ((Timestamp) endTime).toDate().getTime();
        } else if (endTime instanceof Long) {
            this.endTime = (Long) endTime;
        } else if (endTime instanceof Number) {
            this.endTime = ((Number) endTime).longValue();
        }
    }

    public void setCreatedAt(Object createdAt) {
        if (createdAt instanceof Timestamp) {
            this.createdAt = ((Timestamp) createdAt).toDate().getTime();
        } else if (createdAt instanceof Long) {
            this.createdAt = (Long) createdAt;
        } else if (createdAt instanceof Number) {
            this.createdAt = ((Number) createdAt).longValue();
        }
    }
}


