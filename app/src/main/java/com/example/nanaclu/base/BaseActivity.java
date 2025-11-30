package com.example.nanaclu.base;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.NetworkUtils;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register network callback
        NetworkUtils.registerNetworkCallback(this);
        
        // Observe network status changes
        NetworkUtils.getNetworkStatus().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isConnected) {
                if (isConnected != null) {
                    if (isConnected) {
                        onNetworkRestored();
                    } else {
                        onNetworkLost();
                    }
                }
            }
        });
    }

    protected void onNetworkRestored() {
        // Can be overridden by child activities
        showNetworkMessage(getString(R.string.network_restored));
    }

    protected void onNetworkLost() {
        // Can be overridden by child activities
        showNetworkMessage(getString(R.string.no_internet_connection));
    }

    protected void showNetworkMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    protected boolean isNetworkAvailable() {
        return NetworkUtils.isNetworkAvailable(this);
    }
}
