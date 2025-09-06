package com.example.nanaclu.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.example.nanaclu.viewmodel.GroupViewModel;
import com.example.nanaclu.data.model.Group;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class GroupsFragment extends Fragment {
    private GroupViewModel groupViewModel;
    private GroupsAdapter groupsAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_groups, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup toolbar with theme color and white text
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(requireContext()));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setTitle("My Groups");
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_group) {
                showCreateGroupDialog();
                return true;
            }
            return false;
        });

        // Setup SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            groupViewModel.loadUserGroups();
        });
        
        // Setup RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.rvGroups);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        groupsAdapter = new GroupsAdapter();
        recyclerView.setAdapter(groupsAdapter);
        
        // Setup click listener
        groupsAdapter.setOnGroupClickListener(group -> {
            Intent intent = new Intent(getContext(), GroupDetailActivity.class);
            intent.putExtra("group_id", group.groupId);
            startActivityForResult(intent, 100);
        });

        // Setup ViewModel
        groupViewModel = new ViewModelProvider(this).get(GroupViewModel.class);
        
        // Observe data
        groupViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            // Handle loading state if needed
        });
        
        groupViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
        
        groupViewModel.getUserGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups != null) {
                groupsAdapter.setGroups(groups);
                // Debug log
                Toast.makeText(getContext(), "Loaded " + groups.size() + " groups", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No groups found", Toast.LENGTH_SHORT).show();
            }
            // Stop refresh animation
            swipeRefreshLayout.setRefreshing(false);
        });

        // Groups will be loaded in onStart()
    }

    private void showCreateGroupDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_group, null);
        TextInputEditText etGroupName = dialogView.findViewById(R.id.etGroupName);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create New Group")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String groupName = etGroupName.getText().toString().trim();
                    if (!groupName.isEmpty()) {
                        groupViewModel.createGroup(groupName, true); // Default to public
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Load groups when fragment starts
        groupViewModel.loadUserGroups();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always reload groups when fragment resumes
        groupViewModel.loadUserGroups();
    }
}


