package com.hadisaleemi.i221043.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Central place for every Realtime Database path used by Kinnect.
 *
 * users/{uid}                              profile + presence
 * usernames/{username}                     uid (enforces unique usernames)
 * friends/{uid}/{friendUid}                timestamp
 * friendRequests/{toUid}/{fromUid}         timestamp (incoming requests)
 * sentRequests/{fromUid}/{toUid}           timestamp (outgoing requests)
 * closeFriends/{uid}/{friendUid}           true
 * posts/{postId}                           Post
 * reactions/{postId}/{uid}                 reaction key
 * comments/{postId}/{commentId}            Comment
 * commentLikes/{postId}/{commentId}/{uid}  true
 * stories/{uid}/{storyId}                  Story
 * storyViews/{ownerUid}/{storyId}/{uid}    timestamp
 * messages/{chatId}/{messageId}            Message
 * userChats/{uid}/{otherUid}               ChatSummary
 * notifications/{uid}/{id}                 AppNotification
 * calls/{calleeUid}                        CallInfo of the current incoming call
 * callLogs/{uid}/{callId}                  CallInfo
 */
public final class Db {

    private Db() {
    }

    public static DatabaseReference root() {
        return FirebaseDatabase.getInstance().getReference();
    }

    public static String uid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public static DatabaseReference users() {
        return root().child("users");
    }

    public static DatabaseReference user(String uid) {
        return users().child(uid);
    }

    public static DatabaseReference usernames() {
        return root().child("usernames");
    }

    public static DatabaseReference friends(String uid) {
        return root().child("friends").child(uid);
    }

    public static DatabaseReference friendRequests(String uid) {
        return root().child("friendRequests").child(uid);
    }

    public static DatabaseReference sentRequests(String uid) {
        return root().child("sentRequests").child(uid);
    }

    public static DatabaseReference closeFriends(String uid) {
        return root().child("closeFriends").child(uid);
    }

    public static DatabaseReference posts() {
        return root().child("posts");
    }

    public static DatabaseReference reactions(String postId) {
        return root().child("reactions").child(postId);
    }

    public static DatabaseReference comments(String postId) {
        return root().child("comments").child(postId);
    }

    public static DatabaseReference commentLikes(String postId) {
        return root().child("commentLikes").child(postId);
    }

    public static DatabaseReference stories() {
        return root().child("stories");
    }

    public static DatabaseReference storyViews(String ownerUid, String storyId) {
        return root().child("storyViews").child(ownerUid).child(storyId);
    }

    public static DatabaseReference messages(String chatId) {
        return root().child("messages").child(chatId);
    }

    public static DatabaseReference userChats(String uid) {
        return root().child("userChats").child(uid);
    }

    public static DatabaseReference notifications(String uid) {
        return root().child("notifications").child(uid);
    }

    public static DatabaseReference calls(String calleeUid) {
        return root().child("calls").child(calleeUid);
    }

    public static DatabaseReference callLogs(String uid) {
        return root().child("callLogs").child(uid);
    }

    /** Both users derive the same chat id by sorting their uids. */
    public static String chatId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}
