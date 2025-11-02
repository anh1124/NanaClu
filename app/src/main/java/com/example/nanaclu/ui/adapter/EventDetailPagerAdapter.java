package com.example.nanaclu.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.nanaclu.data.model.EventRSVP;
import com.example.nanaclu.ui.event.EventDiscussionFragment;
import com.example.nanaclu.ui.event.RSVPListFragment;

/**
 * Adapter cho ViewPager2 hiển thị các tab chi tiết sự kiện
 * 
 * Chức năng chính:
 * - Quản lý các tab thông tin sự kiện: tham dự, bình luận, chi tiết
 * - Hiển thị danh sách người tham dự theo trạng thái (đồng ý, có thể, từ chối)
 * - Tích hợp với các Fragment để hiển thị nội dung tương ứng
 * - Hỗ trợ cuộn mượt giữa các tab
 */
public class EventDetailPagerAdapter extends FragmentStateAdapter {
    
    private String groupId;
    private String eventId;
    
    public EventDetailPagerAdapter(@NonNull FragmentActivity fragmentActivity, String groupId, String eventId) {
        super(fragmentActivity);
        this.groupId = groupId;
        this.eventId = eventId;
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return RSVPListFragment.newInstance(groupId, eventId, EventRSVP.Status.ATTENDING);
            case 1:
                return RSVPListFragment.newInstance(groupId, eventId, EventRSVP.Status.MAYBE);
            case 2:
                return RSVPListFragment.newInstance(groupId, eventId, EventRSVP.Status.NOT_ATTENDING);
            case 3:
                return EventDiscussionFragment.newInstance(groupId, eventId);
            default:
                return RSVPListFragment.newInstance(groupId, eventId, EventRSVP.Status.ATTENDING);
        }
    }
    
    @Override
    public int getItemCount() {
        return 4; // Going, Maybe, Not Going, Discussion
    }
    
    public String getTabTitle(int position) {
        switch (position) {
            case 0:
                return "Tham gia";
            case 1:
                return "Có thể";
            case 2:
                return "Không";
            case 3:
                return "Thảo luận";
            default:
                return "";
        }
    }
}
