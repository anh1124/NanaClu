package com.example.nanaclu.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nanaclu.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.nanaclu.utils.ThemeUtils;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);
        TextView tvDisplayName = root.findViewById(R.id.tvDisplayName);
        TextView tvEmail = root.findViewById(R.id.tvEmail);
        View btnLogout = root.findViewById(R.id.btnLogout);
        View colorPreview = root.findViewById(R.id.colorPreview);
        View btnPick = root.findViewById(R.id.btnPickColor);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);

        int currentColor = ThemeUtils.getToolbarColor(requireContext());
        colorPreview.setBackgroundColor(currentColor);
        toolbar.setBackgroundColor(currentColor);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        }

        btnLogout.setOnClickListener(v -> openConfirmLogoutDialog());
        btnPick.setOnClickListener(v -> openColorPicker(toolbar, colorPreview));
        return root;
    }

    private void openColorPicker(androidx.appcompat.widget.Toolbar toolbar, View preview) {
        if (getContext() == null) return;
        // đơn giản: chọn nhanh 3 màu preset
        android.app.Dialog d = new android.app.Dialog(getContext());
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(24,24,24,24);
        int[] colors = new int[]{android.graphics.Color.parseColor("#6200EE"), android.graphics.Color.parseColor("#03DAC5"), android.graphics.Color.parseColor("#FF5722")};
        for (int c : colors) {
            android.view.View v = new android.view.View(getContext());
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(80,80);
            lp.setMargins(16,0,16,0);
            v.setLayoutParams(lp);
            v.setBackgroundColor(c);
            v.setOnClickListener(x -> {
                ThemeUtils.saveToolbarColor(requireContext(), c);
                toolbar.setBackgroundColor(c);
                preview.setBackgroundColor(c);
                d.dismiss();
            });
            layout.addView(v);
        }
        d.setContentView(layout);
        d.show();
    }

    private void openConfirmLogoutDialog() {
        if (getContext() == null) return;
        android.app.Dialog d = new android.app.Dialog(getContext());
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_confirm_logout);
        d.setCancelable(true);

        View btnCancel = d.findViewById(R.id.btnCancel);
        View btnConfirm = d.findViewById(R.id.btnConfirm);
        btnCancel.setOnClickListener(v -> d.dismiss());
        btnConfirm.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            if (getActivity() != null) {
                getActivity().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("remember_me", false).apply();
                startActivity(new android.content.Intent(getActivity(), com.example.nanaclu.ui.auth.LoginActivity.class));
                getActivity().finish();
            }
            d.dismiss();
        });

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
}


