package com.hadisaleemi.i221043;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.os.SystemClock;
import android.view.View;

import androidx.test.espresso.NoMatchingViewException;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.util.Db;

import org.hamcrest.Matcher;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Helpers for tests that talk to the real Firebase backend. */
final class TestUtils {

    static final long TIMEOUT_MS = 25_000;
    static final String PASSWORD = "Kinnect#Test2026";

    private TestUtils() {
    }

    /** Polls until the view is on screen; Firebase callbacks are not tracked by Espresso. */
    static void waitForView(Matcher<View> matcher) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | AssertionError e) {
                last = e;
                SystemClock.sleep(300);
            }
        }
        throw new AssertionError("View never appeared: " + matcher, last);
    }

    static <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * One-off read. Uses a single-value listener rather than DatabaseReference.get(), which
     * can hang when disk persistence is enabled (as it is in KinnectApp).
     */
    static DataSnapshot read(DatabaseReference ref) throws Exception {
        TaskCompletionSource<DataSnapshot> result = new TaskCompletionSource<>();
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                result.trySetResult(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.trySetException(error.toException());
            }
        });
        return await(result.getTask());
    }

    static String uniqueEmail(String tag) {
        return String.format(Locale.ROOT, "kinnect.%s.%d@example.com", tag, System.currentTimeMillis());
    }

    /** Creates an account with a finished profile and leaves it signed in. */
    static FirebaseUser createUser(String first, String last) throws Exception {
        FirebaseAuth.getInstance().signOut();
        FirebaseUser fu = await(FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(uniqueEmail(first.toLowerCase(Locale.ROOT)), PASSWORD)).getUser();
        User u = new User();
        u.uid = fu.getUid();
        u.firstName = first;
        u.lastName = last;
        u.name = first + " " + last;
        u.nameLower = u.name.toLowerCase(Locale.ROOT);
        u.email = fu.getEmail();
        u.username = (first + System.currentTimeMillis()).toLowerCase(Locale.ROOT);
        u.usernameLower = u.username;
        u.bio = "Created by an Espresso test";
        u.profileComplete = true;
        u.createdAt = System.currentTimeMillis();
        await(Db.user(u.uid).setValue(u));
        return fu;
    }

    /** Signs in as the given test account and deletes its profile and its Auth user. */
    static void deleteUser(String email, String uid, String username) {
        try {
            FirebaseAuth.getInstance().signOut();
            await(FirebaseAuth.getInstance().signInWithEmailAndPassword(email, PASSWORD));
            if (username != null) await(Db.usernames().child(username).removeValue());
            await(Db.user(uid).removeValue());
            await(FirebaseAuth.getInstance().getCurrentUser().delete());
        } catch (Exception ignored) {
            // Best-effort clean-up; a failed test should still report its own error.
        }
    }
}
