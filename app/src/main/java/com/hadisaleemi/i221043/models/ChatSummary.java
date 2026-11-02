package com.hadisaleemi.i221043.models;

import com.google.firebase.database.IgnoreExtraProperties;

/** One row of userChats/{uid}/{otherUid}. */
@IgnoreExtraProperties
public class ChatSummary {
    public String chatId;
    public String otherUid;
    public String lastMessage;
    public String lastSenderId;
    public long timestamp;
    public int unread;

    public ChatSummary() {
    }
}
