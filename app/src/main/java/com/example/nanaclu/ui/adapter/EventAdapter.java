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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    
    private List<Event> events;
    private OnEventClickListener listener;
    private OnEventLongClickListener longClickListener;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public interface OnEventLongClickListener {
        void onEventLongClick(Event event);
    }
    
    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    public void setOnEventLongClickListener(OnEventLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }
    
    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
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
    
    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }
    
    class EventViewHolder extends RecyclerView.ViewHolder {
        private TextView tvEventDay, tvEventMonth, tvEventTitle, tvEventTime;
        private TextView tvEventLocation, tvEventStatus, tvEventDescription;
        private TextView tvGoingCount, tvMaybeCount, tvNotGoingCount, tvEventCreator;
        
        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventDay = itemView.findViewById(R.id.tvEventDay);
            tvEventMonth = itemView.findViewById(R.id.tvEventMonth);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvEventTime = itemView.findViewById(R.id.tvEventTime);
            tvEventLocation = itemView.findViewById(R.id.tvEventLocation);
            tvEventStatus = itemView.findViewById(R.id.tvEventStatus);
            tvEventDescription = itemView.findViewById(R.id.tvEventDescription);
            tvGoingCount = itemView.findViewById(R.id.tvGoingCount);
            tvMaybeCount = itemView.findViewById(R.id.tvMaybeCount);
            tvNotGoingCount = itemView.findViewById(R.id.tvNotGoingCount);
            tvEventCreator = itemView.findViewById(R.id.tvEventCreator);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onEventClick(events.get(position));
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        longClickListener.onEventLongClick(events.get(position));
                        return true;
                    }
                }
                return false;
            });
        }
        
        public void bind(Event event) {
            // Date formatting
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(event.startTime);
            
            tvEventDay.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
            tvEventMonth.setText(new SimpleDateFormat("MMM", new Locale("vi", "VN"))
                    .format(calendar.getTime()).toUpperCase());
            
            // Basic info
            tvEventTitle.setText(event.title != null ? event.title : "");
            tvEventDescription.setText(event.description != null ? event.description : "");
            tvEventCreator.setText("Tạo bởi: " + (event.creatorName != null ? event.creatorName : "Unknown"));
            
            // Time formatting
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String startTime = timeFormat.format(new Date(event.startTime));
            String endTime = timeFormat.format(new Date(event.endTime));
            tvEventTime.setText(startTime + " - " + endTime);
            
            // Location
            if (event.locationType != null && event.locationData != null) {
                switch (event.locationType) {
                    case "location":
                        tvEventLocation.setText("📍 " + event.locationData);
                        tvEventLocation.setVisibility(View.VISIBLE);
                        break;
                    case "link":
                        tvEventLocation.setText("🔗 " + event.locationData);
                        tvEventLocation.setVisibility(View.VISIBLE);
                        break;
                    default:
                        tvEventLocation.setVisibility(View.GONE);
                        break;
                }
            } else {
                tvEventLocation.setVisibility(View.GONE);
            }
            
            // Status
            long now = System.currentTimeMillis();
            if (event.status.equals("cancelled")) {
                tvEventStatus.setText("Đã hủy");
                tvEventStatus.setBackgroundResource(R.drawable.rounded_red);
            } else if (event.endTime < now) {
                tvEventStatus.setText("Đã kết thúc");
                tvEventStatus.setBackgroundResource(R.drawable.rounded_gray);
            } else if (event.startTime <= now && event.endTime >= now) {
                tvEventStatus.setText("Đang diễn ra");
                tvEventStatus.setBackgroundResource(R.drawable.rounded_orange);
            } else {
                tvEventStatus.setText("Sắp diễn ra");
                tvEventStatus.setBackgroundResource(R.drawable.rounded_green);
            }
            
            // RSVP counts
            tvGoingCount.setText(String.valueOf(event.goingCount));
            tvMaybeCount.setText(String.valueOf(event.maybeCount));
            tvNotGoingCount.setText(String.valueOf(event.notGoingCount));
        }
    }
}
