# Message Storage Optimization: Single-Document Approach

## Status: IMPLEMENTED (both server and client)

## Summary
Messages are now embedded as a `recentMessages` array (last 50) inside the conversation document. This reduces Firestore reads from 30+ per chat open to 1.

## What was done:

### Server (`server/src/socket.js`)
- `persistMessage()` function already appends messages to `recentMessages` array on the conversation doc
- Caps at 50 messages, archives overflow to `messages` collection
- Tracks `oldestRecentTimestamp` for pagination cursor

### Server (`server/src/routes/conversations.js`)
- `GET /messages` reads from `recentMessages` on the conversation doc (1 read) for initial load
- Falls back to archive collection for pagination (`before` cursor)
- Catch-up (`after` param) checks embedded messages first
- Migration endpoint `POST /migrate-recent` backfills existing conversations

### Android Client (`ConversationRepositoryImpl.kt`)
- Firestore listener now parses `recentMessages` from each conversation document
- Upserts parsed messages directly into Room's `messages` table
- Messages appear in the chat UI immediately (0 extra API calls needed for cached conversations)
- The REST API call still works as fallback for initial load / pagination

### Cost Savings
| Operation | Before | After |
|-----------|--------|-------|
| Open a chat (30 msgs) | 30 reads | 1 read |
| Real-time new message | 1 read (listener) | 0 extra (embedded in conv doc listener) |
| Pagination (older) | 30 reads | 30 reads (archive, rare) |

---

## Architecture

### Firestore Document Structure

```
conversations/{conversationId}
├── id: string
├── type: "direct" | "group"
├── participantIds: string[]
├── groupId: string | null
├── name: string
├── photoUrl: string | null
├── lastMessageText: string
├── lastMessageSenderId: string
├── lastMessageTimestamp: number
├── unreadCounts: { [userId]: number }
├── themeColor: string | null
├── emojiShortcut: string | null
├── nicknames: { [userId]: string }
├── participantNames: { [userId]: string }
├── participantPhotos: { [userId]: string }
├── createdAt: number
├── recentMessages: MessageObject[]    ← NEW (array of last 50 messages)
└── oldestRecentTimestamp: number      ← NEW (timestamp of oldest message in array)
```

### Message Object (inside recentMessages array)

```json
{
  "id": "uuid-string",
  "senderId": "userId",
  "senderName": "Display Name",
  "senderPhotoUrl": "https://...",
  "text": "message content",
  "messageType": "TEXT|IMAGE|VOICE|LOCATION",
  "timestamp": 1779055842795,
  "readBy": ["userId1", "userId2"],
  "imageUrl": "https://...",
  "thumbnailUrl": "https://...",
  "voiceUrl": "https://...",
  "voiceDurationMs": 5000,
  "replyToId": "messageId",
  "replyToText": "original text",
  "replyToSenderName": "Name",
  "reactions": { "👍": ["userId1"], "❤️": ["userId2"] },
  "latitude": 23.8103,
  "longitude": 90.4125
}
```

### Archive Collection (for pagination history)

```
messages/{messageId}    ← Keep for older messages beyond the 50-message window
├── conversationId: string
├── ... (same fields as above)
```

---

## Read Cost Comparison

| Operation | Before (per-doc) | After (embedded) |
|-----------|-----------------|------------------|
| Load recent 30 messages | 30 reads | 1 read (conversation doc) |
| Load conversation list (10 convos) | 10 reads | 10 reads (same) |
| Open a chat | 30+ reads | 1 read |
| Receive real-time message | 1 read (listener) | 1 read (listener on conv doc) |
| Paginate older messages | 30 reads | 30 reads (from archive) |

**Savings**: ~30x fewer reads for the most common operation (opening a chat).

---

## Server Changes (Node.js)

### On Message Send

```javascript
// 1. Add message to recentMessages array (atomic)
// 2. Trim array to last 50 messages
// 3. If trimmed, move oldest to archive collection
// 4. Update lastMessage* fields

const conversationRef = db.collection('conversations').doc(conversationId);

await db.runTransaction(async (transaction) => {
  const doc = await transaction.get(conversationRef);
  const data = doc.data();
  
  let recentMessages = data.recentMessages || [];
  recentMessages.push(newMessage);
  
  // Archive overflow messages
  if (recentMessages.length > 50) {
    const overflow = recentMessages.splice(0, recentMessages.length - 50);
    for (const msg of overflow) {
      const archiveRef = db.collection('messages').doc(msg.id);
      transaction.set(archiveRef, { ...msg, conversationId });
    }
  }
  
  transaction.update(conversationRef, {
    recentMessages,
    oldestRecentTimestamp: recentMessages[0]?.timestamp || 0,
    lastMessageText: newMessage.text,
    lastMessageSenderId: newMessage.senderId,
    lastMessageTimestamp: newMessage.timestamp,
    [`unreadCounts.${recipientId}`]: admin.firestore.FieldValue.increment(1)
  });
});
```

