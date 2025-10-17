package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;
import com.example.nanaclu.data.repository.EventRepository;
import com.example.nanaclu.ui.adapter.EventAdapter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class EventListFragment extends Fragment {
    
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvEvents;
    private LinearLayout layoutEmpty;
    private ProgressBar progressBar;
    private EventAdapter adapter;
    private EventRepository eventRepository;
    private String groupId;
    
    public static EventListFragment newInstance(String groupId) {
        EventListFragment fragment = new EventListFragment();
        Bundle args = new Bundle();
        args.putString("groupId", groupId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
        }
        eventRepository = new EventRepository(FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_list, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        rvEvents = view.findViewById(R.id.rvEvents);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        progressBar = view.findViewById(R.id.progressBar);
        
        setupRecyclerView();
        setupSwipeRefresh();
        loadEvents();
    }
    
    private void setupRecyclerView() {
        adapter = new EventAdapter(new ArrayList<>(), event -> {
            // Open event detail
            Intent intent = new Intent(getContext(), EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_GROUP_ID, groupId);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.eventId);
            startActivity(intent);
        });

        // Set long click listener for delete
        adapter.setOnEventLongClickListener(event -> {
            showDeleteEventDialog(event);
        });
        
        rvEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        rvEvents.setAdapter(adapter);
    }
    
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );
        
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadEvents();
        });
    }
    
    private void loadEvents() {
        if (groupId == null) return;

        showLoading(true);
        // Switch back to normal method since debug shows events are loading
        android.util.Log.d("EventListFragment", "Starting to load events for groupId: " + groupId);
        eventRepository.getEvents(groupId,
                events -> {
                    android.util.Log.d("EventListFragment", "Success callback received with " + events.size() + " events");
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false); // Stop refresh animation
                    android.util.Log.d("EventListFragment", "Loaded " + events.size() + " events for groupId: " + groupId);
                    for (Event event : events) {
                        android.util.Log.d("EventListFragment", "Event: " + event.title + 
                                ", status: " + event.status + 
                                ", creatorId: " + event.creatorId + 
                                ", creatorName: " + event.creatorName);
                    }

                    if (events.isEmpty()) {
                        android.util.Log.d("EventListFragment", "Events list is empty, showing empty state");
                        showEmpty(true);
                    } else {
                        android.util.Log.d("EventListFragment", "Updating adapter with " + events.size() + " events");
                        showEmpty(false);
                        adapter.updateEvents(events);
                        android.util.Log.d("EventListFragment", "Adapter updated successfully");
                    }
                },
                error -> {
                    android.util.Log.e("EventListFragment", "Error callback received", error);
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false); // Stop refresh animation
                    showEmpty(true);
                    android.util.Log.e("EventListFragment", "Error loading events", error);
                    android.widget.Toast.makeText(getContext(), "Lỗi load events: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        rvEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }
    
    private void showEmpty(boolean show) {
        layoutEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        rvEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }
    
    public void refreshEvents() {
        loadEvents();
    }

    private void showDeleteEventDialog(Event event) {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            android.widget.Toast.makeText(getContext(), "Bạn cần đăng nhập để thực hiện hành động này", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if user can delete this event
        eventRepository.canUserDeleteEvent(groupId, event.eventId, currentUserId,
                canDelete -> {
                    if (canDelete) {
                        // Show confirmation dialog
                        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                                .setTitle("Xóa sự kiện")
                                .setMessage("Bạn có chắc chắn muốn xóa sự kiện \"" + event.title + "\"?")
                                .setPositiveButton("Xóa", (dialog, which) -> {
                                    deleteEvent(event);
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                    } else {
                        android.widget.Toast.makeText(getContext(), "Bạn không có quyền xóa sự kiện này", android.widget.Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    android.widget.Toast.makeText(getContext(), "Lỗi kiểm tra quyền: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void deleteEvent(Event event) {
        eventRepository.deleteEvent(groupId, event.eventId,
                aVoid -> {
                    android.widget.Toast.makeText(getContext(), "Đã xóa sự kiện", android.widget.Toast.LENGTH_SHORT).show();
                    loadEvents(); // Reload events
                },
                error -> {
                    android.widget.Toast.makeText(getContext(), "Lỗi xóa sự kiện: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
        );
    }
}
