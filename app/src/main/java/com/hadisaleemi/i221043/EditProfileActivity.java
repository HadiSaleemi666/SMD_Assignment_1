package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.Session;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edit profile (screen 16). Also used as the first-time profile setup step: new accounts must
 * pick a unique username and write a bio here before they reach Home.
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String EXTRA_SETUP = "setup";

    private EditText etName, etUsername, etWebsite, etBio, etPhone, etWork, etSchool, etLocation;
    private TextView tvEmail, tvGender;
    private AvatarView avatar;
    private ImageView cover;
    private View progress;

    private boolean setupMode;
    private User current;
    private Uri newAvatar, newCover;

    private final ActivityResultLauncher<Intent> pickAvatar = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (uris.isEmpty()) return;
                newAvatar = uris.get(0);
                avatar.setLocalImage(newAvatar);
            });

    private final ActivityResultLauncher<Intent> pickCover = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (uris.isEmpty()) return;
                newCover = uris.get(0);
                Glide.with(this).load(newCover).centerCrop().into(cover);
            });

    public static Intent setup(Context c) {
        return new Intent(c, EditProfileActivity.class).putExtra(EXTRA_SETUP, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_profile_screen);
        setupMode = getIntent().getBooleanExtra(EXTRA_SETUP, false);

        etName = findViewById(R.id.field_name);
        etUsername = findViewById(R.id.field_username);
        etWebsite = findViewById(R.id.field_website);
        etBio = findViewById(R.id.field_bio);
        etPhone = findViewById(R.id.field_phone);
        etWork = findViewById(R.id.field_work);
        etSchool = findViewById(R.id.field_school);
        etLocation = findViewById(R.id.field_location);
        tvEmail = findViewById(R.id.field_email);
        tvGender = findViewById(R.id.field_gender);
        avatar = findViewById(R.id.avatar);
        cover = findViewById(R.id.cover);
        progress = findViewById(R.id.progress);

        if (setupMode) {
            // First-time users cannot skip this step.
            findViewById(R.id.btn_cancel).setVisibility(View.INVISIBLE);
            findViewById(R.id.usernameHint).setVisibility(View.VISIBLE);
        }

        NavHelper.click(this, R.id.btn_cancel, this::finish);
        NavHelper.click(this, R.id.btn_save, this::save);
        NavHelper.click(this, R.id.btn_edit_picture, () -> pickAvatar.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.avatar, () -> pickAvatar.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.btn_edit_cover, () -> pickCover.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.btn_cover_camera, () -> pickCover.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.field_gender, this::pickGender);

        load();
    }

    @Override
    public void onBackPressed() {
        if (setupMode) {
            Toast.makeText(this, "Choose a username to finish setting up your profile", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void load() {
        String uid = Db.uid();
        if (uid == null) {
            finish();
            return;
        }
        Db.user(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                current = snapshot.getValue(User.class);
                if (current == null) current = new User();
                current.uid = uid;
                etName.setText(current.name);
                etUsername.setText(current.username);
                etWebsite.setText(current.website);
                etBio.setText(current.bio);
                etPhone.setText(current.phone);
                etWork.setText(current.work);
                etSchool.setText(current.school);
                etLocation.setText(current.location);
                tvEmail.setText(current.email);
                tvGender.setText(current.gender == null ? "Male" : current.gender);
                if (newAvatar == null) avatar.setUser(current);
                if (newCover == null) Images.load(cover, current.coverUrl);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickGender() {
        String[] options = {"Female", "Male", "Custom"};
        new AlertDialog.Builder(this)
                .setTitle("Gender")
                .setItems(options, (d, which) -> tvGender.setText(options[which]))
                .show();
    }

    static boolean validUsername(String u) {
        return u != null && u.matches("[a-z0-9._]{3,30}");
    }

    private void save() {
        if (current == null) return;
        String name = etName.getText().toString().trim();
        String username = etUsername.getText().toString().trim().toLowerCase(Locale.ROOT);
        String bio = etBio.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Required");
            return;
        }
        if (!validUsername(username)) {
            etUsername.setError("3-30 characters: letters, numbers, . and _");
            return;
        }
        if (setupMode && TextUtils.isEmpty(bio)) {
            etBio.setError("Tell people a little about yourself");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        claimUsername(username, () -> uploadImages(() -> writeProfile(name, username, bio)));
    }

    /** Claims usernames/{username} in a transaction so two people can never share one. */
    private void claimUsername(String username, Runnable then) {
        String uid = current.uid;
        if (username.equals(current.username)) {
            then.run();
            return;
        }
        Db.usernames().child(username).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                String owner = data.getValue(String.class);
                if (owner != null && !owner.equals(uid)) return Transaction.abort();
                data.setValue(uid);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snap) {
                if (!committed) {
                    progress.setVisibility(View.GONE);
                    etUsername.setError("That username is taken");
                    etUsername.requestFocus();
                    return;
                }
                if (current.username != null && !current.username.isEmpty()) {
                    Db.usernames().child(current.username).removeValue();
                }
                then.run();
            }
        });
    }

    private void uploadImages(Runnable then) {
        if (newAvatar != null) {
            MediaUpload.upload(this, newAvatar, "avatars", new MediaUpload.Listener() {
                @Override
                public void onSuccess(String url) {
                    MediaUpload.delete(current.photoUrl);
                    current.photoUrl = url;
                    newAvatar = null;
                    uploadImages(then);
                }

                @Override
                public void onError(Exception e) {
                    failed(e);
                }
            });
        } else if (newCover != null) {
            MediaUpload.upload(this, newCover, "covers", new MediaUpload.Listener() {
                @Override
                public void onSuccess(String url) {
                    MediaUpload.delete(current.coverUrl);
                    current.coverUrl = url;
                    newCover = null;
                    uploadImages(then);
                }

                @Override
                public void onError(Exception e) {
                    failed(e);
                }
            });
        } else {
            then.run();
        }
    }

    private void writeProfile(String name, String username, String bio) {
        String[] parts = name.split("\\s+", 2);
        Map<String, Object> up = new HashMap<>();
        up.put("name", name);
        up.put("nameLower", name.toLowerCase(Locale.ROOT));
        up.put("firstName", parts[0]);
        up.put("lastName", parts.length > 1 ? parts[1] : "");
        up.put("username", username);
        up.put("usernameLower", username);
        up.put("bio", bio);
        up.put("website", etWebsite.getText().toString().trim());
        up.put("phone", etPhone.getText().toString().trim());
        up.put("work", etWork.getText().toString().trim());
        up.put("school", etSchool.getText().toString().trim());
        up.put("location", etLocation.getText().toString().trim());
        up.put("gender", tvGender.getText().toString());
        up.put("photoUrl", current.photoUrl);
        up.put("coverUrl", current.coverUrl);
        up.put("profileComplete", true);
        Db.user(current.uid).updateChildren(up)
                .addOnSuccessListener(v -> {
                    current.name = name;
                    current.username = username;
                    Session.saveRecentLogin(this, current);
                    UserCache.put(current);
                    if (setupMode) {
                        Intent home = new Intent(this, StartSocialMediaActivity.class);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(home);
                    }
                    finish();
                })
                .addOnFailureListener(this::failed);
    }

    private void failed(Exception e) {
        progress.setVisibility(View.GONE);
        Toast.makeText(this, "Could not save: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
    }
}
