package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.hadisaleemi.i221043.util.Images;

import java.util.ArrayList;
import java.util.List;

/** Full-screen swipeable viewer for the photos of a post or chat. */
public class MediaViewerActivity extends AppCompatActivity {

    private static final String EXTRA_URLS = "urls";
    private static final String EXTRA_INDEX = "index";

    public static Intent intent(Context c, ArrayList<String> urls, int index) {
        return new Intent(c, MediaViewerActivity.class)
                .putStringArrayListExtra(EXTRA_URLS, urls)
                .putExtra(EXTRA_INDEX, index);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);
        List<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null) urls = new ArrayList<>();
        int start = getIntent().getIntExtra(EXTRA_INDEX, 0);
        TextView counter = findViewById(R.id.counter);
        final List<String> all = urls;

        RecyclerView pager = findViewById(R.id.pager);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        pager.setLayoutManager(lm);
        new PagerSnapHelper().attachToRecyclerView(pager);
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView iv = new ImageView(parent.getContext());
                iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return new RecyclerView.ViewHolder(iv) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Images.fit((ImageView) h.itemView, all.get(position));
            }

            @Override
            public int getItemCount() {
                return all.size();
            }
        });
        pager.scrollToPosition(start);
        counter.setText((start + 1) + " / " + all.size());
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                int pos = lm.findFirstCompletelyVisibleItemPosition();
                if (pos >= 0) counter.setText((pos + 1) + " / " + all.size());
            }
        });
        NavHelper.click(this, R.id.close_button, this::finish);
    }
}
