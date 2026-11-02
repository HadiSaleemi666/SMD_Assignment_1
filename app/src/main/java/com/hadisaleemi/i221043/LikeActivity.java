package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.PostActions;
import com.hadisaleemi.i221043.util.Reactions;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

/**
 * Reaction picker (screen 05). The post is shown dimmed behind a bar with all six reactions;
 * choosing one saves reactions/{postId}/{myUid}. Tapping outside the bar reveals who reacted.
 */
public class LikeActivity extends AppCompatActivity {

    private static final String EXTRA_POST = "postId";

    private final Subs subs = new Subs();
    private Post post;
    private String myReaction;
    private TextView label;
    private LinearLayout bar;

    public static Intent intent(Context c, String postId) {
        return new Intent(c, LikeActivity.class).putExtra(EXTRA_POST, postId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.like_screen);
        String postId = getIntent().getStringExtra(EXTRA_POST);
        if (postId == null) {
            finish();
            return;
        }
        label = findViewById(R.id.reactionLabel);
        bar = findViewById(R.id.reactionBar);

        NavHelper.click(this, R.id.backarrow, this::finish);
        View overlay = findViewById(R.id.pickerOverlay);
        overlay.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        NavHelper.click(this, R.id.removeReaction, () -> {
            if (post != null) PostActions.react(post, null, myReaction);
            finish();
        });

        subs.value(Db.posts().child(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                post = s.getValue(Post.class);
                if (post == null) {
                    finish();
                    return;
                }
                post.id = postId;
                bindPost();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.reactions(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                myReaction = s.child(Db.uid()).getValue(String.class);
                buildBar();
                bindReactors(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void bindPost() {
        ((AvatarView) findViewById(R.id.postAvatar)).bind(post.uid);
        TextView name = findViewById(R.id.postName);
        TextView title = findViewById(R.id.title);
        UserCache.get(post.uid, u -> {
            name.setText(u.displayName());
            title.setText(getString(R.string.someones_post, u.firstName()));
        });
        ((TextView) findViewById(R.id.postTime)).setText(TimeFmt.ago(post.timestamp) + " •");
        ((TextView) findViewById(R.id.postText)).setText(post.text);
        ImageView img = findViewById(R.id.n1);
        img.setVisibility(post.imageCount() > 0 ? View.VISIBLE : View.GONE);
        if (post.imageCount() > 0) Images.load(img, post.images.get(0));
    }

    private void buildBar() {
        bar.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int current = Reactions.index(myReaction);
        for (int i = 0; i < Reactions.KEYS.length; i++) {
            final int idx = i;
            ImageView iv = new ImageView(this);
            iv.setImageResource(Reactions.ICONS[i]);
            iv.setContentDescription(Reactions.LABELS[i]);
            int size = (int) ((i == current ? 58 : 44) * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
            lp.gravity = Gravity.BOTTOM;
            iv.setOnClickListener(v -> {
                if (post == null) return;
                PostActions.react(post, Reactions.KEYS[idx], myReaction);
                finish();
            });
            bar.addView(iv, lp);
        }
        label.setVisibility(current >= 0 ? View.VISIBLE : View.INVISIBLE);
        if (current >= 0) label.setText(Reactions.LABELS[current]);
        findViewById(R.id.removeReaction).setVisibility(current >= 0 ? View.VISIBLE : View.GONE);
    }

    private void bindReactors(DataSnapshot s) {
        LinearLayout list = findViewById(R.id.reactors);
        list.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        ((TextView) findViewById(R.id.reactorsHeading)).setText(
                getString(R.string.reactions_count, s.getChildrenCount()));
        for (DataSnapshot c : s.getChildren()) {
            String uid = c.getKey();
            int idx = Reactions.index(c.getValue(String.class));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (15 * d), (int) (6 * d), (int) (15 * d), (int) (6 * d));

            AvatarView av = new AvatarView(this);
            av.bind(uid);
            row.addView(av, new LinearLayout.LayoutParams((int) (42 * d), (int) (42 * d)));

            TextView name = new TextView(this);
            name.setTextColor(getColor(R.color.black));
            name.setTextSize(15);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            nlp.setMarginStart((int) (12 * d));
            row.addView(name, nlp);
            UserCache.get(uid, u -> name.setText(u.displayName()));

            if (idx >= 0) {
                ImageView icon = new ImageView(this);
                icon.setImageResource(Reactions.ICONS[idx]);
                row.addView(icon, new LinearLayout.LayoutParams((int) (24 * d), (int) (24 * d)));
            }
            row.setOnClickListener(v -> PostActions.openProfile(this, uid));
            list.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }
}
