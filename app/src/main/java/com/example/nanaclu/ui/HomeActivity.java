package com.example.nanaclu.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.NoticeCenter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {
    private int[] tabOrder = new int[]{R.id.nav_home, R.id.nav_group, R.id.nav_chat, R.id.nav_me};
    private NoticeCenter noticeCenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Start NoticeCenter
        noticeCenter = NoticeCenter.getInstance();
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUid != null) {
            noticeCenter.start(currentUid, getApplication());
        }

        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        if (bottom != null) {
            // Disable color tint to show original icon colors
            bottom.setItemIconTintList(null);
            bottom.setItemTextColor(null);
            
            bottom.setOnItemSelectedListener(item -> {
                Fragment f;
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    f = new com.example.nanaclu.ui.home.FeedFragment();
                } else if (id == R.id.nav_group) {
                    f = new com.example.nanaclu.ui.group.GroupsFragment();
                } else if (id == R.id.nav_chat) {
                    f = new com.example.nanaclu.ui.chat.ChatFragment();
                } else {
                    f = new com.example.nanaclu.ui.profile.ProfileFragment();
                }
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_container, f)
                        .commit();
                return true;
            });
            String startTab = getIntent().getStringExtra("start_tab");
            if ("chat".equals(startTab)) {
                bottom.setSelectedItemId(R.id.nav_chat);
            } else if ("groups".equals(startTab)) {
                bottom.setSelectedItemId(R.id.nav_group);
            } else if ("me".equals(startTab)) {
                bottom.setSelectedItemId(R.id.nav_me);
            } else {
                bottom.setSelectedItemId(R.id.nav_home);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop NoticeCenter
        if (noticeCenter != null) {
            noticeCenter.stop();
        }
    }

    public void navigateToNextTab() {
        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        if (bottom == null) return;
        int currentId = bottom.getSelectedItemId();
        int idx = 0;
        for (int i = 0; i < tabOrder.length; i++) if (tabOrder[i] == currentId) { idx = i; break; }
        int nextIdx = (idx + 1) % tabOrder.length;
        bottom.setSelectedItemId(tabOrder[nextIdx]);
    }

    public void navigateToPrevTab() {
        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        if (bottom == null) return;
        int currentId = bottom.getSelectedItemId();
        int idx = 0;
        for (int i = 0; i < tabOrder.length; i++) if (tabOrder[i] == currentId) { idx = i; break; }
        int prevIdx = (idx - 1 + tabOrder.length) % tabOrder.length;
        bottom.setSelectedItemId(tabOrder[prevIdx]);
    }
}


