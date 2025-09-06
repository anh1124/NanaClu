package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

public class EditGroupInfoActivity extends AppCompatActivity {

    private String groupId;
    private Group currentGroup;
    private GroupRepository groupRepository;
    
    private TextInputEditText etGroupName;
    private TextInputEditText etGroupDescription;
    private Button btnSave;
    private Button btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_group_info);

        // Lấy dữ liệu từ Intent
        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) {
            finish();
            return;
        }

        // Initialize repository
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        setupToolbar();
        setupUI();
        loadGroupData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chỉnh sửa thông tin nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupUI() {
        etGroupName = findViewById(R.id.etGroupName);
        etGroupDescription = findViewById(R.id.etGroupDescription);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);

        btnSave.setOnClickListener(v -> saveGroupInfo());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadGroupData() {
        groupRepository.getGroupById(groupId, new GroupRepository.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                currentGroup = group;
                etGroupName.setText(group.name);
                etGroupDescription.setText(group.description);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(EditGroupInfoActivity.this, "Lỗi khi tải thông tin nhóm", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveGroupInfo() {
        String newName = etGroupName.getText().toString().trim();
        String newDescription = etGroupDescription.getText().toString().trim();

        if (newName.isEmpty()) {
            etGroupName.setError("Tên nhóm không được để trống");
            return;
        }

        if (currentGroup == null) {
            Toast.makeText(this, "Lỗi: Không có thông tin nhóm", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cập nhật thông tin nhóm
        currentGroup.name = newName;
        currentGroup.description = newDescription;

        groupRepository.updateGroup(currentGroup, new GroupRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(EditGroupInfoActivity.this, "Cập nhật thông tin nhóm thành công", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(EditGroupInfoActivity.this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
