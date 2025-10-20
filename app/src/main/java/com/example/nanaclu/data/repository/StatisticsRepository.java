package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.GroupStatistics;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository để truy vấn thống kê hoạt động nhóm
 */
public class StatisticsRepository {
    private final FirebaseFirestore db;
    private static final String LOGS_COLLECTION = "logs";

    public StatisticsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // Callback interface
    public interface StatisticsCallback {
        void onSuccess(List<GroupStatistics> statistics);
        void onError(Exception e);
    }

    /**
     * Lấy thống kê theo tháng trong một năm
     */
    public void getMonthlyStatistics(String groupId, int year, StatisticsCallback callback) {
        try {
            // Tạo range thời gian cho năm
            Calendar start = Calendar.getInstance();
            start.set(year, 0, 1, 0, 0, 0);
            Calendar end = Calendar.getInstance();
            end.set(year, 11, 31, 23, 59, 59);

            // Query logs trong năm
            Query query = db.collection("groups")
                    .document(groupId)
                    .collection(LOGS_COLLECTION)
                    .whereGreaterThanOrEqualTo("createdAt", new Timestamp(start.getTime()))
                    .whereLessThanOrEqualTo("createdAt", new Timestamp(end.getTime()))
                    .orderBy("createdAt", Query.Direction.ASCENDING);

            query.get()
                    .addOnSuccessListener(querySnapshot -> {
                        try {
                            // Group by tháng và đếm
                            Map<String, GroupStatistics> monthlyStats = new HashMap<>();
                            
                            // Khởi tạo chỉ đến tháng hiện tại
                            Calendar now = Calendar.getInstance();
                            int currentMonth = now.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-indexed
                            int currentYearNow = now.get(Calendar.YEAR);
                            
                            // Chỉ tạo tháng đến tháng hiện tại
                            int maxMonth = (year == currentYearNow) ? currentMonth : 12;
                            for (int month = 1; month <= maxMonth; month++) {
                                String monthKey = String.format("%04d-%02d", year, month);
                                monthlyStats.put(monthKey, new GroupStatistics(monthKey));
                            }

                            // Đếm từ logs
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String type = doc.getString("type");
                                Timestamp timestamp = doc.getTimestamp("createdAt");
                                
                                if (timestamp != null) {
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTime(timestamp.toDate());
                                    String monthKey = String.format("%04d-%02d", year, cal.get(Calendar.MONTH) + 1);
                                    
                                    GroupStatistics stats = monthlyStats.get(monthKey);
                                    if (stats != null) {
                                        // Đếm theo loại
                                        if ("post_created".equals(type)) {
                                            stats.postCount++;
                                        } else if ("event_created".equals(type)) {
                                            stats.eventCount++;
                                        } else if ("member_approved".equals(type)) {
                                            stats.newMemberCount++;
                                        }
                                    }
                                }
                            }

                            // Chuyển thành list và sort theo tháng
                            List<GroupStatistics> result = new ArrayList<>(monthlyStats.values());
                            result.sort((a, b) -> a.periodKey.compareTo(b.periodKey));
                            
                            callback.onSuccess(result);
                        } catch (Exception e) {
                            callback.onError(e);
                        }
                    })
                    .addOnFailureListener(callback::onError);

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Lấy thống kê theo năm
     */
    public void getYearlyStatistics(String groupId, int startYear, int endYear, StatisticsCallback callback) {
        try {
            // Tạo range thời gian
            Calendar start = Calendar.getInstance();
            start.set(startYear, 0, 1, 0, 0, 0);
            Calendar end = Calendar.getInstance();
            end.set(endYear, 11, 31, 23, 59, 59);

            // Query logs trong khoảng thời gian
            Query query = db.collection("groups")
                    .document(groupId)
                    .collection(LOGS_COLLECTION)
                    .whereGreaterThanOrEqualTo("createdAt", new Timestamp(start.getTime()))
                    .whereLessThanOrEqualTo("createdAt", new Timestamp(end.getTime()))
                    .orderBy("createdAt", Query.Direction.ASCENDING);

            query.get()
                    .addOnSuccessListener(querySnapshot -> {
                        try {
                            // Group by năm và đếm
                            Map<String, GroupStatistics> yearlyStats = new HashMap<>();
                            
                            // Khởi tạo tất cả năm trong range
                            for (int year = startYear; year <= endYear; year++) {
                                String yearKey = String.valueOf(year);
                                yearlyStats.put(yearKey, new GroupStatistics(yearKey));
                            }

                            // Đếm từ logs
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String type = doc.getString("type");
                                Timestamp timestamp = doc.getTimestamp("createdAt");
                                
                                if (timestamp != null) {
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTime(timestamp.toDate());
                                    String yearKey = String.valueOf(cal.get(Calendar.YEAR));
                                    
                                    GroupStatistics stats = yearlyStats.get(yearKey);
                                    if (stats != null) {
                                        // Đếm theo loại
                                        if ("post_created".equals(type)) {
                                            stats.postCount++;
                                        } else if ("event_created".equals(type)) {
                                            stats.eventCount++;
                                        } else if ("member_approved".equals(type)) {
                                            stats.newMemberCount++;
                                        }
                                    }
                                }
                            }

                            // Chuyển thành list và sort theo năm
                            List<GroupStatistics> result = new ArrayList<>(yearlyStats.values());
                            result.sort((a, b) -> a.periodKey.compareTo(b.periodKey));
                            
                            callback.onSuccess(result);
                        } catch (Exception e) {
                            callback.onError(e);
                        }
                    })
                    .addOnFailureListener(callback::onError);

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Lấy thống kê tổng quan của nhóm
     */
    public void getGroupOverview(String groupId, StatisticsCallback callback) {
        try {
            // Query tất cả logs của nhóm
            Query query = db.collection("groups")
                    .document(groupId)
                    .collection(LOGS_COLLECTION)
                    .orderBy("createdAt", Query.Direction.ASCENDING);

            query.get()
                    .addOnSuccessListener(querySnapshot -> {
                        try {
                            GroupStatistics totalStats = new GroupStatistics("total");
                            
                            // Đếm tất cả
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String type = doc.getString("type");
                                
                                if ("post_created".equals(type)) {
                                    totalStats.postCount++;
                                } else if ("event_created".equals(type)) {
                                    totalStats.eventCount++;
                                } else if ("member_approved".equals(type)) {
                                    totalStats.newMemberCount++;
                                }
                            }

                            List<GroupStatistics> result = new ArrayList<>();
                            result.add(totalStats);
                            
                            callback.onSuccess(result);
                        } catch (Exception e) {
                            callback.onError(e);
                        }
                    })
                    .addOnFailureListener(callback::onError);

        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
