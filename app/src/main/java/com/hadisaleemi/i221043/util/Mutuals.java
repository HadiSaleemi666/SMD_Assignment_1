package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mutual friends between the signed-in user and someone else. */
public final class Mutuals {

    private Mutuals() {
    }

    public static void count(String other, Callback<Integer> cb) {
        list(other, l -> cb.onResult(l.size()));
    }

    public static void list(String other, Callback<List<String>> cb) {
        String me = Db.uid();
        if (me == null || other == null || other.equals(me)) {
            cb.onResult(new ArrayList<>());
            return;
        }
        Db.friends(me).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot mine) {
                Set<String> myFriends = new HashSet<>();
                for (DataSnapshot c : mine.getChildren()) myFriends.add(c.getKey());
                Db.friends(other).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot theirs) {
                        List<String> mutual = new ArrayList<>();
                        for (DataSnapshot c : theirs.getChildren()) {
                            if (myFriends.contains(c.getKey())) mutual.add(c.getKey());
                        }
                        cb.onResult(mutual);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        cb.onResult(new ArrayList<>());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cb.onResult(new ArrayList<>());
            }
        });
    }
}
