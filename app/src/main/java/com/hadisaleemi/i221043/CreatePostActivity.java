package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.bumptech.glide.Glide;
import com.google.firebase.database.DatabaseReference;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.FriendPicker;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create post (screen 07). Text plus any number of photos picked in the Photo picker (08) or
 * taken with the camera; photos are stored by MediaUpload and the post in posts/{postId}.
 */
public class CreatePostActivity extends AppCompatActivity {

    private static final String EXTRA_OPEN_PICKER = "openPicker";
    private static final String EXTRA_URIS = "uris";

    private final List<Uri> images = new ArrayList<>();
    private final ImagesAdapter imagesAdapter = new ImagesAdapter();
    private EditText text;
    private RecyclerView imageStrip;
    private View postButton;
    private String bgColor;
    private boolean posting;
    private boolean sheetExpanded = true;

    private final ActivityResultLauncher<Intent> pick = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> addImages(PictureSendActivity.results(r.getData())));

    private final ActivityResultLauncher<Intent> capture = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> addImages(CameraActivity.captured(r.getData())));

    public static Intent withPhotos(Context c) {
        return new Intent(c, CreatePostActivity.class).putExtra(EXTRA_OPEN_PICKER, true);
    }

    public static Intent withImage(Context c, Uri uri) {
        ArrayList<String> one = new ArrayList<>();
        one.add(uri.toString());
        return new Intent(c, CreatePostActivity.class).putStringArrayListExtra(EXTRA_URIS, one);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_post_screen);
        text = findViewById(R.id.post_text);
        imageStrip = findViewById(R.id.selectedImages);
        postButton = findViewById(R.id.btnPost);
        imageStrip.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        imageStrip.setAdapter(imagesAdapter);

        String me = Db.uid();
        ((AvatarView) findViewById(R.id.seagreenpfp)).bind(me);
        TextView author = findViewById(R.id.authorName);
        UserCache.get(me, u -> {
            author.setText(u.displayName());
            text.setHint(getString(R.string.whats_on_your_mind, u.firstName()));
        });

        NavHelper.click(this, R.id.backarrow, this::finish);
        NavHelper.click(this, R.id.btnPost, this::post);
        NavHelper.click(this, R.id.line1, this::openPicker);
        NavHelper.click(this, R.id.line2, this::tagPeople);
        NavHelper.click(this, R.id.line3, this::feeling);
        NavHelper.click(this, R.id.line4, this::checkIn);
        NavHelper.click(this, R.id.line5, () -> Toast.makeText(this, "Live video isn't available yet", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.line6, () -> capture.launch(CameraActivity.forCapture(this)));
        NavHelper.click(this, R.id.line7, () -> Toast.makeText(this, "Events aren't available yet", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.sheetHandle, this::toggleSheet);
        NavHelper.click(this, R.id.public_button, () -> Toast.makeText(this, "Posts are shared with your friends", Toast.LENGTH_SHORT).show());
        setupColours();

        ArrayList<String> passed = getIntent().getStringArrayListExtra(EXTRA_URIS);
        if (passed != null) {
            List<Uri> uris = new ArrayList<>();
            for (String s : passed) uris.add(Uri.parse(s));
            addImages(uris);
        } else if (getIntent().getBooleanExtra(EXTRA_OPEN_PICKER, false) && savedInstanceState == null) {
            openPicker();
        }
    }

    private void openPicker() {
        pick.launch(PictureSendActivity.pick(this, true, false));
    }

    private void addImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        images.addAll(uris);
        setBackground(null);
        imagesAdapter.notifyDataSetChanged();
        imageStrip.setVisibility(View.VISIBLE);
        if (sheetExpanded) toggleSheet();
    }

    /** The colour swatches: the first one clears the colour, the others set a coloured background. */
    private void setupColours() {
        LinearLayout row = findViewById(R.id.color_options);
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof CardView) {
                Drawable bg = ((CardView) child).getChildAt(0).getBackground();
                int color = bg instanceof ColorDrawable ? ((ColorDrawable) bg).getColor() : 0xFF0B5F63;
                child.setOnClickListener(v -> {
                    if (!images.isEmpty()) {
                        Toast.makeText(this, "Backgrounds are for text-only posts", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setBackground(String.format(Locale.ROOT, "#%06X", 0xFFFFFF & color));
                });
            } else {
                child.setOnClickListener(v -> setBackground(null));
            }
        }
    }

    private void setBackground(String hex) {
        bgColor = hex;
        if (hex == null) {
            text.setBackground(null);
            text.setTextColor(getColor(R.color.black));
            text.setPadding(0, 0, 0, 0);
        } else {
            text.setBackgroundColor(android.graphics.Color.parseColor(hex));
            text.setTextColor(getColor(R.color.white));
            int p = (int) (20 * getResources().getDisplayMetrics().density);
            text.setPadding(p, p * 3, p, p * 3);
        }
    }

    private void toggleSheet() {
        sheetExpanded = !sheetExpanded;
        View rows = findViewById(R.id.sheetRows);
        ViewGroup.LayoutParams lp = rows.getLayoutParams();
        lp.height = sheetExpanded ? ViewGroup.LayoutParams.WRAP_CONTENT
                : (int) (44 * getResources().getDisplayMetrics().density);
        rows.setLayoutParams(lp);
    }

    private void append(String suffix) {
        String current = text.getText().toString().trim();
        text.setText(current.isEmpty() ? suffix.trim() : current + " " + suffix.trim());
        text.setSelection(text.getText().length());
    }

    private void tagPeople() {
        FriendPicker.show(this, "Tag a friend", uid ->
                UserCache.get(uid, u -> append("— with " + u.displayName())));
    }

    private void feeling() {
        String[] feelings = {"happy 😊", "excited 🤩", "grateful 🙏", "loved ❤️",
                "tired 😴", "sad 😢", "motivated 💪"};
        new AlertDialog.Builder(this).setTitle("How are you feeling?")
                .setItems(feelings, (d, w) -> append("— feeling " + feelings[w]))
                .show();
    }

    private void checkIn() {
        EditText place = new EditText(this);
        place.setHint("Where are you?");
        new AlertDialog.Builder(this).setTitle("Check in").setView(place)
                .setPositiveButton("Add", (d, w) -> {
                    String p = place.getText().toString().trim();
                    if (!p.isEmpty()) append("— at " + p);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void post() {
        if (posting) return;
        String body = text.getText().toString().trim();
        if (body.isEmpty() && images.isEmpty()) {
            Toast.makeText(this, "Write something or add a photo", Toast.LENGTH_SHORT).show();
            return;
        }
        posting = true;
        postButton.setEnabled(false);
        postButton.setAlpha(0.5f);
        Toast.makeText(this, images.isEmpty() ? "Posting…" : "Uploading photos…", Toast.LENGTH_SHORT).show();
        MediaUpload.uploadAll(this, images, "posts", urls -> write(body, urls), e -> {
            posting = false;
            postButton.setEnabled(true);
            postButton.setAlpha(1f);
            Toast.makeText(this, "Upload failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void write(String body, List<String> urls) {
        DatabaseReference ref = Db.posts().push();
        Post p = new Post();
        p.id = ref.getKey();
        p.uid = Db.uid();
        p.text = body;
        p.images = urls;
        p.bgColor = urls.isEmpty() ? bgColor : null;
        p.timestamp = System.currentTimeMillis();
        ref.setValue(p)
                .addOnSuccessListener(v -> finish())
                .addOnFailureListener(e -> {
                    posting = false;
                    postButton.setEnabled(true);
                    postButton.setAlpha(1f);
                    Toast.makeText(this, "Could not post: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private class ImagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_selected_image, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            ImageView iv = h.itemView.findViewById(R.id.selectedImage);
            Glide.with(iv).load(images.get(position)).centerCrop().into(iv);
            h.itemView.findViewById(R.id.removeImage).setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos < 0) return;
                images.remove(pos);
                notifyItemRemoved(pos);
                if (images.isEmpty()) imageStrip.setVisibility(View.GONE);
            });
        }

        @Override
        public int getItemCount() {
            return images.size();
        }
    }
}
