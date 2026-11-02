package com.hadisaleemi.i221043.ui;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.Locale;

/**
 * Round profile picture used everywhere in Kinnect. Shows the user's uploaded photo, or the
 * design's coloured circle with initials (e.g. "JW") when they have not set one yet.
 */
public class AvatarView extends FrameLayout {

    private static final int[] PALETTE = {
            0xFF0B5F63, 0xFF7B4F7F, 0xFF4B4F93, 0xFF2F6E4E, 0xFFA23B52,
            0xFF8B5A2B, 0xFF3F6E8C, 0xFFC98A12, 0xFFBC4A28
    };

    private final TextView initials;
    private final ImageView photo;
    private final GradientDrawable circle = new GradientDrawable();
    private String boundUid;

    public AvatarView(Context context) {
        this(context, null);
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(PALETTE[0]);

        initials = new TextView(context);
        initials.setGravity(Gravity.CENTER);
        initials.setTextColor(0xFFFFFFFF);
        initials.setTypeface(Typeface.DEFAULT_BOLD);
        initials.setBackground(circle);
        initials.setIncludeFontPadding(false);
        addView(initials, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setVisibility(View.GONE);
        addView(photo, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);
        if (isInEditMode()) initials.setText("JW");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initials.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(w, h) * 0.36f);
    }

    /** Loads the user from the shared cache and keeps the view bound to that uid. */
    public void bind(String uid) {
        boundUid = uid;
        User cached = UserCache.peek(uid);
        if (cached != null) {
            show(cached);
            return;
        }
        initials.setText("");
        photo.setVisibility(View.GONE);
        circle.setColor(colorFor(uid));
        UserCache.get(uid, u -> {
            if (uid != null && uid.equals(boundUid)) show(u);
        });
    }

    /** Previews a photo the user just picked, before it has been uploaded. */
    public void setLocalImage(Uri uri) {
        photo.setVisibility(View.VISIBLE);
        if (Images.alive(getContext())) Glide.with(this).load(uri).circleCrop().into(photo);
    }

    public void setUser(User u) {
        boundUid = u == null ? null : u.uid;
        show(u);
    }

    private void show(User u) {
        if (u == null) {
            initials.setText("");
            photo.setVisibility(View.GONE);
            return;
        }
        initials.setText(initialsOf(u.displayName()));
        circle.setColor(colorFor(u.uid));
        initials.setBackground(circle);
        if (u.photoUrl != null && !u.photoUrl.isEmpty()) {
            photo.setVisibility(View.VISIBLE);
            Images.circle(photo, u.photoUrl);
        } else {
            photo.setVisibility(View.GONE);
            Images.clear(photo);
        }
    }

    public static String initialsOf(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(p.charAt(0));
            if (sb.length() == 2) break;
        }
        return sb.toString().toUpperCase(Locale.getDefault());
    }

    public static int colorFor(String uid) {
        if (uid == null) return PALETTE[0];
        return PALETTE[Math.abs(uid.hashCode() % PALETTE.length)];
    }
}
