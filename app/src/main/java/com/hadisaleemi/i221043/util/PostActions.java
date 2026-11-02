package com.hadisaleemi.i221043.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.hadisaleemi.i221043.MyProfileActivity;
import com.hadisaleemi.i221043.ProfileActivity;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.ui.FriendPicker;

import java.util.HashMap;
import java.util.Map;

/** Reactions, sharing and deleting posts; shared by Home, both profiles and post screens. */
public final class PostActions {

    private PostActions() {
    }

    /**
     * One reaction per user per post: picking a reaction replaces the old one, picking the same
     * reaction again (or tapping Like while reacted) removes it.
     */
    public static void react(Post post, String key, String current) {
        String me = Db.uid();
        if (me == null) return;
        if (key == null || key.equals(current)) {
            Db.reactions(post.id).child(me).removeValue();
            return;
        }
        Db.reactions(post.id).child(me).setValue(key);
        if (current == null) {
            Notifs.send(post.uid, AppNotification.REACTION, post.id, "reacted to your post.");
        }
    }

    public static void toggleLike(Post post, String current) {
        react(post, current == null ? "like" : null, current);
    }

    /** Sends the post into a friend's chat as a "post" message and bumps the share counter. */
    public static void share(Activity a, Post post) {
        FriendPicker.show(a, "Send to", friendUid -> {
            Message m = new Message();
            m.type = Message.POST;
            m.postId = post.id;
            m.text = post.text;
            if (post.imageCount() > 0) m.imageUrl = post.images.get(0);
            Chats.send(friendUid, m);
            Db.posts().child(post.id).child("shareCount").runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData data) {
                    Integer n = data.getValue(Integer.class);
                    data.setValue(n == null ? 1 : n + 1);
                    return Transaction.success(data);
                }

                @Override
                public void onComplete(@Nullable DatabaseError e, boolean c, @Nullable DataSnapshot s) {
                }
            });
            Toast.makeText(a, "Post sent", Toast.LENGTH_SHORT).show();
        });
    }

    public static void delete(Post post) {
        Map<String, Object> up = new HashMap<>();
        up.put("posts/" + post.id, null);
        up.put("reactions/" + post.id, null);
        up.put("comments/" + post.id, null);
        up.put("commentLikes/" + post.id, null);
        Db.root().updateChildren(up);
        if (post.images != null) for (String ref : post.images) MediaUpload.delete(ref);
    }

    public static void openProfile(Context c, String uid) {
        if (uid == null) return;
        if (uid.equals(Db.uid())) c.startActivity(new Intent(c, MyProfileActivity.class));
        else c.startActivity(ProfileActivity.intent(c, uid));
    }

    /** "1.2k" style counts used under posts. */
    public static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "k";
        return trim(n / 1_000_000.0) + "m";
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
