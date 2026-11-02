package com.hadisaleemi.i221043.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Message {
    public static final String TEXT = "text";
    public static final String IMAGE = "image";
    public static final String POST = "post";
    public static final String SYSTEM = "system";
    public static final String CALL = "call";
    public static final String STORY_REPLY = "story";

    /** Messages may only be edited or deleted for this long after sending. */
    public static final long EDIT_WINDOW_MS = 5 * 60 * 1000;

    public String id;
    public String senderId;
    public String type = TEXT;
    public String text;
    public String imageUrl;
    public String postId;
    public long timestamp;
    public boolean edited;
    public boolean deleted;
    public String replyToId;
    public String replyToText;
    public String replyToSender;
    /** For system messages: "screenshot" when someone captured the chat. */
    public String event;
    public Map<String, Boolean> hearts = new HashMap<>();

    public Message() {
    }

    @Exclude
    public boolean canModify(String myUid, long now) {
        return myUid != null && myUid.equals(senderId) && !deleted
                && !SYSTEM.equals(type) && !CALL.equals(type)
                && now - timestamp < EDIT_WINDOW_MS;
    }
}
