# Kinnect – Assignment 2 (Firebase backend) – Implementation guide

This document explains how every requirement of *SMD Assignment 2 – Kinnect Backend with
Firebase* was implemented, where the code lives, and how to set the project up.

---

## 1. Setup (do this once) — everything stays on the free **Spark** plan

| Step | What to do |
|---|---|
| 1. Firebase project | Create a project at console.firebase.google.com (Spark plan, no card needed). Add an **Android app** with package `com.hadisaleemi.i221043`, download **`google-services.json`** and put it in `app/`. The Gradle plugin is applied automatically once that file exists (`app/build.gradle.kts`). |
| 2. Authentication | *Build → Authentication → Sign-in method →* enable **Email/Password**. |
| 3. Realtime Database | Create the database, then paste `database.rules.json` into *Rules* and publish (or `firebase deploy --only database`). |
| 4. Push key | Google Cloud console (same project) → *IAM & Admin → Service accounts → Create*. Give it **only** the role *Firebase Cloud Messaging API Admin*, then *Keys → Add key → JSON*. Save the file as **`app/src/main/assets/fcm-service-account.json`** (git-ignored). Without it the app works but sends no pushes. |
| 5. Agora | Create a project at console.agora.io with authentication **"Testing mode: APP ID"** (no certificate — issuing tokens would need a server). Put the App ID in `local.properties`: `agora.appId=YOUR_APP_ID`. |
| 6. Run | Build in Android Studio on two devices and sign up two accounts. |

`local.properties` and the service-account key are git-ignored. Do not share the key; each team
member creates their own copy from the console.

---

## 2. Architecture

```
app/src/main/java/com/hadisaleemi/i221043/
├── *Activity.java          one class per screen (23 design screens + MediaViewerActivity)
├── KinnectApp.java         Application: foreground tracking → presence + incoming-call listener
├── models/                 User, Post, Comment, Story, Message, ChatSummary, AppNotification, CallInfo
├── adapters/               PostAdapter (feed), StoryRowAdapter, PersonAdapter
├── ui/                     AvatarView, StoryPlayer, FriendPicker, EmojiPicker
├── services/               KinnectMessagingService (receives FCM), PushSender (sends FCM),
│                           PushNotifier, CallWatcher
└── util/                   Db (all database paths), AuthFlow, Presence, Friends, Chats,
                            MediaUpload + MediaCache (media in the database), PostActions,
                            Notifs, Badges, Mutuals, ProfileData, ScreenshotDetector,
                            UserCache, TimeFmt, Session, Subs
database.rules.json         Realtime Database security rules + indexes
```

* **All Realtime Database paths** are defined once in `util/Db.java` (the class comment lists the
  full schema).
* **Live data everywhere**: screens attach `ValueEventListener`s, collected in a `Subs` object
  and removed in `onDestroy`, so every list updates in real time without refreshing.
* **Media in the Realtime Database** (Spark has no Cloud Storage). `MediaUpload` resizes photos
  to ≤1280 px JPEG (videos: ≤10 MB, the camera records 15 s SD clips), Base64-encodes the bytes
  and writes them in ≤512 KB chunks to `mediaChunks/{ownerUid}/{mediaId}/{0..n}`. Posts, stories,
  chats and profiles store only a short reference `rtdb:image:{owner}:{id}`. `MediaCache` reads
  the chunks once, decodes them into the app's cache folder, and `Images` hands the file to
  **Glide** (videos show their first frame; the story player plays the cached file). Deleting a
  post/story or replacing an avatar also deletes its chunks, to stay inside the free 1 GB.
* **`AvatarView`** replaces the design's coloured-circle-with-initials avatars: it shows the
  user's photo, or their initials on a colour derived from their uid, exactly like the design.
* **`UserCache`** keeps one live listener per user so list rows can show names/avatars cheaply.

### Database schema (Realtime Database)

