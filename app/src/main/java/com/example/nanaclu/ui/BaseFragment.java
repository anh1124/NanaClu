package com.example.nanaclu.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.nanaclu.utils.ThemeUtils;

public abstract class BaseFragment extends Fragment {
    private BroadcastReceiver themeChangeReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(themeChangeReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(themeChangeReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (themeChangeReceiver != null && getContext() != null) {
            getContext().unregisterReceiver(themeChangeReceiver);
        }
    }

    protected abstract void onThemeChanged();
}
