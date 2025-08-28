package com.example.nanaclu.data.model;

public class Event {
    public String eventId;
    public String title;
    public String description;
    public long startAt;
    public long endAt;
    public String imageId;
    public String createdBy;
    public String status;    // "scheduled" | "canceled" | "end"
    public int maxParticipants;

    public Event() {}
}


