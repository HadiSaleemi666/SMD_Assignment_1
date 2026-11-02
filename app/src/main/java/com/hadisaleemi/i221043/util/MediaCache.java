package com.hadisaleemi.i221043.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns a media reference ("rtdb:image:{owner}:{id}") into a local file. The chunks are read
 * once from mediaChunks/{owner}/{id}, decoded and kept in the app's cache folder, so scrolling
 * the feed again costs no extra database downloads.
 */
public final class MediaCache {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Callback<File>>> PENDING = new HashMap<>();

    private MediaCache() {
    }

    public static boolean isRef(String value) {
        return value != null && value.startsWith("rtdb:");
    }

    public static boolean isVideoRef(String value) {
        return value != null && value.startsWith("rtdb:video:");
    }

    /** @return {kind, owner, id} or null. */
    static String[] parse(String ref) {
        if (!isRef(ref)) return null;
        String[] p = ref.split(":");
        return p.length == 4 ? new String[]{p[1], p[2], p[3]} : null;
    }

    private static File fileFor(Context c, String[] parts) {
        File dir = new File(c.getCacheDir(), "media");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, parts[2] + ("video".equals(parts[0]) ? ".mp4" : ".jpg"));
    }

    static void store(Context c, String ref, byte[] bytes) {
        String[] parts = parse(ref);
        if (parts == null) return;
        try (FileOutputStream out = new FileOutputStream(fileFor(c, parts))) {
            out.write(bytes);
        } catch (Exception ignored) {
        }
    }

    /** Calls back on the main thread with the file, or null if it could not be loaded. */
    public static void resolve(Context context, String ref, Callback<File> cb) {
        String[] parts = parse(ref);
        if (parts == null) {
            cb.onResult(null);
            return;
        }
        Context app = context.getApplicationContext();
        File file = fileFor(app, parts);
        if (file.exists() && file.length() > 0) {
            cb.onResult(file);
            return;
        }
        List<Callback<File>> waiting = PENDING.get(ref);
        if (waiting != null) {
            waiting.add(cb);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(cb);
        PENDING.put(ref, waiting);
        Db.root().child("mediaChunks").child(parts[1]).child(parts[2])
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        List<String> chunks = new ArrayList<>();
                        for (DataSnapshot c : s.getChildren()) chunks.add(c.getValue(String.class));
                        IO.execute(() -> {
                            File result = null;
                            try {
                                if (!chunks.isEmpty()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (String c : chunks) if (c != null) sb.append(c);
                                    byte[] bytes = Base64.decode(sb.toString(), Base64.NO_WRAP);
                                    File tmp = new File(file.getPath() + ".part");
                                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                                        out.write(bytes);
                                    }
                                    if (tmp.renameTo(file)) result = file;
                                }
                            } catch (Exception ignored) {
                            }
                            File done = result;
                            MAIN.post(() -> finish(ref, done));
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        finish(ref, null);
                    }
                });
    }

    private static void finish(String ref, File file) {
        List<Callback<File>> cbs = PENDING.remove(ref);
        if (cbs != null) for (Callback<File> c : cbs) c.onResult(file);
    }
}
