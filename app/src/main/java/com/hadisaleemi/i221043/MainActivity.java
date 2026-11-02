package com.hadisaleemi.i221043;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.hadisaleemi.i221043.util.AuthFlow;

/** Splash (screen 01): visible for 5 seconds, then Home if already signed in, else Log in. */
public class MainActivity extends AppCompatActivity {

    static final long SPLASH_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::route;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(next, SPLASH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(next);
    }

    private void route() {
        if (!KinnectApp.firebaseReady(this)) {
            Toast.makeText(this, "Add app/google-services.json from the Firebase console", Toast.LENGTH_LONG).show();
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // Already logged in: AuthFlow sends the user to Home (or profile setup if unfinished).
            AuthFlow.afterSignIn(this);
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}
