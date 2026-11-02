package com.hadisaleemi.i221043.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.models.ChatSummary;

/** Live red counters on the Messenger and Notifications icons. */
public final class Badges {

    private Badges() {
    }

    /** Number of conversations with unread messages. */
    public static void messages(Subs subs, TextView badge) {
        String me = Db.uid();
        if (me == null || badge == null) return;
        subs.value(Db.userChats(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                int n = 0;
                for (DataSnapshot c : s.getChildren()) {
                    ChatSummary cs = c.getValue(ChatSummary.class);
                    if (cs != null && cs.unread > 0) n++;
                }
                show(badge, n);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Unread in-app notifications plus pending friend requests. */
    public static void notifications(Subs subs, TextView badge) {
        String me = Db.uid();
        if (me == null || badge == null) return;
        subs.value(Db.notifications(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                int n = 0;
                for (DataSnapshot c : s.getChildren()) {
                    AppNotification an = c.getValue(AppNotification.class);
                    if (an != null && !an.read) n++;
                }
                show(badge, n);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Pending incoming friend requests (Friends tab). */
    public static void requests(Subs subs, TextView badge) {
        String me = Db.uid();
        if (me == null || badge == null) return;
        subs.value(Db.friendRequests(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                show(badge, (int) s.getChildrenCount());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private static void show(TextView badge, int n) {
        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        badge.setText(n > 9 ? "9+" : String.valueOf(n));
    }
}
