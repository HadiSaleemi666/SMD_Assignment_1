package com.hadisaleemi.i221043.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.hadisaleemi.i221043.R;

/**
 * Glide wrappers that skip loads for activities that are already finishing. Media references
 * stored in the Realtime Database ("rtdb:...") are resolved to a cached file first; for videos
 * Glide shows the first frame.
 */
public final class Images {

    private static final int MODE_CROP = 0, MODE_CIRCLE = 1, MODE_FIT = 2;

    private Images() {
    }

    public static boolean alive(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                Activity a = (Activity) c;
                return !a.isFinishing() && !a.isDestroyed();
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return true;
    }

    public static void load(ImageView v, String ref) {
        show(v, ref, MODE_CROP);
    }

    public static void circle(ImageView v, String ref) {
        show(v, ref, MODE_CIRCLE);
    }

    /** Whole image without cropping (full-screen viewer). */
    public static void fit(ImageView v, String ref) {
        show(v, ref, MODE_FIT);
    }

    public static void clear(ImageView v) {
        if (!alive(v.getContext())) return;
        v.setTag(R.id.media_ref, null);
        Glide.with(v).clear(v);
    }

    private static void show(ImageView v, String ref, int mode) {
        if (!alive(v.getContext())) return;
        if (ref == null || ref.isEmpty()) {
            clear(v);
            return;
        }
        v.setTag(R.id.media_ref, ref);
        if (!MediaCache.isRef(ref)) {
            apply(Glide.with(v), v, ref, mode);
            return;
        }
        Glide.with(v).clear(v);
        MediaCache.resolve(v.getContext(), ref, file -> {
            // The view may have been recycled for another item while the file was loading.
            if (file == null || !ref.equals(v.getTag(R.id.media_ref)) || !alive(v.getContext())) return;
            apply(Glide.with(v), v, file, mode);
        });
    }

    private static void apply(RequestManager glide, ImageView v, Object model, int mode) {
        RequestBuilder<?> rb = glide.load(model);
        if (mode == MODE_CIRCLE) rb = rb.circleCrop();
        else if (mode == MODE_CROP) rb = rb.centerCrop();
        else rb = rb.fitCenter();
        rb.into(v);
    }
}
