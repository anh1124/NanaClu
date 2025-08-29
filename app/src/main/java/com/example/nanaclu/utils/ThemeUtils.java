package com.example.nanaclu.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public final class ThemeUtils {
    private static final String PREF = "theme";
    private static final String KEY_TOOLBAR_COLOR = "toolbar_color";
    private static final int DEFAULT_COLOR = Color.parseColor("#6200EE"); // purple 500

    private ThemeUtils() {}

    public static void saveToolbarColor(Context context, int color) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_TOOLBAR_COLOR, color).apply();
    }

    public static int getToolbarColor(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getInt(KEY_TOOLBAR_COLOR, DEFAULT_COLOR);
    }
}


