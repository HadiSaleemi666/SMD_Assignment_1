package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.Post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Listeners shared by both profile screens. */
public final class ProfileData {

    private ProfileData() {
    }

    /** All posts by {@code uid}, newest first (uses the ".indexOn": "uid" rule). */
    public static void posts(Subs subs, String uid, Callback<List<Post>> cb) {
        subs.value(Db.posts().orderByChild("uid").equalTo(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                List<Post> list = new ArrayList<>();
                for (DataSnapshot c : s.getChildren()) {
                    Post p = c.getValue(Post.class);
                    if (p == null) continue;
                    p.id = c.getKey();
                    list.add(p);
                }
                list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
                cb.onResult(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Friend uids of {@code uid}, most recently added first. */
    public static void friends(Subs subs, String uid, Callback<List<String>> cb) {
        subs.value(Db.friends(uid).orderByValue(), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                List<String> list = new ArrayList<>();
                for (DataSnapshot c : s.getChildren()) list.add(0, c.getKey());
                cb.onResult(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Adds or removes someone from my Close friends list (who can see close-friends stories). */
    public static void setCloseFriend(String friendUid, boolean close) {
        String me = Db.uid();
        Map<String, Object> up = new HashMap<>();
        up.put("closeFriends/" + me + "/" + friendUid, close ? true : null);
        up.put("closeFriendOf/" + friendUid + "/" + me, close ? true : null);
        Db.root().updateChildren(up);
    }

    public static List<String> allImages(List<Post> posts) {
        List<String> out = new ArrayList<>();
        for (Post p : posts) if (p.images != null) out.addAll(p.images);
        return out;
    }
}
