package com.hadisaleemi.i221043;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.adapters.PostAdapter;
import com.hadisaleemi.i221043.adapters.StoryRowAdapter;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Badges;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Home (screen 04). Shows friends' stories (Create story first) and a real-time feed of posts
 * by the user and their friends, newest first.
 */
public class StartSocialMediaActivity extends AppCompatActivity {

    private static final int FEED_LIMIT = 300;

    private final Subs subs = new Subs();
    private final Set<String> friends = new HashSet<>();
    private final Set<String> closeFriendOf = new HashSet<>();
    private final List<Post> allPosts = new ArrayList<>();
    private DataSnapshot storiesSnapshot;

    private PostAdapter feedAdapter;
    private StoryRowAdapter storyAdapter;
    private View header;

    private final ActivityResultLauncher<String> notifPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_social_media_screen);
        String me = Db.uid();
        if (me == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        NavHelper.click(this, R.id.messageIcon, () -> open(ChatsActivity.class));
        NavHelper.click(this, R.id.btnSearch, () -> open(SearchActivity.class));
        NavHelper.click(this, R.id.tabPeople, () -> open(FriendsActivity.class));
        NavHelper.click(this, R.id.tabShop, () -> open(MarketplaceActivity.class));
        NavHelper.click(this, R.id.tabNotif, () -> open(NotificationsActivity.class));
        NavHelper.click(this, R.id.tabMore, () -> open(MenuActivity.class));

        storyAdapter = new StoryRowAdapter(this);
        feedAdapter = new PostAdapter(this, this::bindHeader);
        RecyclerView feed = findViewById(R.id.feed);
        feed.setLayoutManager(new LinearLayoutManager(this));
        feed.setAdapter(feedAdapter);

        Badges.messages(subs, findViewById(R.id.msgBadge));
        Badges.notifications(subs, findViewById(R.id.notifBadge));
        listen(me);
        askForNotificationPermission();
    }

    private void bindHeader(View h) {
        header = h;
        String me = Db.uid();
        ((AvatarView) h.findViewById(R.id.seagreenpfp)).bind(me);
        TextView prompt = h.findViewById(R.id.search_bar);
        UserCache.get(me, u -> prompt.setText(getString(R.string.whats_on_your_mind, u.firstName())));

        RecyclerView stories = h.findViewById(R.id.stories);
        stories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        stories.setAdapter(storyAdapter);

        h.findViewById(R.id.seagreenpfp).setOnClickListener(v -> open(MyProfileActivity.class));
        prompt.setOnClickListener(v -> open(CreatePostActivity.class));
        h.findViewById(R.id.btnImagePost).setOnClickListener(v ->
                startActivity(CreatePostActivity.withPhotos(this)));
        updateEmptyState();
    }

    private void listen(String me) {
        subs.value(Db.friends(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                friends.clear();
                for (DataSnapshot c : s.getChildren()) friends.add(c.getKey());
                rebuildFeed();
                rebuildStories();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.root().child("closeFriendOf").child(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                closeFriendOf.clear();
                for (DataSnapshot c : s.getChildren()) closeFriendOf.add(c.getKey());
                rebuildStories();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.posts().orderByChild("timestamp").limitToLast(FEED_LIMIT), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                allPosts.clear();
                for (DataSnapshot c : s.getChildren()) {
                    Post p = c.getValue(Post.class);
                    if (p == null) continue;
                    p.id = c.getKey();
                    allPosts.add(p);
                }
                rebuildFeed();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.stories(), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                storiesSnapshot = s;
                rebuildStories();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Posts from me and my friends, newest first. */
    private void rebuildFeed() {
        String me = Db.uid();
        List<Post> visible = new ArrayList<>();
        for (Post p : allPosts) {
            if (me.equals(p.uid) || friends.contains(p.uid)) visible.add(p);
        }
        visible.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        feedAdapter.setPosts(visible);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (header == null) return;
        header.findViewById(R.id.emptyFeed).setVisibility(feedAdapter.postCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /** Active (less than 24h old) stories from me and my friends; close-friends stories only if allowed. */
    private void rebuildStories() {
        if (storiesSnapshot == null) return;
        String me = Db.uid();
        long now = System.currentTimeMillis();
        List<StoryRowAdapter.Group> mine = new ArrayList<>();
        List<StoryRowAdapter.Group> others = new ArrayList<>();
        for (DataSnapshot userNode : storiesSnapshot.getChildren()) {
            String owner = userNode.getKey();
            boolean isMe = me.equals(owner);
            if (!isMe && !friends.contains(owner)) continue;
            StoryRowAdapter.Group g = new StoryRowAdapter.Group();
            g.uid = owner;
            for (DataSnapshot sn : userNode.getChildren()) {
                Story st = sn.getValue(Story.class);
                if (st == null) continue;
                if (!st.isActive(now)) {
                    // Only the owner may delete; other people's expired stories are just hidden.
                    if (isMe) {
                        sn.getRef().removeValue();
                        Db.root().child("storyViews").child(me).child(sn.getKey()).removeValue();
                        MediaUpload.delete(st.mediaUrl);
                    }
                    continue;
                }
                if ("close".equals(st.audience) && !isMe && !closeFriendOf.contains(owner)) continue;
                st.id = sn.getKey();
                g.stories.add(st);
            }
            if (g.stories.isEmpty()) continue;
            g.stories.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
            (isMe ? mine : others).add(g);
        }
        others.sort((a, b) -> Long.compare(b.latest().timestamp, a.latest().timestamp));
        mine.addAll(others);
        storyAdapter.setGroups(mine);
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
        if (feedAdapter != null) feedAdapter.release();
    }
}
