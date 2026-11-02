package com.hadisaleemi.i221043;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.hadisaleemi.i221043.services.CallWatcher;
import com.hadisaleemi.i221043.services.PushNotifier;
import com.hadisaleemi.i221043.services.PushSender;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Presence;

/**
 * Tracks whether any Kinnect screen is visible. That drives the online / offline status and
 * the in-app incoming-call listener.
 */
public class KinnectApp extends Application {

    private static int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!firebaseReady(this)) return;
        String emulator = BuildConfig.FIREBASE_EMULATOR_HOST;
        if (!emulator.isEmpty()) {
            // Debug builds can run against the local Firebase Emulator Suite (see IMPLEMENTATION.md).
            FirebaseAuth.getInstance().useEmulator(emulator, 9099);
            FirebaseDatabase.getInstance().useEmulator(emulator, 9000);
        }
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        PushNotifier.createChannels(this);
        PushSender.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivities++;
                if (startedActivities == 1) onForegroundChanged(true);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) onForegroundChanged(false);
            }

            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity a) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity a) {
            }
        });
    }

    private void onForegroundChanged(boolean fg) {
        if (Db.uid() == null) return;
        Presence.setForeground(fg);
        if (fg) CallWatcher.start(this);
    }

    public static boolean isInForeground() {
        return startedActivities > 0;
    }

    /** False until app/google-services.json has been added from the Firebase console. */
    public static boolean firebaseReady(Context c) {
        return !FirebaseApp.getApps(c).isEmpty();
    }
}
