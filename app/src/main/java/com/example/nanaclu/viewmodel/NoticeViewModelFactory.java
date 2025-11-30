package com.example.nanaclu.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.nanaclu.data.repository.NoticeRepository;

public class NoticeViewModelFactory implements ViewModelProvider.Factory {
    private final NoticeRepository repository;
    private final String currentUid;

    public NoticeViewModelFactory(NoticeRepository repository, String currentUid) {
        this.repository = repository;
        this.currentUid = currentUid;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NoticeViewModel.class)) {
            return (T) new NoticeViewModel(repository, currentUid);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
