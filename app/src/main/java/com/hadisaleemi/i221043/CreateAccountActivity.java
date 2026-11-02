package com.hadisaleemi.i221043;

import android.app.DatePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.util.AuthFlow;
import com.hadisaleemi.i221043.util.Db;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * Sign up (screen 03). Creates the Firebase Auth account, then stores the profile in
 * users/{uid}. The password strength bar updates on every keystroke.
 */
public class CreateAccountActivity extends AppCompatActivity {

    private static final int WEAK = 0xFFBC4A28;
    private static final int MEDIUM = 0xFFC98A12;
    private static final int STRONG = 0xFF0B5F63;
    private static final int EMPTY = 0xFFE3DFD8;

    private EditText etFirst, etLast, etEmail, etPassword;
    private TextView tvDay, tvMonth, tvYear, strengthLabel;
    private View[] segments;
    private View progress;
    private ImageView checkbox;
    private boolean termsAccepted = true;
    private boolean passwordVisible;
    private String gender = "Male";
    private final Calendar birthday = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_account_screen);

        etFirst = findViewById(R.id.etFirstName);
        etLast = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.textView3);
        tvDay = findViewById(R.id.tvDay);
        tvMonth = findViewById(R.id.tvMonth);
        tvYear = findViewById(R.id.tvYear);
        strengthLabel = findViewById(R.id.strengthLabel);
        segments = new View[]{findViewById(R.id.strength1), findViewById(R.id.strength2), findViewById(R.id.strength3)};
        progress = findViewById(R.id.progress);
        checkbox = findViewById(R.id.checkbox);

        birthday.set(2004, Calendar.MARCH, 14);
        showBirthday();

        NavHelper.click(this, R.id.backbutton, this::finish);
        NavHelper.click(this, R.id.loginLink, this::finish);
        NavHelper.click(this, R.id.button2, this::createAccount);
        NavHelper.click(this, R.id.btnReveal, this::togglePassword);
        NavHelper.click(this, R.id.DayView3RL, this::pickBirthday);
        NavHelper.click(this, R.id.MonthView3RL, this::pickBirthday);
        NavHelper.click(this, R.id.YearView3RL, this::pickBirthday);
        NavHelper.click(this, R.id.FemaleChoice, () -> selectGender("Female"));
        NavHelper.click(this, R.id.MaleChoice, () -> selectGender("Male"));
        NavHelper.click(this, R.id.CustomChoice, () -> selectGender("Custom"));
        checkbox.setOnClickListener(v -> {
            termsAccepted = !termsAccepted;
            checkbox.setImageResource(termsAccepted ? R.drawable.checkbox : R.drawable.checkbox_empty);
        });

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        showStrength("");
    }

    /** 0 = empty, 1 = weak, 2 = medium, 3 = strong. */
    static int strength(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        int score = 0;
        if (pw.length() >= 8) score++;
        if (pw.length() >= 12) score++;
        if (pw.matches(".*[a-z].*") && pw.matches(".*[A-Z].*")) score++;
        if (pw.matches(".*\\d.*")) score++;
        if (pw.matches(".*[^A-Za-z0-9].*")) score++;
        if (pw.length() < 6 || score <= 1) return 1;
        if (score <= 3) return 2;
        return 3;
    }

    private void showStrength(String pw) {
        int level = strength(pw);
        int color = level == 1 ? WEAK : level == 2 ? MEDIUM : STRONG;
        for (int i = 0; i < segments.length; i++) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(4 * getResources().getDisplayMetrics().density);
            d.setColor(i < level ? color : EMPTY);
            segments[i].setBackground(d);
        }
        String[] labels = {"", "Weak", "Medium", "Strong"};
        strengthLabel.setText(labels[level]);
        strengthLabel.setTextColor(level == 0 ? 0xFF84817C : color);
    }

    private void selectGender(String g) {
        gender = g;
        int[] cards = {R.id.FemaleChoice, R.id.MaleChoice, R.id.CustomChoice};
        int[] dots = {R.id.dotFemale, R.id.dotMale, R.id.dotCustom};
        String[] names = {"Female", "Male", "Custom"};
        for (int i = 0; i < 3; i++) {
            boolean on = names[i].equals(g);
            CardView card = findViewById(cards[i]);
            card.setForeground(on ? getDrawable(R.drawable.selected_choice_border) : null);
            findViewById(dots[i]).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void pickBirthday() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            birthday.set(y, m, d);
            showBirthday();
        }, birthday.get(Calendar.YEAR), birthday.get(Calendar.MONTH), birthday.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showBirthday() {
        tvDay.setText(String.valueOf(birthday.get(Calendar.DAY_OF_MONTH)));
        tvMonth.setText(new DateFormatSymbols(Locale.getDefault()).getShortMonths()[birthday.get(Calendar.MONTH)]);
        tvYear.setText(String.valueOf(birthday.get(Calendar.YEAR)));
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | (passwordVisible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        etPassword.setSelection(etPassword.getText().length());
    }

    private void createAccount() {
        String first = etFirst.getText().toString().trim();
        String last = etLast.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(first)) {
            etFirst.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(last)) {
            etLast.setError("Required");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Use at least 6 characters");
            return;
        }
        if (!termsAccepted) {
            Toast.makeText(this, "Please accept the Terms and Privacy Policy", Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> saveProfile(r.getUser(), first, last, email))
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Sign up failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveProfile(FirebaseUser fu, String first, String last, String email) {
        User u = new User();
        u.uid = fu.getUid();
        u.firstName = first;
        u.lastName = last;
        u.name = first + " " + last;
        u.nameLower = u.name.toLowerCase(Locale.ROOT);
        u.email = email;
        u.gender = gender;
        u.birthday = String.format(Locale.ROOT, "%04d-%02d-%02d", birthday.get(Calendar.YEAR),
                birthday.get(Calendar.MONTH) + 1, birthday.get(Calendar.DAY_OF_MONTH));
        u.createdAt = System.currentTimeMillis();
        u.profileComplete = false; // the username + bio step (screen 16) finishes the profile
        Db.user(u.uid).setValue(u)
                .addOnSuccessListener(v -> AuthFlow.afterSignIn(this))
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not save profile: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
