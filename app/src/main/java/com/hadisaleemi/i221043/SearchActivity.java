package com.hadisaleemi.i221043;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.adapters.PersonAdapter;
import com.hadisaleemi.i221043.adapters.PostAdapter;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Search (screen 13). As the user types, users are found by username or name with prefix
 * queries on the indexed usernameLower / nameLower fields. The Posts chip filters posts from
 * the user and their friends by text.
 */
public class SearchActivity extends AppCompatActivity {

    private static final int LIMIT = 20;
    private static final int MAX_RECENT = 8;

    private EditText query;
    private RecyclerView results;
    private PersonAdapter people;
    private PostAdapter posts;
    private boolean postMode;
    private int requestToken;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runSearch = this::search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_screen);
        query = findViewById(R.id.tv_query);
        results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        people = new PersonAdapter(this);
        posts = new PostAdapter(this, null);
        results.setAdapter(people);

        NavHelper.click(this, R.id.backarrow, this::finish);
        NavHelper.click(this, R.id.btn_clear, () -> query.setText(""));
        NavHelper.click(this, R.id.chip_all, () -> setPostMode(false));
        NavHelper.click(this, R.id.chip_people, () -> setPostMode(false));
        NavHelper.click(this, R.id.chip_posts, () -> setPostMode(true));
        NavHelper.click(this, R.id.chip_groups, () -> Toast.makeText(this, "Groups are coming soon", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.chip_marketplace, () -> startActivity(new Intent(this, MarketplaceActivity.class)));
        NavHelper.click(this, R.id.clear_recent, () -> {
            Session.setRecentSearches(this, "");
            showRecent();
        });

        people.setOnPick(uid -> {
            remember(query.getText().toString().trim());
            com.hadisaleemi.i221043.util.PostActions.openProfile(this, uid);
        });

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        query.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                remember(query.getText().toString().trim());
                search();
                return true;
            }
            return false;
        });
        query.requestFocus();
        showRecent();
    }

    private void setPostMode(boolean on) {
        postMode = on;
        int[] chips = {R.id.chip_all, R.id.chip_people, R.id.chip_posts};
        for (int id : chips) {
            boolean selected = on ? id == R.id.chip_posts : id == R.id.chip_all;
            TextView chip = findViewById(id);
            chip.setBackgroundResource(selected ? R.drawable.lightseagreen_chip : R.drawable.beige_chip);
            chip.setTextColor(getColor(selected ? R.color.SeaGreen : R.color.black));
        }
        results.setAdapter(on ? posts : people);
        ((TextView) findViewById(R.id.people_heading)).setText(on ? R.string.chip_posts : R.string.people);
        search();
    }

    private void search() {
        String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        int token = ++requestToken;
        boolean empty = TextUtils.isEmpty(q);
        findViewById(R.id.recent_section).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.people_heading).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            people.setUsers(new ArrayList<>());
            posts.setPosts(new ArrayList<>());
            findViewById(R.id.noResults).setVisibility(View.GONE);
            showRecent();
            return;
        }
        if (postMode) searchPosts(q, token);
        else searchPeople(q, token);
    }

    /** Two prefix queries (username and name), merged with exact username matches first. */
    private void searchPeople(String q, int token) {
        String me = Db.uid();
        Map<String, User> found = new LinkedHashMap<>();
        int[] pending = {2};
        ValueEventListener collect = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                for (DataSnapshot c : s.getChildren()) {
                    User u = c.getValue(User.class);
                    if (u == null || c.getKey().equals(me)) continue;
                    u.uid = c.getKey();
                    found.put(u.uid, u);
                }
                if (--pending[0] == 0 && token == requestToken) showPeople(q, found);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (--pending[0] == 0 && token == requestToken) showPeople(q, found);
            }
        };
        Db.users().orderByChild("usernameLower").startAt(q).endAt(q + "").limitToFirst(LIMIT)
                .addListenerForSingleValueEvent(collect);
        Db.users().orderByChild("nameLower").startAt(q).endAt(q + "").limitToFirst(LIMIT)
                .addListenerForSingleValueEvent(collect);
    }

    private void showPeople(String q, Map<String, User> found) {
        List<User> list = new ArrayList<>(found.values());
        list.sort((a, b) -> {
            boolean ea = q.equals(a.usernameLower), eb = q.equals(b.usernameLower);
            if (ea != eb) return ea ? -1 : 1;
            return a.displayName().compareToIgnoreCase(b.displayName());
        });
        List<String> uids = new ArrayList<>();
        for (User u : list) uids.add(u.uid);
        people.setUsers(uids);
        findViewById(R.id.noResults).setVisibility(uids.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Posts by me and my friends whose text contains the query. */
    private void searchPosts(String q, int token) {
        String me = Db.uid();
        Db.friends(me).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot fs) {
                Set<String> allowed = new HashSet<>();
                allowed.add(me);
                for (DataSnapshot c : fs.getChildren()) allowed.add(c.getKey());
                Db.posts().orderByChild("timestamp").limitToLast(300).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        if (token != requestToken) return;
                        List<Post> list = new ArrayList<>();
                        for (DataSnapshot c : s.getChildren()) {
                            Post p = c.getValue(Post.class);
                            if (p == null || !allowed.contains(p.uid) || p.text == null) continue;
                            if (!p.text.toLowerCase(Locale.ROOT).contains(q)) continue;
                            p.id = c.getKey();
                            list.add(p);
                        }
                        list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
                        posts.setPosts(list);
                        findViewById(R.id.noResults).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private List<String> recents() {
        String raw = Session.recentSearches(this);
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split("\n")));
        return out;
    }

    private void remember(String q) {
        if (q.isEmpty()) return;
        List<String> list = recents();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        Session.setRecentSearches(this, TextUtils.join("\n", list));
    }

    private void showRecent() {
        LinearLayout box = findViewById(R.id.recentList);
        box.removeAllViews();
        List<String> list = recents();
        findViewById(R.id.recent_section).setVisibility(list.isEmpty() ? View.GONE
                : (query.getText().length() == 0 ? View.VISIBLE : View.GONE));
        for (String r : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_recent_search, box, false);
            ((TextView) row.findViewById(R.id.recentText)).setText(r);
            row.setOnClickListener(v -> {
                query.setText(r);
                query.setSelection(r.length());
            });
            row.findViewById(R.id.recentRemove).setOnClickListener(v -> {
                List<String> l = recents();
                l.remove(r);
                Session.setRecentSearches(this, TextUtils.join("\n", l));
                showRecent();
            });
            box.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        posts.release();
    }
}
