package com.example.nanaclu.ui.group;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.R;
import com.example.nanaclu.viewmodel.GroupViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.snackbar.Snackbar;
import com.example.nanaclu.utils.ThemeUtils;

public class GroupsFragment extends Fragment {
    private GroupViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_groups, container, false);
        viewModel = new ViewModelProvider(this).get(GroupViewModel.class);

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(requireContext()));
        toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);

        viewModel.createdGroupId.observe(getViewLifecycleOwner(), id -> {
            if (id != null) {
                Snackbar.make(root, "Tạo nhóm thành công", Snackbar.LENGTH_SHORT).show();
            }
        });
        viewModel.error.observe(getViewLifecycleOwner(), err -> {
            if (err != null) {
                Snackbar.make(root, err, Snackbar.LENGTH_SHORT).show();
            }
        });

        return root;
    }

    private boolean onToolbarItemClicked(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_group) {
            openCreateGroupDialog();
            return true;
        }
        return false;
    }

    private void openCreateGroupDialog() {
        if (getContext() == null) return;
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_create_group);
        d.setCancelable(true);

        View btnClose = d.findViewById(R.id.btnClose);
        EditText edtGroupName = d.findViewById(R.id.edtGroupName);
        SwitchMaterial swIsPublic = d.findViewById(R.id.swIsPublic);
        Button btnCreate = d.findViewById(R.id.btnCreate);

        btnClose.setOnClickListener(v -> d.dismiss());
        btnCreate.setOnClickListener(v -> {
            String name = edtGroupName.getText().toString().trim();
            if (name.isEmpty()) {
                edtGroupName.setError("Tên nhóm không được trống");
                return;
            }
            viewModel.createGroup(name, swIsPublic.isChecked());
            d.dismiss();
        });

        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }
}


