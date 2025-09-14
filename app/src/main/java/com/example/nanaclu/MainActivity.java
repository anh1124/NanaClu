package com.example.nanaclu;

import android.os.Bundle;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

        // Auto-login routing based on preference
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean auto = getSharedPreferences("auth", MODE_PRIVATE).getBoolean("auto_login", true);
        if (!auto) {
            // Always show login screen regardless of previous state
            startActivity(new Intent(this, com.example.nanaclu.ui.auth.LoginActivity.class));
            finish();
            return;
        }
        if (user == null) {
            startActivity(new Intent(this, com.example.nanaclu.ui.auth.LoginActivity.class));
            finish();
            return;
        }
        // Refresh cached user profile on app start
        android.content.SharedPreferences up = getSharedPreferences("user_profile", MODE_PRIVATE);
        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
        up.edit()
                .putString("uid", user.getUid())
                .putString("displayName", user.getDisplayName())
                .putString("email", user.getEmail())
                .putString("photoUrl", photoUrl)
                .apply();

        // Update user's photoUrl in Firestore if it's not already saved
        com.example.nanaclu.data.repository.UserRepository userRepo =
                new com.example.nanaclu.data.repository.UserRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance());
        userRepo.updateUserPhotoUrl(user.getUid(), photoUrl);
        // Auto-login enabled and user exists -> go Home
        startActivity(new Intent(this, com.example.nanaclu.ui.HomeActivity.class));
        finish();
    }
}