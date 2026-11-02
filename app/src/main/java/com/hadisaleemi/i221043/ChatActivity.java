package com.hadisaleemi.i221043;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.hadisaleemi.i221043.models.AppNotification;
import com.hadisaleemi.i221043.models.Message;
import com.hadisaleemi.i221043.models.User;
import com.hadisaleemi.i221043.services.PushNotifier;
import com.hadisaleemi.i221043.ui.AvatarView;
import com.hadisaleemi.i221043.ui.EmojiPicker;
import com.hadisaleemi.i221043.util.Chats;
import com.hadisaleemi.i221043.util.Db;
import com.hadisaleemi.i221043.util.Images;
import com.hadisaleemi.i221043.util.MediaUpload;
import com.hadisaleemi.i221043.util.Notifs;
import com.hadisaleemi.i221043.util.ScreenshotDetector;
import com.hadisaleemi.i221043.util.Subs;
import com.hadisaleemi.i221043.util.TimeFmt;
import com.hadisaleemi.i221043.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/**
 * One conversation (screen 21).
 *
 * - text, photo and shared-post messages, replies and a heart reaction per message
 * - edit / delete own messages for 5 minutes after sending (then the options disappear)
 * - "Active now" / last seen, typing indicator and "Seen"
 * - voice / video call buttons (Agora) and screenshot alerts
 */
public class ChatActivity extends AppCompatActivity {

    private static final String EXTRA_UID = "uid";
    private static final long DIVIDER_GAP_MS = 30 * 60 * 1000;
    private static final int TYPE_INTRO = 0, TYPE_MSG = 1;

    /** The chat currently on screen, so FCM does not show a banner for it. */
    public static volatile String openChatUid;

    private final Subs subs = new Subs();
    private final List<Message> messages = new ArrayList<>();
    private final MessageAdapter adapter = new MessageAdapter();
    private String other;
    private String chatId;
    private User otherUser;
    private boolean otherHasRead;

