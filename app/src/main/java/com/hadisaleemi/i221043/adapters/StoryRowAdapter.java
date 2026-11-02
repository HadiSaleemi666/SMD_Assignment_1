package com.hadisaleemi.i221043.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hadisaleemi.i221043.CameraActivity;
import com.hadisaleemi.i221043.R;
import com.hadisaleemi.i221043.StoryViewerActivity;
import com.hadisaleemi.i221043.YourStoryActivity;
import com.hadisaleemi.i221043.models.Story;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/** Horizontal stories row at the top of Home: "Create story" first, then one card per person. */
public class StoryRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** All active stories of one user, newest last. */
    public static class Group {
        public String uid;
        public List<Story> stories = new ArrayList<>();

        public Story latest() {
            return stories.get(stories.size() - 1);
        }
    }

    private final Activity activity;
    private final List<Group> groups = new ArrayList<>();

    public StoryRowAdapter(Activity activity) {
        this.activity = activity;
    }

    public void setGroups(List<Group> g) {
        groups.clear();
        groups.addAll(g);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1;
    }

    @Override
    public int getItemCount() {
        return groups.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        View v = inf.inflate(viewType == 0 ? R.layout.item_story_create : R.layout.item_story, parent, false);
        return new RecyclerView.ViewHolder(v) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        View v = h.itemView;
        if (position == 0) {
            ((AvatarView) v.findViewById(R.id.myAvatar)).bind(Db.uid());
            v.setOnClickListener(x -> activity.startActivity(CameraActivity.forStory(activity)));
            return;
        }
        Group g = groups.get(position - 1);
        Story latest = g.latest();
        ImageView img = v.findViewById(R.id.storyImage);
        Images.load(img, latest.mediaUrl);
        ((AvatarView) v.findViewById(R.id.storyAvatar)).bind(g.uid);
        TextView name = v.findViewById(R.id.storyName);
        boolean mine = g.uid.equals(Db.uid());
        if (mine) {
            name.setText(R.string.your_story);
        } else {
            name.setText("");
            UserCache.get(g.uid, u -> {
                if (h.getBindingAdapterPosition() == position) name.setText(u.displayName());
            });
        }
        v.setOnClickListener(x -> activity.startActivity(mine
                ? YourStoryActivity.intent(activity)
                : StoryViewerActivity.intent(activity, g.uid)));
    }
}
