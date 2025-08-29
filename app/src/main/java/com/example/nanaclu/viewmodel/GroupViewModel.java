package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Group;
import com.example.nanaclu.data.repository.GroupRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.ArrayList;

public class GroupViewModel extends ViewModel {
    private GroupRepository groupRepository;
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> error = new MutableLiveData<>();
    private MutableLiveData<List<Group>> userGroups = new MutableLiveData<>();

    public GroupViewModel() {
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());
    }

    public void createGroup(String name, boolean isPublic) {
        isLoading.setValue(true);
        error.setValue(null);

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        System.out.println("GroupViewModel: Creating group with name: " + name + " for user: " + userId);

        Group group = new Group();
        group.groupId = java.util.UUID.randomUUID().toString();
        group.name = name;
        group.isPublic = isPublic;
        group.createdAt = System.currentTimeMillis();
        group.memberCount = 1;
        group.postCount = 0;
        group.createdBy = userId;
        group.description = "";
        group.avatarImageId = null;
        group.coverImageId = null;

        System.out.println("GroupViewModel: Group object created: " + group.name + " (ID: " + group.groupId + ", createdBy: " + group.createdBy + ")");

        groupRepository.createGroup(group)
                .addOnSuccessListener(aVoid -> {
                    isLoading.setValue(false);
                    System.out.println("GroupViewModel: Group created successfully, reloading groups");
                    // Reload user groups after creating
                    loadUserGroups();
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    error.setValue("Failed to create group: " + e.getMessage());
                    System.out.println("GroupViewModel: Failed to create group: " + e.getMessage());
                });
    }

    public void loadUserGroups() {
        isLoading.setValue(true);
        error.setValue(null);
        
        System.out.println("GroupViewModel: Starting to load user groups");
        System.out.println("GroupViewModel: Current user ID: " + FirebaseAuth.getInstance().getCurrentUser().getUid());

        groupRepository.loadUserGroups()
                .addOnSuccessListener(groups -> {
                    isLoading.setValue(false);
                    System.out.println("GroupViewModel: Repository returned " + (groups != null ? groups.size() : "null") + " groups");
                    
                    if (groups != null) {
                        userGroups.setValue(groups);
                        System.out.println("GroupViewModel: Successfully set " + groups.size() + " groups to LiveData");
                        
                        // Debug: print each group
                        for (Group group : groups) {
                            System.out.println("GroupViewModel: Group in list - name: " + group.name + ", ID: " + group.groupId + ", createdBy: " + group.createdBy);
                        }
                    } else {
                        userGroups.setValue(new ArrayList<>());
                        System.out.println("GroupViewModel: Repository returned null, setting empty list");
                    }
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    error.setValue("Failed to load groups: " + e.getMessage());
                    System.out.println("GroupViewModel: Error loading groups: " + e.getMessage());
                    e.printStackTrace();
                });
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<List<Group>> getUserGroups() {
        return userGroups;
    }
}


