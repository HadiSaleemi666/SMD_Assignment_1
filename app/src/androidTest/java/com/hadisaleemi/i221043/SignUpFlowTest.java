package com.hadisaleemi.i221043;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
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
 * Critical workflow 1: sign up -> profile saved in the Realtime Database -> first-time users are
 * sent to profile setup (screen 16) -> unique username claimed -> Home.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SignUpFlowTest {

    private String email;
    private String username;
    private String uid;

    @Before
    public void setUp() {
        FirebaseAuth.getInstance().signOut();
        long now = System.currentTimeMillis();
        email = TestUtils.uniqueEmail("signup");
        username = "espresso" + now;
    }

    @Test
    public void signUp_savesProfile_thenSetupLeadsHome() throws Exception {
        try (ActivityScenario<CreateAccountActivity> ignored = ActivityScenario.launch(CreateAccountActivity.class)) {
            onView(withId(R.id.etFirstName)).perform(typeText("Espresso"));
            onView(withId(R.id.etLastName)).perform(typeText("Tester"));
            onView(withId(R.id.etEmail)).perform(replaceText(email));
            onView(withId(R.id.textView3)).perform(replaceText(TestUtils.PASSWORD));
            closeSoftKeyboard();
            onView(withId(R.id.button2)).perform(click());

            // First-time users land on the profile setup step, not Home.
            TestUtils.waitForView(withId(R.id.usernameHint));

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            assertNotNull("Firebase Auth account was not created", user);
            uid = user.getUid();
            DataSnapshot profile = TestUtils.read(Db.user(uid));
            assertTrue("users/{uid} was not written", profile.exists());
            assertEquals(email, profile.child("email").getValue(String.class));
            assertEquals("Espresso Tester", profile.child("name").getValue(String.class));
            assertFalse(Boolean.TRUE.equals(profile.child("profileComplete").getValue(Boolean.class)));

            onView(withId(R.id.field_username)).perform(replaceText(username));
            onView(withId(R.id.field_bio)).perform(replaceText("Testing Kinnect end to end"));
            closeSoftKeyboard();
            onView(withId(R.id.btn_save)).perform(click());

            TestUtils.waitForView(withId(R.id.feed));

            DataSnapshot after = TestUtils.read(Db.user(uid));
            assertTrue(Boolean.TRUE.equals(after.child("profileComplete").getValue(Boolean.class)));
            assertEquals(username, after.child("username").getValue(String.class));
            assertEquals(uid, TestUtils.read(Db.usernames().child(username)).getValue(String.class));
        }
    }

    @After
    public void tearDown() {
        if (uid != null) TestUtils.deleteUser(email, uid, username);
        FirebaseAuth.getInstance().signOut();
    }
}
