package com.hadisaleemi.i221043.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class TimeFmt {

    private TimeFmt() {
    }

    /** Short relative time used on posts, comments and notifications: now, 5m, 3h, 2d, 1w. */
    public static String ago(long ts) {
        long diff = Math.max(0, System.currentTimeMillis() - ts);
        long m = diff / 60000;
        if (m < 1) return "now";
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 24) return h + "h";
        long d = h / 24;
        if (d < 7) return d + "d";
        long w = d / 7;
        if (w < 52) return w + "w";
        return (d / 365) + "y";
    }

    /** Time shown in the chat list: 2m, 3h today, Mon within a week, else 12 Mar. */
    public static String chatList(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        long diff = System.currentTimeMillis() - ts;
        if (diff < 60000) return "now";
        if (diff < 3600000) return (diff / 60000) + "m";
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return (diff / 3600000) + "h";
        }
        if (diff < 7L * 24 * 3600000) {
            return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(ts));
        }
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(ts));
    }

    /** Divider inside a conversation, e.g. TODAY 4:49 PM. */
    public static String chatDivider(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(ts));
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return "TODAY " + time;
        }
        String day = new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(ts));
        return day.toUpperCase(Locale.getDefault()) + " " + time;
    }

    public static String lastSeen(boolean online, long lastSeen) {
        if (online) return "Active now";
        if (lastSeen <= 0) return "Offline";
        String ago = ago(lastSeen);
        return "now".equals(ago) ? "Active just now" : "Active " + ago + " ago";
    }

    public static String duration(long seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String joined(long ts) {
        if (ts <= 0) return "Joined Kinnect";
        return "Joined " + new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(ts));
    }
}
