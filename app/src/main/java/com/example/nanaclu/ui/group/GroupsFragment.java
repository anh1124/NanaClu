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
	private TextInputEditText edtSearch;
	private java.util.List<Group> allGroups = new java.util.ArrayList<>();
	private java.util.List<Group> filteredGroups = new java.util.ArrayList<>();

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
		toolbar.setTitle("NANACLUB");
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

		// Setup search
		edtSearch = view.findViewById(R.id.edtSearch);
		edtSearch.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override public void afterTextChanged(android.text.Editable s) {
				filterGroups(s.toString().trim());
			}
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
				allGroups.clear();
				allGroups.addAll(groups);
				filterGroups(edtSearch.getText().toString().trim());
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
		// Inflate dialog layout
		View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_group, null);
		
		// Get views from layout
		android.widget.EditText etGroupCode = dialogView.findViewById(R.id.etGroupCode);
		android.widget.ImageButton btnPaste = dialogView.findViewById(R.id.btnPaste);
		
		// Set input filter for 6 characters
		etGroupCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
		
		// Set paste button click listener
		btnPaste.setOnClickListener(v -> {
			android.content.ClipboardManager cm = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
			if (cm != null && cm.hasPrimaryClip()) {
				android.content.ClipData cd = cm.getPrimaryClip();
				if (cd != null && cd.getItemCount() > 0) {
					CharSequence text = cd.getItemAt(0).coerceToText(requireContext());
					if (text != null) etGroupCode.setText(text.toString().trim());
				}
			}
		});

		// Show dialog
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Tham gia nhóm bằng mã")
				.setView(dialogView)
				.setPositiveButton("Tham gia", (d, w) -> {
					String code = etGroupCode.getText() != null ? etGroupCode.getText().toString().trim() : "";
					if (code.isEmpty()) { 
						android.widget.Toast.makeText(requireContext(), "Vui lòng nhập mã", android.widget.Toast.LENGTH_SHORT).show(); 
						return; 
					}
					android.util.Log.d("GroupsFragment", "Joining group with code: " + code);
					final String upper = code.toUpperCase();
					com.google.firebase.firestore.FirebaseFirestore.getInstance()
							.collection("groups").whereEqualTo("code", upper).limit(1).get()
							.addOnSuccessListener(qs -> {
								final boolean needApproval;
								if (!qs.isEmpty()) {
									com.google.firebase.firestore.DocumentSnapshot ddoc = qs.getDocuments().get(0);
									Boolean ra = ddoc.getBoolean("requireApproval");
									needApproval = ra != null && ra;
								} else {
									needApproval = false;
								}
								
								Runnable doJoin = () -> {
									com.example.nanaclu.data.repository.GroupRepository repo = new com.example.nanaclu.data.repository.GroupRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
									repo.joinGroupByCode(upper)
											.addOnSuccessListener(vv -> {
												android.util.Log.d("GroupsFragment", "Join request success");
												if (needApproval) {
													android.widget.Toast.makeText(requireContext(), "Đã gửi yêu cầu tham gia", android.widget.Toast.LENGTH_SHORT).show();
												} else {
													android.widget.Toast.makeText(requireContext(), "Đã tham gia nhóm", android.widget.Toast.LENGTH_SHORT).show();
												}
												groupViewModel.loadUserGroups();
											})
											.addOnFailureListener(e2 -> {
												android.util.Log.e("GroupsFragment", "Failed to join group: " + e2.getMessage());
												android.widget.Toast.makeText(requireContext(), e2.getMessage(), android.widget.Toast.LENGTH_LONG).show();
											});
								};
								
								if (needApproval) {
									new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
											.setTitle(getString(R.string.join_need_approval_title))
											.setMessage(getString(R.string.join_need_approval_text))
											.setPositiveButton(getString(R.string.ok), (dd, ww) -> doJoin.run())
											.setNegativeButton(getString(R.string.cancel), null)
											.show();
								} else {
									doJoin.run();
								}
							})
							.addOnFailureListener(e -> {
								android.util.Log.e("GroupsFragment", "Failed to check code: " + e.getMessage());
								android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
							});
				})
				.setNegativeButton("Hủy", null)
				.show();
	}

	private void filterGroups(String query) {
		filteredGroups.clear();
		if (query.isEmpty()) {
			filteredGroups.addAll(allGroups);
		} else {
			String lowerQuery = query.toLowerCase();
			for (Group group : allGroups) {
				if (group.name != null && group.name.toLowerCase().contains(lowerQuery)) {
					filteredGroups.add(group);
				}
			}
		}
		groupsAdapter.setGroups(filteredGroups);
	}
}