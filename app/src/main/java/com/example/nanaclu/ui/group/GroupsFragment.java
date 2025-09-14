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
			int id = item.getItemId();
			if (id == R.id.action_add_group) {
				showCreateGroupDialog();
				return true;
			} else if (id == R.id.action_join_group) {
				showJoinGroupDialog();
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
		// Add divider between items
		recyclerView.addItemDecoration(new androidx.recyclerview.widget.DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
		groupsAdapter = new GroupsAdapter();
		recyclerView.setAdapter(groupsAdapter);

		// Setup click listener

		// Swipe navigation left/right between tabs
		recyclerView.setOnTouchListener(new android.view.View.OnTouchListener() {
			float downX;
			final float threshold = getResources().getDisplayMetrics().density * 80; // ~80dp
			@Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
				switch (e.getActionMasked()) {
					case android.view.MotionEvent.ACTION_DOWN:
						downX = e.getX();
						break;
					case android.view.MotionEvent.ACTION_UP:
						float dx = e.getX() - downX;
						if (Math.abs(dx) > threshold && getActivity() instanceof com.example.nanaclu.ui.HomeActivity) {
							com.example.nanaclu.ui.HomeActivity act = (com.example.nanaclu.ui.HomeActivity) getActivity();
							if (dx < 0) act.navigateToNextTab(); else act.navigateToPrevTab();
							return true;
						}
						break;
				}
				return false;
			}
		});
		groupsAdapter.setOnGroupClickListener(group -> {
			// Hiển thị id group khi click
			Toast.makeText(getContext(), "groupId: " + group.groupId, Toast.LENGTH_SHORT).show();
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
			} else {
				// Không hiển thị toast debug
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

	private void showJoinGroupDialog() {
		android.widget.EditText input = new com.google.android.material.textfield.TextInputEditText(requireContext());
		input.setHint("Nhập mã nhóm (6 ký tự)");
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Tham gia nhóm bằng mã")
				.setView(input)
				.setPositiveButton("Tham gia", (d, w) -> {
					String code = input.getText() != null ? input.getText().toString().trim() : "";
					if (code.isEmpty()) { android.widget.Toast.makeText(requireContext(), "Vui lòng nhập mã", android.widget.Toast.LENGTH_SHORT).show(); return; }
					com.example.nanaclu.data.repository.GroupRepository repo = new com.example.nanaclu.data.repository.GroupRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
					repo.joinGroupByCode(code.toUpperCase())
							.addOnSuccessListener(v -> {
								android.widget.Toast.makeText(requireContext(), "Đã tham gia nhóm", android.widget.Toast.LENGTH_SHORT).show();
								groupViewModel.loadUserGroups();
							})
							.addOnFailureListener(e -> {
								android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
							});
				})
				.setNegativeButton("Hủy", null)
				.show();
	}

}


