package com.example.nanaclu;

import android.os.Bundle;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Route to auth if not logged in or remember-me is off
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean remember = getSharedPreferences("auth", MODE_PRIVATE).getBoolean("remember_me", false);
        if (user == null || !remember) {
            startActivity(new Intent(this, com.example.nanaclu.ui.auth.LoginActivity.class));
            return;
        }

        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        if (bottom != null) {
            bottom.setOnItemSelectedListener(item -> {
                Fragment f;
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    f = new com.example.nanaclu.ui.home.FeedFragment();
                } else if (id == R.id.nav_group) {
                    f = new com.example.nanaclu.ui.group.GroupsFragment();
                } else if (id == R.id.nav_chat) {
                    f = new com.example.nanaclu.ui.chat.ChatsFragment();
                } else {
                    f = new com.example.nanaclu.ui.profile.ProfileFragment();
                }
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_container, f)
                        .commit();
                return true;
            });
            bottom.setSelectedItemId(R.id.nav_home);
        }
    }
}