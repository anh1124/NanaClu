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
                        notices.setValue(currentNotices);
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
        
        noticeRepository.markAllSeen(currentUid)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật local data
                    List<Notice> currentNotices = notices.getValue();
                    if (currentNotices != null) {
                        for (Notice notice : currentNotices) {
                            notice.setSeen(true);
                        }
                        notices.setValue(currentNotices);
                    }
                })
                .addOnFailureListener(e -> {
                    error.setValue("Lỗi đánh dấu tất cả đã xem: " + e.getMessage());
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
