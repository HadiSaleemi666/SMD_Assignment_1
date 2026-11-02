package com.hadisaleemi.i221043.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.services.PushSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Sending, editing and deleting chat messages. Each send writes the message plus both users'
 * chat-list rows (userChats) in one multi-path update and bumps the receiver's unread counter.
 * After the write succeeds PushSender sends the FCM push to the other person.
 */
public final class Chats {

    private Chats() {
    }

    public static Message text(String text) {
        Message m = new Message();
        m.type = Message.TEXT;
        m.text = text;
        return m;
    }

    public static void send(String otherUid, Message m) {
        String me = Db.uid();
        if (me == null || otherUid == null) return;
        String chatId = Db.chatId(me, otherUid);
        DatabaseReference ref = Db.messages(chatId).push();
        m.id = ref.getKey();
        m.senderId = me;
        m.timestamp = System.currentTimeMillis();

        Map<String, Object> up = new HashMap<>();
        up.put("messages/" + chatId + "/" + m.id, m);
        putSummary(up, me, otherUid, chatId, preview(m), me, m.timestamp);
        putSummary(up, otherUid, me, chatId, preview(m), me, m.timestamp);
        Db.root().updateChildren(up).addOnSuccessListener(v -> {
            Db.userChats(otherUid).child(me).child("unread").setValue(ServerValue.increment(1));
            PushSender.message(otherUid, m);
        });
    }

    public static void edit(String otherUid, Message m, String newText) {
        String me = Db.uid();
        if (me == null || !m.canModify(me, System.currentTimeMillis())) return;
        String chatId = Db.chatId(me, otherUid);
        Map<String, Object> up = new HashMap<>();
        up.put("text", newText);
        up.put("edited", true);
        Db.messages(chatId).child(m.id).updateChildren(up).addOnSuccessListener(v -> refreshSummaries(otherUid));
    }

    public static void delete(String otherUid, Message m) {
        String me = Db.uid();
        if (me == null || !m.canModify(me, System.currentTimeMillis())) return;
        String chatId = Db.chatId(me, otherUid);
        Db.messages(chatId).child(m.id).removeValue().addOnSuccessListener(v -> refreshSummaries(otherUid));
    }

    public static void toggleHeart(String otherUid, Message m) {
        String me = Db.uid();
        if (me == null) return;
        DatabaseReference h = Db.messages(Db.chatId(me, otherUid)).child(m.id).child("hearts").child(me);
        boolean hearted = m.hearts != null && Boolean.TRUE.equals(m.hearts.get(me));
        if (hearted) h.removeValue();
        else h.setValue(true);
    }

    public static void markRead(String otherUid) {
        String me = Db.uid();
        if (me == null) return;
        Db.userChats(me).child(otherUid).child("unread").setValue(0);
    }

    /** Keeps the chat list preview in sync after the latest message was edited or deleted. */
    private static void refreshSummaries(String otherUid) {
        String me = Db.uid();
        String chatId = Db.chatId(me, otherUid);
        Db.messages(chatId).orderByChild("timestamp").limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Message last = null;
                        for (DataSnapshot c : snapshot.getChildren()) last = c.getValue(Message.class);
                        String text = last == null ? "" : preview(last);
                        String sender = last == null ? me : last.senderId;
                        long ts = last == null ? System.currentTimeMillis() : last.timestamp;
                        Map<String, Object> up = new HashMap<>();
                        putSummary(up, me, otherUid, chatId, text, sender, ts);
                        putSummary(up, otherUid, me, chatId, text, sender, ts);
                        Db.root().updateChildren(up);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private static void putSummary(Map<String, Object> up, String owner, String other, String chatId,
                                   String last, String sender, long ts) {
        String base = "userChats/" + owner + "/" + other + "/";
        up.put(base + "chatId", chatId);
        up.put(base + "otherUid", other);
        up.put(base + "lastMessage", last);
        up.put(base + "lastSenderId", sender);
        up.put(base + "timestamp", ts);
    }

    public static String preview(Message m) {
        if (m == null) return "";
        switch (m.type == null ? Message.TEXT : m.type) {
            case Message.IMAGE: return "Sent a photo";
            case Message.POST: return "Shared a post";
            case Message.STORY_REPLY: return "Replied to a story: " + (m.text == null ? "" : m.text);
            case Message.CALL: return m.text == null ? "Call" : m.text;
            default: return m.text == null ? "" : m.text;
        }
    }
}
