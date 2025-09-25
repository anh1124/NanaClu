package com.example.nanaclu.utils;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class để dọn dẹp các field không cần thiết trong event_attendees subcollection
 */
public class EventAttendeesCleanup {
    
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String GROUPS = "groups";
    private static final String EVENTS = "events";
    private static final String EVENT_ATTENDEES = "event_attendees";
    
    /**
     * Dọn dẹp tất cả event_attendees documents trong một group
     */
    public static Task<Void> cleanupGroupEventAttendees(String groupId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    
                    // Xử lý từng event
                    Task<Void>[] cleanupTasks = new Task[task.getResult().size()];
                    int index = 0;
                    
                    for (QueryDocumentSnapshot eventDoc : task.getResult()) {
                        cleanupTasks[index] = cleanupEventAttendees(groupId, eventDoc.getId());
                        index++;
                    }
                    
                    return Tasks.whenAll(cleanupTasks);
                });
    }
    
    /**
     * Dọn dẹp event_attendees documents của một event cụ thể
     */
    public static Task<Void> cleanupEventAttendees(String groupId, String eventId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    
                    WriteBatch batch = db.batch();
                    
                    for (QueryDocumentSnapshot attendeeDoc : task.getResult()) {
                        Map<String, Object> data = attendeeDoc.getData();
                        Map<String, Object> cleanedData = new HashMap<>();
                        
                        // Chỉ giữ lại các field cần thiết
                        if (data.containsKey("userId")) {
                            cleanedData.put("userId", data.get("userId"));
                        }
                        if (data.containsKey("userName")) {
                            cleanedData.put("userName", data.get("userName"));
                        }
                        if (data.containsKey("attendanceStatus")) {
                            cleanedData.put("attendanceStatus", data.get("attendanceStatus"));
                        } else if (data.containsKey("status")) {
                            // Migrate từ 'status' sang 'attendanceStatus'
                            cleanedData.put("attendanceStatus", data.get("status"));
                        }
                        if (data.containsKey("responseTime")) {
                            cleanedData.put("responseTime", data.get("responseTime"));
                        } else if (data.containsKey("respondedAt")) {
                            // Migrate từ 'respondedAt' sang 'responseTime'
                            cleanedData.put("responseTime", data.get("respondedAt"));
                        } else if (data.containsKey("rsvpTime")) {
                            // Migrate từ 'rsvpTime' sang 'responseTime'
                            cleanedData.put("responseTime", data.get("rsvpTime"));
                        }
                        if (data.containsKey("note")) {
                            cleanedData.put("note", data.get("note"));
                        }
                        
                        // Cập nhật document với dữ liệu đã dọn dẹp
                        batch.set(attendeeDoc.getReference(), cleanedData);
                    }
                    
                    return batch.commit();
                });
    }
    
    /**
     * Dọn dẹp một attendee document cụ thể
     */
    public static Task<Void> cleanupAttendeeDocument(String groupId, String eventId, String attendeeId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .document(attendeeId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        return Tasks.forResult(null);
                    }
                    
                    Map<String, Object> data = task.getResult().getData();
                    Map<String, Object> cleanedData = new HashMap<>();
                    
                    // Chỉ giữ lại các field cần thiết
                    if (data.containsKey("userId")) {
                        cleanedData.put("userId", data.get("userId"));
                    }
                    if (data.containsKey("userName")) {
                        cleanedData.put("userName", data.get("userName"));
                    }
                    if (data.containsKey("attendanceStatus")) {
                        cleanedData.put("attendanceStatus", data.get("attendanceStatus"));
                    } else if (data.containsKey("status")) {
                        // Migrate từ 'status' sang 'attendanceStatus'
                        cleanedData.put("attendanceStatus", data.get("status"));
                    }
                    if (data.containsKey("responseTime")) {
                        cleanedData.put("responseTime", data.get("responseTime"));
                    } else if (data.containsKey("respondedAt")) {
                        // Migrate từ 'respondedAt' sang 'responseTime'
                        cleanedData.put("responseTime", data.get("respondedAt"));
                    } else if (data.containsKey("rsvpTime")) {
                        // Migrate từ 'rsvpTime' sang 'responseTime'
                        cleanedData.put("responseTime", data.get("rsvpTime"));
                    }
                    if (data.containsKey("note")) {
                        cleanedData.put("note", data.get("note"));
                    }
                    
                    return task.getResult().getReference().set(cleanedData);
                });
    }
}
