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

        // Route to auth if not logged in or remember-me is off
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean remember = getSharedPreferences("auth", MODE_PRIVATE).getBoolean("remember_me", false);
        if (user == null || !remember) {
            startActivity(new Intent(this, com.example.nanaclu.ui.auth.LoginActivity.class));
            return;
        }

        // Nếu đã đăng nhập và remember, điều hướng vào HomeActivity (chứa navbar)
        startActivity(new Intent(this, com.example.nanaclu.ui.HomeActivity.class));
        finish();
    }
}