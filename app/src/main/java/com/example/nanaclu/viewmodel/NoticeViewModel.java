package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Notice;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.utils.NoticeCenter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class NoticeViewModel extends ViewModel {
    private NoticeRepository noticeRepository;
    private NoticeCenter noticeCenter;
    private MutableLiveData<List<Notice>> notices = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> error = new MutableLiveData<>();
    
    private String currentUid;
    private DocumentSnapshot lastDocument;
    private boolean hasMoreData = true;

    public NoticeViewModel() {
        noticeRepository = new NoticeRepository(FirebaseFirestore.getInstance());
        noticeCenter = NoticeCenter.getInstance();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUid = auth.getCurrentUser().getUid();
        }
    }

    /**
     * Bắt đầu lắng nghe thông báo
     */
    public void startListening() {
        if (currentUid == null) return;

        noticeRepository.listenLatest(currentUid, 10, (snapshots, e) -> {
            if (e != null) {
                error.setValue("Lỗi tải thông báo: " + e.getMessage());
                return;
            }

            if (snapshots != null) {
                List<Notice> noticeList = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    noticeList.add(Notice.from(doc));
                }
                notices.setValue(noticeList);
                
                // Cập nhật lastDocument cho pagination
                if (!snapshots.getDocuments().isEmpty()) {
                    lastDocument = snapshots.getDocuments().get(snapshots.getDocuments().size() - 1);
                }
            }
        });
    }

    /**
     * Dừng lắng nghe thông báo
     */
    public void stopListening() {
        // NoticeCenter sẽ tự quản lý listener
    }

    /**
     * Refresh danh sách thông báo
     */
    public void refresh() {
        if (currentUid == null) return;
        
        isLoading.setValue(true);
        hasMoreData = true;
        lastDocument = null;
        
        noticeRepository.paginate(currentUid, 10, null)
                .addOnSuccessListener(noticeList -> {
                    notices.setValue(noticeList);
                    isLoading.setValue(false);
                    error.setValue(null);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi tải thông báo: " + e.getMessage());
                    isLoading.setValue(false);
                });
    }

    /**
     * Load thêm thông báo (pagination)
     */
    public void loadMore() {
        if (currentUid == null || !hasMoreData || isLoading.getValue() == Boolean.TRUE) {
            return;
        }

        isLoading.setValue(true);
        
        noticeRepository.paginate(currentUid, 10, lastDocument)
                .addOnSuccessListener(newNotices -> {
                    List<Notice> currentNotices = notices.getValue();
                    if (currentNotices == null) {
                        currentNotices = new ArrayList<>();
                    }
                    
                    currentNotices.addAll(newNotices);
                    notices.setValue(currentNotices);
                    
                    if (newNotices.size() < 10) {
                        hasMoreData = false;
                    }
                    
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi tải thêm thông báo: " + e.getMessage());
                    isLoading.setValue(false);
                });
    }

    /**
     * Đánh dấu thông báo đã xem
     */
    public void markSeen(String noticeId) {
        if (currentUid == null) return;
        
        noticeRepository.markSeen(currentUid, noticeId)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật local data
                    List<Notice> currentNotices = notices.getValue();
                    if (currentNotices != null) {
                        for (Notice notice : currentNotices) {
                            if (notice.getId().equals(noticeId)) {
                                notice.setSeen(true);
                                break;
                            }
                        }
                        // Emit a new list instance to trigger ListAdapter diff
                        notices.setValue(new ArrayList<>(currentNotices));
                    }
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi đánh dấu đã xem: " + e.getMessage());
                });
    }

    /**
     * Đánh dấu tất cả thông báo đã xem
     */
    public void markAllSeen() {
        if (currentUid == null) return;
        // Batch mark all currently loaded unseen items as seen in Firestore
        List<Notice> currentNotices = notices.getValue();
        List<String> idsToMark = new ArrayList<>();
        if (currentNotices != null) {
            for (Notice n : currentNotices) {
                if (!n.isSeen()) idsToMark.add(n.getId());
            }
        }

        noticeRepository.markSeenBatch(currentUid, idsToMark)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật local data
                    List<Notice> list = notices.getValue();
                    if (list != null) {
                        for (Notice notice : list) {
                            notice.setSeen(true);
                        }
                        notices.setValue(new ArrayList<>(list));
                    }
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi đánh dấu tất cả đã xem: " + e.getMessage());
                });
    }

    /**
     * Đánh dấu từng thông báo đã xem (ghi DB từng cái, kèm log)
     */
    public void markAllSeenIndividually() {
        if (currentUid == null) return;

        // Always fetch fresh unseen list from DB to avoid UI-sync mismatch
        noticeRepository.getUnseenNoticeIds(currentUid)
                .addOnSuccessListener(ids -> {
                    int unseenCount = ids != null ? ids.size() : 0;
                    android.util.Log.d("NoticeViewModel", "markAllSeenIndividually (DB) unseenCount=" + unseenCount);

                    // Update UI immediately for those IDs
                    List<Notice> currentNotices = notices.getValue();
                    if (currentNotices != null && ids != null) {
                        for (Notice n : currentNotices) {
                            if (ids.contains(n.getId())) n.setSeen(true);
                        }
                        notices.setValue(new ArrayList<>(currentNotices));
                    }

                    // Update DB one-by-one with logs
                    if (ids != null) {
                        for (String id : ids) {
                            final String noticeId = id;
                            noticeRepository.markSeen(currentUid, noticeId)
                                    .addOnSuccessListener(aVoid -> android.util.Log.d("NoticeViewModel", "Marked seen in DB: " + noticeId))
                                    .addOnFailureListener(e -> android.util.Log.e("NoticeViewModel", "Failed to mark seen: " + noticeId, e));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("NoticeViewModel", "Failed to fetch unseen IDs", e);
                });
    }

    /**
     * Xóa tất cả thông báo khỏi subcollection
     */
    public void deleteAllNotifications() {
        if (currentUid == null) return;

        android.util.Log.d("NoticeViewModel", "Deleting all notifications for user: " + currentUid);
        
        // Clear UI immediately
        notices.setValue(new ArrayList<>());
        
        // Delete all notifications from Firestore
        noticeRepository.deleteAllNotifications(currentUid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("NoticeViewModel", "Successfully deleted all notifications");
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("NoticeViewModel", "Failed to delete all notifications", e);
                    error.setValue("Lỗi xóa thông báo: " + e.getMessage());
                });
    }

    // Getters
    public LiveData<List<Notice>> getNotices() {
        return notices;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getUnreadCount() {
        return noticeCenter.getUnreadCount();
    }

    public boolean hasMoreData() {
        return hasMoreData;
    }
}
