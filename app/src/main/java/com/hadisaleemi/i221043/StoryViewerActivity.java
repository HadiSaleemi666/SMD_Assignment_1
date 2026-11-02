package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.StoryPlayer;
import com.hadisaleemi.i221043.util.Chats;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Notifs;
import com.hadisaleemi.i221043.util.PostActions;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Viewing a friend's story (screen 11). Records a view in storyViews/{owner}/{storyId}/{me};
 * replies and quick reactions arrive in the owner's chat.
 */
public class StoryViewerActivity extends AppCompatActivity implements StoryPlayer.Listener {

    private static final String EXTRA_UID = "uid";

    private String owner;
    private StoryPlayer player;
    private EditText reply;
    private boolean loaded;

    public static Intent intent(Context c, String uid) {
        return new Intent(c, StoryViewerActivity.class).putExtra(EXTRA_UID, uid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_viewer_screen);
        owner = getIntent().getStringExtra(EXTRA_UID);
        if (owner == null) {
            finish();
            return;
        }
        player = new StoryPlayer(this, this, R.id.karachi_label);
        reply = findViewById(R.id.reply_input);

        ((AvatarView) findViewById(R.id.story_pfp)).bind(owner);
        TextView name = findViewById(R.id.story_name);
        UserCache.get(owner, u -> name.setText(u.displayName()));

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.story_pfp, () -> PostActions.openProfile(this, owner));
        NavHelper.click(this, R.id.story_name, () -> PostActions.openProfile(this, owner));
        NavHelper.click(this, R.id.btn_more, () -> {
            player.pause();
            new AlertDialog.Builder(this)
                    .setItems(new String[]{"View profile"}, (d, w) -> PostActions.openProfile(this, owner))
                    .setOnDismissListener(d -> player.resume())
                    .show();
        });
        NavHelper.click(this, R.id.react_like, () -> react("👍"));
        NavHelper.click(this, R.id.react_love, () -> react("❤️"));
        NavHelper.click(this, R.id.react_haha, () -> react("😂"));

        reply.setOnFocusChangeListener((v, focused) -> {
            if (focused) player.pause();
            else player.resume();
        });
        reply.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply();
                return true;
            }
            return false;
        });

        load();
    }

    /** Loads the owner's active stories; close-friends stories only if I am on their list. */
    private void load() {
        String me = Db.uid();
        Db.closeFriends(owner).child(me).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot cf) {
                boolean closeFriend = cf.exists();
                Db.stories().child(owner).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        long now = System.currentTimeMillis();
                        List<Story> list = new ArrayList<>();
                        for (DataSnapshot c : s.getChildren()) {
                            Story st = c.getValue(Story.class);
                            if (st == null || !st.isActive(now)) continue;
                            if ("close".equals(st.audience) && !closeFriend) continue;
                            st.id = c.getKey();
                            list.add(st);
                        }
                        if (list.isEmpty()) {
                            Toast.makeText(StoryViewerActivity.this, "This story is no longer available", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        list.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
                        loaded = true;
                        player.setStories(list, 0);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        finish();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    @Override
    public void onShow(Story story, int index) {
        ((TextView) findViewById(R.id.story_time)).setText(TimeFmt.ago(story.timestamp) + " •");
        Db.storyViews(owner, story.id).child(Db.uid()).setValue(System.currentTimeMillis());
    }

    @Override
    public void onFinished() {
        finish();
    }

    private void sendReply() {
        String text = reply.getText().toString().trim();
        Story s = player.current();
        if (text.isEmpty() || s == null) return;
        Message m = new Message();
        m.type = Message.STORY_REPLY;
        m.text = text;
        m.imageUrl = s.isVideo() ? null : s.mediaUrl;
        Chats.send(owner, m);
        reply.setText("");
        reply.clearFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(reply.getWindowToken(), 0);
        Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show();
    }

    private void react(String emoji) {
        Story s = player.current();
        if (s == null) return;
        Message m = new Message();
        m.type = Message.STORY_REPLY;
        m.text = emoji;
        m.imageUrl = s.isVideo() ? null : s.mediaUrl;
        Chats.send(owner, m);
        Notifs.send(owner, AppNotification.STORY_REACTION, null, "reacted " + emoji + " to your story.");
        Toast.makeText(this, emoji + " sent", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (loaded) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded && !reply.hasFocus()) player.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
