package com.example.nanaclu.ui.group;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class EditGroupInfoActivity extends AppCompatActivity {

    private static final int RC_PICK_COVER = 101;
    private static final int RC_PICK_AVATAR = 102;

    private String groupId;
    private Group currentGroup;
    private GroupRepository groupRepository;

    private TextInputEditText etGroupName;
    private TextInputEditText etGroupDescription;
    private Button btnSave;
    private Button btnCancel;
    private Button btnChangeCover;
    private Button btnChangeAvatar;
    private ImageView imgCoverPreview;
    private ImageView imgAvatarPreview;

    private Uri pendingCoverUri, pendingAvatarUri;
    private boolean hasChanges = false;

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
        btnChangeCover = findViewById(R.id.btnChangeCover);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        imgCoverPreview = findViewById(R.id.imgCoverPreview);
        imgAvatarPreview = findViewById(R.id.imgAvatarPreview);

        btnSave.setOnClickListener(v -> saveGroupInfo());
        btnCancel.setOnClickListener(v -> onBackPressed());

        btnChangeCover.setOnClickListener(v -> { hasChanges = true; pickImage(RC_PICK_COVER); });
        btnChangeAvatar.setOnClickListener(v -> { hasChanges = true; pickImage(RC_PICK_AVATAR); });

        etGroupName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { hasChanges = true; }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        etGroupDescription.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { hasChanges = true; }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
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

        // Cập nhật thông tin nhóm (chỉ up ảnh khi ấn Lưu)
        currentGroup.name = newName;
        currentGroup.description = newDescription;

        Runnable doUpdate = () -> groupRepository.updateGroup(currentGroup, new GroupRepository.UpdateCallback() {
            @Override public void onSuccess() {
                Toast.makeText(EditGroupInfoActivity.this, "Cập nhật thông tin nhóm thành công", Toast.LENGTH_SHORT).show();
                hasChanges = false;
                pendingCoverUri = null;
                pendingAvatarUri = null;
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(Exception e) {
                Toast.makeText(EditGroupInfoActivity.this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Chuỗi upload ảnh nếu có chọn mới
        if (pendingCoverUri != null) {
            byte[] coverBytes = getBytesFromUri(pendingCoverUri);
            if (coverBytes == null) {
                Toast.makeText(this, "Không đọc được ảnh nền", Toast.LENGTH_SHORT).show();
                return;
            }
            groupRepository.uploadGroupImage(coverBytes, "cover", url -> {
                currentGroup.coverImageId = url;
                pendingCoverUri = null;
                if (pendingAvatarUri != null) {
                    byte[] avatarBytes = getBytesFromUri(pendingAvatarUri);
                    if (avatarBytes == null) {
                        Toast.makeText(this, "Không đọc được ảnh đại diện", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    groupRepository.uploadGroupImage(avatarBytes, "avatar", url2 -> {
                        currentGroup.avatarImageId = url2;
                        pendingAvatarUri = null;
                        doUpdate.run();
                    }, e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
                } else {
                    doUpdate.run();
                }
            }, e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
        } else if (pendingAvatarUri != null) {
            byte[] avatarBytes = getBytesFromUri(pendingAvatarUri);
            if (avatarBytes == null) {
                Toast.makeText(this, "Không đọc được ảnh đại diện", Toast.LENGTH_SHORT).show();
                return;
            }
            groupRepository.uploadGroupImage(avatarBytes, "avatar", url2 -> {
                currentGroup.avatarImageId = url2;
                pendingAvatarUri = null;
                doUpdate.run();
            }, e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            doUpdate.run();
        }
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh"), requestCode);
    }

    @Override
    public void onBackPressed() {
        if (!hasChanges) { super.onBackPressed(); return; }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Bỏ thay đổi?")
                .setMessage("Bạn có muốn thoát mà không lưu thay đổi?")
                .setNegativeButton("Ở lại", null)
                .setPositiveButton("Không lưu", (d, w) -> finish())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        hasChanges = true;
        if (requestCode == RC_PICK_COVER) {
            pendingCoverUri = uri;
            imgCoverPreview.setImageURI(uri);
        } else if (requestCode == RC_PICK_AVATAR) {
            pendingAvatarUri = uri;
            imgAvatarPreview.setImageURI(uri);
        }
    }

    private byte[] getBytesFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) > 0) baos.write(buffer, 0, n);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
