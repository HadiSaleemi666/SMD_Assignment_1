package com.hadisaleemi.i221043;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.hadisaleemi.i221043.util.Db;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Critical workflow 2: sending a chat message. The message must show up in the conversation,
 * be stored under messages/{chatId}, update the receiver's chat list (last message + unread)
 * and offer Edit / Delete while it is less than 5 minutes old.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ChatMessagingTest {

    private FirebaseUser receiver;
    private FirebaseUser sender;
    private String receiverEmail;
    private String senderEmail;
    private String receiverUsername;
    private String senderUsername;

    @Before
    public void setUp() throws Exception {
        receiver = TestUtils.createUser("Rida", "Receiver");
        receiverEmail = receiver.getEmail();
        receiverUsername = TestUtils.read(Db.user(receiver.getUid()).child("username")).getValue(String.class);
        sender = TestUtils.createUser("Sami", "Sender"); // stays signed in
        senderEmail = sender.getEmail();
        senderUsername = TestUtils.read(Db.user(sender.getUid()).child("username")).getValue(String.class);
    }

    @Test
    public void sendMessage_appearsInChat_andUpdatesBothChatLists() throws Exception {
        String text = "Hello from Espresso " + System.currentTimeMillis();
        Context ctx = ApplicationProvider.getApplicationContext();
        Intent open = ChatActivity.intent(ctx, receiver.getUid());

        try (ActivityScenario<ChatActivity> ignored = ActivityScenario.launch(open)) {
            onView(withId(R.id.etMsg)).perform(replaceText(text));
            closeSoftKeyboard();
            onView(withId(R.id.btnSend)).perform(click());

            // Shown in the conversation once the Realtime Database listener fires.
            TestUtils.waitForView(withText(text));

            String chatId = Db.chatId(sender.getUid(), receiver.getUid());
            DataSnapshot messages = TestUtils.read(Db.messages(chatId));
            DataSnapshot sent = null;
            for (DataSnapshot m : messages.getChildren()) {
                if (text.equals(m.child("text").getValue(String.class))) sent = m;
            }
            assertNotNull("Message not stored in messages/" + chatId, sent);
            assertEquals(sender.getUid(), sent.child("senderId").getValue(String.class));

            DataSnapshot theirList = TestUtils.read(Db.userChats(receiver.getUid()).child(sender.getUid()));
            assertEquals(text, theirList.child("lastMessage").getValue(String.class));
            Integer unread = theirList.child("unread").getValue(Integer.class);
            assertTrue("Receiver's unread counter was not increased", unread != null && unread >= 1);

            // Fresh message: Edit and Delete are offered (they disappear after 5 minutes).
            onView(withText(text)).perform(longClick());
            onView(withText("Edit")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("Delete")).inRoot(isDialog()).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @After
    public void tearDown() {
        if (sender == null || receiver == null) return;
        String chatId = Db.chatId(sender.getUid(), receiver.getUid());
        try {
            // Still signed in as the sender: own messages can be deleted inside the 5-minute window.
            for (DataSnapshot m : TestUtils.read(Db.messages(chatId)).getChildren()) {
                TestUtils.await(m.getRef().removeValue());
            }
            TestUtils.await(Db.userChats(sender.getUid()).child(receiver.getUid()).removeValue());
        } catch (Exception ignored) {
        }
        TestUtils.deleteUser(senderEmail, sender.getUid(), senderUsername);
        try {
            FirebaseAuth.getInstance().signOut();
            TestUtils.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(receiverEmail, TestUtils.PASSWORD));
            TestUtils.await(Db.userChats(receiver.getUid()).child(sender.getUid()).removeValue());
        } catch (Exception ignored) {
        }
        TestUtils.deleteUser(receiverEmail, receiver.getUid(), receiverUsername);
        FirebaseAuth.getInstance().signOut();
    }
}
