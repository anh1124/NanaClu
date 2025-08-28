package com.example.nanaclu.data.model;

public class Member {
    public String userId;
    public String role;   // "admin" | "member" | "owner"
    public long joinedAt;
    public String status; // "active" | "pending" | "banned"

    public Member() {}
}


