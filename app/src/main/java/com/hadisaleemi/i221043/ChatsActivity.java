package com.hadisaleemi.i221043;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.ChatSummary;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.FriendPicker;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chats (screen 20): every conversation from userChats/{me}, newest first, with the last
 * message, time, unread dot and a live green dot for people who are online.
 */
public class ChatsActivity extends AppCompatActivity {

    private final Subs subs = new Subs();
    private final List<ChatSummary> chats = new ArrayList<>();
    private final List<ChatSummary> shown = new ArrayList<>();
    private final List<String> friends = new ArrayList<>();
    private final List<String> onlineFriends = new ArrayList<>();
    private final Map<String, Boolean> online = new HashMap<>();
    private final Map<String, Runnable> presenceRemovers = new HashMap<>();
    private final ChatAdapter chatAdapter = new ChatAdapter();
    private final ActiveAdapter activeAdapter = new ActiveAdapter();
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);
        String me = Db.uid();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCompose).setOnClickListener(v ->
                FriendPicker.show(this, "New message", uid -> startActivity(ChatActivity.intent(this, uid))));
        NavHelper.click(this, R.id.navPeople, () -> startActivity(new Intent(this, FriendsActivity.class)));
        NavHelper.click(this, R.id.navStories, () -> startActivity(CameraActivity.forStory(this)));

        RecyclerView list = findViewById(R.id.chatList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(chatAdapter);
        RecyclerView active = findViewById(R.id.activeList);
        active.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        active.setAdapter(activeAdapter);

        search = findViewById(R.id.etSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        subs.value(Db.userChats(me).orderByChild("timestamp"), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                chats.clear();
                for (DataSnapshot c : s.getChildren()) {
                    ChatSummary cs = c.getValue(ChatSummary.class);
                    if (cs == null) continue;
                    cs.otherUid = c.getKey();
                    chats.add(0, cs);
                    watchPresence(cs.otherUid);
                }
                filter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.friends(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                friends.clear();
                for (DataSnapshot c : s.getChildren()) {
                    friends.add(c.getKey());
                    watchPresence(c.getKey());
                }
                refreshActive();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /** Live users/{uid}/online for the green dots. */
    private void watchPresence(String uid) {
        if (presenceRemovers.containsKey(uid)) return;
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                online.put(uid, Boolean.TRUE.equals(s.getValue(Boolean.class)));
                refreshActive();
                chatAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        Db.user(uid).child("online").addValueEventListener(l);
        presenceRemovers.put(uid, () -> Db.user(uid).child("online").removeEventListener(l));
    }

    private void refreshActive() {
        onlineFriends.clear();
        for (String f : friends) if (Boolean.TRUE.equals(online.get(f))) onlineFriends.add(f);
        activeAdapter.notifyDataSetChanged();
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (ChatSummary cs : chats) {
            if (q.isEmpty()) {
                shown.add(cs);
                continue;
            }
            User u = UserCache.peek(cs.otherUid);
            if (u != null && u.displayName().toLowerCase(Locale.ROOT).contains(q)) shown.add(cs);
        }
        chatAdapter.notifyDataSetChanged();
        findViewById(R.id.emptyChats).setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
        for (Runnable r : presenceRemovers.values()) r.run();
        presenceRemovers.clear();
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            ChatSummary cs = shown.get(position);
            View v = h.itemView;
            ((AvatarView) v.findViewById(R.id.avatar)).bind(cs.otherUid);
            v.findViewById(R.id.dot).setVisibility(Boolean.TRUE.equals(online.get(cs.otherUid)) ? View.VISIBLE : View.GONE);
            TextView name = v.findViewById(R.id.name);
            name.setText("");
            UserCache.get(cs.otherUid, u -> {
                if (h.getBindingAdapterPosition() == position) name.setText(u.displayName());
            });

            boolean unread = cs.unread > 0;
            boolean mine = Db.uid().equals(cs.lastSenderId);
            String last = cs.lastMessage == null ? "" : cs.lastMessage;
            TextView preview = v.findViewById(R.id.preview);
            preview.setText((mine ? "You: " : "") + last + " · " + TimeFmt.chatList(cs.timestamp));
            preview.setTextColor(getColor(unread ? R.color.ink : R.color.ink2));
            preview.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
            v.findViewById(R.id.trail).setVisibility(unread ? View.VISIBLE : View.INVISIBLE);

            v.setOnClickListener(x -> startActivity(ChatActivity.intent(ChatsActivity.this, cs.otherUid)));
            v.setOnLongClickListener(x -> {
                new AlertDialog.Builder(ChatsActivity.this)
                        .setItems(new String[]{"Delete conversation"}, (d, w) ->
                                Db.userChats(Db.uid()).child(cs.otherUid).removeValue())
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    /** "Your note" first, then friends who are online right now. */
    private class ActiveAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_active, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            AvatarView av = v.findViewById(R.id.avatar);
            TextView label = v.findViewById(R.id.label);
            if (position == 0) {
                av.setVisibility(View.INVISIBLE);
                v.findViewById(R.id.plus).setVisibility(View.VISIBLE);
                v.findViewById(R.id.dot).setVisibility(View.GONE);
                label.setText(R.string.your_note);
                v.setOnClickListener(x -> startActivity(CameraActivity.forStory(ChatsActivity.this)));
                return;
            }
            String uid = onlineFriends.get(position - 1);
            av.setVisibility(View.VISIBLE);
            av.bind(uid);
            v.findViewById(R.id.plus).setVisibility(View.GONE);
            v.findViewById(R.id.dot).setVisibility(View.VISIBLE);
            label.setText("");
            UserCache.get(uid, u -> {
                if (h.getBindingAdapterPosition() == position) label.setText(u.firstName());
            });
            v.setOnClickListener(x -> startActivity(ChatActivity.intent(ChatsActivity.this, uid)));
        }

        @Override
        public int getItemCount() {
            return onlineFriends.size() + 1;
        }
    }
}
