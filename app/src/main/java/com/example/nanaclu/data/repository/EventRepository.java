package com.example.nanaclu.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.nanaclu.data.model.Event;
import com.example.nanaclu.data.model.EventRSVP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventRepository {
    private static final String GROUPS = "groups";
    private static final String EVENTS = "events";
    private static final String EVENT_ATTENDEES = "event_attendees";

    private final FirebaseFirestore db;

    // Callback interfaces
    public interface OnSuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface OnErrorCallback {
        void onError(Exception e);
    }

    // RSVP counts class
    public static class RSVPCounts {
        public int goingCount;
        public int maybeCount;
        public int notGoingCount;

        public RSVPCounts(int goingCount, int maybeCount, int notGoingCount) {
            this.goingCount = goingCount;
            this.maybeCount = maybeCount;
            this.notGoingCount = notGoingCount;
        }
    }
    
    public EventRepository(FirebaseFirestore db) {
        this.db = db;
    }
    
    /**
     * Tạo event mới
     */
    public Task<String> createEvent(Event event) {
        if (event.groupId == null) return Tasks.forException(new IllegalArgumentException("groupId is required"));

        DocumentReference eventRef = db.collection(GROUPS)
                .document(event.groupId)
                .collection(EVENTS)
                .document();

        event.eventId = eventRef.getId();
        event.createdAt = System.currentTimeMillis();

        // Ensure required fields are set
        if (event.status == null || event.status.isEmpty()) {
            event.status = "active";
        }

        // Set creator info
        String currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            event.creatorId = currentUserId;
        }

        return eventRef.set(event).continueWith(task -> {
            if (task.isSuccessful()) {
                // Log event creation
                LogRepository logRepo = new LogRepository(db);
                logRepo.logGroupAction(event.groupId, "event_created", "event", event.eventId, event.title, null);
                return event.eventId;
            } else {
                Exception e = task.getException();
                com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("EventRepository", e);
                throw e;
            }
        });
    }
    
    /**
     * Lấy danh sách events của group
     */
    public Task<List<Event>> getGroupEvents(String groupId) {
        android.util.Log.d("EventRepository", "Getting events for groupId: " + groupId);
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .whereEqualTo("status", "active")
                .get()
                .continueWithTask(task -> {
                    List<Event> events = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        android.util.Log.d("EventRepository", "Query successful, found " + task.getResult().size() + " documents");
                        
                        List<Task<Void>> loadTasks = new ArrayList<>();
                        
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            android.util.Log.d("EventRepository", "Document: " + doc.getId() + ", data: " + doc.getData());
                            Event event = doc.toObject(Event.class);
                            if (event != null) {
                                event.eventId = doc.getId();
                                events.add(event);
                                
                                // Load creator name if creatorId exists but creatorName is null
                                if (event.creatorId != null && !event.creatorId.isEmpty() && 
                                    (event.creatorName == null || event.creatorName.isEmpty())) {
                                    
                                    Task<Void> loadCreatorTask = db.collection("users")
                                            .document(event.creatorId)
                                            .get()
                                            .continueWith(userTask -> {
                                                if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                                                    String creatorName = userTask.getResult().getString("name");
                                                    if (creatorName == null || creatorName.isEmpty()) {
                                                        creatorName = userTask.getResult().getString("displayName");
                                                    }
                                                    event.creatorName = creatorName;
                                                    android.util.Log.d("EventRepository", "Loaded creator name: " + creatorName + " for event: " + event.title);
                                                }
                                                return null;
                                            });
                                    loadTasks.add(loadCreatorTask);
                                }
                            }
                        }
                        
                        // Wait for all creator names to load
                        return Tasks.whenAll(loadTasks).continueWith(allTasks -> events);
                    } else {
                        android.util.Log.e("EventRepository", "Query failed", task.getException());
                        com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("EventRepository", task.getException());
                        return Tasks.forResult(events);
                    }
                });
    }
    
    /**
     * Lấy chi tiết event
     */
    public Task<Event> getEvent(String groupId, String eventId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Event event = task.getResult().toObject(Event.class);
                        if (event != null) {
                            event.eventId = task.getResult().getId();
                        }
                        return event;
                    }
                    return null;
                });
    }
    
    /**
     * Cập nhật event
     */
    public Task<Void> updateEvent(String groupId, String eventId, Map<String, Object> updates) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .update(updates);
    }
    
    /**
     * Xóa event (soft delete)
     */
    public Task<Void> deleteEvent(String groupId, String eventId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "cancelled");
        return updateEvent(groupId, eventId, updates);
    }
    
    /**
     * RSVP cho event
     */
    public Task<Void> rsvpEvent(String groupId, String eventId, EventRSVP rsvp) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .document(rsvp.userId)
                .set(rsvp)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        // Update event counts
                        return updateEventCounts(groupId, eventId);
                    }
                    return task;
                });
    }
    
    /**
     * Lấy danh sách RSVP của event
     */
    public Task<List<EventRSVP>> getEventRSVPs(String groupId, String eventId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .orderBy("respondedAt", Query.Direction.DESCENDING)
                .get()
                .continueWith(task -> {
                    List<EventRSVP> rsvps = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            EventRSVP rsvp = doc.toObject(EventRSVP.class);
                            if (rsvp != null) {
                                rsvps.add(rsvp);
                            }
                        }
                    }
                    return rsvps;
                });
    }
    
    /**
     * Lấy RSVP của user cho event cụ thể
     */
    public Task<EventRSVP> getUserRSVP(String groupId, String eventId, String userId) {
        return db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .document(userId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        return task.getResult().toObject(EventRSVP.class);
                    }
                    return null;
                });
    }
    
    /**
     * Cập nhật số lượng RSVP trong event
     */
    private Task<Void> updateEventCounts(String groupId, String eventId) {
        return getEventRSVPs(groupId, eventId).continueWithTask(task -> {
            if (task.isSuccessful()) {
                List<EventRSVP> rsvps = task.getResult();
                int goingCount = 0, notGoingCount = 0, maybeCount = 0;
                
                for (EventRSVP rsvp : rsvps) {
                    String status = rsvp.attendanceStatus != null ? rsvp.attendanceStatus : rsvp.status; // Backward compatibility
                    android.util.Log.d("EventRepository", "Counting attendee: userId=" + rsvp.userId + ", attendanceStatus=" + status);

                    if (status != null) {
                        switch (status) {
                            case "attending":
                            case "going": // Backward compatibility
                                goingCount++;
                                break;
                            case "not_attending":
                            case "not_going": // Backward compatibility
                                notGoingCount++;
                                break;
                            case "maybe":
                                maybeCount++;
                                break;
                        }
                    }
                }
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("goingCount", goingCount);
                updates.put("notGoingCount", notGoingCount);
                updates.put("maybeCount", maybeCount);
                
                return updateEvent(groupId, eventId, updates);
            }
            return Tasks.forException(task.getException());
        });
    }

    // Callback-based methods for UI compatibility

    public void createEvent(Event event, android.net.Uri imageUri, OnSuccessCallback<String> onSuccess, OnErrorCallback onError) {
        if (imageUri != null) {
            // Upload image first
            uploadEventImage(event.groupId + "_" + System.currentTimeMillis(), imageUri,
                    imageUrl -> {
                        event.imageUrl = imageUrl;
                        createEvent(event)
                                .addOnSuccessListener(onSuccess::onSuccess)
                                .addOnFailureListener(onError::onError);
                    },
                    onError
            );
        } else {
            createEvent(event)
                    .addOnSuccessListener(onSuccess::onSuccess)
                    .addOnFailureListener(onError::onError);
        }
    }

    private void uploadEventImage(String fileName, android.net.Uri imageUri, OnSuccessCallback<String> onSuccess, OnErrorCallback onError) {
        com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
        com.google.firebase.storage.StorageReference imageRef = storage.getReference()
                .child("events")
                .child(fileName + ".jpg");

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    imageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> onSuccess.onSuccess(uri.toString()))
                            .addOnFailureListener(onError::onError);
                })
                .addOnFailureListener(onError::onError);
    }

    public void getEvent(String groupId, String eventId, OnSuccessCallback<Event> onSuccess, OnErrorCallback onError) {
        getEvent(groupId, eventId)
                .addOnSuccessListener(event -> {
                    if (event != null) {
                        onSuccess.onSuccess(event);
                    } else {
                        onError.onError(new Exception("Event không tồn tại"));
                    }
                })
                .addOnFailureListener(onError::onError);
    }

    public void getEvents(String groupId, OnSuccessCallback<java.util.List<Event>> onSuccess, OnErrorCallback onError) {
        getGroupEvents(groupId)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onError::onError);
    }

    public void updateRSVP(String groupId, String eventId, EventRSVP.Status status, OnSuccessCallback<Void> onSuccess, OnErrorCallback onError) {
        android.util.Log.d("EventRepository", "updateAttendance: groupId=" + groupId + ", eventId=" + eventId + ", attendanceStatus=" + status.getValue());

        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            android.util.Log.e("EventRepository", "User not logged in");
            onError.onError(new Exception("User chưa đăng nhập"));
            return;
        }

        EventRSVP rsvp = new EventRSVP();
        rsvp.userId = currentUserId;
        rsvp.attendanceStatus = status.getValue();
        rsvp.responseTime = System.currentTimeMillis();
        
        // Set backward compatibility fields for existing data
        rsvp.status = status.getValue(); // For backward compatibility
        rsvp.respondedAt = rsvp.responseTime; // For backward compatibility
        rsvp.rsvpTime = rsvp.responseTime; // For backward compatibility

        android.util.Log.d("EventRepository", "Creating attendance record: userId=" + currentUserId + ", attendanceStatus=" + status.getValue());

        rsvpEvent(groupId, eventId, rsvp)
                .addOnSuccessListener(result -> {
                    android.util.Log.d("EventRepository", "Attendance updated successfully");
                    // Log RSVP
                    LogRepository logRepo = new LogRepository(db);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("status", status.getValue());
                    logRepo.logGroupAction(groupId, "event_rsvp", "event", eventId, null, metadata);
                    onSuccess.onSuccess(result);
                })
                .addOnFailureListener(error -> {
                    android.util.Log.e("EventRepository", "Failed to update attendance", error);
                    onError.onError(error);
                });
    }

    public void getUserRSVP(String groupId, String eventId, String userId, OnSuccessCallback<EventRSVP> onSuccess, OnErrorCallback onError) {
        getUserRSVP(groupId, eventId, userId)
                .addOnSuccessListener(rsvp -> onSuccess.onSuccess(rsvp))
                .addOnFailureListener(onError::onError);
    }

    public void getRSVPCounts(String groupId, String eventId, OnSuccessCallback<RSVPCounts> onSuccess, OnErrorCallback onError) {
        getEventRSVPs(groupId, eventId)
                .addOnSuccessListener(rsvps -> {
                    int goingCount = 0, maybeCount = 0, notGoingCount = 0;

                    for (EventRSVP rsvp : rsvps) {
                        String status = rsvp.attendanceStatus != null ? rsvp.attendanceStatus : rsvp.status; // Backward compatibility
                        android.util.Log.d("EventRepository", "Counting RSVP: userId=" + rsvp.userId + ", status=" + status);

                        switch (status) {
                            case "attending":
                            case "going": // Backward compatibility
                                goingCount++;
                                break;
                            case "maybe":
                                maybeCount++;
                                break;
                            case "not_attending":
                            case "not_going": // Backward compatibility
                                notGoingCount++;
                                break;
                        }
                    }

                    android.util.Log.d("EventRepository", "Attendance counts: attending=" + goingCount + ", maybe=" + maybeCount + ", notAttending=" + notGoingCount);

                    onSuccess.onSuccess(new RSVPCounts(goingCount, maybeCount, notGoingCount));
                })
                .addOnFailureListener(onError::onError);
    }

    private String getCurrentUserId() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // Debug method - get all events without filter
    public void getAllEventsDebug(String groupId, OnSuccessCallback<java.util.List<Event>> onSuccess, OnErrorCallback onError) {
        android.util.Log.d("EventRepository", "DEBUG: Getting ALL events for groupId: " + groupId);
        db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<Event> events = new java.util.ArrayList<>();
                    android.util.Log.d("EventRepository", "DEBUG: Found " + querySnapshot.size() + " total documents");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        android.util.Log.d("EventRepository", "DEBUG: Document " + doc.getId() + " data: " + doc.getData());
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.eventId = doc.getId();
                            events.add(event);
                            android.util.Log.d("EventRepository", "DEBUG: Parsed event: " + event.title + ", status: " + event.status);
                        } else {
                            android.util.Log.e("EventRepository", "DEBUG: Failed to parse document " + doc.getId());
                        }
                    }
                    android.util.Log.d("EventRepository", "DEBUG: Returning " + events.size() + " events");
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventRepository", "DEBUG: Query failed", e);
                    onError.onError(e);
                });
    }

    // Fix existing events that might have missing fields
    public void fixExistingEvents(String groupId, OnSuccessCallback<Void> onSuccess, OnErrorCallback onError) {
        android.util.Log.d("EventRepository", "Fixing existing events for groupId: " + groupId);

        db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalDocs = querySnapshot.size();
                    android.util.Log.d("EventRepository", "Found " + totalDocs + " events to potentially fix");

                    if (totalDocs == 0) {
                        onSuccess.onSuccess(null);
                        return;
                    }

                    java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                        boolean needsUpdate = false;

                        // Fix status if missing
                        if (doc.get("status") == null) {
                            updates.put("status", "active");
                            needsUpdate = true;
                            android.util.Log.d("EventRepository", "Fixing status for event " + doc.getId());
                        }

                        // Fix createdBy if missing
                        if (doc.get("creatorId") == null) {
                            String currentUserId = getCurrentUserId();
                            if (currentUserId != null) {
                                updates.put("creatorId", currentUserId);
                                needsUpdate = true;
                                android.util.Log.d("EventRepository", "Fixing creatorId for event " + doc.getId());
                            }
                        }

                        if (needsUpdate) {
                            doc.getReference().update(updates)
                                    .addOnCompleteListener(task -> {
                                        if (completed.incrementAndGet() == totalDocs) {
                                            android.util.Log.d("EventRepository", "Finished fixing all events");
                                            onSuccess.onSuccess(null);
                                        }
                                    });
                        } else {
                            if (completed.incrementAndGet() == totalDocs) {
                                android.util.Log.d("EventRepository", "Finished checking all events");
                                onSuccess.onSuccess(null);
                            }
                        }
                    }
                })
                .addOnFailureListener(onError::onError);
    }

    // Delete event
    public void deleteEvent(String groupId, String eventId, OnSuccessCallback<Void> onSuccess, OnErrorCallback onError) {
        android.util.Log.d("EventRepository", "Deleting event: " + eventId + " from group: " + groupId);

        db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventRepository", "Event deleted successfully");
                    // Log event deletion
                    LogRepository logRepo = new LogRepository(db);
                    logRepo.logGroupAction(groupId, "event_cancelled", "event", eventId, null, null);
                    onSuccess.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventRepository", "Failed to delete event", e);
                    onError.onError(e);
                });
    }

    // Check if user can delete event (admin, owner, or creator)
    public void canUserDeleteEvent(String groupId, String eventId, String userId, OnSuccessCallback<Boolean> onSuccess, OnErrorCallback onError) {
        // First check if user is group admin/owner
        db.collection(GROUPS)
                .document(groupId)
                .get()
                .addOnSuccessListener(groupDoc -> {
                    if (groupDoc.exists()) {
                        String ownerId = groupDoc.getString("ownerId");
                        java.util.List<String> adminIds = (java.util.List<String>) groupDoc.get("adminIds");

                        // Check if user is owner or admin
                        boolean isOwner = userId.equals(ownerId);
                        boolean isAdmin = adminIds != null && adminIds.contains(userId);

                        if (isOwner || isAdmin) {
                            onSuccess.onSuccess(true);
                            return;
                        }

                        // Check if user is event creator
                        db.collection(GROUPS)
                                .document(groupId)
                                .collection(EVENTS)
                                .document(eventId)
                                .get()
                                .addOnSuccessListener(eventDoc -> {
                                    if (eventDoc.exists()) {
                                        String creatorId = eventDoc.getString("creatorId");
                                        boolean isCreator = userId.equals(creatorId);
                                        onSuccess.onSuccess(isCreator);
                                    } else {
                                        onSuccess.onSuccess(false);
                                    }
                                })
                                .addOnFailureListener(onError::onError);
                    } else {
                        onSuccess.onSuccess(false);
                    }
                })
                .addOnFailureListener(onError::onError);
    }

    // Get event attendees by status
    public void getEventRSVPs(String groupId, String eventId, EventRSVP.Status status, OnSuccessCallback<java.util.List<EventRSVP>> onSuccess, OnErrorCallback onError) {
        android.util.Log.d("EventRepository", "Getting attendees for event: " + eventId + ", attendanceStatus: " + status.getValue());

        db.collection(GROUPS)
                .document(groupId)
                .collection(EVENTS)
                .document(eventId)
                .collection(EVENT_ATTENDEES)
                .whereEqualTo("attendanceStatus", status.getValue())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<EventRSVP> rsvps = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        EventRSVP rsvp = doc.toObject(EventRSVP.class);
                        if (rsvp != null) {
                            rsvp.attendeeId = doc.getId();
                            rsvps.add(rsvp);
                        }
                    }

                    // Sort by response time manually since we removed orderBy to avoid index requirement
                    rsvps.sort((a, b) -> Long.compare(
                        b.responseTime > 0 ? b.responseTime : b.rsvpTime,
                        a.responseTime > 0 ? a.responseTime : a.rsvpTime
                    ));

                    android.util.Log.d("EventRepository", "Found " + rsvps.size() + " attendees with status: " + status.getValue());

                    if (rsvps.isEmpty()) {
                        onSuccess.onSuccess(rsvps);
                        return;
                    }

                    // Load user info cho từng RSVP
                    final int[] pendingUserLoads = {rsvps.size()};
                    for (EventRSVP rsvp : rsvps) {
                        loadUserInfoForRSVP(rsvp, () -> {
                            pendingUserLoads[0]--;
                            if (pendingUserLoads[0] == 0) {
                                android.util.Log.d("EventRepository", "Returning " + rsvps.size() + " attendees with user info");
                                onSuccess.onSuccess(rsvps);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventRepository", "Failed to get attendees", e);
                    onError.onError(e);
                });
    }

    /**
     * Load user info for attendee
     */
    private void loadUserInfoForRSVP(EventRSVP rsvp, Runnable onComplete) {
        if (rsvp.userId == null || rsvp.userId.isEmpty()) {
            android.util.Log.d("EventRepository", "RSVP has no userId");
            rsvp.userName = "Unknown User";
            rsvp.userAvatarUrl = null;
            onComplete.run();
            return;
        }

        android.util.Log.d("EventRepository", "Loading user info for userId: " + rsvp.userId);
        db.collection("users")
                .document(rsvp.userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        // Use correct field names from User model
                        rsvp.userName = userDoc.getString("displayName");
                        rsvp.userAvatarUrl = userDoc.getString("photoUrl");
                        rsvp.userAvatar = rsvp.userAvatarUrl; // Set alias

                        android.util.Log.d("EventRepository", "User loaded: " + rsvp.userName + ", avatar: " + rsvp.userAvatarUrl);

                        if (rsvp.userName == null || rsvp.userName.isEmpty()) {
                            rsvp.userName = "Unknown User";
                        }
                    } else {
                        android.util.Log.d("EventRepository", "User not found for userId: " + rsvp.userId);
                        rsvp.userName = "Unknown User";
                        rsvp.userAvatarUrl = null;
                        rsvp.userAvatar = null;
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventRepository", "Error loading user info for userId: " + rsvp.userId, e);
                    rsvp.userName = "Unknown User";
                    rsvp.userAvatarUrl = null;
                    rsvp.userAvatar = null;
                    onComplete.run();
                });
    }
}
