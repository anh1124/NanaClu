package com.example.nanaclu.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.data.repository.GroupRepository;

public class GroupViewModelFactory implements ViewModelProvider.Factory {
    private final GroupRepository groupRepository;

    public GroupViewModelFactory(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(GroupViewModel.class)) {
            return (T) new GroupViewModel(groupRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
