package com.hadisaleemi.i221043;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.TimeFmt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Camera (screen 09) built on CameraX. Tap the shutter for a photo, hold it to record a video
 * (up to 15 s, SD). In story mode the result goes to the Story editor (10); in capture mode it is
 * returned to the caller (Create post, Chat).
 */
public class CameraActivity extends AppCompatActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String MODE_STORY = "story";
    private static final String MODE_CAPTURE = "capture";
    private static final String RESULT_URI = "capturedUri";
    // Short, SD clips keep stories under the 10 MB limit for media stored in the database.
    private static final long MAX_VIDEO_MS = 15_000;

    private String mode;
    private PreviewView previewView;
    private TextView recIndicator;
    private ImageView flashButton;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private long recordingStart;
    private boolean useFront;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (granted(Manifest.permission.CAMERA)) startCamera();
                else {
                    Toast.makeText(this, "Camera permission is needed to take photos", Toast.LENGTH_LONG).show();
                    if (MODE_CAPTURE.equals(mode)) finish();
                }
            });

    private final ActivityResultLauncher<Intent> editor = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) finish();
            });

    private final ActivityResultLauncher<Intent> gallery = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (!uris.isEmpty()) deliver(uris.get(0));
            });

    public static Intent forStory(Context c) {
        return new Intent(c, CameraActivity.class).putExtra(EXTRA_MODE, MODE_STORY);
    }

    public static Intent forCapture(Context c) {
        return new Intent(c, CameraActivity.class).putExtra(EXTRA_MODE, MODE_CAPTURE);
    }

    public static List<Uri> captured(Intent data) {
        List<Uri> out = new ArrayList<>();
        if (data != null && data.getStringExtra(RESULT_URI) != null) out.add(Uri.parse(data.getStringExtra(RESULT_URI)));
        return out;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_screen);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_STORY;

        previewView = findViewById(R.id.camera_preview);
        recIndicator = findViewById(R.id.recIndicator);
        flashButton = findViewById(R.id.flash_button);
        if (MODE_CAPTURE.equals(mode)) {
            findViewById(R.id.camera_modes).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.camHint)).setText(R.string.camera_hint_photo);
        }

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.flip_button, () -> {
            useFront = !useFront;
            bindCamera();
        });
        NavHelper.click(this, R.id.flash_button, this::cycleFlash);
        NavHelper.click(this, R.id.gallery_thumbnail, () ->
                gallery.launch(PictureSendActivity.pick(this, false, MODE_STORY.equals(mode))));
        NavHelper.click(this, R.id.mode_text, () -> editor.launch(StoryEditorActivity.textOnly(this)));
        NavHelper.click(this, R.id.mode_post, () -> {
            startActivity(new Intent(this, CreatePostActivity.class));
            finish();
        });

        View shutter = findViewById(R.id.shutter_button);
        shutter.setOnClickListener(v -> takePhoto());
        shutter.setOnLongClickListener(v -> {
            if (MODE_STORY.equals(mode)) startRecording();
            return MODE_STORY.equals(mode);
        });
        shutter.setOnTouchListener((v, e) -> {
            if ((e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) && recording != null) {
                stopRecording();
            }
            return false;
        });

        loadGalleryThumbnail();
        if (granted(Manifest.permission.CAMERA)) startCamera();
        else permissions.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                provider = future.get();
                bindCamera();
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (provider == null) return;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder().setFlashMode(flashMode).build();
        Recorder recorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build();
        videoCapture = VideoCapture.withOutput(recorder);
        CameraSelector selector = useFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        provider.unbindAll();
        try {
            if (MODE_STORY.equals(mode)) provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture);
            else provider.bindToLifecycle(this, selector, preview, imageCapture);
        } catch (Exception e) {
            // Some devices cannot run photo and video use cases together; keep photos working.
            videoCapture = null;
            provider.unbindAll();
            provider.bindToLifecycle(this, selector, preview, imageCapture);
        }
    }

    private void cycleFlash() {
        flashMode = flashMode == ImageCapture.FLASH_MODE_OFF ? ImageCapture.FLASH_MODE_ON
                : flashMode == ImageCapture.FLASH_MODE_ON ? ImageCapture.FLASH_MODE_AUTO : ImageCapture.FLASH_MODE_OFF;
        if (imageCapture != null) imageCapture.setFlashMode(flashMode);
        flashButton.setAlpha(flashMode == ImageCapture.FLASH_MODE_OFF ? 0.6f : 1f);
        String[] names = {"Flash auto", "Flash on", "Flash off"};
        Toast.makeText(this, names[flashMode], Toast.LENGTH_SHORT).show();
    }

    private void takePhoto() {
        if (imageCapture == null || recording != null) return;
        File file = new File(getCacheDir(), "kinnect_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                deliver(Uri.fromFile(file));
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Toast.makeText(CameraActivity.this, "Could not take photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (videoCapture == null || recording != null) return;
        File file = new File(getCacheDir(), "kinnect_" + System.currentTimeMillis() + ".mp4");
        PendingRecording pending = videoCapture.getOutput()
                .prepareRecording(this, new FileOutputOptions.Builder(file).setFileSizeLimit(MediaUpload.MAX_VIDEO_BYTES).build());
        if (granted(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled();
        recordingStart = System.currentTimeMillis();
        recording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                recording = null;
                recIndicator.setVisibility(View.GONE);
                if (!fin.hasError() || fin.getError() == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                        || fin.getError() == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED) {
                    deliver(Uri.fromFile(file));
                } else {
                    Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        recIndicator.setVisibility(View.VISIBLE);
        tickRecording();
    }

    private void tickRecording() {
        if (recording == null) return;
        long elapsed = System.currentTimeMillis() - recordingStart;
        recIndicator.setText("● REC " + TimeFmt.duration(elapsed / 1000));
        if (elapsed >= MAX_VIDEO_MS) {
            stopRecording();
            return;
        }
        handler.postDelayed(this::tickRecording, 250);
    }

    private void stopRecording() {
        if (recording != null) recording.stop();
    }

    private void deliver(Uri uri) {
        if (MODE_CAPTURE.equals(mode)) {
            setResult(RESULT_OK, new Intent().putExtra(RESULT_URI, uri.toString()));
            finish();
        } else {
            editor.launch(StoryEditorActivity.withMedia(this, uri));
        }
    }

    private void loadGalleryThumbnail() {
        boolean allowed = Build.VERSION.SDK_INT >= 33
                ? granted(Manifest.permission.READ_MEDIA_IMAGES)
                : granted(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (!allowed) return;
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                Uri latest = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(0)));
                Glide.with(this).load(latest).centerCrop().into((ImageView) findViewById(R.id.gallery_image));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (recording != null) recording.close();
    }
}