    private RecyclerView list;
    private LinearLayoutManager layoutManager;
    private EditText input;
    private ImageView sendButton;
    private View replyBar;
    private Message replyingTo;
    private DatabaseReference myTyping;
    private ScreenshotDetector screenshots;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> sendImages(PictureSendActivity.results(r.getData())));
    private final ActivityResultLauncher<Intent> takePhoto = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> sendImages(CameraActivity.captured(r.getData())));
    private final ActivityResultLauncher<String> mediaPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    public static Intent intent(Context c, String uid) {
        return new Intent(c, ChatActivity.class).putExtra(EXTRA_UID, uid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        other = getIntent().getStringExtra(EXTRA_UID);
        String me = Db.uid();
        if (other == null || me == null) {
            finish();
            return;
        }
        chatId = Db.chatId(me, other);
        myTyping = Db.root().child("typing").child(chatId).child(me);
        myTyping.onDisconnect().removeValue();

        list = findViewById(R.id.msgList);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        input = findViewById(R.id.etMsg);
        sendButton = findViewById(R.id.btnSend);
        replyBar = findViewById(R.id.replyBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnInfo).setOnClickListener(v -> startActivity(ProfileActivity.intent(this, other)));
        findViewById(R.id.hdrAvatarBox).setOnClickListener(v -> startActivity(ProfileActivity.intent(this, other)));
        findViewById(R.id.btnCall).setOnClickListener(v -> startActivity(CallActivity.outgoing(this, other, false)));
        findViewById(R.id.btnVideo).setOnClickListener(v -> startActivity(CallActivity.outgoing(this, other, true)));
        findViewById(R.id.btnCamera).setOnClickListener(v -> takePhoto.launch(CameraActivity.forCapture(this)));
        findViewById(R.id.btnGallery).setOnClickListener(v -> pickImage.launch(PictureSendActivity.pick(this, true, false)));
        findViewById(R.id.btnPlus).setOnClickListener(v -> pickImage.launch(PictureSendActivity.pick(this, true, false)));
        findViewById(R.id.btnMic).setOnClickListener(v -> Toast.makeText(this, "Voice notes are coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnEmoji).setOnClickListener(v -> EmojiPicker.show(this, input));
        findViewById(R.id.replyCancel).setOnClickListener(v -> setReply(null));
        sendButton.setOnClickListener(v -> sendText());

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hasText = s.toString().trim().length() > 0;
                sendButton.setImageResource(hasText ? R.drawable.ic_send : R.drawable.ol_thumb);
                if (hasText) myTyping.setValue(true);
                else myTyping.removeValue();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        screenshots = new ScreenshotDetector(this, this::onScreenshot);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) askForScreenshotAccess();
        listen(me);
    }

    private void listen(String me) {
        ((AvatarView) findViewById(R.id.hdrAvatar)).bind(other);
        ((AvatarView) findViewById(R.id.smallAvatar2)).bind(other);
        subs.value(Db.user(other), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                User u = s.getValue(User.class);
                if (u == null) return;
                u.uid = other;
                otherUser = u;
                UserCache.put(u);
                ((AvatarView) findViewById(R.id.hdrAvatar)).setUser(u);
                ((TextView) findViewById(R.id.hdrName)).setText(u.displayName());
                ((TextView) findViewById(R.id.hdrStatus)).setText(TimeFmt.lastSeen(u.online, u.lastSeen));
                findViewById(R.id.hdrDot).setVisibility(u.online ? View.VISIBLE : View.GONE);
                adapter.notifyItemChanged(0);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.messages(chatId).orderByChild("timestamp").limitToLast(300), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                boolean atBottom = !list.canScrollVertically(1);
                int before = messages.size();
                messages.clear();
                for (DataSnapshot c : s.getChildren()) {
                    Message m = c.getValue(Message.class);
                    if (m == null) continue;
                    m.id = c.getKey();
                    messages.add(m);
                }
                adapter.notifyDataSetChanged();
                if (atBottom || messages.size() > before) list.scrollToPosition(adapter.getItemCount() - 1);
                if (openChatUid != null) Chats.markRead(other);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        // "Seen": the other person has no unread messages from me.
        subs.value(Db.userChats(other).child(me).child("unread"), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Integer unread = s.getValue(Integer.class);
                otherHasRead = unread == null || unread == 0;
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        subs.value(Db.root().child("typing").child(chatId).child(other), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                boolean typing = Boolean.TRUE.equals(s.getValue(Boolean.class));
                findViewById(R.id.typingRow).setVisibility(typing ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        openChatUid = other;
        Chats.markRead(other);
        PushNotifier.cancel(this, other.hashCode());
        screenshots.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        openChatUid = null;
        myTyping.removeValue();
        screenshots.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }

    // ---- sending ---------------------------------------------------------------------------

    private void sendText() {
        String text = input.getText().toString().trim();
        Message m = Chats.text(text.isEmpty() ? "👍" : text);
        attachReply(m);
        Chats.send(other, m);
        input.setText("");
        setReply(null);
    }

    private void sendImages(List<Uri> uris) {
        if (uris.isEmpty()) return;
        View uploading = findViewById(R.id.uploading);
        uploading.setVisibility(View.VISIBLE);
        MediaUpload.uploadAll(this, uris, "chats", urls -> {
            uploading.setVisibility(View.GONE);
            for (String url : urls) {
                Message m = new Message();
                m.type = Message.IMAGE;
                m.imageUrl = url;
                attachReply(m);
                Chats.send(other, m);
                setReply(null);
            }
        }, e -> {
            uploading.setVisibility(View.GONE);
            Toast.makeText(this, "Could not send photo: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void attachReply(Message m) {
        if (replyingTo == null) return;
        m.replyToId = replyingTo.id;
        m.replyToSender = replyingTo.senderId;
        m.replyToText = Message.IMAGE.equals(replyingTo.type) ? "Photo" : Chats.preview(replyingTo);
    }

    private void setReply(Message m) {
        replyingTo = m;
        replyBar.setVisibility(m == null ? View.GONE : View.VISIBLE);
        if (m == null) return;
        boolean mine = Db.uid().equals(m.senderId);
        ((TextView) findViewById(R.id.replyTitle)).setText(mine ? getString(R.string.replying_to_yourself)
                : getString(R.string.replying_to_name, otherUser == null ? "" : otherUser.firstName()));
        ((TextView) findViewById(R.id.replyText)).setText(Message.IMAGE.equals(m.type) ? "Photo" : Chats.preview(m));
        input.requestFocus();
    }

    // ---- message actions -------------------------------------------------------------------

    /** Long-press menu. Edit and Delete only exist for my own messages younger than 5 minutes. */
    private void showActions(Message m) {
        if (Message.SYSTEM.equals(m.type)) return;
        String me = Db.uid();
        boolean hearted = m.hearts != null && Boolean.TRUE.equals(m.hearts.get(me));
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Reply");
        actions.add(() -> setReply(m));
        labels.add(hearted ? "Remove ❤️" : "React ❤️");
        actions.add(() -> Chats.toggleHeart(other, m));
        if (m.text != null && !m.text.isEmpty()) {
            labels.add("Copy");
            actions.add(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("message", m.text));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            });
        }
        if (m.canModify(me, System.currentTimeMillis())) {
            if (Message.TEXT.equals(m.type)) {
                labels.add("Edit");
                actions.add(() -> editMessage(m));
            }
            labels.add("Delete");
            actions.add(() -> new AlertDialog.Builder(this)
                    .setTitle("Delete message?")
                    .setPositiveButton("Delete", (d, w) -> {
                        if (m.canModify(Db.uid(), System.currentTimeMillis())) Chats.delete(other, m);
                        else Toast.makeText(this, "Messages can only be deleted within 5 minutes", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void editMessage(Message m) {
        EditText field = new EditText(this);
        field.setText(m.text);
        field.setSelection(field.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Edit message")
                .setView(field)
                .setPositiveButton("Save", (d, w) -> {
                    String text = field.getText().toString().trim();
                    if (text.isEmpty() || text.equals(m.text)) return;
                    if (m.canModify(Db.uid(), System.currentTimeMillis())) Chats.edit(other, m, text);
                    else Toast.makeText(this, "Messages can only be edited within 5 minutes", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- screenshots -----------------------------------------------------------------------

    private void askForScreenshotAccess() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) mediaPermission.launch(p);
    }

    /** Adds "Name took a screenshot" to the chat and notifies the other person. */
    private void onScreenshot() {
        UserCache.get(Db.uid(), me -> {
            Message m = new Message();
            m.type = Message.SYSTEM;
            m.event = "screenshot";
            m.text = me.firstName() + " took a screenshot";
            Chats.send(other, m);
            Notifs.send(other, AppNotification.SCREENSHOT, null, "took a screenshot of your chat.");
        });
    }

    // ---- list ------------------------------------------------------------------------------

    private class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_INTRO : TYPE_MSG;
        }

        @Override
        public int getItemCount() {
            return messages.size() + 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            View v = inf.inflate(viewType == TYPE_INTRO ? R.layout.item_chat_intro : R.layout.item_message, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            if (position == 0) bindIntro(h.itemView);
            else bindMessage(h.itemView, position - 1);
        }

        private void bindIntro(View v) {
            ((AvatarView) v.findViewById(R.id.introAvatar)).bind(other);
            TextView name = v.findViewById(R.id.introName);
            TextView details = v.findViewById(R.id.introDetails);
            if (otherUser == null) return;
            name.setText(otherUser.displayName());
            String where = otherUser.location == null || otherUser.location.isEmpty() ? "" : " · Lives in " + otherUser.location;
            details.setText("Kinnect" + where);
            Db.friends(Db.uid()).child(other).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot s) {
                    if (s.exists()) details.setText("You're friends on Kinnect" + where);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }

        private void bindMessage(View v, int i) {
            Message m = messages.get(i);
            String me = Db.uid();
            boolean mine = me.equals(m.senderId);
            Message prev = i > 0 ? messages.get(i - 1) : null;
            Message next = i + 1 < messages.size() ? messages.get(i + 1) : null;

            TextView divider = v.findViewById(R.id.divider);
            boolean showDivider = prev == null || m.timestamp - prev.timestamp > DIVIDER_GAP_MS;
            divider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
            divider.setText(TimeFmt.chatDivider(m.timestamp));

            TextView system = v.findViewById(R.id.systemText);
            View row = v.findViewById(R.id.msgRow);
            if (Message.SYSTEM.equals(m.type) || Message.CALL.equals(m.type)) {
                system.setVisibility(View.VISIBLE);
                system.setText(m.text);
                row.setVisibility(View.GONE);
                v.setOnLongClickListener(null);
                return;
            }
            system.setVisibility(View.GONE);
            row.setVisibility(View.VISIBLE);

            LinearLayout rowLayout = (LinearLayout) row;
            rowLayout.setGravity((mine ? Gravity.END : Gravity.START) | Gravity.BOTTOM);
            LinearLayout column = v.findViewById(R.id.msgColumn);
            column.setGravity(mine ? Gravity.END : Gravity.START);

            // Avatar only on the last message of an incoming group.
            View slot = v.findViewById(R.id.avatarSlot);
            slot.setVisibility(mine ? View.GONE : View.VISIBLE);
            AvatarView av = v.findViewById(R.id.msgAvatar);
            boolean lastOfGroup = next == null || !m.senderId.equals(next.senderId) || Message.SYSTEM.equals(next.type);
            av.setVisibility(!mine && lastOfGroup ? View.VISIBLE : View.INVISIBLE);
            if (!mine) av.bind(m.senderId);

            bindReply(v, m, mine);
            bindBody(v, m, mine);

            ImageView heart = v.findViewById(R.id.heart);
            boolean hearted = m.hearts != null && !m.hearts.isEmpty();
            heart.setVisibility(hearted ? View.VISIBLE : View.GONE);
            FrameLayoutParamsHelper.gravity(heart, mine ? Gravity.BOTTOM | Gravity.START : Gravity.BOTTOM | Gravity.END);

            TextView meta = v.findViewById(R.id.meta);
            boolean seen = mine && next == null && otherHasRead;
            StringBuilder sb = new StringBuilder();
            if (m.edited) sb.append(getString(R.string.edited));
            if (seen) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(getString(R.string.seen));
            }
            meta.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
            meta.setText(sb);

            GestureDetector taps = new GestureDetector(ChatActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    Chats.toggleHeart(other, m);
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    openContent(m);
                    return true;
                }

                @Override
                public void onLongPress(@NonNull MotionEvent e) {
                    showActions(m);
                }
            });
            View bubble = v.findViewById(R.id.bubble);
            bubble.setOnTouchListener((view, e) -> taps.onTouchEvent(e));
        }

        private void bindReply(View v, Message m, boolean mine) {
            TextView label = v.findViewById(R.id.replyLabel);
            TextView quote = v.findViewById(R.id.replyQuote);
            boolean storyReply = Message.STORY_REPLY.equals(m.type);
            boolean reply = m.replyToId != null;
            label.setVisibility(reply || storyReply ? View.VISIBLE : View.GONE);
            quote.setVisibility(reply ? View.VISIBLE : View.GONE);
            if (storyReply) {
                label.setText(mine ? getString(R.string.you_replied_story) : getString(R.string.replied_to_your_story));
            } else if (reply) {
                String me = Db.uid();
                String them = otherUser == null ? "" : otherUser.firstName();
                boolean toMe = me.equals(m.replyToSender);
                String text;
                if (mine) text = toMe ? getString(R.string.you_replied_yourself) : getString(R.string.you_replied_to, them);
                else text = toMe ? getString(R.string.replied_to_you, them) : getString(R.string.replied_to_themselves, them);
                label.setText(text);
                quote.setText(m.replyToText);
            }
        }

        private void bindBody(View v, Message m, boolean mine) {
            ImageView image = v.findViewById(R.id.msgImage);
            TextView caption = v.findViewById(R.id.msgCaption);
            TextView text = v.findViewById(R.id.msgText);
            boolean hasImage = m.imageUrl != null && !m.imageUrl.isEmpty();
            image.setVisibility(hasImage ? View.VISIBLE : View.GONE);
            if (hasImage) Images.load(image, m.imageUrl);

            boolean isPost = Message.POST.equals(m.type);
            caption.setVisibility(isPost ? View.VISIBLE : View.GONE);
            if (isPost) caption.setText(R.string.shared_a_post);

            String body = m.text;
            if (isPost && (body == null || body.isEmpty())) body = getString(R.string.view_post);
            boolean hasText = body != null && !body.isEmpty();
            text.setVisibility(hasText ? View.VISIBLE : View.GONE);
            text.setText(body);
            text.setBackgroundResource(mine ? R.drawable.bg_bubble_out : R.drawable.bg_bubble_in);
            text.setTextColor(mine ? 0xFFFFFFFF : getColor(R.color.ink));
        }
    }

    private void openContent(Message m) {
        if (Message.POST.equals(m.type) && m.postId != null) {
            startActivity(CommentsActivity.intent(this, m.postId));
        } else if (m.imageUrl != null) {
            ArrayList<String> one = new ArrayList<>();
            one.add(m.imageUrl);
            startActivity(MediaViewerActivity.intent(this, one, 0));
        }
    }

    /** Small helper to change a child's FrameLayout gravity. */
    private static final class FrameLayoutParamsHelper {
        static void gravity(View v, int gravity) {
            if (v.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) v.getLayoutParams();
                lp.gravity = gravity;
                v.setLayoutParams(lp);
            }
        }
    }
}
