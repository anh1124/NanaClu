package com.example.nanaclu.ui.security;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.nanaclu.R;

public class PinVerifyDialog {
    private Dialog dialog;
    private PinVerifyCallback callback;
    private TextView tvPinDisplay;
    private String currentPin = "";
    private Context context;

    public PinVerifyDialog(Context context, PinVerifyCallback callback) {
        this.context = context;
        this.callback = callback;
        
        dialog = new Dialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_verify, null);
        dialog.setContentView(view);
        dialog.setCancelable(true);
        
        setupViews(view);
    }

    private void setupViews(View view) {
        tvPinDisplay = view.findViewById(R.id.tvPinDisplay);
        
        // Setup number buttons
        setupNumberButton(view, R.id.btn0, "0");
        setupNumberButton(view, R.id.btn1, "1");
        setupNumberButton(view, R.id.btn2, "2");
        setupNumberButton(view, R.id.btn3, "3");
        setupNumberButton(view, R.id.btn4, "4");
        setupNumberButton(view, R.id.btn5, "5");
        setupNumberButton(view, R.id.btn6, "6");
        setupNumberButton(view, R.id.btn7, "7");
        setupNumberButton(view, R.id.btn8, "8");
        setupNumberButton(view, R.id.btn9, "9");
        
        // Setup action buttons
        view.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteDigit());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            if (callback != null) callback.onCancel();
            dialog.dismiss();
        });
        
        updateDisplay();
    }

    private void setupNumberButton(View parent, int buttonId, String digit) {
        parent.findViewById(buttonId).setOnClickListener(v -> addDigit(digit));
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

    private void verifyPin() {
        SharedPreferences prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE);
        String savedPinHash = prefs.getString("pin_hash", "");
        String enteredPinHash = hashPin(currentPin);

        if (enteredPinHash.equals(savedPinHash)) {
            if (callback != null) callback.onPinVerified();
            dialog.dismiss();
        } else {
            if (callback != null) callback.onPinFailed();
            currentPin = "";
            updateDisplay();
        }
    }

    private String hashPin(String pin) {
        // Simple hash for demo - in production use proper hashing
        return String.valueOf(pin.hashCode());
    }

    public void show() {
        dialog.show();
    }

    public interface PinVerifyCallback {
        void onPinVerified();
        void onPinFailed();
        void onCancel();
    }
}
