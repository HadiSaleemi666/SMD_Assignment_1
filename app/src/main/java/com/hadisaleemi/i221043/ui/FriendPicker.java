package com.hadisaleemi.i221043.ui;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.util.Callback;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/** Simple "pick one of your friends" dialog, used for sharing posts and starting chats. */
public final class FriendPicker {

    private FriendPicker() {
    }

    public static void show(Activity a, String title, Callback<String> picked) {
        String me = Db.uid();
        if (me == null) return;
        Db.friends(me).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> uids = new ArrayList<>();
                for (DataSnapshot c : snapshot.getChildren()) uids.add(c.getKey());
                if (uids.isEmpty()) {
                    Toast.makeText(a, "Add some friends first", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<User> users = new ArrayList<>();
                for (String uid : uids) {
                    UserCache.get(uid, u -> {
                        users.add(u);
                        if (users.size() == uids.size() && !a.isFinishing()) {
                            users.sort((x, y) -> x.displayName().compareToIgnoreCase(y.displayName()));
                            String[] names = new String[users.size()];
                            for (int i = 0; i < users.size(); i++) names[i] = users.get(i).displayName();
                            new AlertDialog.Builder(a)
                                    .setTitle(title)
                                    .setItems(names, (d, which) -> picked.onResult(users.get(which).uid))
                                    .show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
}
