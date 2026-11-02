package com.hadisaleemi.i221043.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.hadisaleemi.i221043.R;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.MediaCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a list of stories inside the story card used by screens 11 and 12: segmented progress
 * bar, 5 seconds per photo (or the video's length), tap left / right to move, hold to pause.
 */
public class StoryPlayer {

    public interface Listener {
        void onShow(Story story, int index);

        void onFinished();
    }

    private static final long PHOTO_MS = 5000;
    private static final long TICK_MS = 50;

    private final Activity activity;
    private final Listener listener;
    private final ImageView image;
    private final VideoView video;
    private final TextView label;
    private final LinearLayout progressRow;
    private final List<ProgressBar> bars = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Story> stories = new ArrayList<>();

    private int index = -1;
    private long elapsed;
    private long duration = PHOTO_MS;
    private boolean paused;
    private boolean ready;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!paused && ready) {
                Story s = current();
                if (s != null && s.isVideo() && video.isPlaying()) elapsed = video.getCurrentPosition();
                else elapsed += TICK_MS;
                if (index >= 0 && index < bars.size()) {
                    bars.get(index).setProgress((int) Math.min(1000, elapsed * 1000 / Math.max(1, duration)));
                }
                if (elapsed >= duration) {
                    next();
                    return;
                }
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    public StoryPlayer(Activity activity, Listener listener, int labelId) {
        this.activity = activity;
        this.listener = listener;
        image = activity.findViewById(R.id.storyImage);
        video = activity.findViewById(R.id.storyVideo);
        label = activity.findViewById(labelId);
        progressRow = activity.findViewById(R.id.story_progress);

        View.OnTouchListener hold = (v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) resume();
            return false;
        };
        View prev = activity.findViewById(R.id.tapPrev);
        View next = activity.findViewById(R.id.tapNext);
        prev.setOnClickListener(v -> prev());
        next.setOnClickListener(v -> next());
        prev.setOnLongClickListener(v -> {
            pause();
            return true;
        });
        next.setOnLongClickListener(v -> {
            pause();
            return true;
        });
        prev.setOnTouchListener(hold);
        next.setOnTouchListener(hold);
    }

    public void setStories(List<Story> list, int start) {
        stories.clear();
        stories.addAll(list);
        progressRow.removeAllViews();
        bars.clear();
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < stories.size(); i++) {
            ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(0x80FFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            if (i < stories.size() - 1) lp.setMarginEnd((int) (4 * d));
            progressRow.addView(pb, lp);
            bars.add(pb);
        }
        show(Math.max(0, Math.min(start, stories.size() - 1)));
    }

    public Story current() {
        return index >= 0 && index < stories.size() ? stories.get(index) : null;
    }

    public int index() {
        return index;
    }

    private void show(int i) {
        handler.removeCallbacks(tick);
        if (i < 0 || i >= stories.size()) {
            listener.onFinished();
            return;
        }
        index = i;
        for (int b = 0; b < bars.size(); b++) bars.get(b).setProgress(b < i ? 1000 : 0);
        Story s = stories.get(i);
        elapsed = 0;
        duration = PHOTO_MS;
        ready = false;

        if (s.isVideo()) {
            // Show the first frame while the clip is fetched from the database, then play it.
            video.stopPlayback();
            video.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            Images.load(image, s.mediaUrl);
            final int shown = i;
            MediaCache.resolve(activity, s.mediaUrl, file -> {
                if (index != shown) return;
                if (file == null) {
                    ready = true;
                    return;
                }
                image.setVisibility(View.GONE);
                video.setVisibility(View.VISIBLE);
                video.setVideoPath(file.getAbsolutePath());
            });
            video.setOnPreparedListener(mp -> {
                duration = Math.max(1000, mp.getDuration());
                ready = true;
                if (!paused) video.start();
            });
            video.setOnErrorListener((mp, what, extra) -> {
                ready = true;
                return true;
            });
        } else {
            video.stopPlayback();
            video.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            if (s.mediaUrl != null) Images.load(image, s.mediaUrl);
            else Images.clear(image);
            ready = true;
        }

        boolean hasText = s.text != null && !s.text.isEmpty();
        label.setVisibility(hasText ? View.VISIBLE : View.GONE);
        if (hasText) {
            label.setText(s.text);
            label.post(() -> {
                View parent = (View) label.getParent();
                label.setX(Math.max(0, s.textX * parent.getWidth() - label.getWidth() / 2f));
                label.setY(Math.max(0, s.textY * parent.getHeight() - label.getHeight() / 2f));
            });
        }
        listener.onShow(s, i);
        handler.postDelayed(tick, TICK_MS);
    }

    public void next() {
        show(index + 1);
    }

    public void prev() {
        show(Math.max(0, index - 1));
    }

    public void pause() {
        paused = true;
        if (video.isPlaying()) video.pause();
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        Story s = current();
        if (s != null && s.isVideo() && ready) video.start();
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        video.stopPlayback();
    }
}
