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

public class SecurityActivity extends AppCompatActivity {
    private SharedPreferences securityPrefs;
    private LinearLayout pinRow;
    private TextView pinStatus;
    private com.google.android.material.materialswitch.MaterialSwitch switchPinEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        securityPrefs = getSharedPreferences("security", MODE_PRIVATE);
        
        setupToolbar();
        setupViews();
        updatePinStatus();
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
        pinStatus = findViewById(R.id.pinStatus);
        switchPinEnabled = findViewById(R.id.switchPinEnabled);

        pinRow.setOnClickListener(v -> showSetupPinDialog());

        // Setup switch listener
        switchPinEnabled.setOnCheckedChangeListener(this::onSwitchChanged);
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

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
