package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.FriendPicker;
import com.hadisaleemi.i221043.ui.StoryPlayer;
import com.hadisaleemi.i221043.util.Chats;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Your own story (screen 12) with who has seen it. Expired stories (older than 24h) are removed
 * here and on Home (only the owner may delete them); everyone else simply stops showing them.
 */
public class YourStoryActivity extends AppCompatActivity implements StoryPlayer.Listener {

    private StoryPlayer player;
    private DatabaseReference viewsRef;
    private ValueEventListener viewsListener;
    private final List<String> viewers = new ArrayList<>();
    private boolean loaded;

    public static Intent intent(Context c) {
        return new Intent(c, YourStoryActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.your_story_screen);
        player = new StoryPlayer(this, this, R.id.saturday_label);
        ((AvatarView) findViewById(R.id.story_pfp)).bind(Db.uid());

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.action_create, () -> {
            startActivity(CameraActivity.forStory(this));
            finish();
        });
        NavHelper.click(this, R.id.action_seen, this::showViewers);
        NavHelper.click(this, R.id.action_send, this::sendToFriend);
        NavHelper.click(this, R.id.action_highlight, () ->
                Toast.makeText(this, "Highlights are coming soon", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.action_more, this::moreOptions);
        load();
    }

    private void load() {
        String me = Db.uid();
        Db.stories().child(me).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                long now = System.currentTimeMillis();
                List<Story> list = new ArrayList<>();
                Map<String, Object> expired = new HashMap<>();
                for (DataSnapshot c : s.getChildren()) {
                    Story st = c.getValue(Story.class);
                    if (st == null) continue;
                    if (!st.isActive(now)) {
                        expired.put("stories/" + me + "/" + c.getKey(), null);
                        expired.put("storyViews/" + me + "/" + c.getKey(), null);
                        MediaUpload.delete(st.mediaUrl);
                        continue;
                    }
                    st.id = c.getKey();
                    list.add(st);
                }
                if (!expired.isEmpty()) Db.root().updateChildren(expired);
                if (list.isEmpty()) {
                    Toast.makeText(YourStoryActivity.this, "You have no active story", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                list.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
                loaded = true;
                player.setStories(list, list.size() - 1);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    @Override
    public void onShow(Story story, int index) {
        String audience = "close".equals(story.audience) ? "Close friends" : "Friends";
        ((TextView) findViewById(R.id.story_time)).setText(TimeFmt.ago(story.timestamp) + " • " + audience);
        watchViews(story);
    }

    /** Live "Seen by N" with the first three viewers' avatars. */
    private void watchViews(Story story) {
        if (viewsRef != null) viewsRef.removeEventListener(viewsListener);
        viewsRef = Db.storyViews(Db.uid(), story.id);
        viewsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                viewers.clear();
                for (DataSnapshot c : s.getChildren()) viewers.add(c.getKey());
                ((TextView) findViewById(R.id.seenText)).setText(getString(R.string.seen_by_n, viewers.size()));
                int[] ids = {R.id.seen1, R.id.seen2, R.id.seen3};
                for (int i = 0; i < ids.length; i++) {
                    AvatarView av = findViewById(ids[i]);
                    av.setVisibility(i < viewers.size() ? View.VISIBLE : View.GONE);
                    if (i < viewers.size()) av.bind(viewers.get(i));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        viewsRef.addValueEventListener(viewsListener);
    }

    private void showViewers() {
        if (viewers.isEmpty()) {
            Toast.makeText(this, "No one has seen this yet", Toast.LENGTH_SHORT).show();
            return;
        }
        player.pause();
        List<String> names = new ArrayList<>();
        for (String uid : viewers) {
            UserCache.get(uid, u -> {
                names.add(u.displayName());
                if (names.size() == viewers.size()) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.seen_by_n, viewers.size()))
                            .setItems(names.toArray(new String[0]), null)
                            .setOnDismissListener(d -> player.resume())
                            .show();
                }
            });
        }
    }

    private void sendToFriend() {
        Story s = player.current();
        if (s == null) return;
        player.pause();
        FriendPicker.show(this, "Send story to", uid -> {
            Message m = new Message();
            m.type = Message.IMAGE;
            m.imageUrl = s.isVideo() ? null : s.mediaUrl;
            m.text = s.text;
            if (m.imageUrl == null) m.type = Message.TEXT;
            if (m.text == null && m.imageUrl == null) m.text = "Shared a story";
            Chats.send(uid, m);
            Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show();
            player.resume();
        });
    }

    private void moreOptions() {
        Story s = player.current();
        if (s == null) return;
        player.pause();
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Delete this story"}, (d, w) -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put("stories/" + Db.uid() + "/" + s.id, null);
                    MediaUpload.delete(s.mediaUrl);
                    up.put("storyViews/" + Db.uid() + "/" + s.id, null);
                    Db.root().updateChildren(up);
                    Toast.makeText(this, "Story deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setOnDismissListener(d -> player.resume())
                .show();
    }

    @Override
    public void onFinished() {
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (loaded) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded) player.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
        if (viewsRef != null) viewsRef.removeEventListener(viewsListener);
    }
}
