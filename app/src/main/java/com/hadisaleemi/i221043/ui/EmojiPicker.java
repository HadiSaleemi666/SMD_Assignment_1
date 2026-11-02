package com.hadisaleemi.i221043.ui;

import android.app.Activity;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Small grid of emojis that inserts the tapped one at the cursor. */
public final class EmojiPicker {

    private static final String[] EMOJIS = {
            "😀", "😂", "😍", "🥰", "😎", "😮",
            "😢", "😡", "👍", "👏", "🙏", "🔥",
            "❤️", "🎉", "💯", "✨", "🌅", "📸"
    };

    private EmojiPicker() {
    }

    public static void show(Activity a, EditText target) {
        float d = a.getResources().getDisplayMetrics().density;
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        grid.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));
        AlertDialog dialog = new AlertDialog.Builder(a).setView(grid).create();
        for (String e : EMOJIS) {
            TextView t = new TextView(a);
            t.setText(e);
            t.setTextSize(26);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
            t.setOnClickListener(v -> {
                int start = Math.max(target.getSelectionStart(), 0);
                target.getText().insert(start, e);
                dialog.dismiss();
            });
            grid.addView(t);
        }
        dialog.show();
    }
}
