package com.hadisaleemi.i221043.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hadisaleemi.i221043.EditProfileActivity;
import com.hadisaleemi.i221043.LoginActivity;
import com.hadisaleemi.i221043.StartSocialMediaActivity;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.services.CallWatcher;

/** What happens right after a user signs in, and on log out. */
public final class AuthFlow {

    private static final long DB_TIMEOUT_MS = 15_000;

    private AuthFlow() {
    }

    /**
     * Registers this device for push, starts presence, remembers the account for the Recent
     * login card, then sends first-time users to profile setup (screen 16) and everyone else
     * to Home.
     */
    public static void afterSignIn(Activity a) {
        String uid = Db.uid();
        if (uid == null) return;
        Presence.setForeground(true);
        CallWatcher.start(a);
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> Db.user(uid).child("fcmToken").setValue(token));

        // If the database can't be reached (e.g. google-services.json has no or the wrong
        // database URL) the read below never returns; tell the user instead of spinning forever.
        boolean[] answered = {false};
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!answered[0] && !a.isFinishing()) {
                Toast.makeText(a, "Can't reach the database. Check your connection and that "
                        + "google-services.json contains your Realtime Database URL.", Toast.LENGTH_LONG).show();
            }
        }, DB_TIMEOUT_MS);

        Db.user(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                answered[0] = true;
                User u = snapshot.getValue(User.class);
                if (u != null) {
                    u.uid = uid;
                    Session.saveRecentLogin(a, u);
                }
                Intent next;
                if (u == null || !u.profileComplete) {
                    next = EditProfileActivity.setup(a);
                } else {
                    next = new Intent(a, StartSocialMediaActivity.class);
                }
                next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                a.startActivity(next);
                a.finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                answered[0] = true;
                Intent next = new Intent(a, StartSocialMediaActivity.class);
                next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                a.startActivity(next);
                a.finish();
            }
        });
    }

    /** Marks the user offline, unregisters push for this device and clears the back stack. */
    public static void logout(Activity a) {
        CallWatcher.stop();
        boolean[] done = {false};
        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            FirebaseMessaging.getInstance().deleteToken();
            UserCache.clear();
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(a, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            a.startActivity(i);
            a.finishAffinity();
        };
        // Wait for the offline write to reach the server (it needs the user's credentials),
        // but never block log out for more than two seconds when there is no connection.
        Presence.goOffline().addOnCompleteListener(t -> finish.run());
        new Handler(Looper.getMainLooper()).postDelayed(finish, 2000);
    }
}
