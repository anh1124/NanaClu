package com.example.nanaclu.ui.post;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.PostRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class PollCreateActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextInputLayout tilPollTitle, tilPollDescription;
    private TextInputEditText etPollTitle, etPollDescription;
    private LinearLayout layoutOptionsContainer;
    private MaterialButton btnAddOption, btnCreatePoll;
    private SwitchMaterial switchMultiple, switchAllowAddOption, switchAnonymous,
            switchAllowViewVoters, switchHideResult;
    private TextView tvDeadline;

    private PostRepository postRepository;
    private String groupId;
    private String currentUserId;
    private Long pollDeadlineMillis = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_poll);

        groupId = getIntent().getStringExtra("group_id");
        if (groupId == null) {
            Toast.makeText(this, "Group ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initRepository();
        setupToolbar();
        setupOptions();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarPoll);
        tilPollTitle = findViewById(R.id.tilPollTitle);
        tilPollDescription = findViewById(R.id.tilPollDescription);
        etPollTitle = findViewById(R.id.etPollTitle);
        etPollDescription = findViewById(R.id.etPollDescription);
        layoutOptionsContainer = findViewById(R.id.layoutOptionsContainer);
        btnAddOption = findViewById(R.id.btnAddOption);
        btnCreatePoll = findViewById(R.id.btnCreatePoll);
        switchMultiple = findViewById(R.id.switchMultiple);
        switchAllowAddOption = findViewById(R.id.switchAllowAddOption);
        switchAnonymous = findViewById(R.id.switchAnonymous);
        switchAllowViewVoters = findViewById(R.id.switchAllowViewVoters);
        switchHideResult = findViewById(R.id.switchHideResult);
        tvDeadline = findViewById(R.id.tvDeadline);
    }

    private void initRepository() {
        postRepository = new PostRepository(FirebaseFirestore.getInstance());
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupOptions() {
        addOptionRow(null);
        addOptionRow(null);
    }

    private void setupListeners() {
        btnAddOption.setOnClickListener(v -> addOptionRow(null));
        tvDeadline.setOnClickListener(v -> pickDeadline());
        btnCreatePoll.setOnClickListener(v -> createPoll());
    }

    private void addOptionRow(@androidx.annotation.Nullable String preset) {
        // Container for a single option row with EditText + remove button
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int)(8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextInputLayout til = new TextInputLayout(this);
        LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        til.setLayoutParams(tilParams);
        TextInputEditText et = new TextInputEditText(this);
        et.setHint("Lựa chọn");
        if (preset != null) et.setText(preset);
        til.addView(et);

        android.widget.ImageButton btnRemove = new android.widget.ImageButton(this);
        int size = (int)(40 * getResources().getDisplayMetrics().density / 3.0f * 2); // ~26dp
        LinearLayout.LayoutParams rmParams = new LinearLayout.LayoutParams(size, size);
        rmParams.leftMargin = (int)(8 * getResources().getDisplayMetrics().density);
        btnRemove.setLayoutParams(rmParams);
        btnRemove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnRemove.setBackground(null);
        btnRemove.setOnClickListener(v -> layoutOptionsContainer.removeView(row));

        row.addView(til);
        row.addView(btnRemove);
        layoutOptionsContainer.addView(row);
    }

    private void pickDeadline() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog timePicker = new TimePickerDialog(this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                pollDeadlineMillis = calendar.getTimeInMillis();
                                java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(this);
                                String dateStr = df.format(calendar.getTime());
                                tvDeadline.setText("Kết thúc: " + dateStr);
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true);
                    timePicker.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private void createPoll() {
        String title = etPollTitle.getText() != null ? etPollTitle.getText().toString().trim() : "";
        String description = etPollDescription.getText() != null ? etPollDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            tilPollTitle.setError("Tiêu đề là bắt buộc");
            return;
        } else {
            tilPollTitle.setError(null);
        }

        List<String> optionTexts = new ArrayList<>();
        for (int i = 0; i < layoutOptionsContainer.getChildCount(); i++) {
            View child = layoutOptionsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View inner = row.getChildAt(j);
                    if (inner instanceof TextInputLayout) {
                        TextInputLayout til = (TextInputLayout) inner;
                        TextInputEditText et = (TextInputEditText) til.getEditText();
                        if (et != null) {
                            String txt = et.getText() != null ? et.getText().toString().trim() : "";
                            if (!TextUtils.isEmpty(txt)) {
                                optionTexts.add(txt);
                            }
                        }
                    }
                }
            }
        }


        Post pollPost = new Post();
        pollPost.postId = UUID.randomUUID().toString();
        pollPost.authorId = currentUserId;
        pollPost.groupId = groupId;
        pollPost.type = "poll";
        pollPost.pollTitle = title;
        pollPost.pollDescription = description;
        pollPost.pollMultiple = switchMultiple.isChecked();
        pollPost.pollAllowAddOption = switchAllowAddOption.isChecked();
        pollPost.pollAnonymous = switchAnonymous.isChecked();
        pollPost.pollAllowViewVoters = switchAllowViewVoters.isChecked();
        pollPost.pollHideResult = switchHideResult.isChecked();
        pollPost.pollDeadline = pollDeadlineMillis;

        btnCreatePoll.setEnabled(false);

        postRepository.createPoll(pollPost, optionTexts, new PostRepository.PostCallback() {
            @Override
            public void onSuccess(Post post) {
                runOnUiThread(() -> {
                    Toast.makeText(PollCreateActivity.this, "Đã tạo bình chọn", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    btnCreatePoll.setEnabled(true);
                    com.example.nanaclu.utils.NetworkErrorLogger.logIfNoNetwork("PollCreateActivity", e);
                    String msg = com.example.nanaclu.utils.NetworkErrorLogger.getNetworkErrorMessage(e);
                    if (msg == null) msg = e.getMessage();
                    Toast.makeText(PollCreateActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
