package com.hadisaleemi.i221043.services;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.CallActivity;
import com.hadisaleemi.i221043.models.CallInfo;
import com.hadisaleemi.i221043.util.Db;

/**
 * Listens on calls/{myUid} while the app is open and shows the incoming-call screen as soon as
 * someone rings. When the app is in the background the FCM push sent by the caller (PushSender) does
 * the same job (see KinnectMessagingService).
 */
public final class CallWatcher {

    /** Calls older than this are treated as missed and never ring. */
    public static final long RING_TIMEOUT_MS = 45_000;

    private static DatabaseReference ref;
    private static ValueEventListener listener;
    private static String watchingUid;

    private CallWatcher() {
    }

    public static synchronized void start(Context context) {
        String me = Db.uid();
        if (me == null || me.equals(watchingUid)) return;
        stop();
        Context app = context.getApplicationContext();
        watchingUid = me;
        ref = Db.calls(me);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CallInfo call = snapshot.getValue(CallInfo.class);
                if (call == null || !CallInfo.RINGING.equals(call.status)) return;
                if (System.currentTimeMillis() - call.timestamp > RING_TIMEOUT_MS) return;
                if (CallActivity.isShowing(call.callId)) return;
                Intent i = CallActivity.incoming(app, call);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(i);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        ref.addValueEventListener(listener);
    }

    public static synchronized void stop() {
        if (ref != null && listener != null) ref.removeEventListener(listener);
        ref = null;
        listener = null;
        watchingUid = null;
    }
}
