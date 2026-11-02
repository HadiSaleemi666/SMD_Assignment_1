package com.hadisaleemi.i221043.models;

import com.google.firebase.database.IgnoreExtraProperties;

/** Signalling record written to calls/{calleeUid} and copied to callLogs/{uid}/{callId}. */
@IgnoreExtraProperties
public class CallInfo {
    public static final String RINGING = "ringing";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";
    public static final String ENDED = "ended";
    public static final String MISSED = "missed";

    public String callId;
    public String callerUid;
    public String calleeUid;
    public String channel;
    public boolean video;
    public String status;
    public long timestamp;
    public long duration;

    public CallInfo() {
    }
}
