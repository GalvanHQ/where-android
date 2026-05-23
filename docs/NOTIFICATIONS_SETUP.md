# Notifications System — Setup Checklist

This file is the operator's setup guide for the notification system. The
codebase implements both client and server sides; the items below are the
**Firebase Console / dashboard steps that can't be expressed in code** and
must be done once per project (typically during initial deployment to
each environment — dev / staging / prod).

If you skip these, the code still runs and basic notifications work, but
some features degrade silently:

- Cloud Scheduler not enabled → 30-day inbox retention not enforced (FIFO cap still works)
- FCM API quota not raised → group chats with 50+ members can hit rate limits

---

## 1. Inbox storage model

Notifications live in a **single document per user**:

```
users/{uid}/inbox/notifications
{
  entries: {
    [notificationId]: {
      id, type, title, body, timestamp, isRead, deepLinkRoute,
      conversationId, groupId, userId, destinationName
    },
    ...
  },
  updatedAt: <epoch millis>
}
```

Why single-doc instead of one-doc-per-notification:

- **1 read per inbox open** — at 50–200 notifications, this saves 50–200×
  reads vs the per-doc model. At 10K MAU that's tens of millions of reads/month.
- Mirrors the existing `users/{uid}/inbox/friendRequests` pattern.
- Cross-device read sync uses dotted-path field updates
  (`entries.${id}.isRead = true`) so two devices flagging different
  entries don't fight over the whole-doc map.

Bounds: capped at **200 entries** (FIFO eviction in the server-side writer)
which keeps the doc safely under Firestore's 1 MiB limit
(~400 bytes × 200 = 80 KB).

---

## 2. Cloud Scheduler — enable for the prune job

The 30-day retention window is enforced by a daily scheduled function
(`scheduledPruneNotifications`). Cloud Scheduler is required.

**Steps:**

1. Open the [Firebase Console → Functions](https://console.firebase.google.com/project/_/functions) tab.
2. After deploying the functions, the scheduled function will create
   itself on first deploy.
3. Confirm Cloud Scheduler API is enabled in the [GCP Console](https://console.cloud.google.com/apis/library/cloudscheduler.googleapis.com).
4. The function runs every 24 hours, scanning every user's inbox doc and
   removing entries with `timestamp < now − 30 days`. Idle inboxes get no
   write (no-op when nothing to prune).

**Cost:** at 10K users, 10K reads + worst-case 10K writes per day = well
under free tier.

---

## 3. Notifications subcollection security rules

Already defined in [`firestore.rules`](../firestore.rules). The single inbox
doc is covered by the existing `match /inbox/{docId}` rule (same one that
protects the friend-requests inbox). Deploy the latest rules:

```bash
firebase deploy --only firestore:rules
```

Rule summary:
- **Read**: owner only.
- **Write**: owner OR admin (Cloud Functions / socket server use the
  Admin SDK which bypasses rules; clients only write the dotted-path
  `entries.${id}.isRead` updates).

---

## 4. FCM Cloud Functions deployment

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

Triggers deployed:
- `onFriendRequestSent` — push when a friend request lands in someone's inbox
- `onFriendRequestAccepted` — push when a friendship doc flips to ACCEPTED
- `onGroupDocumentUpdated` — combined trigger for membership + meetup events
- `onLiveLocationChanged` — sharing started / stopped events (skips per-tick GPS writes)
- `scheduledPruneNotifications` — daily 30-day retention enforcement

---

## 5. FCM API quota (high-volume groups)

Default quota is 6,000 requests / minute / project. A 100-member group
chat where one person types fast could spike past that. Two mitigations:

1. **Quota request**: [Google Cloud Console → IAM → Quotas](https://console.cloud.google.com/iam-admin/quotas) — search for "Firebase Cloud Messaging" and request an increase.
2. **Server-side batching**: not currently implemented. Add when you see
   FCM rate-limit errors in the server logs (`messaging/internal-error`
   with quota-related details).

---

## 6. Notification icon (sanity check)

The app posts notifications with `setSmallIcon(R.drawable.ic_notification)`.
The drawable is a monochrome white pin glyph at
[`app/src/main/res/drawable/ic_notification.xml`](../app/src/main/res/drawable/ic_notification.xml).

If you're getting white squares in the status bar, this file is the place
to fix it — the asset is intentionally pure white on transparent
background per Android's tint behavior. Don't replace it with the launcher
icon (multi-color, will get clipped).

---

## 7. Smoke test after deploying

After deployment, validate the path end-to-end:

1. **Friend request**: Phone A sends a friend request to Phone B.
   Expected: Phone B receives FCM push within 5s, in-app notification
   inbox shows the entry.
2. **Cross-device read**: Mark the inbox entry read on Phone B (via Phone B's app).
   Expected: If Phone B is signed in on a tablet too, the tablet's inbox
   reflects the read state within ~2s.
3. **Mute duration**: Phone A mutes a chat with Phone B for 1 hour.
   Phone B sends a message.
   Expected: Phone A does NOT receive a system tray push but the in-app
   inbox does record the message. After 1 hour, push delivery resumes.
4. **Mention bypass**: Phone A mutes a group. Phone B @-mentions Phone A.
   Expected: Phone A still receives the push (mention overrides mute).
5. **Quiet hours**: Phone A enables quiet hours from 22:00–07:00. At 23:00,
   Phone B sends a message.
   Expected: Phone A's inbox shows the message, but no sound/vibration.
   If `fullBlock` is on, no notification at all.

---

## 8. Things to monitor in production

- **`messaging/registration-token-not-registered` rate** — if this climbs,
  users are uninstalling without signing out cleanly. We auto-clean stale
  tokens but a high rate is a usage signal worth watching.
- **Function execution time on `onGroupDocumentUpdated`** — should be < 1s
  p95. Spikes here mean either large groups (fan-out) or slow
  display-name lookups (the resolver hits Firestore once per affected user).
- **Inbox doc size** — should stay under ~80 KB. If you see users with
  bigger docs, the FIFO cap is broken.
- **`scheduledPruneNotifications` execution time** — should scale linearly
  with active user count. If it approaches the 9-minute cap, paginate
  more aggressively in the prune loop.

---

## Author
Where notifications module — last updated alongside the single-doc inbox migration.
