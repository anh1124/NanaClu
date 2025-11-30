package com.example.nanaclu.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Notice;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.utils.NoticeCenter;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class NoticeViewModel extends ViewModel {
    private final NoticeRepository noticeRepository;
    private final NoticeCenter noticeCenter;
    private final String currentUid;

    private final MutableLiveData<List<Notice>> _notices = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>();

    public final LiveData<List<Notice>> notices = _notices;
    public final LiveData<Boolean> isLoading = _isLoading;
    public final LiveData<String> error = _error;

    private DocumentSnapshot lastDocument;
    private boolean hasMoreData = true;

    // Constructor injection - BẮT BUỘC
    public NoticeViewModel(NoticeRepository noticeRepository, String currentUid) {
        this.noticeRepository = noticeRepository;
        this.noticeCenter = NoticeCenter.getInstance();
        this.currentUid = currentUid;
    }

    /**
     * Bắt đầu lắng nghe thông báo
     */
    public void startListening() {
        if (currentUid == null) return;
        
        noticeRepository.listenLatest(currentUid, 10, (snapshots, e) -> {
            if (e != null) {
                _error.postValue("Lỗi tải thông báo: " + e.getMessage());
                return;
            }
            if (snapshots != null) {
                List<Notice> list = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    list.add(Notice.from(doc));
                }
                _notices.postValue(list);
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
        // NoticeCenter will manage its own listeners
    }

    /**
     * Refresh danh sách thông báo
     */
    public void refresh() {
        if (currentUid == null) return;
        _isLoading.postValue(true);
        hasMoreData = true;
        lastDocument = null;

        noticeRepository.paginate(currentUid, 10, null)
                .addOnSuccessListener(list -> {
                    _notices.postValue(list);
                    _isLoading.postValue(false);
                    _error.postValue(null);
                })
                .addOnFailureListener(e -> {
                    _error.postValue("Lỗi tải thông báo: " + e.getMessage());
                    _isLoading.postValue(false);
                });
    }

    /**
     * Load thêm thông báo (pagination)
     */
    public void loadMore() {
        if (currentUid == null || !hasMoreData || Boolean.TRUE.equals(_isLoading.getValue())) return;
        _isLoading.postValue(true);

        noticeRepository.paginate(currentUid, 10, lastDocument)
                .addOnSuccessListener(newNotices -> {
                    List<Notice> current = new ArrayList<>(_notices.getValue() != null ? _notices.getValue() : new ArrayList<>());
                    current.addAll(newNotices);
                    _notices.postValue(current);
                    hasMoreData = newNotices.size() >= 10;
                    _isLoading.postValue(false);
                })
                .addOnFailureListener(e -> {
                    _error.postValue("Lỗi tải thêm: " + e.getMessage());
                    _isLoading.postValue(false);
                });
    }

    /**
     * Đánh dấu thông báo đã xem
     */
    public void markSeen(String noticeId) {
        if (currentUid == null || noticeId == null) return;
        
        noticeRepository.markSeen(currentUid, noticeId)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật local data
                    List<Notice> currentNotices = _notices.getValue();
                    if (currentNotices != null) {
                        for (Notice notice : currentNotices) {
                            if (noticeId.equals(notice.getId())) {
                                notice.setSeen(true);
                                _notices.postValue(new ArrayList<>(currentNotices));
                                break;
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> 
                    _error.postValue("Lỗi đánh dấu đã xem: " + e.getMessage())
                );
    }

    /**
     * Đánh dấu tất cả thông báo đã xem
     */
    public void markAllSeen() {
        if (currentUid == null) return;
        
        List<Notice> currentNotices = _notices.getValue();
        if (currentNotices == null || currentNotices.isEmpty()) return;

        List<String> idsToMark = new ArrayList<>();
        for (Notice notice : currentNotices) {
            if (!notice.isSeen()) {
                idsToMark.add(notice.getId());
            }
        }

        if (idsToMark.isEmpty()) return;

        noticeRepository.markSeenBatch(currentUid, idsToMark)
                .addOnSuccessListener(aVoid -> {
                    for (Notice notice : currentNotices) {
                        notice.setSeen(true);
                    }
                    _notices.postValue(new ArrayList<>(currentNotices));
                })
                .addOnFailureListener(e -> 
                    _error.postValue("Lỗi đánh dấu tất cả đã xem: " + e.getMessage())
                );
    }

    /**
     * Đánh dấu từng thông báo đã xem (ghi DB từng cái, kèm log)
     */
    public void markAllSeenIndividually() {
        if (currentUid == null) return;

        noticeRepository.getUnseenNoticeIds(currentUid)
                .addOnSuccessListener(ids -> {
                    if (ids == null || ids.isEmpty()) return;
                    
                    Log.d("NoticeViewModel", "markAllSeenIndividually (DB) unseenCount=" + ids.size());

                    // Update UI immediately for those IDs
                    List<Notice> currentNotices = _notices.getValue();
                    if (currentNotices != null) {
                        boolean updated = false;
                        for (Notice notice : currentNotices) {
                            if (ids.contains(notice.getId())) {
                                notice.setSeen(true);
                                updated = true;
                            }
                        }
                        if (updated) {
                            _notices.postValue(new ArrayList<>(currentNotices));
                        }
                    }

                    // Update DB one-by-one with logs
                    for (String id : ids) {
                        noticeRepository.markSeen(currentUid, id)
                                .addOnSuccessListener(aVoid -> 
                                    Log.d("NoticeViewModel", "Marked seen in DB: " + id)
                                )
                                .addOnFailureListener(e -> 
                                    Log.e("NoticeViewModel", "Failed to mark seen: " + id, e)
                                );
                    }
                })
                .addOnFailureListener(e -> 
                    Log.e("NoticeViewModel", "Failed to fetch unseen IDs", e)
                );
    }

    /**
     * Xóa tất cả thông báo khỏi subcollection
     */
    public void deleteAllNotifications() {
        if (currentUid == null) return;

        Log.d("NoticeViewModel", "Deleting all notifications for user: " + currentUid);
        
        // Clear UI immediately
        _notices.postValue(new ArrayList<>());
        
        // Delete all notifications from Firestore
        noticeRepository.deleteAllNotifications(currentUid)
                .addOnSuccessListener(aVoid -> 
                    Log.d("NoticeViewModel", "Successfully deleted all notifications")
                )
                .addOnFailureListener(e -> {
                    Log.e("NoticeViewModel", "Failed to delete all notifications", e);
                    _error.postValue("Lỗi xóa thông báo: " + e.getMessage());
                });
    }

    /**
     * Kiểm tra xem còn dữ liệu để tải thêm không
     * @return true nếu còn dữ liệu, false nếu đã tải hết
     */
    public boolean hasMoreData() {
        return hasMoreData;
    }
    
    /**
     * Lấy số lượng thông báo chưa đọc
     * @return LiveData chứa số lượng thông báo chưa đọc
     */
    public LiveData<Integer> getUnreadCount() {
        return noticeCenter.getUnreadCount();
    }
}
