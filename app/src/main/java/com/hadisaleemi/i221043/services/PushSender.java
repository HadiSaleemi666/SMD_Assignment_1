package com.hadisaleemi.i221043.services;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.CallInfo;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.util.Chats;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.UserCache;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends Firebase Cloud Messaging pushes straight from the sender's phone (the Spark plan has no
 * Cloud Functions).
 *
 * The app holds a Google service-account key (assets/fcm-service-account.json, git-ignored)
 * whose only role is "Firebase Cloud Messaging API Admin". It signs a JWT with that key,
 * swaps it for an OAuth access token, and calls the FCM HTTP v1 API with a data-only message
 * addressed to the receiver's token from users/{uid}/fcmToken. KinnectMessagingService on the
 * receiving phone turns it into the notification.
 *
 * Note: anyone who unpacks the APK can read this key, so it is acceptable for a class project
 * but a production app would send pushes from a server.
 */
public final class PushSender {

    private static final String TAG = "PushSender";
    private static final String KEY_ASSET = "fcm-service-account.json";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static String projectId;
    private static String clientEmail;
    private static PrivateKey privateKey;
    private static String accessToken;
    private static long accessTokenExpiry;

    private PushSender() {
    }

    /** Loads the service-account key once at start-up; pushes are skipped if it is missing. */
    public static void init(Context c) {
        try (InputStream in = c.getAssets().open(KEY_ASSET)) {
            JSONObject json = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            projectId = json.getString("project_id");
            clientEmail = json.getString("client_email");
            String pem = json.getString("private_key")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.decode(pem, Base64.DEFAULT);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            Log.w(TAG, "No usable " + KEY_ASSET + " in assets; push notifications are disabled", e);
        }
    }

    public static boolean enabled() {
        return privateKey != null;
    }

    // ---- what the app sends ----------------------------------------------------------------

    /** New chat message, or the "took a screenshot" alert. */
    public static void message(String toUid, Message m) {
        String me = Db.uid();
        if (me == null || toUid == null) return;
        boolean screenshot = Message.SYSTEM.equals(m.type) && "screenshot".equals(m.event);
        if (Message.SYSTEM.equals(m.type) && !screenshot) return;
        UserCache.get(me, u -> {
            Map<String, String> data = new HashMap<>();
            data.put("type", screenshot ? "screenshot" : "message");
            data.put("fromUid", me);
            data.put("title", u.displayName());
            data.put("body", screenshot ? u.firstName() + " took a screenshot of your chat" : Chats.preview(m));
            toUser(toUid, data);
        });
    }

    public static void call(CallInfo call) {
        UserCache.get(call.callerUid, u -> {
            Map<String, String> data = new HashMap<>();
            data.put("type", "call");
            data.put("fromUid", call.callerUid);
            data.put("title", u.displayName());
            data.put("body", call.video ? "Incoming video call" : "Incoming voice call");
            data.put("callId", call.callId);
            data.put("channel", call.channel);
            data.put("video", String.valueOf(call.video));
            data.put("timestamp", String.valueOf(call.timestamp));
            toUser(call.calleeUid, data);
        });
    }

    public static void friendRequest(String toUid) {
        String me = Db.uid();
        UserCache.get(me, u -> {
            Map<String, String> data = new HashMap<>();
            data.put("type", "friend_request");
            data.put("fromUid", me);
            data.put("title", u.displayName());
            data.put("body", u.displayName() + " sent you a friend request");
            toUser(toUid, data);
        });
    }

    // ---- FCM HTTP v1 -----------------------------------------------------------------------

    private static void toUser(String uid, Map<String, String> data) {
        if (!enabled()) return;
        Db.user(uid).child("fcmToken").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                String token = s.getValue(String.class);
                if (token == null || token.isEmpty()) return;
                NET.execute(() -> send(token, data));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private static void send(String token, Map<String, String> data) {
        try {
            JSONObject payload = new JSONObject();
            for (Map.Entry<String, String> e : data.entrySet()) payload.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            JSONObject android = new JSONObject()
                    .put("priority", "HIGH")
                    .put("ttl", "call".equals(data.get("type")) ? "45s" : "86400s");
            JSONObject message = new JSONObject()
                    .put("token", token)
                    .put("data", payload)
                    .put("android", android);
            String body = new JSONObject().put("message", message).toString();

            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Bearer " + accessToken());
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code != 200) {
                InputStream err = c.getErrorStream();
                Log.w(TAG, "FCM send failed " + code + ": " + (err == null ? "" : new String(readAll(err), StandardCharsets.UTF_8)));
            }
            c.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "FCM send failed", e);
        }
    }

    /** OAuth token for the service account, reused until five minutes before it expires. */
    private static synchronized String accessToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken != null && now < accessTokenExpiry - 300) return accessToken;

        String header = b64url(new JSONObject().put("alg", "RS256").put("typ", "JWT").toString());
        String claims = b64url(new JSONObject()
                .put("iss", clientEmail)
                .put("scope", SCOPE)
                .put("aud", TOKEN_URL)
                .put("iat", now)
                .put("exp", now + 3600)
                .toString());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        String jwt = header + "." + claims + "." + b64url(sig.sign());

        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")
                + "&assertion=" + jwt;
        HttpURLConnection c = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = c.getOutputStream()) {
            out.write(form.getBytes(StandardCharsets.UTF_8));
        }
        if (c.getResponseCode() != 200) {
            InputStream err = c.getErrorStream();
            throw new IllegalStateException("Token request failed: "
                    + (err == null ? c.getResponseCode() : new String(readAll(err), StandardCharsets.UTF_8)));
        }
        JSONObject res = new JSONObject(new String(readAll(c.getInputStream()), StandardCharsets.UTF_8));
        c.disconnect();
        accessToken = res.getString("access_token");
        accessTokenExpiry = now + res.optLong("expires_in", 3600);
        return accessToken;
    }

    private static String b64url(String s) {
        return b64url(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] b) {
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
