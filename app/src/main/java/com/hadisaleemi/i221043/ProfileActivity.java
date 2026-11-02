package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.adapters.PostAdapter;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Friends;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.Mutuals;
import com.hadisaleemi.i221043.util.ProfileData;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Another user's profile (screen 17): live friend count, mutual friends, the Add friend button
 * (Add friend -> Requested -> Friends), Message, photos and posts.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String EXTRA_UID = "uid";

    private final Subs subs = new Subs();
    private String uid;
    private int state = Friends.NONE;
    private boolean closeFriend;
    private int friendCount;
    private Friends.Handle stateHandle;
    private PostAdapter postAdapter;
    private List<String> images = new ArrayList<>();

    public static Intent intent(Context c, String uid) {
        return new Intent(c, ProfileActivity.class).putExtra(EXTRA_UID, uid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getIntent().getStringExtra(EXTRA_UID);
        if (uid == null) {
            finish();
            return;
        }
        if (uid.equals(Db.uid())) {
            startActivity(new Intent(this, MyProfileActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_profile);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSearch).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.btnMessage).setOnClickListener(v -> startActivity(ChatActivity.intent(this, uid)));
        findViewById(R.id.btnAdd).setOnClickListener(v -> onAddClicked());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMore());
        findViewById(R.id.btnSeeAll).setOnClickListener(v -> {
            if (!images.isEmpty()) startActivity(MediaViewerActivity.intent(this, new ArrayList<>(images), 0));
        });

        postAdapter = new PostAdapter(this, null);
        RecyclerView posts = findViewById(R.id.profilePosts);
        posts.setLayoutManager(new LinearLayoutManager(this));
        posts.setAdapter(postAdapter);

        ((AvatarView) findViewById(R.id.avatar)).bind(uid);
        listen();
    }

    private void listen() {
        subs.value(Db.user(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = uid;
                UserCache.put(u);
                ((AvatarView) findViewById(R.id.avatar)).setUser(u);
                ((TextView) findViewById(R.id.titleName)).setText(u.displayName());
                ((TextView) findViewById(R.id.tvName)).setText(u.displayName());
                setOptional(R.id.tvBio, u.bio, null);
                setOptional(R.id.tvWork, u.work, R.id.rowWork);
                setOptional(R.id.tvLives, u.location == null || u.location.isEmpty() ? null : "Lives in " + u.location, R.id.rowLives);
                ((TextView) findViewById(R.id.tvJoined)).setText(TimeFmt.joined(u.createdAt));
                Images.load(findViewById(R.id.cover), u.coverUrl);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        ProfileData.friends(subs, uid, list -> {
            friendCount = list.size();
            Mutuals.list(uid, this::showMutuals);
        });
        subs.value(Db.closeFriends(Db.uid()).child(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                closeFriend = s.exists();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        stateHandle = Friends.observe(uid, this::showState);
        ProfileData.posts(subs, uid, this::showPosts);
    }

    private void setOptional(int textId, String value, Integer rowId) {
        boolean has = value != null && !value.trim().isEmpty();
        ((TextView) findViewById(textId)).setText(has ? value : "");
        findViewById(rowId == null ? textId : rowId).setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private void showMutuals(List<String> mutual) {
        String counts = getResources().getQuantityString(R.plurals.friend_count, friendCount, friendCount);
        if (!mutual.isEmpty()) counts += " · " + mutual.size() + " mutual";
        ((TextView) findViewById(R.id.tvCounts)).setText(counts);

        findViewById(R.id.mutualRow).setVisibility(mutual.isEmpty() ? View.GONE : View.VISIBLE);
        int[] ids = {R.id.mf1, R.id.mf2, R.id.mf3};
        for (int i = 0; i < ids.length; i++) {
            AvatarView av = findViewById(ids[i]);
            av.setVisibility(i < mutual.size() ? View.VISIBLE : View.GONE);
            if (i < mutual.size()) av.bind(mutual.get(i));
        }
        if (mutual.isEmpty()) return;
        TextView tv = findViewById(R.id.tvMutual);
        List<String> names = new ArrayList<>();
        int show = Math.min(2, mutual.size());
        for (int i = 0; i < show; i++) {
            UserCache.get(mutual.get(i), u -> {
                names.add(u.displayName());
                if (names.size() < show) return;
                int others = mutual.size() - show;
                String text = "Friends with " + names.get(0)
                        + (show > 1 ? (others > 0 ? ", " : " and ") + names.get(1) : "")
                        + (others > 0 ? " and " + others + (others == 1 ? " other" : " others") : "");
                tv.setText(text);
            });
        }
    }

    private void showState(int s) {
        state = s;
        TextView label = findViewById(R.id.tvAdd);
        ImageView icon = findViewById(R.id.icAdd);
        LinearLayout btn = findViewById(R.id.btnAdd);
        label.setText(Friends.label(s));
        boolean primary = s == Friends.NONE || s == Friends.INCOMING;
        btn.setBackgroundResource(primary ? R.drawable.bg_teal12 : R.drawable.bg_cream12);
        int color = getColor(primary ? R.color.white : R.color.ink);
        label.setTextColor(color);
        icon.setColorFilter(color);
        icon.setImageResource(s == Friends.FRIENDS ? R.drawable.ol_people : R.drawable.ol_person_add);
    }

    private void onAddClicked() {
        if (state == Friends.FRIENDS) showMore();
        else if (state == Friends.REQUESTED) {
            new AlertDialog.Builder(this)
                    .setMessage("Cancel your friend request?")
                    .setPositiveButton("Cancel request", (d, w) -> Friends.cancelRequest(uid))
                    .setNegativeButton("Keep", null)
                    .show();
        } else {
            Friends.primaryAction(uid, state);
        }
    }

    private void showMore() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (state == Friends.FRIENDS) {
            items.add(closeFriend ? "Remove from Close friends" : "Add to Close friends");
            actions.add(() -> ProfileData.setCloseFriend(uid, !closeFriend));
            items.add("Unfriend");
            actions.add(() -> new AlertDialog.Builder(this)
                    .setMessage("Remove this person from your friends?")
                    .setPositiveButton("Unfriend", (d, w) -> Friends.unfriend(uid))
                    .setNegativeButton("Cancel", null)
                    .show());
        } else if (state == Friends.INCOMING) {
            items.add("Delete request");
            actions.add(() -> Friends.decline(uid));
        }
        items.add("Message");
        actions.add(() -> startActivity(ChatActivity.intent(this, uid)));
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void showPosts(List<Post> list) {
        postAdapter.setPosts(list);
        findViewById(R.id.noPosts).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        images = ProfileData.allImages(list);
        int[] ids = {R.id.ph1, R.id.ph2, R.id.ph3};
        for (int i = 0; i < ids.length; i++) {
            ImageView iv = findViewById(ids[i]);
            iv.setVisibility(i < images.size() ? View.VISIBLE : View.INVISIBLE);
            if (i < images.size()) {
                Images.load(iv, images.get(i));
                final int index = i;
                iv.setOnClickListener(v -> startActivity(MediaViewerActivity.intent(this, new ArrayList<>(images), index)));
            }
        }
        boolean hasPhotos = !images.isEmpty();
        findViewById(R.id.photosHeader).setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        findViewById(R.id.photosRow).setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
        if (stateHandle != null) stateHandle.remove();
        if (postAdapter != null) postAdapter.release();
    }
}
