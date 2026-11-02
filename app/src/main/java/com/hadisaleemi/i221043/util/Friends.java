package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.services.PushSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Friend-request state machine. Every change is a single multi-path update so both users'
 * nodes always agree:
 *
 *   send     friendRequests/{other}/{me} + sentRequests/{me}/{other}
 *   accept   friends/{me}/{other} + friends/{other}/{me}, request nodes removed
 *   delete   request nodes removed
 */
public final class Friends {

    public static final int NONE = 0;
    public static final int REQUESTED = 1;
    public static final int INCOMING = 2;
    public static final int FRIENDS = 3;
    public static final int SELF = 4;

    public interface Handle {
        void remove();
    }

    private Friends() {
    }

    public static void sendRequest(String other) {
        String me = Db.uid();
        if (me == null || other == null || other.equals(me)) return;
        long now = System.currentTimeMillis();
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + other + "/" + me, now);
        up.put("sentRequests/" + me + "/" + other, now);
        Db.root().updateChildren(up);
        Notifs.send(other, AppNotification.FRIEND_REQUEST, null, "sent you a friend request.");
        PushSender.friendRequest(other);
    }

    public static void cancelRequest(String other) {
        String me = Db.uid();
        if (me == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + other + "/" + me, null);
        up.put("sentRequests/" + me + "/" + other, null);
        Db.root().updateChildren(up);
    }

    public static void accept(String other) {
        String me = Db.uid();
        if (me == null) return;
        long now = System.currentTimeMillis();
        Map<String, Object> up = new HashMap<>();
        up.put("friends/" + me + "/" + other, now);
        up.put("friends/" + other + "/" + me, now);
        up.put("friendRequests/" + me + "/" + other, null);
        up.put("sentRequests/" + other + "/" + me, null);
        Db.root().updateChildren(up);
        Notifs.send(other, AppNotification.FRIEND_ACCEPT, null, "accepted your friend request.");
    }

    public static void decline(String other) {
        String me = Db.uid();
        if (me == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("friendRequests/" + me + "/" + other, null);
        up.put("sentRequests/" + other + "/" + me, null);
        Db.root().updateChildren(up);
    }

    public static void unfriend(String other) {
        String me = Db.uid();
        if (me == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("friends/" + me + "/" + other, null);
        up.put("friends/" + other + "/" + me, null);
        up.put("closeFriends/" + me + "/" + other, null);
        Db.root().updateChildren(up);
    }

    /** Runs the right action for the Add friend button in its current state. */
    public static void primaryAction(String other, int state) {
        if (state == NONE) sendRequest(other);
        else if (state == REQUESTED) cancelRequest(other);
        else if (state == INCOMING) accept(other);
    }

    public static String label(int state) {
        switch (state) {
            case REQUESTED: return "Requested";
            case INCOMING: return "Confirm";
            case FRIENDS: return "Friends";
            default: return "Add friend";
        }
    }

    /** Live friendship state between the signed-in user and {@code other}. */
    public static Handle observe(String other, Callback<Integer> cb) {
        String me = Db.uid();
        if (me == null || other == null) return () -> { };
        if (me.equals(other)) {
            cb.onResult(SELF);
            return () -> { };
        }
        final boolean[] flags = new boolean[3]; // friends, requested, incoming
        DatabaseReference[] refs = {
                Db.friends(me).child(other),
                Db.sentRequests(me).child(other),
                Db.friendRequests(me).child(other)
        };
        ValueEventListener[] listeners = new ValueEventListener[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            listeners[i] = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot s) {
                    flags[idx] = s.exists();
                    if (flags[0]) cb.onResult(FRIENDS);
                    else if (flags[2]) cb.onResult(INCOMING);
                    else if (flags[1]) cb.onResult(REQUESTED);
                    else cb.onResult(NONE);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            refs[i].addValueEventListener(listeners[i]);
        }
        return () -> {
            for (int i = 0; i < 3; i++) refs[i].removeEventListener(listeners[i]);
        };
    }
}
