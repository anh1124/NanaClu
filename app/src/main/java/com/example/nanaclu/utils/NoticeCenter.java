package com.example.nanaclu.utils;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.nanaclu.data.model.Notice;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.ui.chat.ChatRoomActivity;
import com.example.nanaclu.ui.event.EventDetailActivity;
import com.example.nanaclu.ui.group.GroupDetailActivity;
import com.example.nanaclu.ui.notifications.NotificationsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service điều phối thông báo realtime, banner và badge
 */
public class NoticeCenter {
    private static final String TAG = "NoticeCenter";
    private static NoticeCenter instance;
    
    private NoticeRepository noticeRepository;
    private ListenerRegistration listenerRegistration;
    private MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    private String currentUid;
    private Application application;
    private long lastBannerTime = 0;
    private static final long BANNER_THROTTLE_MS = 3000; // 3 giây

    private NoticeCenter() {
        noticeRepository = new NoticeRepository(FirebaseFirestore.getInstance());
    }

    public static NoticeCenter getInstance() {
        if (instance == null) {
            instance = new NoticeCenter();
        }
        return instance;
    }

    /**
     * Bắt đầu lắng nghe thông báo cho user
     */
    public void start(String uid, Application app) {
        if (currentUid != null && currentUid.equals(uid)) {
            return; // Đã đang lắng nghe cho user này
        }

        stop(); // Dừng listener cũ nếu có
        currentUid = uid;
        application = app;

        listenerRegistration = noticeRepository.listenLatest(uid, 20, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, com.google.firebase.firestore.FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Error listening to notices", e);
                    return;
                }

                if (snapshots == null) return;

                // Cập nhật unread count
                updateUnreadCount(snapshots);

                // Xử lý thông báo mới
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        Notice notice = Notice.from(dc.getDocument());
                        handleNewNotice(notice);
                    }
                }
            }
        });

        Log.d(TAG, "Started listening for notices for user: " + uid);
    }

    /**
     * Dừng lắng nghe thông báo
     */
    public void stop() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        currentUid = null;
        application = null;
        Log.d(TAG, "Stopped listening for notices");
    }

    /**
     * Lấy LiveData cho unread count
     */
    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    /**
     * Đánh dấu tất cả thông báo đã xem
     */
    public void markAllSeen() {
        if (currentUid != null) {
            noticeRepository.markAllSeen(currentUid)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Marked all notices as seen");
                        unreadCount.setValue(0);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark all notices as seen", e));
        }
    }

    /**
     * Cập nhật unread count từ snapshot
     */
    private void updateUnreadCount(QuerySnapshot snapshots) {
        AtomicInteger count = new AtomicInteger(0);
        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
            Boolean seen = doc.getBoolean("seen");
            if (seen == null || !seen) {
                count.incrementAndGet();
            }
        }
        unreadCount.setValue(count.get());
    }

    /**
     * Xử lý thông báo mới - hiển thị banner nếu cần
     */
    private void handleNewNotice(Notice notice) {
        // Throttle banner để tránh spam
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBannerTime < BANNER_THROTTLE_MS) {
            return;
        }

        // Không hiển thị banner cho tin nhắn nếu đang ở chat room đó
        if ("message".equals(notice.getType()) && 
            ActiveScreenTracker.isInChatRoom(notice.getObjectId())) {
            return;
        }

        // Hiển thị banner
        showBanner(notice);
        lastBannerTime = currentTime;
    }

    /**
     * Hiển thị banner thông báo
     */
    private void showBanner(Notice notice) {
        if (application == null) return;

        Context context = application.getApplicationContext();
        Toast.makeText(context, notice.getTitle() + ": " + notice.getMessage(), Toast.LENGTH_LONG).show();
        
        // TODO: Thay thế Toast bằng Snackbar hoặc custom banner view
        // Có thể thêm click listener để mở màn hình đích
    }

    /**
     * Mở màn hình thông báo
     */
    public void openNotifications(Context context) {
        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Navigate đến màn hình đích từ notice
     */
    public void navigateToTarget(Context context, Notice notice) {
        android.util.Log.d(TAG, "=== START navigateToTarget ===");
        android.util.Log.d(TAG, "Notice type: " + notice.getType());
        android.util.Log.d(TAG, "Notice objectType: " + notice.getObjectType());
        android.util.Log.d(TAG, "Notice objectId: " + notice.getObjectId());
        android.util.Log.d(TAG, "Notice groupId: " + notice.getGroupId());
        
        Intent intent = null;

        switch (notice.getType()) {
            case "event_created":
                // Mở EventDetailActivity để xem event cụ thể
                intent = new Intent(context, EventDetailActivity.class);
                intent.putExtra(com.example.nanaclu.ui.event.EventDetailActivity.EXTRA_GROUP_ID, notice.getGroupId());
                // objectId chứa eventId cho event notices
                String resolvedEventId = notice.getEventId() != null ? notice.getEventId() : notice.getObjectId();
                intent.putExtra(com.example.nanaclu.ui.event.EventDetailActivity.EXTRA_EVENT_ID, resolvedEventId);
                android.util.Log.d(TAG, "Opening EventDetailActivity with groupId=" + notice.getGroupId() + ", eventId=" + resolvedEventId);
                break;

            case "message":
                // Mở ChatRoomActivity
                intent = new Intent(context, ChatRoomActivity.class);
                intent.putExtra("chatId", notice.getObjectId());
                intent.putExtra("chatType", notice.getGroupId() != null ? "group" : "private");
                if (notice.getGroupId() != null) {
                    intent.putExtra("groupId", notice.getGroupId());
                }
                intent.putExtra("chatTitle", notice.getTitle());
                break;

            case "like":
            case "comment":
            case "post_approved":
            case "new_post":
                // Mở PostDetailActivity để xem post cụ thể
                intent = new Intent(context, com.example.nanaclu.ui.post.PostDetailActivity.class);
                intent.putExtra(com.example.nanaclu.ui.post.PostDetailActivity.EXTRA_GROUP_ID, notice.getGroupId());
                intent.putExtra(com.example.nanaclu.ui.post.PostDetailActivity.EXTRA_POST_ID, notice.getObjectId());
                android.util.Log.d(TAG, "Opening PostDetailActivity with groupId=" + notice.getGroupId() + ", postId=" + notice.getObjectId());
                break;

            case "friend_request":
            case "friend_accepted":
                // Mở FriendsActivity để xem danh sách bạn bè
                intent = new Intent(context, com.example.nanaclu.ui.friends.FriendsActivity.class);
                break;
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * Đánh dấu notice đã xem
     */
    public void markNoticeSeen(String noticeId) {
        if (currentUid != null) {
            noticeRepository.markSeen(currentUid, noticeId)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Marked notice as seen: " + noticeId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark notice as seen", e));
        }
    }
}
