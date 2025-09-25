package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;
import com.google.firebase.firestore.FirebaseFirestore;

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
        android.util.Log.d("EventAdapter", "onBindViewHolder called for position: " + position + ", total events: " + events.size());
        Event event = events.get(position);
        android.util.Log.d("EventAdapter", "About to bind event: " + event.title);
        holder.bind(event);
        android.util.Log.d("EventAdapter", "Event binding completed for: " + event.title);
    }
    
    @Override
    public int getItemCount() {
        return events.size();
    }
    
    public void updateEvents(List<Event> newEvents) {
        android.util.Log.d("EventAdapter", "updateEvents called with " + newEvents.size() + " events");
        this.events = newEvents;
        android.util.Log.d("EventAdapter", "About to call notifyDataSetChanged");
        notifyDataSetChanged();
        android.util.Log.d("EventAdapter", "notifyDataSetChanged completed");
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
            
            // Load creator name if missing
            android.util.Log.d("EventAdapter", "Binding event: " + event.title + 
                    ", creatorId: " + event.creatorId + 
                    ", creatorName: " + event.creatorName);
            
            if (event.creatorName != null && !event.creatorName.isEmpty()) {
                android.util.Log.d("EventAdapter", "Using cached creator name: " + event.creatorName);
                tvEventCreator.setText("Tạo bởi: " + event.creatorName);
            } else if (event.creatorId != null && !event.creatorId.isEmpty()) {
                android.util.Log.d("EventAdapter", "Loading creator name for ID: " + event.creatorId);
                tvEventCreator.setText("Tạo bởi: Đang tải...");
                loadCreatorName(event.creatorId);
            } else {
                android.util.Log.w("EventAdapter", "Both creatorId and creatorName are null/empty for event: " + event.title);
                tvEventCreator.setText("Tạo bởi: Unknown");
            }
            
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
        
        private void loadCreatorName(String creatorId) {
            android.util.Log.d("EventAdapter", "Starting to load creator name for ID: " + creatorId);
            
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(creatorId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        android.util.Log.d("EventAdapter", "Firestore response for creatorId " + creatorId + 
                                ", exists: " + documentSnapshot.exists());
                        
                        if (documentSnapshot.exists()) {
                            String creatorName = documentSnapshot.getString("name");
                            android.util.Log.d("EventAdapter", "Retrieved creator name: '" + creatorName + "' for ID: " + creatorId);
                            
                            if (creatorName != null && !creatorName.isEmpty()) {
                                android.util.Log.d("EventAdapter", "Setting creator name: " + creatorName);
                                tvEventCreator.setText("Tạo bởi: " + creatorName);
                                
                                // Update the event object for future use
                                for (Event event : events) {
                                    if (event.creatorId != null && event.creatorId.equals(creatorId)) {
                                        android.util.Log.d("EventAdapter", "Updating event object with creator name: " + creatorName);
                                        event.creatorName = creatorName;
                                        break;
                                    }
                                }
                            } else {
                                android.util.Log.w("EventAdapter", "Creator name is null/empty in document for ID: " + creatorId);
                                tvEventCreator.setText("Tạo bởi: Unknown");
                            }
                        } else {
                            android.util.Log.w("EventAdapter", "Creator document does not exist for ID: " + creatorId);
                            tvEventCreator.setText("Tạo bởi: Unknown");
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("EventAdapter", "Failed to load creator name for ID: " + creatorId, e);
                        tvEventCreator.setText("Tạo bởi: Unknown");
                    });
        }
    }
}