```
users/{uid}                         name, username, bio, photoUrl, coverUrl, online, lastSeen, fcmToken, ...
usernames/{username}                uid                       (guarantees unique usernames)
friends/{uid}/{friendUid}           timestamp
friendRequests/{toUid}/{fromUid}    timestamp                 (incoming)
sentRequests/{fromUid}/{toUid}      timestamp                 (outgoing → "Requested")
closeFriends/{uid}/{friendUid}      true   +  closeFriendOf/{friendUid}/{uid}
posts/{postId}                      uid, text, images[], bgColor, timestamp, shareCount
reactions/{postId}/{uid}            like|love|haha|wow|sad|angry   (one per user per post)
comments/{postId}/{commentId}       uid, text, imageUrl, parentId, replyToUid, timestamp
commentLikes/{postId}/{commentId}/{uid}  true
stories/{uid}/{storyId}             mediaUrl, mediaType, text, textX/Y, audience, timestamp, expiresAt
storyViews/{owner}/{storyId}/{uid}  timestamp
messages/{chatId}/{msgId}           senderId, type, text, imageUrl, postId, edited, replyTo*, hearts/{uid}, event
userChats/{uid}/{otherUid}          lastMessage, lastSenderId, timestamp, unread
typing/{chatId}/{uid}               true while typing
notifications/{uid}/{id}            type, fromUid, postId, text, timestamp, read
calls/{calleeUid}                   current ringing call (signalling)
callLogs/{uid}/{callId}             call history (status, duration)
mediaChunks/{owner}/{mediaId}/{n}   Base64 pieces of a photo or video
```
`chatId` = the two uids sorted and joined with `_`, so both users compute the same id.

---

## 3. Requirement by requirement

### Splash Screen (5) — `MainActivity`
* A `Handler` posts the navigation after **exactly 5 s** (`SPLASH_MS = 5000`); the old
  tap-to-skip was removed.
* If `FirebaseAuth.getCurrentUser() != null` it calls `AuthFlow.afterSignIn()` which opens
  **Home** (or profile setup if that was never finished); otherwise it opens **Log in**.

### User Authentication (5)
* **Sign up (03)** – `CreateAccountActivity`: the static mock-up texts became `EditText`s
  (first/last name, email, password), the birthday cards open a `DatePickerDialog`, gender cards
  are selectable. `createUserWithEmailAndPassword` creates the account and the profile is written
  to `users/{uid}` with `profileComplete = false`.
* **Password strength** – the static PNG bar was replaced by three segments + a label that are
  recomputed in a `TextWatcher` on **every keystroke** (`strength()` scores length, upper/lower
  case, digits and symbols → Weak / Medium / Strong in rust / amber / teal).
* **First-time users → profile setup (16)** – `AuthFlow.afterSignIn` reads the profile; if
  `profileComplete` is false it opens `EditProfileActivity.setup()`. In setup mode Cancel/Back
  are disabled and a username and bio are required. The username is claimed with a
  **transaction on `usernames/{username}`** so two users can never get the same one; then
  `profileComplete = true` and the user goes to Home.
* **Log in (02)** – `signInWithEmailAndPassword`; "Forget Password?" sends a reset email.
* **Recent login card** – after every sign-in `Session.saveRecentLogin` stores the account
  (name, email, photo) in SharedPreferences; the card shows it and **tapping it pre-fills the
  email**. Hidden when no account has logged in on the device.
