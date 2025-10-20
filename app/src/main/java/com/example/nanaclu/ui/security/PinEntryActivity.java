package com.example.nanaclu.ui.security;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.MainActivity;
import com.example.nanaclu.R;
import com.example.nanaclu.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

public class PinEntryActivity extends AppCompatActivity {
    private TextView tvPinDisplay;
    private TextView tvAttempts;
    private String currentPin = "";
    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS = 5;
    private SharedPreferences securityPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_entry);

        securityPrefs = getSharedPreferences("security", MODE_PRIVATE);
        
        setupViews();
        updateDisplay();
    }

    private void setupViews() {
        tvPinDisplay = findViewById(R.id.tvPinDisplay);
        tvAttempts = findViewById(R.id.tvAttempts);
        
        // Setup number buttons
        setupNumberButton(R.id.btn0, "0");
        setupNumberButton(R.id.btn1, "1");
        setupNumberButton(R.id.btn2, "2");
        setupNumberButton(R.id.btn3, "3");
        setupNumberButton(R.id.btn4, "4");
        setupNumberButton(R.id.btn5, "5");
        setupNumberButton(R.id.btn6, "6");
        setupNumberButton(R.id.btn7, "7");
        setupNumberButton(R.id.btn8, "8");
        setupNumberButton(R.id.btn9, "9");
        
        // Setup action buttons
        findViewById(R.id.btnDelete).setOnClickListener(v -> deleteDigit());
        findViewById(R.id.btnForgotPin).setOnClickListener(v -> forgotPin());
        
        updateAttemptsDisplay();
    }

    private void setupNumberButton(int buttonId, String digit) {
        findViewById(buttonId).setOnClickListener(v -> addDigit(digit));
    }

    private void addDigit(String digit) {
        if (currentPin.length() < 4) {
            currentPin += digit;
            updateDisplay();

            if (currentPin.length() == 4) {
                verifyPin();
            }
        }
    }

    private void deleteDigit() {
        if (currentPin.length() > 0) {
            currentPin = currentPin.substring(0, currentPin.length() - 1);
            updateDisplay();
        }
    }

    private void updateDisplay() {
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < currentPin.length()) {
                display.append("●");
            } else {
                display.append("○");
            }
            if (i < 3) display.append(" ");
        }
        tvPinDisplay.setText(display.toString());
    }

    private void updateAttemptsDisplay() {
        if (failedAttempts > 0) {
            int remaining = MAX_ATTEMPTS - failedAttempts;
            tvAttempts.setText("Còn lại " + remaining + " lần thử");
            tvAttempts.setVisibility(View.VISIBLE);
            tvAttempts.setTextColor(remaining <= 2 ? getColor(R.color.red_error) : getColor(R.color.gray));
        } else {
            tvAttempts.setVisibility(View.GONE);
        }
    }

    private void verifyPin() {
        String savedPinHash = securityPrefs.getString("pin_hash", "");
        String enteredPinHash = hashPin(currentPin);

        android.util.Log.d("PinEntry", "Saved hash: " + savedPinHash + ", Entered hash: " + enteredPinHash);

        if (enteredPinHash.equals(savedPinHash)) {
            // PIN correct - proceed to main app
            Intent intent = new Intent(this, com.example.nanaclu.ui.HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            // PIN incorrect
            failedAttempts++;
            currentPin = "";
            updateDisplay();
            updateAttemptsDisplay();
            
            if (failedAttempts >= MAX_ATTEMPTS) {
                // Too many failed attempts - force logout
                forceLogout();
            } else {
                Toast.makeText(this, "PIN không đúng", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void forgotPin() {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Quên PIN?")
                .setMessage("Bạn sẽ cần đăng nhập lại để sử dụng ứng dụng.")
                .setPositiveButton("Đăng nhập lại", (dialog, which) -> forceLogout(true))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void forceLogout() {
        forceLogout(false);
    }

    private void forceLogout(boolean isForgotPin) {
        // Clear PIN settings
        securityPrefs.edit()
                .putBoolean("pin_enabled", false)
                .remove("pin_hash")
                .apply();

        // Clear auto login
        getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putBoolean("auto_login", false)
                .apply();

        // Use AuthRepository for proper logout with cache clearing
        com.example.nanaclu.data.repository.AuthRepository authRepo = 
                new com.example.nanaclu.data.repository.AuthRepository(this);
        
        authRepo.logout(this).addOnCompleteListener(task -> {
            // Go to login screen
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            if (isForgotPin) {
                Toast.makeText(this, "Đã đăng xuất. Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Đã đăng xuất do nhập sai PIN quá nhiều lần", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String hashPin(String pin) {
        // Simple hash for demo - in production use proper hashing
        return String.valueOf(pin.hashCode());
    }

    @Override
    public void onBackPressed() {
        // Prevent going back - user must enter PIN or logout
        Toast.makeText(this, "Vui lòng nhập PIN để tiếp tục", Toast.LENGTH_SHORT).show();
    }
}
