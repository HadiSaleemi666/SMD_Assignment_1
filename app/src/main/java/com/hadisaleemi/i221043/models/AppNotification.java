package com.hadisaleemi.i221043.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class AppNotification {
    public static final String REACTION = "reaction";
    public static final String COMMENT = "comment";
    public static final String REPLY = "reply";
    public static final String COMMENT_LIKE = "comment_like";
    public static final String FRIEND_REQUEST = "friend_request";
    public static final String FRIEND_ACCEPT = "friend_accept";
    public static final String STORY_REACTION = "story_reaction";
    public static final String SCREENSHOT = "screenshot";

    public String id;
    public String type;
    public String fromUid;
    public String postId;
    public String text;
    public long timestamp;
    public boolean read;

    public AppNotification() {
    }
}
