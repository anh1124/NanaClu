package com.example.nanaclu.data.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminRepository {
    private final FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";
    private static final String GROUPS_COLLECTION = "groups";
    private static final String CHATS_COLLECTION = "chats";

    public AdminRepository(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Kiểm tra quyền admin của user hiện tại (theo trường isadmin trên Firestore).
     * Success: callback.onResult(true/false)
     * Error: callback.onError(exception)
     */
    public void checkIsAdmin(String userId, AdminCheckCallback callback) {
        db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        Boolean isadmin = doc.getBoolean("isadmin");
                        callback.onResult(Boolean.TRUE.equals(isadmin));
                    } else {
                        callback.onResult(false);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Lấy toàn bộ dữ liệu từ Firestore và chuyển thành JSON string
     * Bao gồm: users, groups, chats và tất cả subcollections (convert các kiểu đặc biệt)
     */
    public void exportAllDataToJson(ExportDataCallback callback) {
        JSONObject allData = new JSONObject();

        // Users
        db.collection(USERS_COLLECTION).get()
                .addOnSuccessListener(usersSnapshot -> {
                    JSONArray usersArray = new JSONArray();
                    for (QueryDocumentSnapshot doc : usersSnapshot) {
                        usersArray.put(convertMapToJson(doc.getData()));
                    }
                    try { allData.put("users", usersArray); } catch (Exception ignored) {}

                    // Groups
                    db.collection(GROUPS_COLLECTION).get()
                            .addOnSuccessListener(groupsSnapshot -> {
                                JSONArray groupsArray = new JSONArray();

                                if (groupsSnapshot.isEmpty()) {
                                    try { allData.put("groups", groupsArray); } catch (Exception ignored) {}
                                    exportChatsAndFinish(allData, callback);
                                    return;
                                }

                                AtomicInteger completedGroups = new AtomicInteger(0);
                                int totalGroups = groupsSnapshot.size();

                                for (QueryDocumentSnapshot groupDoc : groupsSnapshot) {
                                    String groupId = groupDoc.getId();
                                    JSONObject groupJson = convertMapToJson(groupDoc.getData());
                                    exportGroupSubcollections(groupId, groupJson, () -> {
                                        groupsArray.put(groupJson);
                                        if (completedGroups.incrementAndGet() == totalGroups) {
                                            try { allData.put("groups", groupsArray); } catch (Exception ignored) {}
                                            exportChatsAndFinish(allData, callback);
                                        }
                                    });
                                }
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Lấy tất cả subcollections của một group (posts, events, members, chats -> messages)
     * groupJson là JSONObject đại diện cho document group, helper sẽ thêm các trường posts/events...
     */
    private void exportGroupSubcollections(String groupId, JSONObject groupJson, Runnable onComplete) {
        // posts
        db.collection(GROUPS_COLLECTION).document(groupId).collection("posts").get()
                .addOnSuccessListener(postsSnapshot -> {
                    JSONArray postsArray = new JSONArray();
                    for (QueryDocumentSnapshot doc : postsSnapshot) {
                        postsArray.put(convertMapToJson(doc.getData()));
                    }
                    try { groupJson.put("posts", postsArray); } catch (Exception ignored) {}

                    // events
                    db.collection(GROUPS_COLLECTION).document(groupId).collection("events").get()
                            .addOnSuccessListener(eventsSnapshot -> {
                                JSONArray eventsArray = new JSONArray();
                                for (QueryDocumentSnapshot doc : eventsSnapshot) {
                                    eventsArray.put(convertMapToJson(doc.getData()));
                                }
                                try { groupJson.put("events", eventsArray); } catch (Exception ignored) {}

                                // members
                                db.collection(GROUPS_COLLECTION).document(groupId).collection("members").get()
                                        .addOnSuccessListener(membersSnapshot -> {
                                            JSONArray membersArray = new JSONArray();
                                            for (QueryDocumentSnapshot doc : membersSnapshot) {
                                                membersArray.put(convertMapToJson(doc.getData()));
                                            }
                                            try { groupJson.put("members", membersArray); } catch (Exception ignored) {}

                                            // reports
                                            exportGroupReportsToJson(groupId, groupJson, () -> {
                                                // chats in group
                                                db.collection(GROUPS_COLLECTION).document(groupId).collection("chats").get()
                                                    .addOnSuccessListener(groupChatsSnapshot -> {
                                                        JSONArray groupChatsArray = new JSONArray();

                                                        if (groupChatsSnapshot.isEmpty()) {
                                                            try { groupJson.put("chats", groupChatsArray); } catch (Exception ignored) {}
                                                            onComplete.run();
                                                            return;
                                                        }

                                                        AtomicInteger completedGroupChats = new AtomicInteger(0);
                                                        int totalGroupChats = groupChatsSnapshot.size();

                                                        for (QueryDocumentSnapshot chatDoc : groupChatsSnapshot) {
                                                            String chatId = chatDoc.getId();
                                                            JSONObject chatJson = convertMapToJson(chatDoc.getData());

                                                            // messages in group chat
                                                            db.collection(GROUPS_COLLECTION).document(groupId)
                                                                    .collection("chats").document(chatId).collection("messages").get()
                                                                    .addOnSuccessListener(messagesSnapshot -> {
                                                                        JSONArray messagesArray = new JSONArray();
                                                                        for (QueryDocumentSnapshot msgDoc : messagesSnapshot) {
                                                                            messagesArray.put(convertMapToJson(msgDoc.getData()));
                                                                        }
                                                                        try { chatJson.put("messages", messagesArray); } catch (Exception ignored) {}
                                                                        groupChatsArray.put(chatJson);

                                                                        if (completedGroupChats.incrementAndGet() == totalGroupChats) {
                                                                            try { groupJson.put("chats", groupChatsArray); } catch (Exception ignored) {}
                                                                            onComplete.run();
                                                                        }
                                                                    })
                                                                    .addOnFailureListener(e -> {
                                                                        // vẫn thêm chat nhưng không có messages
                                                                        try { groupChatsArray.put(chatJson); } catch (Exception ignored) {}
                                                                        if (completedGroupChats.incrementAndGet() == totalGroupChats) {
                                                                            try { groupJson.put("chats", groupChatsArray); } catch (Exception ignored) {}
                                                                            onComplete.run();
                                                                        }
                                                                    });
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        try { groupJson.put("chats", new JSONArray()); } catch (Exception ignored) {}
                                                        onComplete.run();
                                                    });
                                            });
                                        })
                                        .addOnFailureListener(e -> {
                                            try { groupJson.put("members", new JSONArray()); } catch (Exception ignored) {}
                                            onComplete.run();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                try { groupJson.put("events", new JSONArray()); } catch (Exception ignored) {}
                                onComplete.run();
                            });
                })
                .addOnFailureListener(e -> {
                    try { groupJson.put("posts", new JSONArray()); } catch (Exception ignored) {}
                    onComplete.run();
                });
    }

    /**
     * Export reports của một group vào JSON
     * Thêm reports array vào groupJson và gọi onComplete khi xong
     */
    private void exportGroupReportsToJson(String groupId, JSONObject groupJson, Runnable onComplete) {
        db.collection(GROUPS_COLLECTION).document(groupId).collection("reports").get()
            .addOnSuccessListener(reportsSnapshot -> {
                JSONArray reportsArray = new JSONArray();
                for (QueryDocumentSnapshot doc : reportsSnapshot) {
                    reportsArray.put(convertMapToJson(doc.getData()));
                }
                try { 
                    groupJson.put("reports", reportsArray); 
                } catch (Exception ignored) {}
                onComplete.run();
            })
            .addOnFailureListener(e -> {
                // Nếu lỗi, vẫn thêm array rỗng và tiếp tục
                try { 
                    groupJson.put("reports", new JSONArray()); 
                } catch (Exception ignored) {}
                onComplete.run();
            });
    }

    /**
     * Lấy chats (private) và hoàn thành export
     */
    private void exportChatsAndFinish(JSONObject allData, ExportDataCallback callback) {
        db.collection(CHATS_COLLECTION).get()
                .addOnSuccessListener(chatsSnapshot -> {
                    JSONArray chatsArray = new JSONArray();

                    if (chatsSnapshot.isEmpty()) {
                        try { allData.put("chats", chatsArray); } catch (Exception ignored) {}
                        finishExport(allData, callback);
                        return;
                    }

                    AtomicInteger completedChats = new AtomicInteger(0);
                    int totalChats = chatsSnapshot.size();

                    for (QueryDocumentSnapshot chatDoc : chatsSnapshot) {
                        String chatId = chatDoc.getId();
                        JSONObject chatJson = convertMapToJson(chatDoc.getData());

                        db.collection(CHATS_COLLECTION).document(chatId).collection("messages").get()
                                .addOnSuccessListener(messagesSnapshot -> {
                                    JSONArray messagesArray = new JSONArray();
                                    for (QueryDocumentSnapshot msgDoc : messagesSnapshot) {
                                        messagesArray.put(convertMapToJson(msgDoc.getData()));
                                    }
                                    try { chatJson.put("messages", messagesArray); } catch (Exception ignored) {}
                                    chatsArray.put(chatJson);

                                    if (completedChats.incrementAndGet() == totalChats) {
                                        try { allData.put("chats", chatsArray); } catch (Exception ignored) {}
                                        finishExport(allData, callback);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // nếu lỗi, vẫn tiếp tục
                                    chatsArray.put(chatJson);
                                    if (completedChats.incrementAndGet() == totalChats) {
                                        try { allData.put("chats", chatsArray); } catch (Exception ignored) {}
                                        finishExport(allData, callback);
                                    }
                                });
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Hoàn thành export và chuyển thành JSON string (pretty print)
     */
    private void finishExport(JSONObject allData, ExportDataCallback callback) {
        try {
            // pretty print 2 spaces
            callback.onSuccess(allData.toString(2));
        } catch (Exception e) {
            callback.onError(new Exception("Lỗi chuyển đổi JSON: " + e.getMessage()));
        }
    }

    /**
     * Helper: convert Map<String,Object> (doc.getData()) thành JSONObject đệ quy,
     * chuyển các kiểu Firestore đặc biệt sang primitives JSON-friendly.
     */
    private JSONObject convertMapToJson(Map<String, Object> map) {
        JSONObject obj = new JSONObject();
        if (map == null) return obj;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            try {
                Object converted = toJsonCompatible(entry.getValue());
                obj.put(entry.getKey(), converted);
            } catch (Exception e) {
                // nếu có key không thể chuyển đổi, ghi string fallback
                try { obj.put(entry.getKey(), String.valueOf(entry.getValue())); } catch (Exception ignored) {}
            }
        }
        return obj;
    }

    /**
     * Helper: convert List<Object> thành JSONArray đệ quy
     */
    private JSONArray convertListToJson(List<Object> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (Object v : list) {
            arr.put(toJsonCompatible(v));
        }
        return arr;
    }

    /**
     * Convert các giá trị Firestore (Timestamp, GeoPoint, DocumentReference, Map, List, Date...)
     * sang các kiểu primitive/JSONObject/JSONArray an toàn cho org.json
     */
    private Object toJsonCompatible(Object value) {
        if (value == null) return JSONObject.NULL;

        // Timestamp (com.google.firebase.Timestamp) -> epoch millis
        if (value instanceof Timestamp) {
            Timestamp ts = (Timestamp) value;
            // chuyển thành milliseconds để dễ dùng
            long millis = ts.toDate().getTime();
            return millis;
        }

        // GeoPoint -> { latitude, longitude }
        if (value instanceof GeoPoint) {
            GeoPoint gp = (GeoPoint) value;
            JSONObject o = new JSONObject();
            try {
                o.put("latitude", gp.getLatitude());
                o.put("longitude", gp.getLongitude());
            } catch (Exception ignored) {}
            return o;
        }

        // DocumentReference -> trả về path (hoặc id nếu bạn muốn)
        if (value instanceof DocumentReference) {
            DocumentReference dr = (DocumentReference) value;
            return dr.getPath();
        }

        // Map -> JSONObject (đệ quy)
        if (value instanceof Map) {
            //noinspection unchecked
            return convertMapToJson((Map<String, Object>) value);
        }

        // List -> JSONArray (đệ quy)
        if (value instanceof List) {
            //noinspection unchecked
            return convertListToJson((List<Object>) value);
        }

        // Date -> millis
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }

        // Các kiểu primitive / String / Number / Boolean
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        // Fallback: trả về toString()
        return String.valueOf(value);
    }

    public interface AdminCheckCallback {
        void onResult(boolean isAdmin);
        void onError(Exception e);
    }

    public interface ExportDataCallback {
        void onSuccess(String jsonData);
        void onError(Exception e);
    }
}
