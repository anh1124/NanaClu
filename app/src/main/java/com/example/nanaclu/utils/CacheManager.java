package com.example.nanaclu.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class to manage cache clearing operations when users log out.
 * Clears SharedPreferences, Glide image cache, and Firebase Firestore offline cache.
 */
public class CacheManager {
    private static final String TAG = "CacheManager";
    
    // SharedPreferences file names to clear
    private static final String[] PREF_FILES = {
        "auth",
        "user_profile", 
        "security",
        "theme_prefs"
    };
    
    private final Context context;
    private final ExecutorService executorService;
    
    public CacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Clear all app caches asynchronously.
     * This includes SharedPreferences, Glide cache, and Firebase Firestore offline cache.
     * 
     * @return Task that completes when all caches are cleared
     */
    public Task<Void> clearAllCaches() {
        Log.d(TAG, "Starting cache clearing process...");
        
        return Tasks.call(executorService, () -> {
            try {
                // Clear SharedPreferences
                clearSharedPreferences();
                
                // Clear Glide cache
                clearGlideCache();
                
                // Clear Firebase Firestore offline cache
                clearFirestoreCache();
                
                Log.d(TAG, "All caches cleared successfully");
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error clearing caches", e);
                throw e;
            }
        });
    }
    
    /**
     * Clear all SharedPreferences files used by the app.
     */
    private void clearSharedPreferences() {
        Log.d(TAG, "Clearing SharedPreferences...");
        
        for (String prefName : PREF_FILES) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();
                Log.d(TAG, "Cleared SharedPreferences: " + prefName);
            } catch (Exception e) {
                Log.w(TAG, "Failed to clear SharedPreferences: " + prefName, e);
            }
        }
    }
    
    /**
     * Clear Glide image cache (both memory and disk).
     */
    private void clearGlideCache() {
        Log.d(TAG, "Clearing Glide cache...");
        
        try {
            // Clear memory cache on main thread
            Tasks.await(Tasks.call(() -> {
                Glide.get(context).clearMemory();
                return null;
            }));
            
            // Clear disk cache on background thread
            Glide.get(context).clearDiskCache();
            
            Log.d(TAG, "Glide cache cleared successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear Glide cache", e);
        }
    }
    
    /**
     * Clear Firebase Firestore offline cache.
     * Note: This must be called after signing out from Firebase Auth.
     */
    private void clearFirestoreCache() {
        Log.d(TAG, "Clearing Firebase Firestore offline cache...");
        
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            // Disable network first
            Tasks.await(db.disableNetwork());
            
            // Clear persistence
            Tasks.await(db.clearPersistence());
            
            // Re-enable network
            Tasks.await(db.enableNetwork());
            
            Log.d(TAG, "Firebase Firestore cache cleared successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear Firebase Firestore cache", e);
            // Try to re-enable network even if clearing failed
            try {
                FirebaseFirestore.getInstance().enableNetwork();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to re-enable Firebase network", ex);
            }
        }
    }
    
    /**
     * Clear only SharedPreferences (for cases where Firebase cache clearing is not needed).
     */
    public void clearSharedPreferencesOnly() {
        Log.d(TAG, "Clearing SharedPreferences only...");
        clearSharedPreferences();
    }
    
    /**
     * Clear only Glide cache (for cases where other caches are not needed).
     */
    public void clearGlideCacheOnly() {
        Log.d(TAG, "Clearing Glide cache only...");
        clearGlideCache();
    }
    
    /**
     * Shutdown the executor service when done.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
