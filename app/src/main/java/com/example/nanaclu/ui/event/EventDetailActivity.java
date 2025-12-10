package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;
import com.example.nanaclu.data.model.EventRSVP;
import com.example.nanaclu.data.repository.EventRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import androidx.viewpager2.widget.ViewPager2;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.example.nanaclu.ui.adapter.EventDetailPagerAdapter;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_GROUP_ID = "group_id";

    private ImageView ivEventImage;
    private TextView tvEventTitle, tvEventDescription, tvEventLocation, tvEventDateTime;
    private TextView tvGoingCount, tvMaybeCount, tvNotGoingCount;
    private Button btnGoing, btnMaybe, btnNotGoing;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private EventDetailPagerAdapter pagerAdapter;
    private CollapsingToolbarLayout collapsingToolbar;
    private SwipeRefreshLayout swipeRefresh;

    private EventRepository eventRepository;
    private String eventId, groupId;
    private Event currentEvent;
    private EventRSVP.Status currentUserStatus = EventRSVP.Status.NOT_RESPONDED;

    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy 'lúc' HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail_enhanced);

        // Get data from intent
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        android.util.Log.d("EventDetailActivity", "onCreate with eventId=" + eventId + ", groupId=" + groupId);
        
        if (eventId == null || groupId == null) {
            android.util.Log.e("EventDetailActivity", "Missing required data: eventId=" + eventId + ", groupId=" + groupId);
            Toast.makeText(this, "Lỗi: Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupEventHandlers();

        eventRepository = new EventRepository(FirebaseFirestore.getInstance());

        loadEventDetails();
        loadUserRSVPStatus();
    }

    private void initViews() {
        ivEventImage = findViewById(R.id.ivEventImage);
        tvEventTitle = findViewById(R.id.tvEventTitle);
        tvEventDescription = findViewById(R.id.tvEventDescription);
        tvEventLocation = findViewById(R.id.tvEventLocation);
        tvEventDateTime = findViewById(R.id.tvEventDateTime);

        tvGoingCount = findViewById(R.id.tvGoingCount);
        tvMaybeCount = findViewById(R.id.tvMaybeCount);
        tvNotGoingCount = findViewById(R.id.tvNotGoingCount);

        btnGoing = findViewById(R.id.btnGoing);
        btnMaybe = findViewById(R.id.btnMaybe);
        btnNotGoing = findViewById(R.id.btnNotGoing);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        setupTabs();
        setupSwipeRefresh();
    }

    private void setupTabs() {
        pagerAdapter = new EventDetailPagerAdapter(this, groupId, eventId);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(pagerAdapter.getTabTitle(position));
        }).attach();

        // Add click listeners to tabs for quick refresh
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                final int position = i;
                tab.view.setOnClickListener(v -> {
                    if (tabLayout.getSelectedTabPosition() == position) {
                        // If clicking on already selected tab, refresh that fragment
                        refreshCurrentFragment(position);
                    }
                });
            }
        }
    }

    private void refreshCurrentFragment(int position) {
        android.util.Log.d("EventDetailActivity", "Refreshing fragment at position: " + position);

        // Get the current fragment
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + position);

        if (fragment instanceof RSVPListFragment) {
            ((RSVPListFragment) fragment).refreshData();
        } else if (fragment instanceof EventDiscussionFragment) {
            ((EventDiscussionFragment) fragment).refreshData();
        }

        // Also refresh RSVP counts
        if (position < 3) { // Only for RSVP tabs, not discussion
            loadRSVPCounts();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Apply theme color to toolbar
        int toolbarColor = com.example.nanaclu.utils.ThemeUtils.getThemeColor(this);
        toolbar.setBackgroundColor(toolbarColor);
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết sự kiện");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_event_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_edit) {
            // TODO: Open edit event activity
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            showDeleteConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupEventHandlers() {
        btnGoing.setOnClickListener(v -> updateRSVP(EventRSVP.Status.ATTENDING));
        btnMaybe.setOnClickListener(v -> updateRSVP(EventRSVP.Status.MAYBE));
        btnNotGoing.setOnClickListener(v -> updateRSVP(EventRSVP.Status.NOT_ATTENDING));
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            loadEventDetails();
            loadRSVPCounts();
            loadUserRSVPStatus();
        });
    }

    private void loadEventDetails() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        eventRepository.getEvent(groupId, eventId,
                new EventRepository.OnSuccessCallback<Event>() {
                    @Override
                    public void onSuccess(Event event) {
                        currentEvent = event;
                        displayEventDetails(event);
                        loadRSVPCounts();
                        if (swipeRefresh != null) {
                            swipeRefresh.setRefreshing(false);
                        }
                    }
                },
                new EventRepository.OnErrorCallback() {
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(EventDetailActivity.this, "Lỗi tải sự kiện: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (swipeRefresh != null) {
                            swipeRefresh.setRefreshing(false);
                        }
                        finish();
                    }
                }
        );
    }

    private void displayEventDetails(Event event) {
        tvEventTitle.setText(event.title);
        tvEventDescription.setText(event.description);
        tvEventLocation.setText(event.location);

        // Set title in collapsing toolbar
        if (collapsingToolbar != null) {
            collapsingToolbar.setTitle(event.title);
        }

        // Format date time
        String startTime = dateTimeFormat.format(event.startTime);
        String endTime = dateTimeFormat.format(event.endTime);
        tvEventDateTime.setText(String.format("Từ %s\nĐến %s", startTime, endTime));

        // Load event image
        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(event.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(ivEventImage);
        } else {
            ivEventImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        // Load creator info if missing
        if (event.creatorName == null && event.creatorId != null) {
            loadCreatorInfo(event.creatorId);
        } else if (event.creatorName == null) {
            // If both creatorId and creatorName are null, show "Unknown"
            getSupportActionBar().setSubtitle("Tạo bởi: Unknown");
        } else {
            getSupportActionBar().setSubtitle("Tạo bởi: " + event.creatorName);
        }
    }

    private void loadCreatorInfo(String creatorId) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(creatorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String creatorName = documentSnapshot.getString("displayName");
                        if (creatorName != null && !creatorName.isEmpty()) {
                            getSupportActionBar().setSubtitle("Tạo bởi: " + creatorName);
                            // Update the event object
                            if (currentEvent != null) {
                                currentEvent.creatorName = creatorName;
                            }
                        } else {
                            getSupportActionBar().setSubtitle("Tạo bởi: Unknown");
                        }
                    } else {
                        getSupportActionBar().setSubtitle("Tạo bởi: Unknown");
                    }
                })
                .addOnFailureListener(e -> {
                    getSupportActionBar().setSubtitle("Tạo bởi: Unknown");
                });
    }

    private void loadUserRSVPStatus() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) return;

        eventRepository.getUserRSVP(groupId, eventId, currentUserId,
                new EventRepository.OnSuccessCallback<EventRSVP>() {
                    @Override
                    public void onSuccess(EventRSVP rsvp) {
                        if (rsvp != null) {
                            String status = rsvp.attendanceStatus != null ? rsvp.attendanceStatus : rsvp.status; // Backward compatibility
                            android.util.Log.d("EventDetailActivity", "User attendance status: " + status);
                            currentUserStatus = EventRSVP.Status.fromString(status);
                            updateRSVPButtons();
                        } else {
                            android.util.Log.d("EventDetailActivity", "No attendance record found for current user");
                        }
                    }
                },
                new EventRepository.OnErrorCallback() {
                    @Override
                    public void onError(Exception e) {
                        // User hasn't responded yet, keep default status
                    }
                }
        );
    }

    private void loadRSVPCounts() {
        eventRepository.getRSVPCounts(groupId, eventId,
                new EventRepository.OnSuccessCallback<EventRepository.RSVPCounts>() {
                    @Override
                    public void onSuccess(EventRepository.RSVPCounts counts) {
                        tvGoingCount.setText(String.valueOf(counts.goingCount));
                        tvMaybeCount.setText(String.valueOf(counts.maybeCount));
                        tvNotGoingCount.setText(String.valueOf(counts.notGoingCount));
                    }
                },
                new EventRepository.OnErrorCallback() {
                    @Override
                    public void onError(Exception e) {
                        // Keep default counts (0)
                    }
                }
        );
    }

    private void updateRSVP(EventRSVP.Status newStatus) {
        android.util.Log.d("EventDetailActivity", "updateAttendance called with status: " + newStatus.getValue());

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            android.util.Log.e("EventDetailActivity", "User not logged in");
            Toast.makeText(this, "Vui lòng đăng nhập để tham gia sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable buttons temporarily
        setRSVPButtonsEnabled(false);

        eventRepository.updateRSVP(groupId, eventId, newStatus,
                new EventRepository.OnSuccessCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        currentUserStatus = newStatus;
                        updateRSVPButtons();
                        loadRSVPCounts(); // Refresh counts
                        setRSVPButtonsEnabled(true);

                        String message = "";
                        switch (newStatus) {
                            case ATTENDING:
                                message = "Bạn đã xác nhận tham gia sự kiện";
                                break;
                            case MAYBE:
                                message = "Bạn đã đánh dấu có thể tham gia";
                                break;
                            case NOT_ATTENDING:
                                message = "Bạn đã xác nhận không tham gia";
                                break;
                        }

                        android.util.Log.d("EventDetailActivity", "Attendance updated successfully: " + message);
                        Toast.makeText(EventDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                },
                new EventRepository.OnErrorCallback() {
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(EventDetailActivity.this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        setRSVPButtonsEnabled(true);
                    }
                }
        );
    }

    private void updateRSVPButtons() {
        // Reset all buttons
        btnGoing.setSelected(false);
        btnMaybe.setSelected(false);
        btnNotGoing.setSelected(false);

        // Highlight current selection
        if (currentUserStatus != null) {
            android.util.Log.d("EventDetailActivity", "Updating attendance buttons for status: " + currentUserStatus.getValue());
            switch (currentUserStatus) {
                case ATTENDING:
                    btnGoing.setSelected(true);
                    break;
                case MAYBE:
                    btnMaybe.setSelected(true);
                    break;
                case NOT_ATTENDING:
                    btnNotGoing.setSelected(true);
                    break;
            }
        }
    }

    private void setRSVPButtonsEnabled(boolean enabled) {
        btnGoing.setEnabled(enabled);
        btnMaybe.setEnabled(enabled);
        btnNotGoing.setEnabled(enabled);
    }

    private void showDeleteConfirmation() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            android.widget.Toast.makeText(this, "Bạn cần đăng nhập để thực hiện hành động này", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if user can delete this event
        eventRepository.canUserDeleteEvent(groupId, eventId, currentUserId,
                canDelete -> {
                    if (canDelete) {
                        // Show confirmation dialog
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Xóa sự kiện")
                                .setMessage("Bạn có chắc chắn muốn xóa sự kiện này?")
                                .setPositiveButton("Xóa", (dialog, which) -> {
                                    deleteEvent();
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                    } else {
                        android.widget.Toast.makeText(this, "Bạn không có quyền xóa sự kiện này", android.widget.Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    android.widget.Toast.makeText(this, "Lỗi kiểm tra quyền: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void deleteEvent() {
        eventRepository.deleteEvent(groupId, eventId,
                aVoid -> {
                    android.widget.Toast.makeText(this, "Đã xóa sự kiện", android.widget.Toast.LENGTH_SHORT).show();
                    finish(); // Close activity
                },
                error -> {
                    android.widget.Toast.makeText(this, "Lỗi xóa sự kiện: " + error.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
        );
    }
}
