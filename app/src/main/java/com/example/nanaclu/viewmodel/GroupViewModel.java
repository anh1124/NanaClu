package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;

import java.util.ArrayList;
import java.util.List;

public class GroupViewModel extends ViewModel {
    private final GroupRepository groupRepository;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    private final MutableLiveData<List<Group>> _userGroups = new MutableLiveData<>();
    private String currentUserId;

    // Expose LiveData for the view to observe
    public final LiveData<Boolean> isLoading = _isLoading;
    public final LiveData<String> error = _error;
    public final LiveData<List<Group>> userGroups = _userGroups;

    public GroupViewModel(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public void setCurrentUser(String userId) {
        this.currentUserId = userId;
        loadUserGroups();
    }

    public void createGroup(String name, boolean isPublic) {
        if (currentUserId == null) {
            _error.setValue("User not authenticated");
            return;
        }

        _isLoading.setValue(true);
        _error.setValue(null);

        Group group = new Group();
        group.groupId = java.util.UUID.randomUUID().toString();
        group.name = name;
        group.isPublic = isPublic;
        group.createdAt = System.currentTimeMillis();
        group.memberCount = 1;
        group.postCount = 0;
        group.createdBy = currentUserId;
        group.description = "";
        group.avatarImageId = null;
        group.coverImageId = null;

        groupRepository.generateUniqueCode(6)
                .addOnSuccessListener(code -> {
                    group.code = code;
                    groupRepository.createGroup(group)
                            .addOnSuccessListener(aVoid -> {
                                _isLoading.setValue(false);
                                loadUserGroups();
                            })
                            .addOnFailureListener(e -> {
                                _isLoading.setValue(false);
                                _error.setValue("Failed to create group: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    _isLoading.setValue(false);
                    _error.setValue("Failed to generate code: " + e.getMessage());
                });
    }

    public void loadUserGroups() {
        if (currentUserId == null) {
            _error.setValue("User not authenticated");
            return;
        }

        _isLoading.setValue(true);
        _error.setValue(null);

        groupRepository.loadUserGroups()
                .addOnSuccessListener(groups -> {
                    _isLoading.setValue(false);
                    if (groups != null) {
                        _userGroups.setValue(groups);
                    } else {
                        _userGroups.setValue(new ArrayList<>());
                    }
                })
                .addOnFailureListener(e -> {
                    _isLoading.setValue(false);
                    _error.setValue("Failed to load groups: " + e.getMessage());
                });
    }

    // No need for getters as we're exposing LiveData fields directly
}


