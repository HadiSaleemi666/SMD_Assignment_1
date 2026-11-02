package com.hadisaleemi.i221043;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DatabaseReference;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.EmojiPicker;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.MediaUpload;

/**
 * Story editor (screen 10). Previews the photo or video, lets the user add a draggable text
 * label and shares it to "Your story" (all friends) or "Close friends". Stories are stored in
 * stories/{uid}/{storyId} with expiresAt = timestamp + 24h.
 */
public class StoryEditorActivity extends AppCompatActivity {

    private static final String EXTRA_URI = "uri";

    private Uri media;
    private boolean video;
    private TextView label;
    private View progress;
    private boolean sharing;

    public static Intent withMedia(Context c, Uri uri) {
        return new Intent(c, StoryEditorActivity.class).putExtra(EXTRA_URI, uri.toString());
    }

    public static Intent textOnly(Context c) {
        return new Intent(c, StoryEditorActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_editor_screen);
        label = findViewById(R.id.saturday_label);
        progress = findViewById(R.id.progress);
        ((AvatarView) findViewById(R.id.myAvatar)).bind(Db.uid());

        String raw = getIntent().getStringExtra(EXTRA_URI);
        if (raw != null) {
            media = Uri.parse(raw);
            video = MediaUpload.isVideo(this, media);
            showMedia();
        } else {
            editText(); // text-only story starts with the label editor
        }

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.tool_text, this::editText);
        NavHelper.click(this, R.id.tool_emoji, () -> {
            EditText holder = new EditText(this);
            holder.setText(label.getText());
            holder.setSelection(holder.getText().length());
            EmojiPicker.show(this, holder);
            holder.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    setLabel(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        });
        NavHelper.click(this, R.id.tool_tag, () -> toast("Tagging is coming soon"));
        NavHelper.click(this, R.id.tool_music, () -> toast("Music is coming soon"));
        NavHelper.click(this, R.id.tool_effects, () -> toast("Effects are coming soon"));
        NavHelper.click(this, R.id.pill_your_story, () -> share("public"));
        NavHelper.click(this, R.id.pill_close_friends, () -> share("close"));
        NavHelper.click(this, R.id.btn_next, () -> share("public"));
        enableDragging();
    }

    private void showMedia() {
        ImageView img = findViewById(R.id.storyImage);
        VideoView vv = findViewById(R.id.storyVideo);
        if (video) {
            img.setVisibility(View.GONE);
            vv.setVisibility(View.VISIBLE);
            vv.setVideoURI(media);
            vv.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                vv.start();
            });
        } else {
            Glide.with(this).load(media).centerCrop().into(img);
        }
    }

    private void editText() {
        EditText input = new EditText(this);
        input.setText(label.getText());
        input.setHint("Saturday plans");
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Add text")
                .setView(input)
                .setPositiveButton("Done", (d, w) -> setLabel(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setLabel(String text) {
        label.setText(text);
        label.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The label can be dragged anywhere on the story. */
    @SuppressLint("ClickableViewAccessibility")
    private void enableDragging() {
        final float[] offset = new float[2];
        label.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    offset[0] = v.getX() - e.getRawX();
                    offset[1] = v.getY() - e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    View parent = (View) v.getParent();
                    float x = Math.max(0, Math.min(parent.getWidth() - v.getWidth(), e.getRawX() + offset[0]));
                    float y = Math.max(0, Math.min(parent.getHeight() - v.getHeight(), e.getRawY() + offset[1]));
                    v.setX(x);
                    v.setY(y);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(e.getEventTime() - e.getDownTime()) < 200) editText();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void share(String audience) {
        if (sharing) return;
        String text = label.getText().toString().trim();
        if (media == null && text.isEmpty()) {
            toast("Add some text or a photo first");
            return;
        }
        sharing = true;
        progress.setVisibility(View.VISIBLE);
        if (media == null) {
            write(audience, null, text);
            return;
        }
        MediaUpload.upload(this, media, "stories", new MediaUpload.Listener() {
            @Override
            public void onSuccess(String url) {
                write(audience, url, text);
            }

            @Override
            public void onError(Exception e) {
                sharing = false;
                progress.setVisibility(View.GONE);
                toast("Upload failed: " + e.getLocalizedMessage());
            }
        });
    }

    private void write(String audience, String url, String text) {
        String me = Db.uid();
        DatabaseReference ref = Db.stories().child(me).push();
        Story s = new Story();
        s.id = ref.getKey();
        s.uid = me;
        s.mediaUrl = url;
        s.mediaType = url == null ? "text" : (video ? "video" : "image");
        s.text = text.isEmpty() ? null : text;
        View parent = (View) label.getParent();
        if (parent.getWidth() > 0 && label.getVisibility() == View.VISIBLE) {
            s.textX = (label.getX() + label.getWidth() / 2f) / parent.getWidth();
            s.textY = (label.getY() + label.getHeight() / 2f) / parent.getHeight();
        }
        s.audience = audience;
        s.timestamp = System.currentTimeMillis();
        s.expiresAt = s.timestamp + Story.LIFETIME_MS;
        ref.setValue(s)
                .addOnSuccessListener(v -> {
                    toast("close".equals(audience) ? "Shared to Close friends" : "Shared to your story");
                    startActivity(YourStoryActivity.intent(this));
                    setResult(Activity.RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    sharing = false;
                    progress.setVisibility(View.GONE);
                    toast("Could not share: " + e.getLocalizedMessage());
                });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
