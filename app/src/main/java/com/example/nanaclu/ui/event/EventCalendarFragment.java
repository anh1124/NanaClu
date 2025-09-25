package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;
import com.example.nanaclu.data.repository.EventRepository;
import com.example.nanaclu.ui.adapter.CalendarAdapter;
import com.example.nanaclu.ui.adapter.CalendarEventAdapter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCalendarFragment extends Fragment {

    private String groupId;
    private EventRepository eventRepository;

    // Views
    private ImageView btnPrevMonth, btnNextMonth;
    private TextView tvCurrentMonth;
    private RecyclerView rvCalendar;
    private LinearLayout layoutSelectedDateEvents;
    private TextView tvSelectedDate;
    private RecyclerView rvSelectedDateEvents;

    // Adapters
    private CalendarAdapter calendarAdapter;
    private CalendarEventAdapter selectedDateEventAdapter;

    // Data
    private Calendar currentCalendar;
    private List<Event> allEvents;
    private Map<String, List<Event>> eventsMap;

    public static EventCalendarFragment newInstance(String groupId) {
        EventCalendarFragment fragment = new EventCalendarFragment();
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
        currentCalendar = Calendar.getInstance();
        allEvents = new ArrayList<>();
        eventsMap = new HashMap<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupCalendar();
        setupSelectedDateEvents();
        loadEvents();
    }

    private void initViews(View view) {
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth);
        btnNextMonth = view.findViewById(R.id.btnNextMonth);
        tvCurrentMonth = view.findViewById(R.id.tvCurrentMonth);
        rvCalendar = view.findViewById(R.id.rvCalendar);
        layoutSelectedDateEvents = view.findViewById(R.id.layoutSelectedDateEvents);
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate);
        rvSelectedDateEvents = view.findViewById(R.id.rvSelectedDateEvents);

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            calendarAdapter.setSelectedPosition(-1); // Reset selection when changing month
            layoutSelectedDateEvents.setVisibility(View.GONE); // Hide selected date events
            updateCalendarDisplay();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            calendarAdapter.setSelectedPosition(-1); // Reset selection when changing month
            layoutSelectedDateEvents.setVisibility(View.GONE); // Hide selected date events
            updateCalendarDisplay();
        });
    }

    private void setupCalendar() {
        calendarAdapter = new CalendarAdapter((day, events) -> {
            // Show selected date events
            showSelectedDateEvents(day, events);
        });

        rvCalendar.setLayoutManager(new GridLayoutManager(getContext(), 7));
        rvCalendar.setAdapter(calendarAdapter);

        updateCalendarDisplay();
    }

    private void setupSelectedDateEvents() {
        selectedDateEventAdapter = new CalendarEventAdapter(event -> {
            // Open event detail
            Intent intent = new Intent(getContext(), EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_GROUP_ID, groupId);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.eventId);
            startActivity(intent);
        });

        rvSelectedDateEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSelectedDateEvents.setAdapter(selectedDateEventAdapter);
    }

    private void loadEvents() {
        if (groupId == null) return;

        eventRepository.getEvents(groupId,
                events -> {
                    allEvents = events;
                    organizeEventsByDate();
                    updateCalendarDisplay();
                },
                error -> {
                    // Handle error
                    android.widget.Toast.makeText(getContext(), "Lỗi load events: " + error.getMessage(),
                            android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    private void organizeEventsByDate() {
        eventsMap.clear();

        for (Event event : allEvents) {
            Calendar eventCal = Calendar.getInstance();
            eventCal.setTimeInMillis(event.startTime);

            String dateKey = String.format("%04d-%02d-%02d",
                    eventCal.get(Calendar.YEAR),
                    eventCal.get(Calendar.MONTH) + 1,
                    eventCal.get(Calendar.DAY_OF_MONTH));

            eventsMap.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(event);
        }
    }

    private void updateCalendarDisplay() {
        // Update month title
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", new Locale("vi", "VN"));
        tvCurrentMonth.setText(monthFormat.format(currentCalendar.getTime()));

        // Generate calendar days
        List<CalendarAdapter.CalendarDay> calendarDays = generateCalendarDays();
        calendarAdapter.updateCalendar(calendarDays, eventsMap);
    }

    private List<CalendarAdapter.CalendarDay> generateCalendarDays() {
        List<CalendarAdapter.CalendarDay> days = new ArrayList<>();

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Add days from previous month
        Calendar prevMonth = (Calendar) cal.clone();
        prevMonth.add(Calendar.MONTH, -1);
        int daysInPrevMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = firstDayOfWeek - 2; i >= 0; i--) {
            days.add(new CalendarAdapter.CalendarDay(
                    prevMonth.get(Calendar.YEAR),
                    prevMonth.get(Calendar.MONTH),
                    daysInPrevMonth - i,
                    false
            ));
        }

        // Add days of current month
        for (int day = 1; day <= daysInMonth; day++) {
            days.add(new CalendarAdapter.CalendarDay(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    day,
                    true
            ));
        }

        // Add days from next month to fill the grid
        Calendar nextMonth = (Calendar) cal.clone();
        nextMonth.add(Calendar.MONTH, 1);
        int remainingDays = 42 - days.size(); // 6 weeks * 7 days

        for (int day = 1; day <= remainingDays; day++) {
            days.add(new CalendarAdapter.CalendarDay(
                    nextMonth.get(Calendar.YEAR),
                    nextMonth.get(Calendar.MONTH),
                    day,
                    false
            ));
        }

        return days;
    }

    private void showSelectedDateEvents(CalendarAdapter.CalendarDay day, List<Event> events) {
        if (events.isEmpty()) {
            layoutSelectedDateEvents.setVisibility(View.GONE);
            return;
        }

        // Format selected date
        Calendar selectedCal = day.getCalendar();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", new Locale("vi", "VN"));
        tvSelectedDate.setText(dateFormat.format(selectedCal.getTime()));

        // Show events
        selectedDateEventAdapter.updateEvents(events);
        layoutSelectedDateEvents.setVisibility(View.VISIBLE);
    }

    public void refreshEvents() {
        loadEvents();
    }
}
