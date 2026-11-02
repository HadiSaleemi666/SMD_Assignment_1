package com.hadisaleemi.i221043.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.hadisaleemi.i221043.models.User;

/** Device-local preferences: the last account that logged in and recent searches. */
public final class Session {

    private static final String PREFS = "kinnect_session";

    private Session() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveRecentLogin(Context c, User u) {
        if (u == null) return;
        prefs(c).edit()
                .putString("recent_uid", u.uid)
                .putString("recent_email", u.email)
                .putString("recent_name", u.displayName())
                .putString("recent_photo", u.photoUrl)
                .apply();
    }

    public static String recentEmail(Context c) {
        return prefs(c).getString("recent_email", null);
    }

    public static String recentName(Context c) {
        return prefs(c).getString("recent_name", null);
    }

    public static String recentPhoto(Context c) {
        return prefs(c).getString("recent_photo", null);
    }

    public static String recentUid(Context c) {
        return prefs(c).getString("recent_uid", null);
    }

    /** Recent searches are stored newest first, one per line. */
    public static String recentSearches(Context c) {
        return prefs(c).getString("recent_searches", "");
    }

    public static void setRecentSearches(Context c, String joined) {
        prefs(c).edit().putString("recent_searches", joined).apply();
    }
}
