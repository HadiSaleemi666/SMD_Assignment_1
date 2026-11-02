package com.hadisaleemi.i221043.util;

import android.app.Activity;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.util.Locale;

/**
 * Detects screenshots taken while the chat is on screen.
 *
 * Android 14+ : Activity.registerScreenCaptureCallback (DETECT_SCREEN_CAPTURE permission).
 * Older      : watches MediaStore for a new image whose name or folder contains "screenshot"
 *              (needs read access to images, which the photo picker already asks for).
 */
public class ScreenshotDetector {

    private static final long DEBOUNCE_MS = 3000;
    private static final long RECENT_S = 15;

    private final Activity activity;
    private final Runnable onScreenshot;
    private ContentObserver observer;
    private Object captureCallback; // Activity.ScreenCaptureCallback on API 34+
    private long lastFired;

    public ScreenshotDetector(Activity activity, Runnable onScreenshot) {
        this.activity = activity;
        this.onScreenshot = onScreenshot;
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Activity.ScreenCaptureCallback cb = this::fire;
            captureCallback = cb;
            activity.registerScreenCaptureCallback(activity.getMainExecutor(), cb);
            return;
        }
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (isNewScreenshot(uri)) fire();
            }
        };
        activity.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
    }

    public void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && captureCallback != null) {
            activity.unregisterScreenCaptureCallback((Activity.ScreenCaptureCallback) captureCallback);
            captureCallback = null;
        }
        if (observer != null) {
            activity.getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
    }

    private boolean isNewScreenshot(Uri uri) {
        if (uri == null) uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED};
        try (Cursor c = activity.getContentResolver().query(uri, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c == null || !c.moveToFirst()) return false;
            String name = c.getString(0);
            String path = c.getString(1);
            long added = c.getLong(2);
            String all = ((name == null ? "" : name) + " " + (path == null ? "" : path)).toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis() / 1000;
            return all.contains("screenshot") && now - added <= RECENT_S;
        } catch (Exception e) {
            return false;
        }
    }

    private void fire() {
        long now = System.currentTimeMillis();
        if (now - lastFired < DEBOUNCE_MS) return;
        lastFired = now;
        activity.runOnUiThread(onScreenshot);
    }
}
