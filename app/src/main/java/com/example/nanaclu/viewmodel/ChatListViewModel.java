package com.example.nanaclu.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nanaclu.data.model.Chat;
import com.example.nanaclu.data.repository.ChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChatListViewModel extends ViewModel {
    private final ChatRepository repo;
    private final MutableLiveData<List<Chat>> _threads = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<Chat>> threads = _threads;

    private Chat lastItem; // simple pagination anchor

    public ChatListViewModel() {
        this.repo = new ChatRepository(FirebaseFirestore.getInstance());
    }

    public void refresh() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        repo.listUserChats(uid, 30, null).addOnSuccessListener(list -> {
            _threads.postValue(list);
            lastItem = list.isEmpty() ? null : list.get(list.size() - 1);
        });
    }

    public void paginate() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null || lastItem == null) return;
        repo.listUserChats(uid, 30, lastItem).addOnSuccessListener(list -> {
            List<Chat> cur = new ArrayList<>(_threads.getValue() != null ? _threads.getValue() : new ArrayList<>());
            cur.addAll(list);
            _threads.postValue(cur);
            lastItem = list.isEmpty() ? lastItem : list.get(list.size() - 1);
        });
    }
}

