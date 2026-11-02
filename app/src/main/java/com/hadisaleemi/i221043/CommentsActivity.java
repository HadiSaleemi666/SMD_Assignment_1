package com.hadisaleemi.i221043;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.models.Comment;
import com.hadisaleemi.i221043.models.Post;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.EmojiPicker;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.Notifs;
import com.hadisaleemi.i221043.util.PostActions;
import com.hadisaleemi.i221043.util.Reactions;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comments (screen 06): threaded comments with replies, comment likes and image comments.
 *
 * comments/{postId}/{id}             {uid, text, imageUrl, parentId, replyToUid, timestamp}
 * commentLikes/{postId}/{id}/{uid}   true
 */
public class CommentsActivity extends AppCompatActivity {

    private static final String EXTRA_POST = "postId";

    private final Subs subs = new Subs();
    private final List<Comment> all = new ArrayList<>();
    private final List<Comment> rows = new ArrayList<>();
    private final Map<String, Map<String, Boolean>> likes = new HashMap<>();
    private final CommentAdapter adapter = new CommentAdapter();

    private String postId;
    private Post post;
    private boolean newestFirst;

    private EditText input;
    private View replyBar, attachment;
    private TextView replyName;
    private ImageView attachmentImage;
    private String replyParentId, replyToUid;
    private Uri pendingImage;
    private boolean sending;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (uris.isEmpty()) return;
                pendingImage = uris.get(0);
                attachment.setVisibility(View.VISIBLE);
                Glide.with(this).load(pendingImage).centerCrop().into(attachmentImage);
            });

    public static Intent intent(Context c, String postId) {
        return new Intent(c, CommentsActivity.class).putExtra(EXTRA_POST, postId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.comments_screen);
        postId = getIntent().getStringExtra(EXTRA_POST);
        if (postId == null) {
            finish();
            return;
        }

        input = findViewById(R.id.prompt);
        replyBar = findViewById(R.id.replyBar);
        replyName = findViewById(R.id.ak);
        attachment = findViewById(R.id.attachment);
        attachmentImage = findViewById(R.id.attachmentImage);

        RecyclerView list = findViewById(R.id.commentList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        NavHelper.click(this, R.id.backarrow, this::finish);
        NavHelper.click(this, R.id.RL2, () -> startActivity(LikeActivity.intent(this, postId)));
        NavHelper.click(this, R.id.sortChip, this::toggleSort);
        NavHelper.click(this, R.id.cancel, this::clearReply);
        NavHelper.click(this, R.id.camera, () -> pickImage.launch(PictureSendActivity.pick(this, false, false)));
        NavHelper.click(this, R.id.removeAttachment, () -> {
            pendingImage = null;
            attachment.setVisibility(View.GONE);
        });
        NavHelper.click(this, R.id.emoji_button, () -> EmojiPicker.show(this, input));
        NavHelper.click(this, R.id.send, this::send);

        listen();
    }

    private void listen() {
        subs.value(Db.posts().child(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                post = s.getValue(Post.class);
                if (post != null) post.id = postId;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.comments(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                all.clear();
                for (DataSnapshot c : s.getChildren()) {
                    Comment cm = c.getValue(Comment.class);
                    if (cm == null) continue;
                    cm.id = c.getKey();
                    all.add(cm);
                }
                rebuild();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.commentLikes(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                likes.clear();
                for (DataSnapshot c : s.getChildren()) {
                    Map<String, Boolean> m = new HashMap<>();
                    for (DataSnapshot u : c.getChildren()) m.put(u.getKey(), true);
                    likes.put(c.getKey(), m);
                }
                rebuild();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.reactions(postId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                bindReactionSummary(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void bindReactionSummary(DataSnapshot s) {
        int[] counts = new int[Reactions.KEYS.length];
        for (DataSnapshot c : s.getChildren()) {
            int i = Reactions.index(c.getValue(String.class));
            if (i >= 0) counts[i]++;
        }
        LinearLayout icons = findViewById(R.id.reactionIcons);
        icons.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int shown = 0; shown < 3; shown++) {
            int best = -1;
            for (int i = 0; i < counts.length; i++) if (counts[i] > 0 && (best < 0 || counts[i] > counts[best])) best = i;
            if (best < 0) break;
            counts[best] = 0;
            ImageView iv = new ImageView(this);
            iv.setImageResource(Reactions.ICONS[best]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d));
            if (shown > 0) lp.setMarginStart((int) (-6 * d));
            icons.addView(iv, lp);
        }
        long total = s.getChildrenCount();
        ((TextView) findViewById(R.id.reaction_number)).setText(total == 0
                ? getString(R.string.no_reactions) : PostActions.compact(total));
    }

    private int likeCount(String id) {
        Map<String, Boolean> m = likes.get(id);
        return m == null ? 0 : m.size();
    }

    /** Top-level comments (by relevance or newest) each followed by their replies, oldest first. */
    private void rebuild() {
        List<Comment> top = new ArrayList<>();
        Map<String, List<Comment>> replies = new HashMap<>();
        for (Comment c : all) {
            if (c.parentId == null) top.add(c);
            else {
                List<Comment> l = replies.get(c.parentId);
                if (l == null) {
                    l = new ArrayList<>();
                    replies.put(c.parentId, l);
                }
                l.add(c);
            }
        }
        if (newestFirst) {
            top.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        } else {
            top.sort((a, b) -> {
                int byLikes = Integer.compare(likeCount(b.id), likeCount(a.id));
                return byLikes != 0 ? byLikes : Long.compare(a.timestamp, b.timestamp);
            });
        }
        rows.clear();
        for (Comment t : top) {
            rows.add(t);
            List<Comment> r = replies.get(t.id);
            if (r != null) {
                r.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
                rows.addAll(r);
            }
        }
        adapter.notifyDataSetChanged();
        findViewById(R.id.emptyComments).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleSort() {
        newestFirst = !newestFirst;
        ((TextView) findViewById(R.id.most_relevant_text)).setText(newestFirst ? R.string.newest : R.string.most_relevant);
        rebuild();
    }

    private void startReply(Comment c) {
        replyParentId = c.parentId == null ? c.id : c.parentId;
        replyToUid = c.uid;
        replyBar.setVisibility(View.VISIBLE);
        UserCache.get(c.uid, u -> replyName.setText(u.displayName()));
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void clearReply() {
        replyParentId = null;
        replyToUid = null;
        replyBar.setVisibility(View.GONE);
    }

    private void send() {
        if (sending) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImage == null) return;
        sending = true;
        if (pendingImage != null) {
            MediaUpload.upload(this, pendingImage, "comments", new MediaUpload.Listener() {
                @Override
                public void onSuccess(String url) {
                    write(text, url);
                }

                @Override
                public void onError(Exception e) {
                    sending = false;
                    Toast.makeText(CommentsActivity.this, "Upload failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            write(text, null);
        }
    }

    private void write(String text, String imageUrl) {
        DatabaseReference ref = Db.comments(postId).push();
        Comment c = new Comment();
        c.id = ref.getKey();
        c.uid = Db.uid();
        c.text = text.isEmpty() ? null : text;
        c.imageUrl = imageUrl;
        c.parentId = replyParentId;
        c.replyToUid = replyToUid;
        c.timestamp = System.currentTimeMillis();
        ref.setValue(c).addOnCompleteListener(t -> sending = false);

        String preview = text.isEmpty() ? "sent a photo" : "“" + text + "”";
        if (replyToUid != null) {
            Notifs.send(replyToUid, AppNotification.REPLY, postId, "replied to your comment: " + preview);
        }
        if (post != null && (replyToUid == null || !replyToUid.equals(post.uid))) {
            Notifs.send(post.uid, AppNotification.COMMENT, postId, "commented on your post: " + preview);
        }

        input.setText("");
        pendingImage = null;
        attachment.setVisibility(View.GONE);
        clearReply();
    }

    private void toggleLike(Comment c) {
        String me = Db.uid();
        Map<String, Boolean> m = likes.get(c.id);
        DatabaseReference ref = Db.commentLikes(postId).child(c.id).child(me);
        if (m != null && m.containsKey(me)) {
            ref.removeValue();
        } else {
            ref.setValue(true);
            Notifs.send(c.uid, AppNotification.COMMENT_LIKE, postId, "liked your comment.");
        }
    }

    private void confirmDelete(Comment c) {
        new AlertDialog.Builder(this)
                .setTitle("Delete comment?")
                .setPositiveButton("Delete", (d, w) -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put("comments/" + postId + "/" + c.id, null);
                    up.put("commentLikes/" + postId + "/" + c.id, null);
                    if (c.parentId == null) {
                        for (Comment r : all) {
                            if (c.id.equals(r.parentId)) {
                                up.put("comments/" + postId + "/" + r.id, null);
                                up.put("commentLikes/" + postId + "/" + r.id, null);
                            }
                        }
                    }
                    Db.root().updateChildren(up);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }

    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final AvatarView avatar;
            final TextView name, text, time, like, reply, likeCount;
            final View likePill, imgCard;
            final ImageView img;

            VH(View v) {
                super(v);
                avatar = v.findViewById(R.id.commentAvatar);
                name = v.findViewById(R.id.commentName);
                text = v.findViewById(R.id.commentText);
                time = v.findViewById(R.id.commentTime);
                like = v.findViewById(R.id.commentLike);
                reply = v.findViewById(R.id.commentReply);
                likeCount = v.findViewById(R.id.likeCount);
                likePill = v.findViewById(R.id.likeCountPill);
                imgCard = v.findViewById(R.id.commentImgCard);
                img = v.findViewById(R.id.commentImg);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Comment c = rows.get(position);
            String me = Db.uid();
            float d = getResources().getDisplayMetrics().density;
            boolean isReply = c.parentId != null;

            // Replies are indented under their parent with a smaller avatar.
            h.itemView.setPaddingRelative((int) ((isReply ? 58 : 10) * d), h.itemView.getPaddingTop(),
                    h.itemView.getPaddingEnd(), h.itemView.getPaddingBottom());
            ViewGroup.LayoutParams alp = h.avatar.getLayoutParams();
            alp.width = alp.height = (int) ((isReply ? 32 : 45) * d);
            h.avatar.setLayoutParams(alp);
            h.avatar.bind(c.uid);
            h.avatar.setOnClickListener(v -> PostActions.openProfile(CommentsActivity.this, c.uid));

            h.name.setText("");
            UserCache.get(c.uid, u -> {
                if (h.getBindingAdapterPosition() == position) h.name.setText(u.displayName());
            });

            boolean hasText = c.text != null && !c.text.isEmpty();
            h.text.setVisibility(hasText ? View.VISIBLE : View.GONE);
            h.text.setText(c.text);
            if (hasText && isReply && c.replyToUid != null) {
                UserCache.get(c.replyToUid, u -> {
                    if (h.getBindingAdapterPosition() == position) h.text.setText("@" + u.displayName() + " " + c.text);
                });
            }

            h.imgCard.setVisibility(c.imageUrl != null ? View.VISIBLE : View.GONE);
            if (c.imageUrl != null) {
                Images.load(h.img, c.imageUrl);
                ArrayList<String> one = new ArrayList<>();
                one.add(c.imageUrl);
                h.imgCard.setOnClickListener(v -> startActivity(MediaViewerActivity.intent(CommentsActivity.this, one, 0)));
            }

            h.time.setText(TimeFmt.ago(c.timestamp));
            int n = likeCount(c.id);
            Map<String, Boolean> m = likes.get(c.id);
            boolean liked = m != null && m.containsKey(me);
            h.like.setTextColor(getColor(liked ? R.color.SeaGreen : R.color.LightBrown));
            h.likePill.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            h.likeCount.setText(String.valueOf(n));
            h.like.setOnClickListener(v -> toggleLike(c));
            h.reply.setOnClickListener(v -> startReply(c));
            h.itemView.setOnLongClickListener(v -> {
                if (me != null && me.equals(c.uid)) confirmDelete(c);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