### GET /api/conversations/:id/messages

```javascript
// For recent messages: read from conversation doc (1 read)
app.get('/api/conversations/:id/messages', async (req, res) => {
  const { before, limit = 30 } = req.query;
  
  if (!before) {
    // Initial load: return from recentMessages array (1 Firestore read)
    const doc = await db.collection('conversations').doc(req.params.id).get();
    const recentMessages = doc.data().recentMessages || [];
    const limited = recentMessages.slice(-limit);
    return res.json({
      messages: limited,
      nextCursor: limited.length > 0 ? limited[0].timestamp.toString() : null,
      hasMore: recentMessages.length > limit || doc.data().oldestRecentTimestamp > 0
    });
  }
  
  // Pagination: read from archive collection (N reads, but rare)
  const snapshot = await db.collection('messages')
    .where('conversationId', '==', req.params.id)
    .where('timestamp', '<', parseInt(before))
    .orderBy('timestamp', 'desc')
    .limit(limit)
    .get();
  
  const messages = snapshot.docs.map(doc => doc.data());
  return res.json({
    messages: messages.reverse(),
    nextCursor: messages.length > 0 ? messages[0].timestamp.toString() : null,
    hasMore: messages.length === limit
  });
});
```

### On Reaction/ReadReceipt Update

```javascript
// Update the specific message in the recentMessages array
// Use arrayRemove + arrayUnion pattern, or read-modify-write in transaction

await db.runTransaction(async (transaction) => {
  const doc = await transaction.get(conversationRef);
  const recentMessages = doc.data().recentMessages || [];
  
  const msgIndex = recentMessages.findIndex(m => m.id === messageId);
  if (msgIndex >= 0) {
    // Update in-place
    recentMessages[msgIndex].readBy = [...new Set([...recentMessages[msgIndex].readBy, userId])];
    transaction.update(conversationRef, { recentMessages });
  }
  
  // Also update archive if message is old
  const archiveRef = db.collection('messages').doc(messageId);
  transaction.update(archiveRef, { readBy: admin.firestore.FieldValue.arrayUnion(userId) });
});
```

---

## Android Client Changes

### No changes needed for the REST API flow

The client already:
1. Calls `GET /api/conversations/:id/messages` → gets messages
2. Caches in Room
3. Observes Room Flow for UI

The server response format stays the same (`MessageDto` / `MessagePageDto`). The optimization is entirely server-side — the server reads 1 Firestore document instead of 30.

### Optional: Direct Firestore Listener (eliminates Socket.IO dependency)

If you want to eliminate the Socket.IO server entirely and use Firestore as the real-time transport:

```kotlin
// Listen to the conversation document for real-time message updates
firestore.collection("conversations").document(conversationId)
    .addSnapshotListener { snapshot, error ->
        val recentMessages = snapshot?.get("recentMessages") as? List<Map<String, Any>>
        // Parse and upsert to Room
    }
```

This gives you real-time messages with 0 additional reads (the listener is already active for conversation metadata).

---

## Firestore Rules Update

```javascript
match /conversations/{conversationId} {
  allow read: if isAuthenticated() && request.auth.uid in resource.data.participantIds;
  
  // Participants can update client-side metadata only
  // Server (Admin SDK) manages recentMessages, unreadCounts, lastMessage*
  allow update: if isAuthenticated()
    && request.auth.uid in resource.data.participantIds
    && (!request.resource.data.diff(resource.data).affectedKeys()
        .hasAny(['recentMessages', 'oldestRecentTimestamp', 'unreadCounts']));
}
```

---

## Limits and Considerations

| Constraint | Value | Impact |
|-----------|-------|--------|
| Firestore doc size limit | 1 MB | ~50 messages with full metadata ≈ 50-100 KB (safe) |
| Array field limit | 20,000 elements | 50 messages is well within |
| Write rate per doc | 1 write/sec sustained | For active group chats, use batched writes or queue |
| Transaction limit | 500 ops | Fine for single-doc updates |

### Write Contention Mitigation (for active groups)

If a group has 10+ people sending simultaneously:
- Use a **write queue** on the server: batch incoming messages and flush every 500ms
- Or use **Firestore distributed counters** pattern with sharded sub-documents

For your development phase with few users, the simple transaction approach works fine.

---

## Migration Steps

1. **Server**: Update message send handler to write to `recentMessages` array
2. **Server**: Update GET messages endpoint to read from conversation doc first
3. **Server**: Update reaction/read-receipt handlers to modify array in-place
4. **Android**: No client code changes needed (REST API response format unchanged)
5. **Firestore Rules**: Already support this (rules reference `recentMessages`)
6. **Delete**: Remove the top-level `messages` collection documents (no existing users)

---

## Summary

- **1 read** to load a chat (down from 30+)
- **Server-only change** — Android client code stays the same
- **Real-time** still works via Socket.IO (or optionally via Firestore listener on the conversation doc)
- **Pagination** for older messages still uses the archive collection
- **No migration needed** — no existing users
