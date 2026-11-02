package com.hadisaleemi.i221043.util;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/** Collects a screen's Firebase listeners so they can all be removed in onDestroy. */
public class Subs {

    private final List<Runnable> removers = new ArrayList<>();

    public ValueEventListener value(Query q, ValueEventListener l) {
        q.addValueEventListener(l);
        removers.add(() -> q.removeEventListener(l));
        return l;
    }

    public ChildEventListener child(Query q, ChildEventListener l) {
        q.addChildEventListener(l);
        removers.add(() -> q.removeEventListener(l));
        return l;
    }

    public void add(Runnable remover) {
        removers.add(remover);
    }

    public void clear() {
        for (Runnable r : removers) r.run();
        removers.clear();
    }
}
