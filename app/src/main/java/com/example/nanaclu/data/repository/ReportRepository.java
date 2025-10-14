package com.example.nanaclu.data.repository;

import com.example.nanaclu.data.model.ReportModel;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.CollectionReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository xử lý các thao tác CRUD cho báo cáo post trong group
 * Lưu trữ dữ liệu trong subcollection: groups/{groupId}/reports/{reportId}
 */
public class ReportRepository {
    private final FirebaseFirestore db;
    private static final String GROUPS = "groups";
    private static final String REPORTS = "reports"; // subcollection name

    public ReportRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public interface Callback<T> { 
        void onSuccess(T result); 
        void onError(Exception e); 
    }

    /**
     * Thêm report vào groups/{groupId}/reports (auto id)
     * callback.onSuccess(reportId) khi thành công
     */
    public void submitGroupReport(String groupId, ReportModel report, Callback<String> cb) {
        CollectionReference coll = db.collection(GROUPS).document(groupId).collection(REPORTS);
        coll.add(report.toMap())
            .addOnSuccessListener(docRef -> cb.onSuccess(docRef.getId()))
            .addOnFailureListener(cb::onError);
    }

    /**
     * Tuỳ chọn: submit và mirror sang top-level "reports" để thống kê toàn hệ thống.
     * Non-blocking mirror (không bắt buộc) - nếu lỗi vẫn tiếp tục
     */
    public void submitGroupReportAndMirror(String groupId, ReportModel report, Callback<String> cb) {
        submitGroupReport(groupId, report, new Callback<String>() {
            @Override public void onSuccess(String id) {
                try {
                    // Mirror sang top-level collection để admin toàn hệ thống có thể xem
                    report.reportId = id;
                    Map<String,Object> map = report.toMap();
                    map.put("groupId", groupId);
                    db.collection("reports").document(id).set(map);
                } catch (Exception ignored) {
                    // Nếu mirror thất bại, vẫn coi như thành công vì report chính đã được lưu
                }
                cb.onSuccess(id);
            }
            @Override public void onError(Exception e) { 
                cb.onError(e); 
            }
        });
    }

    /**
     * Lấy báo cáo của 1 group, có thể filter theo status (null => all)
     * callback.onSuccess(List<ReportModel>) với danh sách báo cáo
     */
    public void fetchGroupReports(String groupId, String statusFilter, Callback<List<ReportModel>> cb) {
        com.google.firebase.firestore.Query q = db.collection(GROUPS).document(groupId).collection(REPORTS)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING);
        if (statusFilter != null) q = q.whereEqualTo("status", statusFilter);

        q.get().addOnSuccessListener((QuerySnapshot snaps) -> {
            List<ReportModel> list = new ArrayList<>();
            for (QueryDocumentSnapshot d : snaps) {
                ReportModel report = ReportModel.fromMap(d.getId(), d.getData());
                if (report != null) list.add(report);
            }
            cb.onSuccess(list);
        }).addOnFailureListener(cb::onError);
    }

    /**
     * Lấy chi tiết một report cụ thể
     * callback.onSuccess(ReportModel) hoặc callback.onError() nếu không tìm thấy
     */
    public void getReportById(String groupId, String reportId, Callback<ReportModel> cb) {
        DocumentReference docRef = db.collection(GROUPS).document(groupId).collection(REPORTS).document(reportId);
        docRef.get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                ReportModel report = ReportModel.fromMap(doc.getId(), doc.getData());
                cb.onSuccess(report);
            } else {
                cb.onError(new Exception("Report not found"));
            }
        }).addOnFailureListener(cb::onError);
    }

    /**
     * Cập nhật status của report + extra fields (moderatorId, moderatorNote, action...)
     * callback.onSuccess(null) khi xong
     */
    public void updateReportStatus(String groupId, String reportId, String newStatus, Map<String,Object> extra, Callback<Void> cb) {
        DocumentReference r = db.collection(GROUPS).document(groupId).collection(REPORTS).document(reportId);
        Map<String,Object> update = new java.util.HashMap<>();
        update.put("status", newStatus);
        if (extra != null) update.putAll(extra);
        r.update(update).addOnSuccessListener(a -> cb.onSuccess(null)).addOnFailureListener(cb::onError);
    }

    /**
     * Xoá report (nếu cần)
     * callback.onSuccess(null) khi xóa thành công
     */
    public void deleteReport(String groupId, String reportId, Callback<Void> cb) {
        db.collection(GROUPS).document(groupId).collection(REPORTS).document(reportId)
            .delete().addOnSuccessListener(a -> cb.onSuccess(null)).addOnFailureListener(cb::onError);
    }

    /**
     * Đếm số lượng reports theo status trong một group
     * callback.onSuccess(Map<String, Integer>) với key là status, value là count
     */
    public void getReportCountsByStatus(String groupId, Callback<Map<String, Integer>> cb) {
        db.collection(GROUPS).document(groupId).collection(REPORTS).get()
            .addOnSuccessListener(snapshot -> {
                Map<String, Integer> counts = new java.util.HashMap<>();
                counts.put("pending", 0);
                counts.put("reviewed", 0);
                counts.put("dismissed", 0);
                counts.put("action_taken", 0);
                
                for (QueryDocumentSnapshot doc : snapshot) {
                    String status = doc.getString("status");
                    if (status != null) {
                        Integer current = counts.get(status);
                        counts.put(status, current != null ? current + 1 : 1);
                    }
                }
                cb.onSuccess(counts);
            })
            .addOnFailureListener(cb::onError);
    }
}
