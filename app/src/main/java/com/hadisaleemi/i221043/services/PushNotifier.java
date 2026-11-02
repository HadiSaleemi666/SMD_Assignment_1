package com.hadisaleemi.i221043.services;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.hadisaleemi.i221043.CallActivity;
import com.hadisaleemi.i221043.ChatActivity;
import com.hadisaleemi.i221043.FriendsActivity;
import com.hadisaleemi.i221043.NotificationsActivity;
import com.hadisaleemi.i221043.R;
import com.hadisaleemi.i221043.models.CallInfo;

/** Builds the system notifications for FCM pushes. */
public final class PushNotifier {

    public static final String CH_MESSAGES = "messages";
    public static final String CH_CALLS = "calls";
    public static final String CH_SOCIAL = "social";

    private PushNotifier() {
    }

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel msg = new NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH);
        msg.setDescription("New chat messages and screenshot alerts");
        NotificationChannel calls = new NotificationChannel(CH_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Incoming voice and video calls");
        NotificationChannel social = new NotificationChannel(CH_SOCIAL, "Friends & activity", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(msg);
        nm.createNotificationChannel(calls);
        nm.createNotificationChannel(social);
    }

    private static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void showMessage(Context c, String fromUid, String title, String body) {
        if (!allowed(c)) return;
        Intent open = ChatActivity.intent(c, fromUid);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, fromUid.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_MESSAGES)
                .setSmallIcon(R.drawable.ol_messenger)
                .setColor(0xFF0B5F63)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        notify(c, fromUid.hashCode(), b);
    }

    public static void showCall(Context c, CallInfo call, String callerName) {
        if (!allowed(c)) return;
        Intent ring = CallActivity.incoming(c, call);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, call.callId.hashCode(), ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_CALLS)
                .setSmallIcon(R.drawable.ol_call)
                .setColor(0xFF0B5F63)
                .setContentTitle(callerName)
                .setContentText(call.video ? "Incoming video call" : "Incoming voice call")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setTimeoutAfter(CallWatcher.RING_TIMEOUT_MS)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        notify(c, call.callId.hashCode(), b);
    }

    public static void showSocial(Context c, String type, String title, String body) {
        if (!allowed(c)) return;
        Class<?> target = "friend_request".equals(type) ? FriendsActivity.class : NotificationsActivity.class;
        Intent open = new Intent(c, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, type.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_SOCIAL)
                .setSmallIcon(R.drawable.ol_bell)
                .setColor(0xFF0B5F63)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi);
        notify(c, (int) (System.currentTimeMillis() % Integer.MAX_VALUE), b);
    }

    public static void cancel(Context c, int id) {
        NotificationManagerCompat.from(c).cancel(id);
    }

    @SuppressWarnings("MissingPermission")
    private static void notify(Context c, int id, NotificationCompat.Builder b) {
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
