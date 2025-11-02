package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter cho RecyclerView hiển thị lịch tháng
 * 
 * Chức năng chính:
 * - Hiển thị các ngày trong tháng dưới dạng lưới
 * - Đánh dấu các ngày có sự kiện
 * - Hỗ trợ chọn ngày và xem sự kiện
 * - Tự động cập nhật khi thay đổi tháng/năm
 */
public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {
    
    private List<CalendarDay> calendarDays;
    private Map<String, List<Event>> eventsMap; // dateKey -> events
    private OnDayClickListener listener;
    private int selectedPosition = -1;
    private int todayPosition = -1;
    
    public interface OnDayClickListener {
        void onDayClick(CalendarDay day, List<Event> events);
    }
    
    public CalendarAdapter(OnDayClickListener listener) {
        this.listener = listener;
        this.calendarDays = new ArrayList<>();
        this.eventsMap = new HashMap<>();
    }
    
    public void updateCalendar(List<CalendarDay> days, Map<String, List<Event>> eventsMap) {
        this.calendarDays = days;
        this.eventsMap = eventsMap;

        // Reset today position
        todayPosition = -1;

        // Find today position only if today is in current month view
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < days.size(); i++) {
            CalendarDay day = days.get(i);
            if (day.isToday(today) && day.isCurrentMonth) {
                todayPosition = i;
                break;
            }
        }

        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new CalendarViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        CalendarDay day = calendarDays.get(position);
        holder.bind(day, position);
    }
    
    @Override
    public int getItemCount() {
        return calendarDays.size();
    }
    
    public void setSelectedPosition(int position) {
        int oldSelected = selectedPosition;
        selectedPosition = position;
        
        if (oldSelected != -1) {
            notifyItemChanged(oldSelected);
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }
    }
    
    class CalendarViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayNumber;
        TextView tvEventCount;
        View viewTodayIndicator;
        View viewSelectedIndicator;
        
        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            tvEventCount = itemView.findViewById(R.id.tvEventCount);
            viewTodayIndicator = itemView.findViewById(R.id.viewTodayIndicator);
            viewSelectedIndicator = itemView.findViewById(R.id.viewSelectedIndicator);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    CalendarDay day = calendarDays.get(position);
                    if (day.isCurrentMonth) {
                        setSelectedPosition(position);
                        String dateKey = day.getDateKey();
                        List<Event> events = eventsMap.getOrDefault(dateKey, new ArrayList<>());
                        listener.onDayClick(day, events);
                    }
                }
            });
        }
        
        public void bind(CalendarDay day, int position) {
            tvDayNumber.setText(String.valueOf(day.dayOfMonth));

            // Show today indicator
            viewTodayIndicator.setVisibility(position == todayPosition ? View.VISIBLE : View.GONE);

            // Show selected indicator
            boolean isSelected = position == selectedPosition;
            viewSelectedIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // Set text color based on month and selection
            if (day.isCurrentMonth) {
                if (isSelected) {
                    // White text when selected
                    tvDayNumber.setTextColor(itemView.getContext().getColor(android.R.color.white));
                } else {
                    // Normal text color
                    tvDayNumber.setTextColor(itemView.getContext().getColor(R.color.text_primary));
                }
                itemView.setAlpha(1.0f);
            } else {
                tvDayNumber.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
                itemView.setAlpha(0.3f);
            }

            // Show event count
            String dateKey = day.getDateKey();
            List<Event> events = eventsMap.getOrDefault(dateKey, new ArrayList<>());
            if (events.size() > 0 && day.isCurrentMonth) {
                tvEventCount.setVisibility(View.VISIBLE);
                tvEventCount.setText(String.valueOf(events.size()));
            } else {
                tvEventCount.setVisibility(View.GONE);
            }
        }
    }
    
    public static class CalendarDay {
        public int year;
        public int month; // 0-based
        public int dayOfMonth;
        public boolean isCurrentMonth;
        
        public CalendarDay(int year, int month, int dayOfMonth, boolean isCurrentMonth) {
            this.year = year;
            this.month = month;
            this.dayOfMonth = dayOfMonth;
            this.isCurrentMonth = isCurrentMonth;
        }
        
        public String getDateKey() {
            return String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
        }
        
        public boolean isToday(Calendar today) {
            return year == today.get(Calendar.YEAR) &&
                   month == today.get(Calendar.MONTH) &&
                   dayOfMonth == today.get(Calendar.DAY_OF_MONTH);
        }
        
        public Calendar getCalendar() {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth);
            return cal;
        }
    }
}
