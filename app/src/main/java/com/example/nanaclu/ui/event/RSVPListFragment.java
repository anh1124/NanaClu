package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.EventRSVP;
import com.example.nanaclu.data.repository.EventRepository;
import com.example.nanaclu.ui.adapter.RSVPUserAdapter;
import com.example.nanaclu.ui.profile.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class RSVPListFragment extends Fragment {
    
    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_EVENT_ID = "event_id";
    private static final String ARG_STATUS = "status";
    
    private String groupId;
    private String eventId;
    private EventRSVP.Status status;
    
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private RSVPUserAdapter adapter;
    private EventRepository eventRepository;
    
    public static RSVPListFragment newInstance(String groupId, String eventId, EventRSVP.Status status) {
        RSVPListFragment fragment = new RSVPListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putString(ARG_EVENT_ID, eventId);
        args.putString(ARG_STATUS, status.getValue());
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString(ARG_GROUP_ID);
            eventId = getArguments().getString(ARG_EVENT_ID);
            String statusValue = getArguments().getString(ARG_STATUS);
            status = EventRSVP.Status.fromValue(statusValue);
        }
        eventRepository = new EventRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rsvp_list, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new RSVPUserAdapter(new ArrayList<>(), this::openUserProfile);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadRSVPUsers);

        loadRSVPUsers();

        return view;
    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void loadRSVPUsers() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        eventRepository.getEventRSVPs(groupId, eventId, status,
                rsvps -> {
                    adapter.updateRSVPs(rsvps);
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                },
                error -> {
                    android.util.Log.e("RSVPListFragment", "Error loading attendees", error);
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                }
        );
    }
    
    public void refreshData() {
        android.util.Log.d("RSVPListFragment", "Refreshing RSVP data for status: " + status.getValue());
        loadRSVPUsers();
    }
}
