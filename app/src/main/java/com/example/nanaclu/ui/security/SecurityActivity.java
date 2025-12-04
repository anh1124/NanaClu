package com.example.nanaclu.ui.security;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SecurityActivity extends AppCompatActivity {
    private SharedPreferences securityPrefs;
    private LinearLayout pinRow;
    private LinearLayout changePasswordRow;
    private TextView pinStatus;
    private com.google.android.material.materialswitch.MaterialSwitch switchPinEnabled;
    private com.google.android.material.materialswitch.MaterialSwitch switchAllowStrangerMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        securityPrefs = getSharedPreferences("security", MODE_PRIVATE);
        
        setupToolbar();
        setupViews();
        updatePinStatus();
        checkPasswordChangeAvailability();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bảo mật");
        }
        
        // Apply theme color
        int toolbarColor = ThemeUtils.getThemeColor(this);
        toolbar.setBackgroundColor(toolbarColor);
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
    }

    private void setupViews() {
        pinRow = findViewById(R.id.pinRow);
        changePasswordRow = findViewById(R.id.changePasswordRow);
        pinStatus = findViewById(R.id.pinStatus);
        switchPinEnabled = findViewById(R.id.switchPinEnabled);
        switchAllowStrangerMessages = findViewById(R.id.switchAllowStrangerMessages);

        pinRow.setOnClickListener(v -> showSetupPinDialog());
        changePasswordRow.setOnClickListener(v -> {
            if (changePasswordRow.isEnabled()) {
                showChangePasswordDialog();
            } else {
                // Show message for disabled state
                Toast.makeText(this, "Chức năng này không khả dụng với tài khoản Google", Toast.LENGTH_SHORT).show();
            }
        });

        // Setup switch listeners
        switchPinEnabled.setOnCheckedChangeListener(this::onSwitchChanged);
        switchAllowStrangerMessages.setOnCheckedChangeListener(this::onStrangerMessagesSwitchChanged);

        // Load stranger messages setting
        loadStrangerMessagesSetting();
    }

    private void onStrangerMessagesSwitchChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
        // Save setting to Firebase
        saveStrangerMessagesSetting(isChecked);
    }

    private void loadStrangerMessagesSetting() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        Boolean allowStrangerMessages = doc.getBoolean("allowStrangerMessages");
                        // Default to true if not set
                        boolean isEnabled = allowStrangerMessages != null ? allowStrangerMessages : true;
                        switchAllowStrangerMessages.setOnCheckedChangeListener(null);
                        switchAllowStrangerMessages.setChecked(isEnabled);
                        switchAllowStrangerMessages.setOnCheckedChangeListener(this::onStrangerMessagesSwitchChanged);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("SecurityActivity", "Failed to load stranger messages setting", e);
                        // Default to true
                        switchAllowStrangerMessages.setOnCheckedChangeListener(null);
                        switchAllowStrangerMessages.setChecked(true);
                        switchAllowStrangerMessages.setOnCheckedChangeListener(this::onStrangerMessagesSwitchChanged);
                    });
        }
    }

    private void saveStrangerMessagesSetting(boolean isEnabled) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .update("allowStrangerMessages", isEnabled)
                    .addOnSuccessListener(aVoid -> {
                        String message = isEnabled ? "Đã bật nhận tin nhắn từ người lạ" : "Đã tắt nhận tin nhắn từ người lạ";
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("SecurityActivity", "Failed to save stranger messages setting", e);
                        Toast.makeText(this, "Lỗi khi lưu cài đặt", Toast.LENGTH_SHORT).show();
                        // Revert switch
                        switchAllowStrangerMessages.setOnCheckedChangeListener(null);
                        switchAllowStrangerMessages.setChecked(!isEnabled);
                        switchAllowStrangerMessages.setOnCheckedChangeListener(this::onStrangerMessagesSwitchChanged);
                    });
        }
    }

    private void onSwitchChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            // User wants to enable PIN - reset switch to OFF first, then show setup dialog
            switchPinEnabled.setOnCheckedChangeListener(null);
            switchPinEnabled.setChecked(false);
            switchPinEnabled.setOnCheckedChangeListener(this::onSwitchChanged);
            showSetupPinDialog();
        } else {
            // Disable PIN
            disablePin();
        }
    }

    private void updatePinStatus() {
        boolean isPinEnabled = securityPrefs.getBoolean("pin_enabled", false);
        switchPinEnabled.setOnCheckedChangeListener(null); // Tạm tắt listener
        switchPinEnabled.setChecked(isPinEnabled);
        switchPinEnabled.setOnCheckedChangeListener(this::onSwitchChanged);

        if (isPinEnabled) {
            pinStatus.setText("Đã thiết lập");
            pinStatus.setTextColor(getColor(R.color.green));
            pinRow.setVisibility(android.view.View.VISIBLE);
        } else {
            pinStatus.setText("Chưa thiết lập");
            pinStatus.setTextColor(getColor(R.color.gray));
            pinRow.setVisibility(android.view.View.GONE);
        }
    }

    private void disablePin() {
        boolean isPinEnabled = securityPrefs.getBoolean("pin_enabled", false);

        if (isPinEnabled) {
            // Disable PIN - need to verify current PIN first
            showVerifyPinDialog(() -> {
                securityPrefs.edit()
                        .putBoolean("pin_enabled", false)
                        .remove("pin_hash")
                        .apply();
                updatePinStatus();
                Toast.makeText(this, "Đã tắt PIN", Toast.LENGTH_SHORT).show();
            });
        } else {
            // Already disabled
            updatePinStatus();
        }
    }

    private void showSetupPinDialog() {
        PinSetupDialog dialog = new PinSetupDialog(this, new PinSetupDialog.PinSetupCallback() {
            @Override
            public void onPinSetup(String pin) {
                // Hash and save PIN
                String pinHash = hashPin(pin);
                securityPrefs.edit()
                        .putBoolean("pin_enabled", true)
                        .putString("pin_hash", pinHash)
                        .apply();
                updatePinStatus();
                Toast.makeText(SecurityActivity.this, "Đã thiết lập PIN thành công", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
                // User cancelled PIN setup - reset switch to OFF
                updatePinStatus();
            }
        });
        dialog.show();
    }

    private void showVerifyPinDialog(Runnable onSuccess) {
        PinVerifyDialog dialog = new PinVerifyDialog(this, new PinVerifyDialog.PinVerifyCallback() {
            @Override
            public void onPinVerified() {
                onSuccess.run();
            }

            @Override
            public void onPinFailed() {
                Toast.makeText(SecurityActivity.this, "PIN không đúng", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
                // Do nothing
            }
        });
        dialog.show();
    }

    private String hashPin(String pin) {
        // Simple hash for demo - in production use proper hashing
        return String.valueOf(pin.hashCode());
    }

    private void checkPasswordChangeAvailability() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Check if user is using email/password authentication
            boolean isEmailProvider = false;
            for (com.google.firebase.auth.UserInfo provider : currentUser.getProviderData()) {
                if ("password".equals(provider.getProviderId())) {
                    isEmailProvider = true;
                    break;
                }
            }
            
            // Always show password change row, but with different states
            changePasswordRow.setVisibility(View.VISIBLE);
            
            if (isEmailProvider) {
                // Enable for email/password users
                changePasswordRow.setEnabled(true);
                changePasswordRow.setAlpha(1.0f);
                updatePasswordChangeText("Thay đổi mật khẩu tài khoản");
            } else {
                // Disable for OAuth users (Google)
                changePasswordRow.setEnabled(false);
                changePasswordRow.setAlpha(0.6f);
                updatePasswordChangeText("Không hoạt động với OAuth (Google)");
            }
        } else {
            changePasswordRow.setVisibility(View.GONE);
        }
    }
    
    private void updatePasswordChangeText(String text) {
        TextView tvDescription = changePasswordRow.findViewById(R.id.tvPasswordDescription);
        if (tvDescription != null) {
            tvDescription.setText(text);
        }
    }

    private void showChangePasswordDialog() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(this, new ChangePasswordDialog.ChangePasswordCallback() {
            @Override
            public void onPasswordChanged() {
                android.util.Log.d("PasswordChange", "onPasswordChanged callback called");
                Toast.makeText(SecurityActivity.this, "Mật khẩu đã được thay đổi thành công", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
                // User cancelled - do nothing
            }
        });
        dialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
