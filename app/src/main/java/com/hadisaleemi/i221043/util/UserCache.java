package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a live copy of every user profile the app has asked for, so list rows can show names
 * and avatars without each row opening its own listener.
 */
public final class UserCache {

    private static final Map<String, User> CACHE = new HashMap<>();
    private static final Map<String, List<Callback<User>>> PENDING = new HashMap<>();
    private static final Map<String, ValueEventListener> LISTENERS = new HashMap<>();

    private UserCache() {
    }

    public static User peek(String uid) {
        return uid == null ? null : CACHE.get(uid);
    }

    public static void get(String uid, Callback<User> cb) {
        if (uid == null) return;
        User cached = CACHE.get(uid);
        if (cached != null) {
            cb.onResult(cached);
            return;
        }
        List<Callback<User>> waiting = PENDING.get(uid);
        if (waiting != null) {
            waiting.add(cb);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(cb);
        PENDING.put(uid, waiting);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User u = snapshot.getValue(User.class);
                if (u == null) {
                    u = new User();
                    u.name = "Kinnect user";
                }
                u.uid = uid;
                CACHE.put(uid, u);
                List<Callback<User>> cbs = PENDING.remove(uid);
                if (cbs != null) for (Callback<User> c : cbs) c.onResult(u);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                List<Callback<User>> cbs = PENDING.remove(uid);
                LISTENERS.remove(uid);
                User u = new User();
                u.uid = uid;
                u.name = "Kinnect user";
                if (cbs != null) for (Callback<User> c : cbs) c.onResult(u);
            }
        };
        LISTENERS.put(uid, l);
        Db.user(uid).addValueEventListener(l);
    }

    public static void put(User u) {
        if (u != null && u.uid != null) CACHE.put(u.uid, u);
    }

    /** Called on log out so the next account starts with a clean cache. */
    public static void clear() {
        for (Map.Entry<String, ValueEventListener> e : LISTENERS.entrySet()) {
            Db.user(e.getKey()).removeEventListener(e.getValue());
        }
        LISTENERS.clear();
        CACHE.clear();
        PENDING.clear();
    }
}
