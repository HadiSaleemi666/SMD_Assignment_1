package com.hadisaleemi.i221043;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.AuthFlow;
import com.hadisaleemi.i221043.util.Session;

/** Log in (screen 02) with Firebase email/password auth and the Recent login card. */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private View progress;
    private boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        etEmail = findViewById(R.id.textView2);
        etPassword = findViewById(R.id.textView3);
        progress = findViewById(R.id.progress);

        bindRecentLogin();

        NavHelper.click(this, R.id.button, this::login);
        NavHelper.click(this, R.id.btnReveal, this::togglePassword);
        NavHelper.click(this, R.id.text4, this::resetPassword);
        NavHelper.click(this, R.id.button2, () -> startActivity(new Intent(this, CreateAccountActivity.class)));
    }

    /** Shows the last account that signed in on this device; tapping it pre-fills the email. */
    private void bindRecentLogin() {
        String email = Session.recentEmail(this);
        View card = findViewById(R.id.cardView2);
        View label = findViewById(R.id.text2);
        if (email == null) {
            card.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            return;
        }
        User recent = new User();
        recent.uid = Session.recentUid(this);
        recent.name = Session.recentName(this);
        recent.photoUrl = Session.recentPhoto(this);
        ((AvatarView) findViewById(R.id.pfp)).setUser(recent);
        ((TextView) findViewById(R.id.textView)).setText(recent.displayName());
        card.setOnClickListener(v -> {
            etEmail.setText(email);
            etPassword.requestFocus();
        });
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | (passwordVisible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        etPassword.setSelection(etPassword.getText().length());
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter your password");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> AuthFlow.afterSignIn(this))
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Log in failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email first");
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> Toast.makeText(this, "Password reset email sent", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }
}
