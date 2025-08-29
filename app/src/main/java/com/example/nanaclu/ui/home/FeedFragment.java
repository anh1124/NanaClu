package com.example.nanaclu.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;

public class FeedFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_feed, container, false);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setBackgroundColor(ThemeUtils.getToolbarColor(requireContext()));
        return root;
    }
}


