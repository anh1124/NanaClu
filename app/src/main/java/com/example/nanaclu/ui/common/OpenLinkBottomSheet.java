package com.example.nanaclu.ui.common;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ShareLinkUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class OpenLinkBottomSheet extends BottomSheetDialogFragment {

    private TextInputEditText edtLink;
    private MaterialButton btnPaste, btnSend;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_open_link, container, false);
        
        initViews(view);
        setupClickListeners();
        
        return view;
    }

    private void initViews(View view) {
        edtLink = view.findViewById(R.id.edtLink);
        btnPaste = view.findViewById(R.id.btnPaste);
        btnSend = view.findViewById(R.id.btnSend);
    }

    private void setupClickListeners() {
        btnPaste.setOnClickListener(v -> {
            pasteFromClipboard();
        });

        btnSend.setOnClickListener(v -> {
            openLink();
        });
    }

    private void pasteFromClipboard() {
        Context context = getContext();
        if (context == null) return;

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                String text = clipData.getItemAt(0).getText().toString();
                edtLink.setText(text);
                Toast.makeText(context, "Đã dán từ clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Clipboard trống", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Clipboard trống", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLink() {
        String link = edtLink.getText() != null ? edtLink.getText().toString().trim() : "";
        
        if (link.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng nhập link", Toast.LENGTH_SHORT).show();
            return;
        }

        Activity activity = getActivity();
        if (activity != null) {
            ShareLinkUtils.openPostLink(activity, link);
            dismiss();
        }
    }
}
