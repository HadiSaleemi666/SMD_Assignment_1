package com.hadisaleemi.i221043.util;

import com.hadisaleemi.i221043.R;

/** The six post reactions offered by the reaction picker (screen 05). */
public final class Reactions {

    public static final String[] KEYS = {"like", "love", "haha", "wow", "sad", "angry"};
    public static final String[] LABELS = {"Like", "Love", "Haha", "Wow", "Sad", "Angry"};
    public static final int[] ICONS = {
            R.drawable.react_like, R.drawable.react_love, R.drawable.react_haha,
            R.drawable.react_wow, R.drawable.react_sad, R.drawable.react_angry
    };
    public static final int[] COLORS = {
            0xFF0B5F63, 0xFFD13A55, 0xFFC98A12, 0xFFC98A12, 0xFFC98A12, 0xFFBC4A28
    };

    private Reactions() {
    }

    public static int index(String key) {
        if (key == null) return -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }
}
