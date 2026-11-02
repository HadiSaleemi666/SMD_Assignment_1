package com.hadisaleemi.i221043;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.CallInfo;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.services.PushSender;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Chats;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

/**
 * Voice and video calls (screen 22) with the Agora RTC SDK.
 *
 * Signalling goes through Firebase: the caller writes calls/{calleeUid} with status "ringing";
 * the callee's app (CallWatcher in the foreground, or the FCM push in the background) opens
 * this screen in incoming mode. Accepting sets status "accepted" and both sides join the
 * Agora channel named after the call id (App ID only, see startAgora). Every call is logged in callLogs/{uid}/{callId} and as a
 * message in the chat.
 */
public class CallActivity extends AppCompatActivity {

    private static final String EXTRA_PEER = "peer";
    private static final String EXTRA_VIDEO = "video";
    private static final String EXTRA_INCOMING = "incoming";
    private static final String EXTRA_CALL_ID = "callId";
    private static final String EXTRA_CHANNEL = "channel";
    private static final String EXTRA_CALLER = "caller";
    private static final String EXTRA_TIMESTAMP = "timestamp";

    private static volatile String showingCallId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String me, peer, callId, channel, calleeUid, callerUid;
    private boolean video, incoming, connected, ended, muted, speaker;
    private long connectedAt;
    private RtcEngine engine;
    private ValueEventListener signalListener;
    private TextView status;

