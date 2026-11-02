package com.hadisaleemi.i221043.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Story {
    public static final long LIFETIME_MS = 24L * 60 * 60 * 1000;

    public String id;
    public String uid;
    public String mediaUrl;
    /** "image" or "video". */
    public String mediaType;
    public String text;
    /** Label position as a fraction of the story card. */
    public float textX = 0.5f;
    public float textY = 0.45f;
    /** "public" (Your story) or "close" (Close friends). */
    public String audience;
    public long timestamp;
    public long expiresAt;

    public Story() {
    }

    @Exclude
    public boolean isVideo() {
        return "video".equals(mediaType);
    }

    public boolean isActive(long now) {
        return expiresAt > now;
    }
}
