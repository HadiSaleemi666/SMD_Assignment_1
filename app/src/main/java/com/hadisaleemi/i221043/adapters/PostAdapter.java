package com.hadisaleemi.i221043.adapters;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.CommentsActivity;
import com.hadisaleemi.i221043.LikeActivity;
import com.hadisaleemi.i221043.MediaViewerActivity;
import com.hadisaleemi.i221043.R;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.PostActions;
import com.hadisaleemi.i221043.util.Reactions;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feed of posts used on Home and both profile screens. For every post on screen it keeps a live
 * listener on reactions/{postId} and comments/{postId} so reaction icons and counts update in
 * real time.
 */
public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface HeaderBinder {
        void bind(View header);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_POST = 1;

    private final Activity activity;
    private final HeaderBinder headerBinder;
    private final List<Post> posts = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();

    private final Map<String, Map<String, String>> reactions = new HashMap<>();
    private final Map<String, Long> commentCounts = new HashMap<>();
    private final Map<String, ValueEventListener> reactionListeners = new HashMap<>();
    private final Map<String, ValueEventListener> commentListeners = new HashMap<>();

    public PostAdapter(Activity activity, HeaderBinder headerBinder) {
        this.activity = activity;
        this.headerBinder = headerBinder;
    }

    private int offset() {
        return headerBinder == null ? 0 : 1;
    }

    public void setPosts(List<Post> newPosts) {
        posts.clear();
        for (Post p : newPosts) if (!hidden.contains(p.id)) posts.add(p);
        Set<String> ids = new HashSet<>();
        for (Post p : posts) {
            ids.add(p.id);
            watch(p.id);
        }
        for (String id : new ArrayList<>(reactionListeners.keySet())) {
            if (!ids.contains(id)) unwatch(id);
        }
        notifyDataSetChanged();
    }

    public int postCount() {
        return posts.size();
    }

    /** Detaches all Firebase listeners; call from the host's onDestroy. */
    public void release() {
        for (String id : new ArrayList<>(reactionListeners.keySet())) unwatch(id);
    }

    private void watch(String postId) {
        if (reactionListeners.containsKey(postId)) return;
        ValueEventListener rl = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Map<String, String> map = new LinkedHashMap<>();
                for (DataSnapshot c : s.getChildren()) map.put(c.getKey(), c.getValue(String.class));
                reactions.put(postId, map);
                refresh(postId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        ValueEventListener cl = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                commentCounts.put(postId, s.getChildrenCount());
                refresh(postId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        reactionListeners.put(postId, rl);
        commentListeners.put(postId, cl);
        Db.reactions(postId).addValueEventListener(rl);
        Db.comments(postId).addValueEventListener(cl);
    }

    private void unwatch(String postId) {
        ValueEventListener rl = reactionListeners.remove(postId);
        ValueEventListener cl = commentListeners.remove(postId);
        if (rl != null) Db.reactions(postId).removeEventListener(rl);
        if (cl != null) Db.comments(postId).removeEventListener(cl);
        reactions.remove(postId);
        commentCounts.remove(postId);
    }

    private void refresh(String postId) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).id.equals(postId)) {
                notifyItemChanged(i + offset());
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < offset() ? TYPE_HEADER : TYPE_POST;
    }

    @Override
    public int getItemCount() {
        return posts.size() + offset();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_feed_header, parent, false);
            headerBinder.bind(v);
            return new RecyclerView.ViewHolder(v) {
            };
        }
        return new PostVH(inf.inflate(R.layout.item_post, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostVH) ((PostVH) holder).bind(posts.get(position - offset()));
    }

    class PostVH extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView name, time, text, bgText, gridMore, reactionText, countsText, likeWord;
        final ImageView img1, img2, img3, likeImg;
        final View grid, gridRight, cell3, more, hide;
        final LinearLayout reactionIcons;
        String boundId;

        PostVH(View v) {
            super(v);
            avatar = v.findViewById(R.id.postAvatar);
            name = v.findViewById(R.id.postName);
            time = v.findViewById(R.id.postTime);
            text = v.findViewById(R.id.postText);
            bgText = v.findViewById(R.id.postBgText);
            grid = v.findViewById(R.id.postGrid);
            gridRight = v.findViewById(R.id.gridRight);
            cell3 = v.findViewById(R.id.gridCell3);
            img1 = v.findViewById(R.id.gridImg1);
            img2 = v.findViewById(R.id.gridImg2);
            img3 = v.findViewById(R.id.gridImg3);
            gridMore = v.findViewById(R.id.gridMore);
            reactionIcons = v.findViewById(R.id.reactionIcons);
            reactionText = v.findViewById(R.id.reactionText);
            countsText = v.findViewById(R.id.countsText);
            likeImg = v.findViewById(R.id.likeimg);
            likeWord = v.findViewById(R.id.likeword);
            more = v.findViewById(R.id.postMore);
            hide = v.findViewById(R.id.postHide);
        }

        void bind(Post p) {
            boundId = p.id;
            String me = Db.uid();
            avatar.bind(p.uid);
            name.setText("");
            UserCache.get(p.uid, u -> {
                if (p.id.equals(boundId)) name.setText(u.displayName());
            });
            time.setText(TimeFmt.ago(p.timestamp) + " •");
            avatar.setOnClickListener(v -> PostActions.openProfile(activity, p.uid));
            name.setOnClickListener(v -> PostActions.openProfile(activity, p.uid));

            bindBody(p);
            bindReactions(p, me);

            itemView.findViewById(R.id.btnLike).setOnClickListener(v ->
                    PostActions.toggleLike(p, myReaction(p.id, me)));
            itemView.findViewById(R.id.btnLike).setOnLongClickListener(v -> {
                activity.startActivity(LikeActivity.intent(activity, p.id));
                return true;
            });
            itemView.findViewById(R.id.btnComment).setOnClickListener(v ->
                    activity.startActivity(CommentsActivity.intent(activity, p.id)));
            itemView.findViewById(R.id.btnShare).setOnClickListener(v -> PostActions.share(activity, p));
            itemView.findViewById(R.id.reactionsRow).setOnClickListener(v ->
                    activity.startActivity(CommentsActivity.intent(activity, p.id)));

            boolean mine = p.uid != null && p.uid.equals(me);
            more.setOnClickListener(v -> {
                String[] items = mine ? new String[]{"Delete post"} : new String[]{"View profile", "Hide post"};
                new AlertDialog.Builder(activity).setItems(items, (d, which) -> {
                    if (mine) confirmDelete(p);
                    else if (which == 0) PostActions.openProfile(activity, p.uid);
                    else hidePost(p);
                }).show();
            });
            hide.setOnClickListener(v -> hidePost(p));
        }

        private void bindBody(Post p) {
            int n = p.imageCount();
            boolean hasText = p.text != null && !p.text.trim().isEmpty();
            boolean coloured = n == 0 && hasText && p.bgColor != null;
            text.setVisibility(hasText && !coloured ? View.VISIBLE : View.GONE);
            text.setText(p.text);
            bgText.setVisibility(coloured ? View.VISIBLE : View.GONE);
            if (coloured) {
                bgText.setText(p.text);
                try {
                    bgText.setBackgroundColor(Color.parseColor(p.bgColor));
                } catch (IllegalArgumentException e) {
                    bgText.setBackgroundColor(0xFF0B5F63);
                }
            }

            grid.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
            if (n == 0) return;
            float d = activity.getResources().getDisplayMetrics().density;
            grid.getLayoutParams().height = (int) ((n == 1 ? 300 : 260) * d);
            gridRight.setVisibility(n >= 2 ? View.VISIBLE : View.GONE);
            cell3.setVisibility(n >= 3 ? View.VISIBLE : View.GONE);
            Images.load(img1, p.images.get(0));
            if (n >= 2) Images.load(img2, p.images.get(1));
            if (n >= 3) Images.load(img3, p.images.get(2));
            gridMore.setVisibility(n > 3 ? View.VISIBLE : View.GONE);
            gridMore.setText("+" + (n - 3));
            img1.setOnClickListener(v -> openViewer(p, 0));
            img2.setOnClickListener(v -> openViewer(p, 1));
            cell3.setOnClickListener(v -> openViewer(p, 2));
        }

        private void bindReactions(Post p, String me) {
            Map<String, String> map = reactions.get(p.id);
            if (map == null) map = new HashMap<>();
            String mine = map.get(me);
            int idx = Reactions.index(mine);
            if (idx >= 0) {
                likeImg.setImageResource(Reactions.ICONS[idx]);
                likeWord.setText(Reactions.LABELS[idx]);
                likeWord.setTextColor(Reactions.COLORS[idx]);
            } else {
                likeImg.setImageResource(R.drawable.like);
                likeWord.setText(R.string.like);
                likeWord.setTextColor(activity.getColor(R.color.gray));
            }

            // Up to three most-used reaction icons, overlapping like the design.
            int[] counts = new int[Reactions.KEYS.length];
            for (String r : map.values()) {
                int i = Reactions.index(r);
                if (i >= 0) counts[i]++;
            }
            reactionIcons.removeAllViews();
            float d = activity.getResources().getDisplayMetrics().density;
            for (int shown = 0; shown < 3; shown++) {
                int best = -1;
                for (int i = 0; i < counts.length; i++) if (counts[i] > 0 && (best < 0 || counts[i] > counts[best])) best = i;
                if (best < 0) break;
                counts[best] = 0;
                ImageView iv = new ImageView(activity);
                iv.setImageResource(Reactions.ICONS[best]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (20 * d), (int) (20 * d));
                if (shown > 0) lp.setMarginStart((int) (-5 * d));
                reactionIcons.addView(iv, lp);
            }

            int total = map.size();
            if (total == 0) {
                reactionText.setText("");
            } else if (mine != null) {
                reactionText.setText(total == 1 ? "You" : "You and " + PostActions.compact(total - 1) + " others");
            } else {
                String someone = map.keySet().iterator().next();
                reactionText.setText(PostActions.compact(total));
                UserCache.get(someone, u -> {
                    if (!p.id.equals(boundId)) return;
                    reactionText.setText(total == 1 ? u.firstName()
                            : u.firstName() + " and " + PostActions.compact(total - 1) + " others");
                });
            }

            Long c = commentCounts.get(p.id);
            long comments = c == null ? 0 : c;
            StringBuilder sb = new StringBuilder();
            if (comments > 0) sb.append(PostActions.compact(comments)).append(comments == 1 ? " comment" : " comments");
            if (p.shareCount > 0) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(PostActions.compact(p.shareCount)).append(p.shareCount == 1 ? " share" : " shares");
            }
            countsText.setText(sb);
            itemView.findViewById(R.id.reactionsRow).setVisibility(total == 0 && sb.length() == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private String myReaction(String postId, String me) {
        Map<String, String> map = reactions.get(postId);
        return map == null ? null : map.get(me);
    }

    private void openViewer(Post p, int index) {
        activity.startActivity(MediaViewerActivity.intent(activity, new ArrayList<>(p.images), index));
    }

    private void hidePost(Post p) {
        hidden.add(p.id);
        int i = posts.indexOf(p);
        if (i >= 0) {
            posts.remove(i);
            notifyItemRemoved(i + offset());
        }
    }

    private void confirmDelete(Post p) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete post?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> PostActions.delete(p))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
