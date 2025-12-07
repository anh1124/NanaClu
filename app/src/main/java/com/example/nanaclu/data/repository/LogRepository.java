package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.GroupLog;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LogRepository {
    private final FirebaseFirestore db;
    private static final String LOGS_COLLECTION = "logs";

    public LogRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // Callback interfaces
    public interface LogsCallback {
        void onSuccess(List<GroupLog> logs, DocumentSnapshot lastDoc);
        void onError(Exception e);
    }

    public interface ActorsCallback {
        void onSuccess(List<Map<String, String>> actors);
        void onError(Exception e);
    }

    public interface ExportCallback {
        void onSuccess(String jsonString);
        void onError(Exception e);
    }

    /**
     * Log a group action (fire-and-forget)
     */
    public void logGroupAction(String groupId, String type, String targetType, String targetId,
                              String targetName, Map<String, Object> metadata) {
        try {
            android.util.Log.d("LogRepository", "🔍 Starting logGroupAction: groupId=" + groupId + ", type=" + type);

            // Skip logging for unwanted actions as per requirements
            if (GroupLog.TYPE_COMMENT_ADDED.equals(type) ||
                GroupLog.TYPE_COMMENT_DELETED.equals(type) ||
                GroupLog.TYPE_EVENT_RSVP.equals(type)) {
                android.util.Log.d("LogRepository", "⏭️ Skipping log for unwanted action type: " + type);
                return;
            }

            String currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                android.util.Log.e("LogRepository", "❌ Cannot log action: user not logged in");
                return;
            }

            android.util.Log.d("LogRepository", "✅ User authenticated: " + currentUserId);

            // Get current user name for caching
            getCurrentUserName(currentUserId, actorName -> {
                Map<String, Object> logData = new HashMap<>();
                logData.put("groupId", groupId);
                logData.put("type", type);
                logData.put("actorId", currentUserId);
                logData.put("actorName", actorName);
                logData.put("targetType", targetType);
                logData.put("targetId", targetId);
                logData.put("targetName", targetName);
                logData.put("message", generateMessage(type, actorName, targetName, metadata));
                logData.put("createdAt", FieldValue.serverTimestamp());
                if (metadata != null) {
                    logData.put("metadata", metadata);
                }

                // Fire-and-forget: don't wait for completion
                android.util.Log.d("LogRepository", "📝 Uploading log to Firestore: " + logData);
                db.collection("groups")
                        .document(groupId)
                        .collection(LOGS_COLLECTION)
                        .add(logData)
                        .addOnSuccessListener(docRef ->
                            android.util.Log.d("LogRepository", "✅ Log uploaded successfully: " + docRef.getId()))
                        .addOnFailureListener(e ->
                            android.util.Log.e("LogRepository", "❌ Failed to log action: " + type, e));
            });
        } catch (Exception e) {
            android.util.Log.e("LogRepository", "Error logging action: " + type, e);
        }
    }

    /**
     * Get group logs with pagination
     */
    public void getGroupLogs(String groupId, int limit, DocumentSnapshot lastDoc, LogsCallback callback) {
        Query baseQuery = db.collection("groups")
                .document(groupId)
                .collection(LOGS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit);

        Query query = lastDoc != null ? baseQuery.startAfter(lastDoc) : baseQuery;

        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<GroupLog> logs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        GroupLog log = doc.toObject(GroupLog.class);
                        if (log != null) {
                            log.logId = doc.getId();
                            logs.add(log);
                        }
                    }
                    DocumentSnapshot newLastDoc = querySnapshot.isEmpty() ? null : 
                            querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    callback.onSuccess(logs, newLastDoc);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Query logs with filters
     */
    public void queryLogs(String groupId, LogFilters filters, int limit, DocumentSnapshot lastDoc, LogsCallback callback) {
        Query baseQuery = db.collection("groups")
                .document(groupId)
                .collection(LOGS_COLLECTION);

        // Apply filters
        if (filters.actorId != null && !filters.actorId.isEmpty()) {
            baseQuery = baseQuery.whereEqualTo("actorId", filters.actorId);
        }
        if (filters.type != null && !filters.type.isEmpty()) {
            baseQuery = baseQuery.whereEqualTo("type", filters.type);
        }

        baseQuery = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit);
        Query query = lastDoc != null ? baseQuery.startAfter(lastDoc) : baseQuery;

        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<GroupLog> logs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        GroupLog log = doc.toObject(GroupLog.class);
                        if (log != null) {
                            log.logId = doc.getId();
                            // Apply date filter in memory if needed
                            if (filters.dateRange != null && !filters.dateRange.isInRange(log.createdAt.toDate().getTime())) {
                                continue;
                            }
                            logs.add(log);
                        }
                    }
                    DocumentSnapshot newLastDoc = querySnapshot.isEmpty() ? null : 
                            querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    callback.onSuccess(logs, newLastDoc);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Get distinct actors for filter autocomplete
     */
    public void getDistinctActors(String groupId, ActorsCallback callback) {
        db.collection("groups")
                .document(groupId)
                .collection(LOGS_COLLECTION)
                .orderBy("actorId")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, String> actorMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String actorId = doc.getString("actorId");
                        String actorName = doc.getString("actorName");
                        if (actorId != null && actorName != null) {
                            actorMap.put(actorId, actorName);
                        }
                    }
                    List<Map<String, String>> actors = new ArrayList<>();
                    for (Map.Entry<String, String> entry : actorMap.entrySet()) {
                        Map<String, String> actor = new HashMap<>();
                        actor.put("id", entry.getKey());
                        actor.put("name", entry.getValue());
                        actors.add(actor);
                    }
                    callback.onSuccess(actors);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Export logs to JSON string
     */
    public void exportLogsToJson(String groupId, LogFilters filters, ExportCallback callback) {
        // Get all logs (no pagination for export)
        Query baseQuery = db.collection("groups")
                .document(groupId)
                .collection(LOGS_COLLECTION);

        if (filters.actorId != null && !filters.actorId.isEmpty()) {
            baseQuery = baseQuery.whereEqualTo("actorId", filters.actorId);
        }
        if (filters.type != null && !filters.type.isEmpty()) {
            baseQuery = baseQuery.whereEqualTo("type", filters.type);
        }

        baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    try {
                        JSONObject exportData = new JSONObject();
                        exportData.put("groupId", groupId);
                        exportData.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                        exportData.put("totalLogs", querySnapshot.size());

                        JSONArray logsArray = new JSONArray();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            GroupLog log = doc.toObject(GroupLog.class);
                            if (log != null) {
                                log.logId = doc.getId();
                                
                                // Apply date filter if needed
                                if (filters.dateRange != null && !filters.dateRange.isInRange(log.createdAt.toDate().getTime())) {
                                    continue;
                                }

                                JSONObject logJson = new JSONObject();
                                logJson.put("logId", log.logId);
                                logJson.put("type", log.type);
                                logJson.put("actorId", log.actorId);
                                logJson.put("actorName", log.actorName);
                                logJson.put("targetType", log.targetType);
                                logJson.put("targetId", log.targetId);
                                logJson.put("targetName", log.targetName);
                                logJson.put("message", log.message);
                                logJson.put("createdAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(log.createdAt.toDate()));
                                if (log.metadata != null) {
                                    logJson.put("metadata", new JSONObject(log.metadata));
                                }
                                logsArray.put(logJson);
                            }
                        }
                        exportData.put("logs", logsArray);
                        callback.onSuccess(exportData.toString(2));
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Get current user's display name
     */
    private void getCurrentUserName(String userId, UserNameCallback callback) {
        android.util.Log.d("LogRepository", "🔍 Getting user name for: " + userId);
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String displayName = doc.getString("displayName");
                        if (displayName == null || displayName.isEmpty()) {
                            displayName = doc.getString("name");
                        }
                        String finalName = displayName != null ? displayName : "Unknown User";
                        android.util.Log.d("LogRepository", "✅ Got user name: " + finalName);
                        callback.onUserName(finalName);
                    } else {
                        android.util.Log.w("LogRepository", "⚠️ User document not found: " + userId);
                        callback.onUserName("Unknown User");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("LogRepository", "❌ Failed to get user name: " + userId, e);
                    callback.onUserName("Unknown User");
                });
    }

    private String getCurrentUserId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null ? 
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    /**
     * Generate Vietnamese message for log
     */
    private String generateMessage(String type, String actorName, String targetName, Map<String, Object> metadata) {
        if (actorName == null) actorName = "Unknown User";
        
        switch (type) {
            case GroupLog.TYPE_POST_CREATED:
                return actorName + " tạo post \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_POST_DELETED:
                return actorName + " xóa post \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_COMMENT_ADDED:
                return actorName + " bình luận vào post \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_COMMENT_DELETED:
                return actorName + " xóa bình luận \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_EVENT_CREATED:
                return actorName + " tạo sự kiện \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_EVENT_CANCELLED:
                return actorName + " hủy sự kiện \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_EVENT_RSVP:
                if (metadata != null && metadata.containsKey("status")) {
                    String status = (String) metadata.get("status");
                    String statusText = "attending".equals(status) ? "Going" : 
                                      "maybe".equals(status) ? "Maybe" : "Not Going";
                    return actorName + " RSVP \"" + statusText + "\" cho sự kiện \"" + (targetName != null ? targetName : "") + "\"";
                }
                return actorName + " RSVP cho sự kiện \"" + (targetName != null ? targetName : "") + "\"";
            case GroupLog.TYPE_GROUP_UPDATED:
                if (metadata != null && metadata.containsKey("from") && metadata.containsKey("to")) {
                    return actorName + " đổi tên nhóm từ \"" + metadata.get("from") + "\" → \"" + metadata.get("to") + "\"";
                }
                return actorName + " " + (targetName != null ? targetName : "cập nhật nhóm");
            case GroupLog.TYPE_GROUP_IMAGE_UPDATED:
                return actorName + " " + (targetName != null ? targetName : "cập nhật ảnh nhóm");
            case GroupLog.TYPE_MEMBER_APPROVED:
                return actorName + " duyệt yêu cầu tham gia của " + (targetName != null ? targetName : "thành viên");
            case GroupLog.TYPE_MEMBER_REJECTED:
                return actorName + " từ chối yêu cầu tham gia của " + (targetName != null ? targetName : "thành viên");
            case GroupLog.TYPE_MEMBER_REMOVED:
                return actorName + " xóa " + (targetName != null ? targetName : "thành viên") + " khỏi nhóm";
            case GroupLog.TYPE_MEMBER_BLOCKED:
                return actorName + " chặn " + (targetName != null ? targetName : "thành viên");
            case GroupLog.TYPE_MEMBER_UNBLOCKED:
                return actorName + " bỏ chặn " + (targetName != null ? targetName : "thành viên");
            case GroupLog.TYPE_OWNERSHIP_TRANSFERRED:
                if (metadata != null && metadata.containsKey("fromUserId") && metadata.containsKey("toUserId")) {
                    return "Chuyển quyền sở hữu nhóm từ " + metadata.get("fromUserId") + " → " + metadata.get("toUserId");
                }
                return "Chuyển quyền sở hữu nhóm";
            case GroupLog.TYPE_ROLE_CHANGED:
                if (metadata != null && metadata.containsKey("newRole")) {
                    String role = (String) metadata.get("newRole");
                    String roleText = "owner".equals(role) ? "chủ sở hữu" : 
                                    "admin".equals(role) ? "admin" : "thành viên";
                    return actorName + " chuyển " + (targetName != null ? targetName : "thành viên") + " thành " + roleText;
                }
                return actorName + " thay đổi quyền của " + (targetName != null ? targetName : "thành viên");
            case GroupLog.TYPE_POLICY_CHANGED:
                return actorName + " thay đổi chính sách tham gia nhóm";
            case GroupLog.TYPE_GROUP_DELETED:
                return actorName + " xóa nhóm";
            default:
                return actorName + " thực hiện hành động " + type;
        }
    }

    // Helper interfaces
    private interface UserNameCallback {
        void onUserName(String userName);
    }

    // Filter classes
    public static class LogFilters {
        public String actorId;
        public String type;
        public DateRange dateRange;

        public LogFilters() {}

        public LogFilters(String actorId, String type, DateRange dateRange) {
            this.actorId = actorId;
            this.type = type;
            this.dateRange = dateRange;
        }
    }

    public static class DateRange {
        public long startTime;
        public long endTime;

        public DateRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public boolean isInRange(long timestamp) {
            return timestamp >= startTime && timestamp <= endTime;
        }
    }
}
