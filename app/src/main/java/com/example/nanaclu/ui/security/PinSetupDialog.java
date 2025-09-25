package com.example.nanaclu.ui.security;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.nanaclu.R;

public class PinSetupDialog {
    private Dialog dialog;
    private PinSetupCallback callback;
    private TextView tvTitle;
    private TextView tvPinDisplay;
    private String currentPin = "";
    private String confirmPin = "";
    private boolean isConfirmMode = false;

    public PinSetupDialog(Context context, PinSetupCallback callback) {
        this.callback = callback;
        
        dialog = new Dialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_setup, null);
        dialog.setContentView(view);
        dialog.setCancelable(true);
        
        setupViews(view);
    }

    private void setupViews(View view) {
        tvTitle = view.findViewById(R.id.tvPinTitle);
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
                if (!isConfirmMode) {
                    // First PIN entered, now confirm
                    confirmPin = currentPin;
                    currentPin = "";
                    isConfirmMode = true;
                    tvTitle.setText("Xác nhận PIN");
                    updateDisplay();
                } else {
                    // Confirm PIN entered
                    if (currentPin.equals(confirmPin)) {
                        // PIN matches
                        if (callback != null) callback.onPinSetup(currentPin);
                        dialog.dismiss();
                    } else {
                        // PIN doesn't match
                        Toast.makeText(dialog.getContext(), "PIN không khớp. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                        resetToFirstStep();
                    }
                }
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

    private void resetToFirstStep() {
        currentPin = "";
        confirmPin = "";
        isConfirmMode = false;
        tvTitle.setText("Thiết lập PIN");
        updateDisplay();
    }

    public void show() {
        dialog.show();
    }

    public interface PinSetupCallback {
        void onPinSetup(String pin);
        void onCancel();
    }
}
