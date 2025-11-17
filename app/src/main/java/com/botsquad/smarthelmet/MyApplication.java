package com.botsquad.smarthelmet;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("MyApplication", "=== MyApplication.onCreate() STARTED ===");
        
        try {
            Log.d("MyApplication", "Step 1: Calling super.onCreate()");
            // super.onCreate() is already called, but logging for clarity
            
            Log.d("MyApplication", "Step 2: Checking Firebase initialization");
            // Initialize Firebase if not already initialized
            try {
                java.util.List<FirebaseApp> apps = FirebaseApp.getApps(this);
                Log.d("MyApplication", "Step 3: Firebase apps count: " + apps.size());
                
                if (apps.isEmpty()) {
                    Log.d("MyApplication", "Step 4: Initializing Firebase...");
                    FirebaseApp.initializeApp(this);
                    Log.d("MyApplication", "Step 5: Firebase initialized successfully");
                } else {
                    Log.d("MyApplication", "Step 4: Firebase already initialized (" + apps.size() + " app(s))");
                }
            } catch (Exception e) {
                Log.e("MyApplication", "Step 3-5 ERROR: Firebase initialization failed", e);
                throw e; // Re-throw to be caught by outer catch
            }
            
            // Initialize App Check with error handling
            // App Check helps prevent Firebase from blocking the device
            Log.d("MyApplication", "Step 6: Starting App Check initialization");
            try {
                FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
                Log.d("MyApplication", "Step 7: Got FirebaseAppCheck instance");
                
                PlayIntegrityAppCheckProviderFactory playIntegrityFactory = PlayIntegrityAppCheckProviderFactory.getInstance();
                Log.d("MyApplication", "Step 8: Got PlayIntegrityAppCheckProviderFactory instance");
                
                firebaseAppCheck.installAppCheckProviderFactory(playIntegrityFactory);
                Log.d("MyApplication", "Step 9: App Check initialized successfully with Play Integrity");
            } catch (NoClassDefFoundError e) {
                Log.w("MyApplication", "Step 6-9 WARNING: Play Integrity not available (expected on some devices): " + e.getMessage());
                Log.w("MyApplication", "Stack trace:", e);
                // App Check failure is not critical - app can still work without it
            } catch (Exception e) {
                Log.e("MyApplication", "Step 6-9 ERROR: App Check initialization failed (non-critical): " + e.getMessage(), e);
                // App Check failure is not critical - app can still work without it
            }
            
            Log.d("MyApplication", "=== MyApplication.onCreate() COMPLETED SUCCESSFULLY ===");
            
        } catch (Throwable e) {
            Log.e("MyApplication", "=== CRITICAL ERROR in MyApplication.onCreate() ===", e);
            Log.e("MyApplication", "Error type: " + e.getClass().getName());
            Log.e("MyApplication", "Error message: " + e.getMessage());
            if (e.getCause() != null) {
                Log.e("MyApplication", "Caused by: " + e.getCause(), e.getCause());
            }
            Log.e("MyApplication", "Stack trace:", e);
            // Don't crash - let the app try to continue
            // But log everything so we can see what went wrong
        }
    }
}

