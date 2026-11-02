package com.hadisaleemi.i221043;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.adapters.PostAdapter;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.AuthFlow;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.PostActions;
import com.hadisaleemi.i221043.util.ProfileData;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.List;

/** Your own profile (screen 15). The camera buttons upload a new profile or cover photo. */
public class MyProfileActivity extends AppCompatActivity {

    private final Subs subs = new Subs();
    private PostAdapter postAdapter;

    private final ActivityResultLauncher<Intent> pickAvatar = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> upload(PictureSendActivity.results(r.getData()), "avatars", "photoUrl"));

    private final ActivityResultLauncher<Intent> pickCover = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> upload(PictureSendActivity.results(r.getData()), "covers", "coverUrl"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_screen);
        String me = Db.uid();
        if (me == null) {
            finish();
            return;
        }

        NavHelper.click(this, R.id.backarrow, this::finish);
        NavHelper.click(this, R.id.btn_search, () -> startActivity(new Intent(this, SearchActivity.class)));
        NavHelper.click(this, R.id.btn_cover_camera, () -> pickCover.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.btn_avatar_camera, () -> pickAvatar.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.btn_add_story, () -> startActivity(CameraActivity.forStory(this)));
        NavHelper.click(this, R.id.btn_edit_profile, () -> startActivity(new Intent(this, EditProfileActivity.class)));
        NavHelper.click(this, R.id.find_friends, () -> startActivity(new Intent(this, FriendsActivity.class)));
        NavHelper.click(this, R.id.friends_total, () -> startActivity(new Intent(this, FriendsActivity.class)));
        NavHelper.click(this, R.id.btn_more, () -> new AlertDialog.Builder(this)
                .setItems(new String[]{"Edit profile", "Log out"}, (d, w) -> {
                    if (w == 0) startActivity(new Intent(this, EditProfileActivity.class));
                    else AuthFlow.logout(this);
                }).show());
        NavHelper.click(this, R.id.tab_photos, () -> Toast.makeText(this, "Tap a photo in your posts to view it", Toast.LENGTH_SHORT).show());

        postAdapter = new PostAdapter(this, null);
        RecyclerView posts = findViewById(R.id.profilePosts);
        posts.setLayoutManager(new LinearLayoutManager(this));
        posts.setAdapter(postAdapter);

        subs.value(Db.user(me), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = me;
                UserCache.put(u);
                bind(u);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        ProfileData.friends(subs, me, this::bindFriends);
        ProfileData.posts(subs, me, list -> {
            postAdapter.setPosts(list);
            findViewById(R.id.noPosts).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void bind(User u) {
        ((TextView) findViewById(R.id.titleName)).setText(u.displayName());
        ((TextView) findViewById(R.id.profile_name)).setText(u.displayName());
        ((AvatarView) findViewById(R.id.profileAvatar)).setUser(u);
        Images.load(findViewById(R.id.coverImage), u.coverUrl);
        TextView bio = findViewById(R.id.profile_bio);
        bio.setText(u.bio == null || u.bio.isEmpty() ? getString(R.string.add_bio_hint) : u.bio);
        detail(R.id.detail_work_text, u.work, "Add your workplace");
        detail(R.id.detail_school_text, u.school, "Add your school");
        detail(R.id.detail_location_text, u.location == null || u.location.isEmpty() ? null : "Lives in " + u.location, "Add your city");
        ((TextView) findViewById(R.id.detail_joined_text)).setText(TimeFmt.joined(u.createdAt));
    }

    private void detail(int id, String value, String placeholder) {
        TextView tv = findViewById(id);
        boolean has = value != null && !value.trim().isEmpty();
        tv.setText(has ? value : placeholder);
        tv.setTextColor(getColor(has ? R.color.black : R.color.gray));
        tv.setOnClickListener(has ? null : v -> startActivity(new Intent(this, EditProfileActivity.class)));
    }

    private void bindFriends(List<String> friends) {
        int n = friends.size();
        ((TextView) findViewById(R.id.friends_count)).setText(String.valueOf(n));
        ((TextView) findViewById(R.id.friends_total)).setText(getResources().getQuantityString(R.plurals.friend_count, n, n));
        int[] cards = {R.id.friend_lm, R.id.friend_ak, R.id.friend_of};
        int[] inits = {R.id.friend_lm_init, R.id.friend_ak_init, R.id.friend_of_init};
        int[] photos = {R.id.friend_lm_photo, R.id.friend_ak_photo, R.id.friend_of_photo};
        int[] names = {R.id.friend_lm_name, R.id.friend_ak_name, R.id.friend_of_name};
        for (int i = 0; i < cards.length; i++) {
            CardView card = findViewById(cards[i]);
            if (i >= n) {
                card.setVisibility(View.INVISIBLE);
                continue;
            }
            card.setVisibility(View.VISIBLE);
            String uid = friends.get(i);
            card.setCardBackgroundColor(AvatarView.colorFor(uid));
            TextView init = findViewById(inits[i]);
            ImageView photo = findViewById(photos[i]);
            TextView name = findViewById(names[i]);
            UserCache.get(uid, u -> {
                init.setText(AvatarView.initialsOf(u.displayName()));
                name.setText(u.displayName());
                Images.load(photo, u.photoUrl);
            });
            card.setOnClickListener(v -> PostActions.openProfile(this, uid));
        }
    }

    private void upload(List<Uri> uris, String folder, String field) {
        if (uris.isEmpty()) return;
        Toast.makeText(this, "Uploading…", Toast.LENGTH_SHORT).show();
        MediaUpload.upload(this, uris.get(0), folder, new MediaUpload.Listener() {
            @Override
            public void onSuccess(String url) {
                User me = UserCache.peek(Db.uid());
                String old = me == null ? null : ("photoUrl".equals(field) ? me.photoUrl : me.coverUrl);
                Db.user(Db.uid()).child(field).setValue(url).addOnSuccessListener(v -> MediaUpload.delete(old));
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MyProfileActivity.this, "Upload failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
        if (postAdapter != null) postAdapter.release();
    }
}
