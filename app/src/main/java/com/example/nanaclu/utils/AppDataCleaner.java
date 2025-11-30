package com.example.nanaclu.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.Objects;

public class AppDataCleaner {
    private static final String TAG = "AppDataCleaner";
    private static final String ADMIN_UID = "YOUR_ADMIN_UID_HERE"; // Thay bằng UID của bạn

    public static boolean isAdmin() {
        String userId = FirebaseAuth.getInstance().getUid();
        return userId != null && userId.equals(ADMIN_UID);
    }

    public static void clearAllDataAndShutdown(Context context) {
        try {
            // 1. Clear Firestore
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.terminate().addOnCompleteListener(task -> {
                firestore.clearPersistence()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore cache cleared"))
                    .addOnFailureListener(e -> Log.e(TAG, "Clear failed", e));
            });

            // 2. Clear all SharedPreferences
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists() && prefsDir.isDirectory()) {
                for (File file : Objects.requireNonNull(prefsDir.listFiles())) {
                    String name = file.getName().replace(".xml", "");
                    context.getSharedPreferences(name, Context.MODE_PRIVATE)
                           .edit().clear().apply();
                    file.delete();
                }
            }

            // 3. Clear cache
            deleteRecursive(context.getCacheDir());
            deleteRecursive(context.getExternalCacheDir());

            // 4. Kill the app
            Process.killProcess(Process.myPid());
            System.exit(0);
        } catch (Exception e) {
            Log.e(TAG, "Cleanup failed", e);
            System.exit(0);
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
