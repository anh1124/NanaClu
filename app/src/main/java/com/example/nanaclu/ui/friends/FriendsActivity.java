package com.example.nanaclu.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.nanaclu.R;
import com.example.nanaclu.ui.friends.BlockedUsersActivity;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Activity hiển thị danh sách bạn bè và lời mời kết bạn
 * Sử dụng TabLayout + ViewPager2 với 2 tabs
 */
public class FriendsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        setupToolbar();
        setupViewPager();
        setupTabLayout();
    }

    private void setupToolbar() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_friends, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_blocked_users) {
            // Mở BlockedUsersActivity
            Intent intent = new Intent(this, BlockedUsersActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupViewPager() {
        viewPager = findViewById(R.id.viewPager);
        
        // Tạo adapter cho ViewPager2
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new FriendsListFragment();
                    case 1:
                        return new FriendRequestsFragment();
                    default:
                        return new FriendsListFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 2; // 2 tabs: Bạn bè và Lời mời
            }
        };
        
        viewPager.setAdapter(adapter);
    }

    private void setupTabLayout() {
        tabLayout = findViewById(R.id.tabLayout);
        
        // Kết nối TabLayout với ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Bạn bè");
                    break;
                case 1:
                    tab.setText("Lời mời");
                    break;
            }
        }).attach();
    }
}
