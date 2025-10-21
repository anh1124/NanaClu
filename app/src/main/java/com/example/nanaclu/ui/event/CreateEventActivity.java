package com.example.nanaclu.ui.event;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Event;
import com.example.nanaclu.data.repository.EventRepository;
import com.example.nanaclu.data.repository.NoticeRepository;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CreateEventActivity extends AppCompatActivity {

    private EditText edtTitle, edtDescription, edtLocation;
    private Button btnStartDate, btnStartTime, btnEndDate, btnEndTime, btnSelectImage, btnCreateEvent;
    private ImageView ivEventImage;

    private Calendar startDateTime = Calendar.getInstance();
    private Calendar endDateTime = Calendar.getInstance();
    private Uri selectedImageUri;

    private EventRepository eventRepository;
    private String groupId;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(ivEventImage);
                    btnSelectImage.setText("Đổi ảnh");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        // Get groupId from intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            Toast.makeText(this, "Lỗi: Không tìm thấy nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupEventHandlers();

        eventRepository = new EventRepository(FirebaseFirestore.getInstance());

        // Set default end time to 2 hours after start time
        endDateTime.setTime(startDateTime.getTime());
        endDateTime.add(Calendar.HOUR_OF_DAY, 2);
        updateDateTimeButtons();
    }

    private void initViews() {
        edtTitle = findViewById(R.id.edtEventTitle);
        edtDescription = findViewById(R.id.edtEventDescription);
        edtLocation = findViewById(R.id.edtEventLocation);

        btnStartDate = findViewById(R.id.btnStartDate);
        btnStartTime = findViewById(R.id.btnStartTime);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnEndTime = findViewById(R.id.btnEndTime);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnCreateEvent = findViewById(R.id.btnCreateEvent);

        ivEventImage = findViewById(R.id.ivEventImage);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo sự kiện mới");
        }
    }

    private void setupEventHandlers() {
        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnStartTime.setOnClickListener(v -> showTimePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));
        btnEndTime.setOnClickListener(v -> showTimePicker(false));

        btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnCreateEvent.setOnClickListener(v -> createEvent());
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDateTime : endDateTime;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // If setting start date and it's after end date, adjust end date
                    if (isStartDate && startDateTime.after(endDateTime)) {
                        endDateTime.setTime(startDateTime.getTime());
                        endDateTime.add(Calendar.HOUR_OF_DAY, 2);
                    }

                    updateDateTimeButtons();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Don't allow past dates
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void showTimePicker(boolean isStartTime) {
        Calendar calendar = isStartTime ? startDateTime : endDateTime;

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);

                    // If setting start time and it's after end time on same day, adjust end time
                    if (isStartTime && isSameDay(startDateTime, endDateTime) && startDateTime.after(endDateTime)) {
                        endDateTime.setTime(startDateTime.getTime());
                        endDateTime.add(Calendar.HOUR_OF_DAY, 1);
                    }

                    updateDateTimeButtons();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        );

        timePickerDialog.show();
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void updateDateTimeButtons() {
        btnStartDate.setText(dateFormat.format(startDateTime.getTime()));
        btnStartTime.setText(timeFormat.format(startDateTime.getTime()));
        btnEndDate.setText(dateFormat.format(endDateTime.getTime()));
        btnEndTime.setText(timeFormat.format(endDateTime.getTime()));
    }

    private void createEvent() {
        String title = edtTitle.getText().toString().trim();
        String description = edtDescription.getText().toString().trim();
        String location = edtLocation.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(title)) {
            edtTitle.setError("Vui lòng nhập tiêu đề sự kiện");
            edtTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(location)) {
            edtLocation.setError("Vui lòng nhập địa điểm");
            edtLocation.requestFocus();
            return;
        }

        if (startDateTime.after(endDateTime)) {
            Toast.makeText(this, "Thời gian kết thúc phải sau thời gian bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double submission
        btnCreateEvent.setEnabled(false);
        btnCreateEvent.setText("Đang tạo...");

        // Create event object
        Event event = new Event();
        event.title = title;
        event.description = description;
        event.location = location;
        event.startTime = startDateTime.getTimeInMillis();
        event.endTime = endDateTime.getTimeInMillis();
        event.groupId = groupId;

        // Save to database
        eventRepository.createEvent(event, selectedImageUri,
                new EventRepository.OnSuccessCallback<String>() {
                    @Override
                    public void onSuccess(String eventId) {
                        Toast.makeText(CreateEventActivity.this, "Đã tạo sự kiện thành công!", Toast.LENGTH_SHORT).show();

                        // Create notice for all group members
                        createEventNotice(eventId, title);

                        // Return to previous screen
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("eventId", eventId);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                },
                new EventRepository.OnErrorCallback() {
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(CreateEventActivity.this, "Lỗi tạo sự kiện: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                        // Re-enable button
                        btnCreateEvent.setEnabled(true);
                        btnCreateEvent.setText("Tạo sự kiện");
                    }
                }
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createEventNotice(String eventId, String eventTitle) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUid == null) {
            android.util.Log.w("CreateEventActivity", "Current user is null, cannot create event notice");
            return;
        }

        android.util.Log.d("CreateEventActivity", "Creating event notice for eventId: " + eventId + ", groupId: " + groupId);

        // Get current user name
        UserRepository userRepo = new UserRepository(FirebaseFirestore.getInstance());
        userRepo.getUserById(currentUid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                String actorName = user != null ? user.displayName : "Người dùng";
                android.util.Log.d("CreateEventActivity", "Got actor name: " + actorName);

                // Get group members
                GroupRepository groupRepo = new GroupRepository(FirebaseFirestore.getInstance());
                groupRepo.getGroupMembers(groupId, new GroupRepository.GroupMembersCallback() {
                    @Override
                    public void onSuccess(java.util.List<String> memberIds) {
                        android.util.Log.d("CreateEventActivity", "Got group members: " + memberIds.size() + " members");
                        
                        // Create notices for all members
                        NoticeRepository noticeRepo = new NoticeRepository(FirebaseFirestore.getInstance());
                        noticeRepo.createGroupEventNotice(groupId, eventId, currentUid, actorName, memberIds, eventTitle)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("CreateEventActivity", "Successfully created event notices for " + memberIds.size() + " members");
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("CreateEventActivity", "Failed to create event notices", e);
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("CreateEventActivity", "Failed to get group members", e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("CreateEventActivity", "Failed to get current user", e);
                // Fallback with default name
                String actorName = "Người dùng";
                
                // Get group members with fallback name
                GroupRepository groupRepo = new GroupRepository(FirebaseFirestore.getInstance());
                groupRepo.getGroupMembers(groupId, new GroupRepository.GroupMembersCallback() {
                    @Override
                    public void onSuccess(java.util.List<String> memberIds) {
                        android.util.Log.d("CreateEventActivity", "Got group members (fallback): " + memberIds.size() + " members");
                        
                        NoticeRepository noticeRepo = new NoticeRepository(FirebaseFirestore.getInstance());
                        noticeRepo.createGroupEventNotice(groupId, eventId, currentUid, actorName, memberIds, eventTitle)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("CreateEventActivity", "Successfully created event notices (fallback) for " + memberIds.size() + " members");
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("CreateEventActivity", "Failed to create event notices (fallback)", e);
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("CreateEventActivity", "Failed to get group members (fallback)", e);
                    }
                });
            }
        });
    }
}
