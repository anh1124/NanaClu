package com.example.nanaclu.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

public final class ThemeUtils {
    private static final String PREF = "theme";
    private static final String KEY_THEME_COLOR = "theme_color";
    private static final int DEFAULT_COLOR = Color.parseColor("#6200EE"); // purple 500
    public static final String ACTION_THEME_CHANGED = "com.example.nanaclu.THEME_CHANGED";

    private ThemeUtils() {}

    public static void saveThemeColor(Context context, int color) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_THEME_COLOR, color).apply();
        
        // Broadcast theme change
        Intent intent = new Intent(ACTION_THEME_CHANGED);
        intent.putExtra("theme_color", color);
        context.sendBroadcast(intent);
    }

    public static int getThemeColor(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getInt(KEY_THEME_COLOR, DEFAULT_COLOR);
    }

    // Backward compatibility methods
    @Deprecated
    public static void saveToolbarColor(Context context, int color) {
        saveThemeColor(context, color);
    }

    @Deprecated
    public static int getToolbarColor(Context context) {
        return getThemeColor(context);
    }

    // Method to apply theme color to toolbar
    public static void applyThemeToToolbar(androidx.appcompat.widget.Toolbar toolbar, Context context) {
        if (toolbar != null) {
            int themeColor = getThemeColor(context);
            toolbar.setBackgroundColor(themeColor);
            toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        }
    }

    // Method to apply theme color to MaterialToolbar
    public static void applyThemeToMaterialToolbar(com.google.android.material.appbar.MaterialToolbar toolbar, Context context) {
        if (toolbar != null) {
            int themeColor = getThemeColor(context);
            toolbar.setBackgroundColor(themeColor);
            toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        }
    }
}


