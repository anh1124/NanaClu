package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.repository.GroupRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;

public class GroupViewModel extends ViewModel {
    private final GroupRepository repository = new GroupRepository();

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    private final MutableLiveData<String> _createdGroupId = new MutableLiveData<>(null);

    public LiveData<Boolean> loading = _loading;
    public LiveData<String> error = _error;
    public LiveData<String> createdGroupId = _createdGroupId;

    public void createGroup(String name, boolean isPublic) {
        _loading.setValue(true);
        _error.setValue(null);
        _createdGroupId.setValue(null);
        Task<DocumentReference> t = repository.createGroup(name, isPublic);
        t.addOnSuccessListener(ref -> {
            _loading.setValue(false);
            _createdGroupId.setValue(ref.getId());
        }).addOnFailureListener(e -> {
            _loading.setValue(false);
            _error.setValue(e.getMessage());
        });
    }
}