    private final Runnable ringTimeout = () -> {
        if (!connected) finishCall(CallInfo.MISSED);
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!connected) return;
            status.setText(TimeFmt.duration((System.currentTimeMillis() - connectedAt) / 1000));
            handler.postDelayed(this, 1000);
        }
    };

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (hasPermissions()) startAgora();
                else {
                    Toast.makeText(this, "Microphone permission is needed for calls", Toast.LENGTH_LONG).show();
                    finishCall(incoming ? CallInfo.DECLINED : CallInfo.ENDED);
                }
            });

    public static Intent outgoing(Context c, String peerUid, boolean video) {
        return new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER, peerUid)
                .putExtra(EXTRA_VIDEO, video);
    }

    public static Intent incoming(Context c, CallInfo call) {
        return new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER, call.callerUid)
                .putExtra(EXTRA_CALLER, call.callerUid)
                .putExtra(EXTRA_VIDEO, call.video)
                .putExtra(EXTRA_INCOMING, true)
                .putExtra(EXTRA_CALL_ID, call.callId)
                .putExtra(EXTRA_CHANNEL, call.channel)
                .putExtra(EXTRA_TIMESTAMP, call.timestamp);
    }

    public static boolean isShowing(String callId) {
        return callId != null && callId.equals(showingCallId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_call);

        me = Db.uid();
        peer = getIntent().getStringExtra(EXTRA_PEER);
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        incoming = getIntent().getBooleanExtra(EXTRA_INCOMING, false);
        if (me == null || peer == null) {
            finish();
            return;
        }
        status = findViewById(R.id.tvTimer);
        ((AvatarView) findViewById(R.id.callAvatar)).bind(peer);
        TextView name = findViewById(R.id.callName);
        UserCache.get(peer, u -> name.setText(u.displayName()));

        ImageView notes = findViewById(R.id.btnNotes);
        if (video) notes.setImageResource(R.drawable.ic_camera);
        else notes.setVisibility(View.GONE);
        notes.setOnClickListener(v -> {
            if (engine != null) engine.switchCamera();
        });
        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());
        findViewById(R.id.btnEnd).setOnClickListener(v -> finishCall(connected ? CallInfo.ENDED : (incoming ? CallInfo.DECLINED : CallInfo.MISSED)));
        findViewById(R.id.btnAccept).setOnClickListener(v -> accept());
        findViewById(R.id.btnDecline).setOnClickListener(v -> finishCall(CallInfo.DECLINED));

        if (incoming) {
            callId = getIntent().getStringExtra(EXTRA_CALL_ID);
            channel = getIntent().getStringExtra(EXTRA_CHANNEL);
            callerUid = peer;
            calleeUid = me;
            status.setText(video ? R.string.incoming_video_call : R.string.incoming_voice_call);
            findViewById(R.id.incomingControls).setVisibility(View.VISIBLE);
            findViewById(R.id.callControls).setVisibility(View.GONE);
        } else {
            callerUid = me;
            calleeUid = peer;
            callId = Db.root().child("callIds").push().getKey();
            channel = callId;
            status.setText(R.string.calling);
            ring();
        }
        showingCallId = callId;
        watchSignal();
        handler.postDelayed(ringTimeout, com.hadisaleemi.i221043.services.CallWatcher.RING_TIMEOUT_MS);
        if (!incoming) requestPermissionsAndStart();
    }

    /** Caller: write the ringing record that the callee's app reacts to. */
    private void ring() {
        CallInfo info = new CallInfo();
        info.callId = callId;
        info.callerUid = me;
        info.calleeUid = peer;
        info.channel = channel;
        info.video = video;
        info.status = CallInfo.RINGING;
        info.timestamp = System.currentTimeMillis();
        Map<String, Object> up = new HashMap<>();
        up.put("calls/" + peer, info);
        up.put("callLogs/" + me + "/" + callId, info);
        up.put("callLogs/" + peer + "/" + callId, info);
        Db.root().updateChildren(up).addOnSuccessListener(v -> PushSender.call(info));
        Db.calls(peer).onDisconnect().removeValue();
    }

    /** Both sides watch calls/{callee} for accept / decline / hang-up from the other person. */
    private void watchSignal() {
        signalListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                CallInfo info = s.getValue(CallInfo.class);
                if (info == null || !callId.equals(info.callId)) {
                    // Record removed: the other person hung up or cancelled.
                    if (connected || incoming) remoteEnded();
                    return;
                }
                if (CallInfo.DECLINED.equals(info.status) && !incoming) {
                    Toast.makeText(CallActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                    finishCall(CallInfo.DECLINED);
                } else if (CallInfo.ENDED.equals(info.status) || CallInfo.MISSED.equals(info.status)) {
                    remoteEnded();
                } else if (CallInfo.ACCEPTED.equals(info.status) && !incoming && !connected) {
                    status.setText(R.string.connecting);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        Db.calls(calleeUid).addValueEventListener(signalListener);
    }

    private void accept() {
        findViewById(R.id.incomingControls).setVisibility(View.GONE);
        findViewById(R.id.callControls).setVisibility(View.VISIBLE);
        status.setText(R.string.connecting);
        Db.calls(me).child("status").setValue(CallInfo.ACCEPTED);
        requestPermissionsAndStart();
    }

    private String[] neededPermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        if (video) p.add(Manifest.permission.CAMERA);
        return p.toArray(new String[0]);
    }

    private boolean hasPermissions() {
        for (String p : neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPermissionsAndStart() {
        if (hasPermissions()) startAgora();
        else permissions.launch(neededPermissions());
    }

    /**
     * Joins with the App ID only (no token). This needs the Agora project to use the
     * "Testing mode: APP ID" authentication, because issuing tokens would require a server,
     * which the Firebase Spark plan does not provide.
     */
    private void startAgora() {
        if (BuildConfig.AGORA_APP_ID.isEmpty()) {
            Toast.makeText(this, "Set agora.appId in local.properties", Toast.LENGTH_LONG).show();
            finishCall(CallInfo.ENDED);
            return;
        }
        joinChannel(null);
    }

    private final IRtcEngineEventHandler events = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> onPeerJoined(uid));
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> remoteEnded());
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> Toast.makeText(CallActivity.this, "Call error " + err, Toast.LENGTH_SHORT).show());
        }
    };

    private void joinChannel(String token) {
        if (ended) return;
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = BuildConfig.AGORA_APP_ID;
            config.mEventHandler = events;
            engine = RtcEngine.create(config);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start call: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finishCall(CallInfo.ENDED);
            return;
        }
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.publishMicrophoneTrack = true;
        options.autoSubscribeAudio = true;
        if (video) {
            engine.enableVideo();
            FrameLayout local = findViewById(R.id.localVideo);
            SurfaceView surface = new SurfaceView(this);
            surface.setZOrderMediaOverlay(true);
            local.addView(surface);
            engine.setupLocalVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            engine.startPreview();
            findViewById(R.id.localVideoCard).setVisibility(View.VISIBLE);
            options.publishCameraTrack = true;
            options.autoSubscribeVideo = true;
            speaker = true;
        } else {
            engine.disableVideo();
        }
        engine.setDefaultAudioRoutetoSpeakerphone(speaker);
        engine.joinChannel(token, channel, 0, options);
        updateSpeakerIcon();
    }

    private void onPeerJoined(int uid) {
        if (connected) return;
        connected = true;
        connectedAt = System.currentTimeMillis();
        handler.removeCallbacks(ringTimeout);
        handler.post(tick);
        if (video) {
            FrameLayout remote = findViewById(R.id.remoteVideo);
            remote.setVisibility(View.VISIBLE);
            SurfaceView surface = new SurfaceView(this);
            remote.addView(surface);
            engine.setupRemoteVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, uid));
            findViewById(R.id.halo1).setVisibility(View.GONE);
            findViewById(R.id.halo2).setVisibility(View.GONE);
            findViewById(R.id.callAvatarRing).setVisibility(View.GONE);
        }
    }

    private void toggleMute() {
        muted = !muted;
        if (engine != null) engine.muteLocalAudioStream(muted);
        View b = findViewById(R.id.btnMute);
        b.setAlpha(muted ? 0.5f : 1f);
    }

    private void toggleSpeaker() {
        speaker = !speaker;
        if (engine != null) engine.setEnableSpeakerphone(speaker);
        updateSpeakerIcon();
    }

    private void updateSpeakerIcon() {
        findViewById(R.id.btnSpeaker).setAlpha(speaker ? 1f : 0.6f);
    }

    private void remoteEnded() {
        if (ended) return;
        finishCall(connected ? CallInfo.ENDED : (incoming ? CallInfo.MISSED : CallInfo.ENDED), false);
    }

    private void finishCall(String result) {
        finishCall(result, true);
    }

    /**
     * Leaves the Agora channel, clears the ringing record, updates both call logs and (from the
     * caller's side) adds a call message to the chat.
     */
    private void finishCall(String result, boolean notifyPeer) {
        if (ended) return;
        ended = true;
        handler.removeCallbacksAndMessages(null);
        long duration = connected ? (System.currentTimeMillis() - connectedAt) / 1000 : 0;

        if (signalListener != null) Db.calls(calleeUid).removeEventListener(signalListener);
        Map<String, Object> up = new HashMap<>();
        if (notifyPeer) {
            if (incoming && CallInfo.DECLINED.equals(result)) up.put("calls/" + calleeUid + "/status", CallInfo.DECLINED);
            else up.put("calls/" + calleeUid, null);
        }
        for (String uid : new String[]{callerUid, calleeUid}) {
            up.put("callLogs/" + uid + "/" + callId + "/status", result);
            up.put("callLogs/" + uid + "/" + callId + "/duration", duration);
        }
        Db.root().updateChildren(up);
        if (incoming && CallInfo.DECLINED.equals(result)) {
            // Let the caller see "declined", then clean up the ringing record.
            handler.postDelayed(() -> Db.calls(calleeUid).removeValue(), 3000);
        }

        if (!incoming) {
            Message m = new Message();
            m.type = Message.CALL;
            String kind = video ? "Video call" : "Voice call";
            m.text = connected ? kind + " · " + TimeFmt.duration(duration)
                    : (CallInfo.DECLINED.equals(result) ? kind + " declined" : "Missed " + kind.toLowerCase());
            Chats.send(peer, m);
        }

        if (engine != null) {
            engine.leaveChannel();
            engine.stopPreview();
            RtcEngine.destroy();
            engine = null;
        }
        showingCallId = null;
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back does not hang up; use the red button.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!ended && callId != null) finishCall(connected ? CallInfo.ENDED : CallInfo.MISSED);
    }
}
