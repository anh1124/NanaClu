package com.example.nanaclu.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nanaclu.utils.ThemeUtils;

public abstract class BaseActivity extends AppCompatActivity {
    private BroadcastReceiver themeChangeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register theme change receiver
        themeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ThemeUtils.ACTION_THEME_CHANGED.equals(intent.getAction())) {
                    onThemeChanged();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(ThemeUtils.ACTION_THEME_CHANGED);
        registerReceiver(themeChangeReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (themeChangeReceiver != null) {
            unregisterReceiver(themeChangeReceiver);
        }
    }

    protected abstract void onThemeChanged();
}
