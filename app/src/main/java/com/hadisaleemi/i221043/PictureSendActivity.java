package com.hadisaleemi.i221043;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hadisaleemi.i221043.util.TimeFmt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Photo picker (screen 08). Shows the device gallery from MediaStore in a 4-column grid with
 * numbered multi-select. Returns the chosen content URIs to the caller (Create post, Story,
 * Edit profile, Comments and Chat all reuse it).
 */
public class PictureSendActivity extends AppCompatActivity {

    private static final String EXTRA_MULTI = "multi";
    private static final String EXTRA_VIDEO = "video";
    private static final String RESULT_URIS = "uris";
    private static final int FILTER_ALL = 0, FILTER_PHOTO = 1, FILTER_VIDEO = 2;

    private static class Item {
        Uri uri;
        boolean video;
        long durationMs;

        @Override
        public boolean equals(Object o) {
            return o instanceof Item && ((Item) o).uri.equals(uri);
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<Item> selected = new ArrayList<>();
    private Item previewItem;
    private boolean allowMulti, allowVideo, multiMode;
    private int filter = FILTER_ALL;

    private ImageView preview;
    private View previewVideoBadge;
    private TextView empty;
    private RecyclerView grid;
    private final GridAdapter adapter = new GridAdapter();

    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (hasPermission()) loadMedia();
                else fallbackToSystemPicker();
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> systemPickOne = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                List<Uri> out = new ArrayList<>();
                if (uri != null) out.add(uri);
                finishWith(out);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> systemPickMany = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(10), this::finishWith);

    /** @param multiple allow selecting several items (posts); @param video include videos (stories). */
    public static Intent pick(Context c, boolean multiple, boolean video) {
        return new Intent(c, PictureSendActivity.class)
                .putExtra(EXTRA_MULTI, multiple)
                .putExtra(EXTRA_VIDEO, video);
    }

    public static List<Uri> results(Intent data) {
        List<Uri> out = new ArrayList<>();
        if (data == null) return out;
        ArrayList<String> raw = data.getStringArrayListExtra(RESULT_URIS);
        if (raw != null) for (String s : raw) out.add(Uri.parse(s));
        return out;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picture_send_screen);
        allowMulti = getIntent().getBooleanExtra(EXTRA_MULTI, false);
        allowVideo = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        multiMode = allowMulti;

        preview = findViewById(R.id.previewImage);
        previewVideoBadge = findViewById(R.id.previewVideoBadge);
        empty = findViewById(R.id.empty);
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        grid.setAdapter(adapter);

        View multiBtn = findViewById(R.id.btnSelectMultiple);
        multiBtn.setVisibility(allowMulti ? View.VISIBLE : View.GONE);
        multiBtn.setOnClickListener(v -> {
            multiMode = !multiMode;
            if (!multiMode && selected.size() > 1) {
                Item keep = selected.get(selected.size() - 1);
                selected.clear();
                selected.add(keep);
            }
            adapter.notifyDataSetChanged();
        });

        if (!allowVideo) findViewById(R.id.tabVideo).setVisibility(View.GONE);
        NavHelper.click(this, R.id.cancel_text, this::finish);
        NavHelper.click(this, R.id.most_relevant_text, this::done);
        NavHelper.click(this, R.id.tabGallery, () -> setFilter(FILTER_ALL));
        NavHelper.click(this, R.id.tabPhoto, () -> setFilter(FILTER_PHOTO));
        NavHelper.click(this, R.id.tabVideo, () -> setFilter(FILTER_VIDEO));
        empty.setOnClickListener(v -> fallbackToSystemPicker());

        if (hasPermission()) loadMedia();
        else permission.launch(permissionsNeeded());
    }

