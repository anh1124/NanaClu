package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho RecyclerView hiển thị danh sách sự kiện trong lịch
 * 
 * Chức năng chính:
 * - Hiển thị danh sách sự kiện theo ngày đã chọn
 * - Hiển thị thông tin chi tiết sự kiện: thời gian, tiêu đề, địa điểm
 * - Hỗ trợ click vào sự kiện để xem chi tiết
 * - Tự động cập nhật khi có sự kiện mới hoặc thay đổi
 */
public class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {
    
    private List<Event> events;
    private OnEventClickListener listener;
    
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }
    
    public CalendarEventAdapter(OnEventClickListener listener) {
        this.events = new ArrayList<>();
        this.listener = listener;
    }
    
    public void updateEvents(List<Event> events) {
        this.events = events;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_event, parent, false);
        return new EventViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event);
    }
    
    @Override
    public int getItemCount() {
        return events.size();
    }
    
    class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventTime;
        TextView tvEventTitle;
        TextView tvEventLocation;
        TextView tvRSVPCount;
        
        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventTime = itemView.findViewById(R.id.tvEventTime);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvEventLocation = itemView.findViewById(R.id.tvEventLocation);
            tvRSVPCount = itemView.findViewById(R.id.tvRSVPCount);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEventClick(events.get(position));
                }
            });
        }
        
        public void bind(Event event) {
            // Format time
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(event.startTime);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvEventTime.setText(timeFormat.format(calendar.getTime()));
            
            // Event title
            tvEventTitle.setText(event.title);
            
            // Event location
            if (event.location != null && !event.location.isEmpty()) {
                tvEventLocation.setText(event.location);
                tvEventLocation.setVisibility(View.VISIBLE);
            } else {
                tvEventLocation.setVisibility(View.GONE);
            }
            
            // RSVP count
            int totalRSVP = event.goingCount + event.maybeCount;
            if (totalRSVP > 0) {
                tvRSVPCount.setText(totalRSVP + " người");
                tvRSVPCount.setVisibility(View.VISIBLE);
            } else {
                tvRSVPCount.setVisibility(View.GONE);
            }
        }
    }
}
