package com.example.nanaclu.ui.event;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class GroupEventActivity extends AppCompatActivity {

    private String groupId;
    private String groupName;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private FloatingActionButton fabCreateEvent;
    private EventListFragment eventListFragment;
    private EventCalendarFragment eventCalendarFragment;

    private ActivityResultLauncher<Intent> createEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Event created successfully, refresh both fragments
                    if (eventListFragment != null) {
                        eventListFragment.refreshEvents();
                    }
                    if (eventCalendarFragment != null) {
                        eventCalendarFragment.refreshEvents();
                    }
                }
            }
    );
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_event);
        
        // Get data from intent
        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        
        if (groupId == null) {
            finish();
            return;
        }
        
        setupToolbar();
        setupViewPager();
        setupFab();
    }
    
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        toolbar.setTitle("Sự kiện - " + (groupName != null ? groupName : "Nhóm"));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Tint navigation icon to white
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(
                    android.graphics.Color.WHITE, 
                    android.graphics.PorterDuff.Mode.SRC_ATOP
            );
        }
    }
    
    private void setupViewPager() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        
        EventPagerAdapter adapter = new EventPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Danh sách");
                    break;
                case 1:
                    tab.setText("Lịch");
                    break;
            }
        }).attach();
        
        // Add click listeners to tabs for refresh functionality
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Tab selected, no refresh needed here
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Tab unselected
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // User clicked on already selected tab - refresh the fragment
                refreshCurrentFragment(tab.getPosition());
            }
        });
    }
    
    private void setupFab() {
        fabCreateEvent = findViewById(R.id.fabCreateEvent);
        fabCreateEvent.setOnClickListener(v -> {
            // Open create event activity
            Intent intent = new Intent(this, CreateEventActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("groupName", groupName);
            createEventLauncher.launch(intent);
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Refresh both fragments
            if (eventListFragment != null) {
                eventListFragment.refreshEvents();
            }
            if (eventCalendarFragment != null) {
                eventCalendarFragment.refreshEvents();
            }
        }
    }
    
    private void refreshCurrentFragment(int position) {
        switch (position) {
            case 0:
                // Refresh list fragment
                if (eventListFragment != null) {
                    eventListFragment.refreshEvents();
                }
                break;
            case 1:
                // Refresh calendar fragment
                if (eventCalendarFragment != null) {
                    eventCalendarFragment.refreshEvents();
                }
                break;
        }
    }
    
    private class EventPagerAdapter extends FragmentStateAdapter {
        
        public EventPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }
        
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    eventListFragment = EventListFragment.newInstance(groupId);
                    return eventListFragment;
                case 1:
                    eventCalendarFragment = EventCalendarFragment.newInstance(groupId);
                    return eventCalendarFragment;
                default:
                    eventListFragment = EventListFragment.newInstance(groupId);
                    return eventListFragment;
            }
        }
        
        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