    private String[] permissionsNeeded() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) return true;
        if (Build.VERSION.SDK_INT >= 33) return granted(Manifest.permission.READ_MEDIA_IMAGES);
        return granted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    /** Without gallery access, hand over to Android's own photo picker instead. */
    private void fallbackToSystemPicker() {
        ActivityResultContracts.PickVisualMedia.VisualMediaType type = allowVideo
                ? ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE
                : ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
        PickVisualMediaRequest req = new PickVisualMediaRequest.Builder().setMediaType(type).build();
        if (allowMulti) systemPickMany.launch(req);
        else systemPickOne.launch(req);
    }

    private void setFilter(int f) {
        filter = f;
        int[] tabs = {R.id.tabGallery, R.id.tabPhoto, R.id.tabVideo};
        for (int i = 0; i < tabs.length; i++) {
            TextView t = findViewById(tabs[i]);
            boolean on = i == f;
            t.setTextColor(getColor(on ? R.color.SeaGreen : R.color.black));
            t.setBackgroundResource(on ? R.drawable.one_sided_border_seagreen_upper : R.drawable.one_sided_border_black_upper);
        }
        loadMedia();
    }

    private void loadMedia() {
        int f = filter;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Item> found = query(f);
            runOnUiThread(() -> {
                items.clear();
                items.addAll(found);
                adapter.notifyDataSetChanged();
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                empty.setText(hasPermission() ? getString(R.string.picker_empty) : getString(R.string.picker_permission));
                if (previewItem == null && !items.isEmpty()) showPreview(items.get(0));
            });
        });
    }

    private List<Item> query(int f) {
        List<Item> out = new ArrayList<>();
        Uri files = MediaStore.Files.getContentUri("external");
        String[] proj = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.VideoColumns.DURATION};
        String sel;
        boolean images = f != FILTER_VIDEO;
        boolean videos = allowVideo && f != FILTER_PHOTO;
        if (images && videos) {
            sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        } else if (videos) {
            sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        } else {
            sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }
        try (Cursor c = getContentResolver().query(files, proj, sel, null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < 600) {
                long id = c.getLong(0);
                boolean video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                Item it = new Item();
                it.video = video;
                it.durationMs = video ? c.getLong(2) : 0;
                it.uri = ContentUris.withAppendedId(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void showPreview(Item it) {
        previewItem = it;
        Glide.with(this).load(it.uri).centerCrop().into(preview);
        previewVideoBadge.setVisibility(it.video ? View.VISIBLE : View.GONE);
    }

    private void onItemClicked(Item it) {
        showPreview(it);
        if (multiMode) {
            if (selected.contains(it)) selected.remove(it);
            else if (selected.size() < 10) selected.add(it);
        } else {
            selected.clear();
            selected.add(it);
        }
        adapter.notifyDataSetChanged();
    }

    private void done() {
        List<Uri> out = new ArrayList<>();
        for (Item it : selected) out.add(it.uri);
        if (out.isEmpty() && previewItem != null) out.add(previewItem.uri);
        finishWith(out);
    }

    private void finishWith(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        ArrayList<String> raw = new ArrayList<>();
        for (Uri u : uris) raw.add(u.toString());
        Intent data = new Intent().putStringArrayListExtra(RESULT_URIS, raw);
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, data);
        finish();
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View frame, circle;
            final TextView number, duration;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                frame = v.findViewById(R.id.selectedFrame);
                circle = v.findViewById(R.id.circle);
                number = v.findViewById(R.id.number);
                duration = v.findViewById(R.id.duration);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker, parent, false);
            int side = parent.getWidth() / 4;
            v.getLayoutParams().height = side > 0 ? side : (int) (90 * getResources().getDisplayMetrics().density);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Item it = items.get(position);
            Glide.with(h.thumb).load(it.uri).centerCrop().into(h.thumb);
            int idx = selected.indexOf(it);
            h.frame.setVisibility(idx >= 0 ? View.VISIBLE : View.GONE);
            h.number.setVisibility(idx >= 0 ? View.VISIBLE : View.GONE);
            h.number.setText(multiMode ? String.valueOf(idx + 1) : "✓");
            h.circle.setVisibility(idx >= 0 ? View.GONE : View.VISIBLE);
            h.duration.setVisibility(it.video ? View.VISIBLE : View.GONE);
            if (it.video) h.duration.setText(TimeFmt.duration(it.durationMs / 1000));
            h.itemView.setOnClickListener(v -> onItemClicked(it));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