* **Log out (Menu 19)** – `AuthFlow.logout`: writes `online=false`, `lastSeen`, removes the FCM
  token, waits for that write (max 2 s), signs out, then starts Log in with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` and `finishAffinity()` → **back stack cleared**.

### Stories (10)
* **Camera (09)** – `CameraActivity` uses **CameraX** (`PreviewView`, `ImageCapture`,
  `VideoCapture`): tap = photo, hold = video (≤15 s SD, red REC timer), flash, flip camera.
  The gallery thumbnail opens the Photo picker (images **and videos**). "Text" mode makes a
  text-only story.
* **Story editor (10)** – `StoryEditorActivity` previews the photo/`VideoView`, the **Aa** tool
  adds a text label that can be **dragged** anywhere (its position is saved as fractions so it
  appears in the same place on every screen size). **Your story** or **Close friends** stores
  the media in the database (see Architecture) and writes `stories/{uid}/{id}` with `audience` and
  `expiresAt = timestamp + 24 h`.
* **Disappear after 24 h** – every reader filters `expiresAt > now`, so a story vanishes the
  moment it expires for everyone. The owner's app also deletes their expired stories, views and
  media chunks whenever Home or Your story loads (only the owner may delete them).
* **Home row** – `StartSocialMediaActivity` listens to `stories/` and `friends/{me}`;
  `StoryRowAdapter` shows **"Create story" first**, then your story, then friends ordered by
  newest story. Close-friends stories are shown only to people in the owner's list
  (`closeFriendOf/{me}`), which owners manage from a friend's profile → ⋯ → Add to Close friends.
* **Viewer (11) / Your story (12)** – `ui/StoryPlayer` draws the segmented progress bar, 5 s per
  photo or the video length, tap left/right, hold to pause. Viewing records
  `storyViews/{owner}/{story}/{me}`; Your story shows a live **"Seen by N"** with avatars.
  Replies and quick reactions are delivered to the owner's chat.

### Posts, Reactions & Comments (10)
* **Create post (07) + Photo picker (08)** – `PictureSendActivity` is a real gallery picker
  (MediaStore query, 4-column grid, numbered **multi-select**, Photo/Video tabs; it falls back to
  the system picker if permission is denied). `CreatePostActivity` shows the selected photos,
  supports colour backgrounds for text posts, tag/feeling/check-in, uploads every photo and writes
  `posts/{id}` with the `images` list.
* **Multi-image grid with "+N"** – `item_post.xml` + `PostAdapter.bindBody`: 1 photo = full width,
  2 = side by side, 3+ = big photo on the left and two stacked on the right; when there are more
  than 3, the third tile gets the dark overlay with **`+N`** (N = count − 3) exactly like screen
  04. Tapping opens a swipeable full-screen viewer.
* **Six reactions (05)** – the six reaction icons were cut out of the design's reaction strip.
  `LikeActivity` is screen 05: the dimmed post with the reaction bar; picking one writes
  `reactions/{postId}/{myUid}` → **one reaction per user per post**. Picking another reaction
  replaces it; picking the same one or "Remove reaction" deletes it. Tap Like = like/unlike,
  long-press Like = picker. The rule `.validate` only accepts the six keys.
* **Real-time summary** – `PostAdapter` keeps a live listener on `reactions/{postId}` and
  `comments/{postId}` for every visible post, so the **top-3 reaction icons**, "You and N others",
  comment and share counts change instantly on every device.
* **Comments (06)** – `CommentsActivity`: threaded list (top-level comments, each followed by its
  replies, indented). **Reply** shows the "Replying to *name* · Cancel" bar and saves `parentId`
  and `replyToUid`; **comment likes** in `commentLikes/…` with the counter bubble; **image
  comments** via the camera icon. Long-press your own comment to delete.
  "Most relevant" toggles to "Newest".
* **Feed** – Home listens to the latest 300 posts and shows only **mine + my friends'**, sorted
  **newest first**.
* Notifications are created for reactions, comments, replies and comment likes.

### Messaging System (10)
* **Chats (20)** – `ChatsActivity` lists `userChats/{me}` ordered by time: **last message**
  ("You: …" for your own), **relative time**, a **teal unread dot** + bold text when
  `unread > 0`. The active row shows friends who are online right now.
* **Send** – `Chats.send` writes the message and **both users' chat-list rows in one multi-path
  update**, then increments the receiver's `unread` with `ServerValue.increment(1)`; opening the
  chat resets it to 0.
* **Media** – text, **photos** (gallery multi-select or camera) and **posts** (Share button on any
  post → pick a friend → a `post` message that opens the post when tapped). Story replies arrive
  as messages too.
* **Edit / delete within 5 minutes** – `Message.canModify()` returns true only for my own
  message while `now − timestamp < 5 min`. The long-press menu adds **Edit** and **Delete** only
  then, and checks again when you confirm. The database rule enforces the same window on the
  server: `now - data.child('timestamp').val() < 300000`. Edited messages get `edited=true` and
  show **"Edited"** under the bubble; deleting removes the message and refreshes the chat
  previews.
* **Replies & heart** – long-press → Reply shows a reply bar; the sent message stores
  `replyToId/Text/Sender` and is drawn with the "You replied to Aisha" label and quoted bubble
  from screen 21. **Double-tap** (or long-press → React ❤️) toggles `hearts/{uid}`; the small
  heart badge appears on the bubble for both users.
* Extras: typing indicator (`typing/{chatId}`), "Seen" under your last message (receiver's
  unread = 0), time dividers ("TODAY 4:49 PM"), emoji picker, 👍 when the box is empty.

### Voice & Video Calls (10) — Agora SDK
* The **phone** and **video** icons in a chat open `CallActivity.outgoing(...)`.
* **Signalling through Firebase**: the caller writes `calls/{calleeUid}` (`status: ringing`,
  `channel = callId`). The callee is notified two ways: while Kinnect is open, `CallWatcher`
  (listening on `calls/{me}`) opens the incoming-call screen; in the background the caller's phone
  sends an FCM push (`PushSender.call`) that shows a full-screen call notification.
* Accept sets `status: accepted`; both sides join the **Agora** channel with
  `RtcEngine.joinChannel`. Video calls render the remote `SurfaceView` full-screen and your
  camera picture-in-picture. Mute, speaker and switch-camera map to
  `muteLocalAudioStream`, `setEnableSpeakerphone`, `switchCamera`; the timer starts when the
  other person joins (`onUserJoined`).
* **Tokens**: the Agora project uses *Testing mode: APP ID*, so the app joins with the App ID
  and no token (a token server is not possible on the Spark plan).
* Declined / missed (45 s timeout) / ended calls are written to `callLogs/{uid}/{callId}` and a
  call message ("Voice call · 03:12", "Missed video call") is added to the chat.

### Friend System & Profiles (10)
* `util/Friends` is the request state machine (each action is one atomic multi-path update):
  send → `friendRequests/{them}/{me}` + `sentRequests/{me}/{them}`; confirm → both `friends`
  entries + remove requests; delete/cancel → remove requests; unfriend.
* `Friends.observe(uid)` combines three listeners into **NONE / REQUESTED / INCOMING / FRIENDS**
  and drives the button text **Add friend → Requested → Friends** (or **Confirm** for an
  incoming request) on Search (13), Other profile (17), Friends (14) and Notifications (18).
* **Friends (14)** – real **pending-request count** (`friendRequests/{me}`), request cards with
  mutual-friend avatars and **Confirm / Delete**, a **"Your friends"** chip with the real list,
  and "People you may know" ranked by mutual friends.
* **Profiles** – My profile (15) shows the **real friend count**, friend preview cards, details
  and your posts; the other profile (17) shows "N friends · **M mutual**" and "Friends with A, B
  and N others" with mutual avatars, photos and posts.
* **Edit profile (16)** – profile picture, cover photo, name, username
  (unique, transaction), bio, website, phone, gender, work, school, city.

### Push Notifications (10) — FCM
* On every sign-in (and in `onNewToken`) the device's FCM token is saved in
  `users/{uid}/fcmToken`; it is removed on log out.
* **Sending (no server on Spark)** – after a message is saved, `Chats.send` calls
  `PushSender.message`. `PushSender` loads the service-account key from assets, signs a JWT
  (RS256), exchanges it at `oauth2.googleapis.com/token` for an access token (cached for an
  hour), reads the receiver's `users/{uid}/fcmToken`, and POSTs a **data message** to the
  **FCM HTTP v1** endpoint `fcm.googleapis.com/v1/projects/{id}/messages:send` (sender name +
  text / "Sent a photo").
* **Receiving** – `KinnectMessagingService.onMessageReceived` builds the notification (Messages
  channel) even when the app is closed; tapping it opens that chat. No banner is shown for the
  chat that is already open.
* **Security trade-off** – the key ships inside the APK. It only has the FCM role, so the worst
  case is someone sending pushes to your users; acceptable for a class project, while a real app
  would send pushes from a server (Cloud Functions on Blaze).
* Also pushed: incoming calls, friend requests and screenshot alerts. Android 13+ asks for the
  POST_NOTIFICATIONS permission on Home.

### Search & Filters (5) — `SearchActivity`
* The pill is now an `EditText`; after a 250 ms debounce on each keystroke it runs two indexed
  **prefix queries**: `orderByChild("usernameLower")` and `orderByChild("nameLower")` with
  `startAt(q).endAt(q + "\uf8ff")`. Results are merged (exact username first) and every row has
  the live Add friend button and mutual-friend count.
* Filter chips: **All / People** search people, **Posts** searches the text of posts by you and
  your friends; recent searches are remembered on the device.

### Online / Offline Status (5) — `Presence`
* `KinnectApp` counts started activities: when the first Kinnect screen becomes visible
  `online=true`; when the app goes to the background `online=false` + `lastSeen`.
* `.info/connected` + **`onDisconnect()`** set `online=false` and `lastSeen` on the server if the
  app is killed or loses connection.
* Chats (20) shows the **green dot** from a live listener on each person's `online`; the Chat
  header (21) shows **"Active now"** or "Active 5m ago".

### Security & Privacy (5) — screenshots
* `util/ScreenshotDetector`, active while a chat is on screen:
  Android 14+ uses the official **`Activity.registerScreenCaptureCallback`**
  (`DETECT_SCREEN_CAPTURE` permission); older versions watch **MediaStore** for a new image whose
  name/folder contains "screenshot".
* On detection the chat gets a **system message "Aisha took a screenshot"** (shown centred in the
  conversation for both people), an in-app notification is created, and `PushSender` sends the
  other person a push: *"Aisha took a screenshot of your chat"*.

### Test Cases (5) — `app/src/androidTest/…`
Both tests run against the real Firebase project and clean up the accounts they create.
1. **`SignUpFlowTest`** – fills the sign-up form, taps Create Account, verifies the Auth account
   and the `users/{uid}` node (profileComplete=false), checks that the **profile setup** screen is
   shown, saves a unique username + bio, then verifies Home is shown and that
   `profileComplete=true` and `usernames/{username}` = uid.
2. **`ChatMessagingTest`** – creates two accounts, opens a chat, sends a message and verifies it
   appears in the conversation, is stored in `messages/{chatId}`, updates the receiver's
   `userChats` row (last message + unread), and that **Edit/Delete** are offered while the
   message is less than 5 minutes old.

Run them with a device connected: `./gradlew connectedDebugAndroidTest`.

**Optional – local Firebase Emulator Suite.** Add `firebase.emulatorHost=127.0.0.1` to
`local.properties`, run `firebase emulators:start`, and forward the ports to the device with
`adb reverse tcp:9099 tcp:9099` and `adb reverse tcp:9000 tcp:9000`. Debug builds then talk to
the local Auth and Database emulators (`KinnectApp`), so you can test without touching the real
project. Remove the line to go back to the real backend.

---

## 4. Security rules (`database.rules.json`)
* Users can only write their own profile; usernames can only be claimed if free.
* Friend nodes can only be created when a matching request exists.
* Only participants (uid contained in the chat id) can read/write a chat; the 5-minute
  edit/delete window is enforced server-side; only the six reaction keys are accepted.
* Notifications can be created by others only with their own uid as `fromUid`.
* Media chunks live under `mediaChunks/{ownerUid}/…`: anyone signed in can read a file by its id,
  only the owner can write or delete it, and each chunk is limited to 600 000 characters.
* Indexes: `users.usernameLower/nameLower/createdAt`, `posts.timestamp/uid`,
  `messages.timestamp`, `userChats.timestamp`, `notifications.timestamp`, `friends/.value`.

---

## 5. Notes / limitations
* Package stays `com.hadisaleemi.i221043`. The assignment asks for
  `com.groupname.roll1_roll2`; rename it in Android Studio (*Refactor → Rename* on the package,
  then update `namespace`/`applicationId` in `app/build.gradle.kts` and the Firebase app).
* `gradle.properties` sets `android.uniquePackageNames=false` because the Agora SDK ships several
  AARs with the same manifest namespace, which AGP 9 otherwise rejects.
* Close-friends filtering of stories is done in the app (Realtime Database cannot filter reads per
  item); the story list itself is readable by signed-in users.
* Marketplace (23) is not part of the backend requirements and keeps its design content.
* Free Spark limits: 1 GB stored and 10 GB downloaded per month in the Realtime Database. Media is
  compressed and cached on the phone, so a class demo uses a small fraction of that.
