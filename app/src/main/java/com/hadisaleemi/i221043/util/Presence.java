package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Online / offline status. users/{uid}/online is true while Kinnect is in the foreground; an
 * onDisconnect hook flips it to false (and stamps lastSeen) if the connection drops or the app
 * is killed, so other users never see a stale green dot.
 */
public final class Presence {

    private static DatabaseReference connectedRef;
    private static ValueEventListener connectedListener;
    private static String trackedUid;
    private static boolean foreground;

    private Presence() {
    }

    public static synchronized void start() {
        String uid = Db.uid();
        if (uid == null || uid.equals(trackedUid)) return;
        stopListening();
        trackedUid = uid;
        DatabaseReference me = Db.user(uid);
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected == null || !connected) return;
                me.child("online").onDisconnect().setValue(false);
                me.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP);
                write(uid, foreground);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    public static synchronized void setForeground(boolean fg) {
        foreground = fg;
        String uid = Db.uid();
        if (uid != null) {
            if (!uid.equals(trackedUid)) start();
            write(uid, fg);
        }
    }

    /**
     * Marks the user offline, removes this device's push token and stops tracking; called on
     * log out before signing out so the write still carries the user's credentials.
     */
    public static synchronized Task<Void> goOffline() {
        String uid = trackedUid != null ? trackedUid : Db.uid();
        stopListening();
        if (uid == null) return Tasks.forResult(null);
        Db.user(uid).child("online").onDisconnect().cancel();
        Db.user(uid).child("lastSeen").onDisconnect().cancel();
        Map<String, Object> up = new HashMap<>();
        up.put("online", false);
        up.put("lastSeen", ServerValue.TIMESTAMP);
        up.put("fcmToken", null);
        return Db.user(uid).updateChildren(up);
    }

    private static void stopListening() {
        if (connectedRef != null && connectedListener != null) {
            connectedRef.removeEventListener(connectedListener);
        }
        connectedRef = null;
        connectedListener = null;
        trackedUid = null;
    }

    private static void write(String uid, boolean online) {
        Map<String, Object> up = new HashMap<>();
        up.put("online", online);
        up.put("lastSeen", ServerValue.TIMESTAMP);
        Db.user(uid).updateChildren(up);
    }
}
