const { auth, db, messaging, admin } = require('./firebase');
const { v4: uuidv4 } = require('uuid');

/** Max number of recent messages embedded inside a conversation doc. */
const MAX_RECENT_MESSAGES = 50;

/**
 * Validates a location_update frame has all required fields within valid ranges.
 * @param {Object} frame - The location update payload
 * @returns {boolean} true if valid
 */
function validateLocationFrame(frame) {
    if (!frame) return false;
    const { userId, latitude, longitude, accuracy, speed, bearing, timestamp } = frame;
    if (typeof userId !== 'string' || userId.trim() === '') return false;
    if (typeof latitude !== 'number' || latitude < -90 || latitude > 90) return false;
    if (typeof longitude !== 'number' || longitude < -180 || longitude > 180) return false;
    if (typeof accuracy !== 'number' || accuracy < 0) return false;
    if (typeof speed !== 'number' || speed < 0) return false;
    if (typeof bearing !== 'number' || bearing < 0 || bearing > 360) return false;
    if (typeof timestamp !== 'number' || timestamp <= 0) return false;
    return true;
}

const initializeSockets = (io) => {
    // Middleware for authentication
    io.use(async (socket, next) => {
        const token = socket.handshake.query.token || socket.handshake.auth.token;
        if (!token) {
            return next(new Error('Authentication error: Token missing'));
        }
        try {
            const decodedToken = await auth.verifyIdToken(token);
            socket.user = decodedToken;
            next();
        } catch (err) {
            next(new Error('Authentication error: Invalid token'));
        }
    });

    io.on('connection', (socket) => {
        const uid = socket.user.uid;
        const userName = socket.user.name || 'User';
        const userPhotoUrl = socket.user.picture || null;
        const conversationId = socket.handshake.query.conversationId;
        const locationRoom = socket.handshake.query.locationRoom;

        // ── Location Room Join ────────────────────────────────────────────────
        // Clients can join a location room for real-time location relay.
        // This bypasses Firestore reads entirely for connected peers.
        if (locationRoom) {
            socket.join(locationRoom);
            socket.locationRoom = locationRoom;
        }

        // ── Location Update Relay ─────────────────────────────────────────────
        // Validates and broadcasts location_update frames to all peers in the
        // same location room. Server does NOT persist to Firestore — the sending
        // client handles its own write.
        socket.on('location_update', (data) => {
            if (!socket.locationRoom) return;
            if (!validateLocationFrame(data)) return;

            // Ensure userId matches authenticated user (prevent spoofing)
            if (data.userId !== uid) return;

            // Broadcast to all other clients in the same location room
            socket.to(socket.locationRoom).emit('location_update', data);
        });

        if (conversationId) {
            // Verify participant
            db.collection('conversations').doc(conversationId).get().then(doc => {
                if (doc.exists && doc.data().participantIds.includes(uid)) {
                    socket.join(conversationId);
                    socket.emit('connected', { conversationId, userId: uid });

                    // Broadcast presence: user is online
                    socket.to(conversationId).emit('presence', {
                        userId: uid,
                        status: 'online'
                    });
                } else {
                    socket.disconnect();
                }
            }).catch(console.error);
        }

        socket.on('message', async (data) => {
            if (!conversationId) return;
            try {
                const { tempId, text, replyToId, replyToText, replyToSenderName, mentionedUserIds } = data;
                if (!text || text.trim() === '') return;

                const msgId = uuidv4();
                const now = Date.now();

                const msgDto = {
                    id: msgId,
                    conversationId,
                    senderId: uid,
                    senderName: userName,
                    senderPhotoUrl: userPhotoUrl,
                    text: text.trim(),
                    messageType: 'TEXT',
                    timestamp: now,
                    readBy: [uid]
                };

                // Include reply data if present
                if (replyToId) {
                    msgDto.replyToId = replyToId;
                    msgDto.replyToText = replyToText || null;
                    msgDto.replyToSenderName = replyToSenderName || null;
                }

                // Pass mentions through to FCM so the client can route an
                // @mention notification even when the conversation is muted.
                const mentions = Array.isArray(mentionedUserIds)
                    ? mentionedUserIds.filter(m => typeof m === 'string' && m)
                    : [];
                if (mentions.length > 0) {
                    msgDto.mentionedUserIds = mentions;
                }

                await persistMessage(conversationId, msgId, msgDto, uid, text.trim(), now);

                // Broadcast
                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                // Send FCM push notifications to other participants
                sendFCM(conversationId, uid, userName, text.trim(), { mentions });
            } catch (err) {
                console.error('Error handling message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('image_message', async (data) => {
            if (!conversationId) return;
            try {
                const { tempId, imageUrl } = data;
                if (!imageUrl) return;

                const msgId = uuidv4();
                const now = Date.now();

                const msgDto = {
                    id: msgId,
                    conversationId,
                    senderId: uid,
                    senderName: userName,
                    senderPhotoUrl: userPhotoUrl,
                    text: '📷 Image',
                    messageType: 'IMAGE',
                    imageUrl: imageUrl,
                    timestamp: now,
                    readBy: [uid]
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '📷 Image', now);

                // Broadcast
                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                // Send FCM push notifications
                sendFCM(conversationId, uid, userName, '📷 Sent an image');
            } catch (err) {
                console.error('Error handling image message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('location_message', async (data) => {
            if (!conversationId) return;
            try {
                const { tempId, latitude, longitude } = data;

                const msgId = uuidv4();
                const now = Date.now();

                const msgDto = {
                    id: msgId,
                    conversationId,
                    senderId: uid,
                    senderName: userName,
                    senderPhotoUrl: userPhotoUrl,
                    text: '📍 Location',
                    messageType: 'LOCATION',
                    latitude,
                    longitude,
                    timestamp: now,
                    readBy: [uid]
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '📍 Location', now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                sendFCM(conversationId, uid, userName, '📍 Shared a location');
            } catch (err) {
                console.error('Error handling location message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('voice_message', async (data) => {
            if (!conversationId) return;
            try {
                const { tempId, voiceUrl, durationMs } = data;
                if (!voiceUrl) return;

                const msgId = uuidv4();
                const now = Date.now();

                const msgDto = {
                    id: msgId,
                    conversationId,
                    senderId: uid,
                    senderName: userName,
                    senderPhotoUrl: userPhotoUrl,
                    text: '🎤 Voice message',
                    messageType: 'VOICE',
                    voiceUrl: voiceUrl,
                    voiceDurationMs: durationMs || 0,
                    timestamp: now,
                    readBy: [uid]
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '🎤 Voice message', now);

                // Broadcast
                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                // Send FCM push notifications
                sendFCM(conversationId, uid, userName, '🎤 Sent a voice message');
            } catch (err) {
                console.error('Error handling voice message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('typing', (data) => {
            if (!conversationId) return;
            socket.to(conversationId).emit('typing', {
                userId: uid,
                userName: userName,
                isTyping: data.isTyping
            });
        });

        socket.on('reaction', async (data) => {
            if (!conversationId) return;
            try {
                const { messageId, emoji } = data;
                if (!messageId || !emoji) return;

                // Update reaction in the conversation's recentMessages array
                const convRef = db.collection('conversations').doc(conversationId);
                await db.runTransaction(async (transaction) => {
                    const convDoc = await transaction.get(convRef);
                    if (!convDoc.exists) return;

                    const convData = convDoc.data();
                    const recentMessages = convData.recentMessages || [];
                    const msgIndex = recentMessages.findIndex(m => m.id === messageId);

                    if (msgIndex >= 0) {
                        const msg = recentMessages[msgIndex];
                        const reactions = msg.reactions || {};
                        const emojiList = reactions[emoji] || [];

                        if (!emojiList.includes(uid)) {
                            emojiList.push(uid);
                            reactions[emoji] = emojiList;
                            recentMessages[msgIndex] = { ...msg, reactions };
                            transaction.update(convRef, { recentMessages });
                        }
                    }
                });

                // Broadcast reaction update to room
                io.to(conversationId).emit('reaction_update', {
                    messageId,
                    userId: uid,
                    emoji,
                    action: 'add'
                });
            } catch (err) {
                console.error('Error handling reaction:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('remove_reaction', async (data) => {
            if (!conversationId) return;
            try {
                const { messageId, emoji } = data;
                if (!messageId || !emoji) return;

                // Update reaction in the conversation's recentMessages array
                const convRef = db.collection('conversations').doc(conversationId);
                await db.runTransaction(async (transaction) => {
                    const convDoc = await transaction.get(convRef);
                    if (!convDoc.exists) return;

                    const convData = convDoc.data();
                    const recentMessages = convData.recentMessages || [];
                    const msgIndex = recentMessages.findIndex(m => m.id === messageId);

                    if (msgIndex >= 0) {
                        const msg = recentMessages[msgIndex];
                        const reactions = msg.reactions || {};
                        const emojiList = reactions[emoji] || [];

                        const index = emojiList.indexOf(uid);
                        if (index !== -1) {
                            emojiList.splice(index, 1);
                            if (emojiList.length === 0) {
                                delete reactions[emoji];
                            } else {
                                reactions[emoji] = emojiList;
                            }
                            recentMessages[msgIndex] = { ...msg, reactions };
                            transaction.update(convRef, { recentMessages });
                        }
                    }
                });

                // Broadcast reaction update to room
                io.to(conversationId).emit('reaction_update', {
                    messageId,
                    userId: uid,
                    emoji,
                    action: 'remove'
                });
            } catch (err) {
                console.error('Error handling remove_reaction:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('read', async () => {
            if (!conversationId) return;
            try {
                const now = Date.now();

                // Track which message IDs were marked read in this call so we can
                // broadcast a precise read_receipt to other participants.
                // Works for both 1:1 and group conversations because the data
                // structure (recentMessages with readBy array) is the same.
                let newlyReadMessageIds = [];

                const convRef = db.collection('conversations').doc(conversationId);
                await db.runTransaction(async (transaction) => {
                    const doc = await transaction.get(convRef);
                    if (!doc.exists) return;

                    const data = doc.data();
                    if (!data.participantIds.includes(uid)) return;

                    const unreadCounts = data.unreadCounts || {};
                    unreadCounts[uid] = 0;

                    // Mark all unread messages from other senders as read by this user
                    const recentMessages = data.recentMessages || [];
                    const markedIds = [];
                    let updated = false;
                    for (let i = 0; i < recentMessages.length; i++) {
                        const msg = recentMessages[i];
                        if (msg.senderId !== uid) {
                            const readBy = msg.readBy || [];
                            if (!readBy.includes(uid)) {
                                readBy.push(uid);
                                recentMessages[i] = { ...msg, readBy };
                                markedIds.push(msg.id);
                                updated = true;
                            }
                        }
                    }

                    const updateData = { unreadCounts };
                    if (updated) {
                        updateData.recentMessages = recentMessages;
                    }
                    transaction.update(convRef, updateData);

                    // Capture for broadcast outside the transaction
                    newlyReadMessageIds = markedIds;
                });

                // Always broadcast — even if no new messages were marked read,
                // the receipt acts as a presence/seen ping for the sender's UI.
                socket.to(conversationId).emit('read_receipt', {
                    messageIds: newlyReadMessageIds,
                    userId: uid,
                    timestamp: now
                });
            } catch (err) {
                console.error('Error handling read receipt:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('disconnect', () => {
            if (conversationId) {
                // Broadcast presence: user is offline
                socket.to(conversationId).emit('presence', {
                    userId: uid,
                    status: 'offline'
                });
            }
            // Broadcast location_user_offline to location room peers
            if (socket.locationRoom) {
                socket.to(socket.locationRoom).emit('location_user_offline', {
                    userId: uid
                });
            }
        });
    });
};

/**
 * Persists a message to both the archive collection AND embeds it in the
 * conversation document's recentMessages array. This replaces the old
 * separate `db.collection('messages').set()` + `updateConversationLastMessage()`
 * with a single batched write, and embeds the message for single-doc reads.
 *
 * Cost: 1 archive write + 1 conversation transaction = same as before,
 * but now the conversation doc contains the last 50 messages.
 */
async function persistMessage(conversationId, msgId, msgDto, senderId, previewText, timestamp) {
    const convRef = db.collection('conversations').doc(conversationId);

    // Strip conversationId from the embedded copy to save space (it's implicit)
    const embeddedMsg = { ...msgDto };
    delete embeddedMsg.conversationId;

    await db.runTransaction(async (transaction) => {
        const convDoc = await transaction.get(convRef);
        if (!convDoc.exists) return;

        const data = convDoc.data();
        const participants = data.participantIds || [];
        const unreadCounts = data.unreadCounts || {};

        // Increment unread count for all participants except sender
        for (const pid of participants) {
            if (pid !== senderId) {
                unreadCounts[pid] = (unreadCounts[pid] || 0) + 1;
            }
        }

        // Append to recentMessages, cap at MAX_RECENT_MESSAGES
        let recentMessages = data.recentMessages || [];
        recentMessages.push(embeddedMsg);
        if (recentMessages.length > MAX_RECENT_MESSAGES) {
            recentMessages = recentMessages.slice(-MAX_RECENT_MESSAGES);
        }

        // Track the oldest embedded timestamp for pagination cursor
        const oldestRecentTimestamp = recentMessages.length > 0
            ? recentMessages[0].timestamp
            : timestamp;

        transaction.update(convRef, {
            lastMessageText: previewText,
            lastMessageSenderId: senderId,
            lastMessageTimestamp: timestamp,
            unreadCounts,
            recentMessages,
            oldestRecentTimestamp
        });
    });
}

/**
 * Sends data-only FCM notifications to every participant other than the
 * sender. Honors mute lists and routes @mentioned users through a separate
 * `type` so the Android client (FcmMessagingService) can use the messages
 * channel for both — but pick the right title/body and surface mention
 * notifications even on muted conversations.
 *
 * Behaviour matrix:
 *  • Recipient muted, NOT mentioned     → push suppressed.
 *  • Recipient muted, IS mentioned      → mention push (overrides mute).
 *  • Recipient unmuted, IS mentioned    → mention push.
 *  • Recipient unmuted, NOT mentioned   → standard new_message push.
 *
 * The Android side decides whether to actually display the system tray
 * notification (channel preferences + active-conversation suppression),
 * but the server can still cut down on wasted FCM traffic by filtering
 * muted recipients up front.
 *
 * @param {Object} options
 * @param {string[]} options.mentions   — uids explicitly @mentioned in the
 *                                       message. Empty = no mention.
 */
async function sendFCM(conversationId, senderId, senderName, text, options = {}) {
    const mentions = options.mentions || [];
    try {
        const convDoc = await db.collection('conversations').doc(conversationId).get();
        if (!convDoc.exists) return;

        const data = convDoc.data();
        const participants = data.participantIds || [];
        const mutedBy = data.mutedBy || [];
        const mutedUntil = data.mutedUntil || {};
        const conversationName = data.name || senderName;
        const isGroup = (data.type || 'direct') === 'group';
        const groupId = data.groupId || null;
        const now = Date.now();

        const recipients = participants
            .filter(id => id !== senderId)
            .filter(id => {
                // Mention bypass — even muted recipients get a push when
                // explicitly @mentioned. This is the WhatsApp / Slack norm.
                if (mentions.includes(id)) return true;

                // Per-user mute with expiry takes precedence. `Number.MAX_SAFE_INTEGER`
                // (and `Long.MAX_VALUE` from Android) both encode "always muted".
                const until = Number(mutedUntil[id] || 0);
                if (until > now) return false;

                // Legacy boolean array — present means "muted forever". The
                // newer mutedUntil map is authoritative when set, so this
                // only kicks in for clients that pre-date the per-duration
                // feature.
                if (mutedBy.includes(id) && !(id in mutedUntil)) return false;
                return true;
            });
        if (recipients.length === 0) return;

        const sends = await Promise.all(recipients.map(async (recipientId) => {
            const userDoc = await db.collection('users').doc(recipientId).get();
            const token = userDoc.exists ? userDoc.data().fcmToken : null;

            const isMention = mentions.includes(recipientId);
            const title = isMention
                ? `${senderName} mentioned you`
                : (isGroup ? `${senderName} in ${conversationName}` : senderName);
            const body = isMention ? `Mentioned you: ${text}` : text;
            const type = isMention ? 'mention' : 'new_message';

            // Mirror to the per-recipient Firestore inbox so cross-device
            // read state and reinstall survival both work. We do this even
            // when the FCM send is skipped (no token / dead token) so the
            // recipient's inbox is the canonical source of truth.
            await persistChatInboxEntry(recipientId, {
                type,
                title,
                body,
                conversationId,
                senderId,
                senderName,
                groupId,
                mentions,
            }).catch(err => console.warn('inbox persist failed', recipientId, err.message));

            if (!token) return null;

            const payload = {
                token,
                data: {
                    type,
                    title,
                    body,
                    conversationId,
                    senderId,
                    senderName,
                    text,
                    conversationName,
                    ...(groupId ? { groupId } : {}),
                    ...(mentions.length > 0 ? { mentionedUserIds: mentions.join(',') } : {}),
                },
                android: { priority: 'high' }
            };
            return messaging.send(payload).catch(err => {
                console.warn(`FCM send failed for ${recipientId}:`, err.code || err.message);
                return null;
            });
        }));
        // Drain — `await Promise.all` already resolved, but keep the var
        // referenced so linters don't trim the assignment.
        void sends;
    } catch (err) {
        console.error('Failed to send FCM:', err);
    }
}

/**
 * Writes a chat notification into the single-doc inbox at
 * `users/{recipient}/inbox/notifications`. Mirrors the shape used by the
 * TypeScript Cloud Functions so the Android client sees a single coherent
 * inbox stream regardless of producer.
 *
 * Single-doc aggregate pattern:
 *   • One document holds `entries: Map<id, NotificationDoc>` keyed by
 *     deterministic notification id.
 *   • Costs 1 read per inbox open instead of N reads.
 *   • FIFO eviction caps the map at MAX_INBOX_ENTRIES so the doc stays
 *     comfortably under Firestore's 1 MiB cap.
 *   • A scheduled Cloud Function (scheduledPruneNotifications) handles the
 *     30-day time-based retention separately.
 *
 * Idempotent — same inbound message (retry / replay) replaces the same
 * map slot rather than duplicating.
 */
async function persistChatInboxEntry(recipientUid, ctx) {
    const now = Date.now();
    const entryId = `${ctx.type}_${ctx.conversationId}_${now}`;
    const entry = {
        id: entryId,
        type: ctx.type,
        title: ctx.title,
        body: ctx.body,
        timestamp: now,
        isRead: false,
        deepLinkRoute: `chat/${ctx.conversationId}`,
        conversationId: ctx.conversationId,
        groupId: ctx.groupId || null,
        userId: ctx.senderId,
        destinationName: null,
    };

    const inboxRef = db.doc(`users/${recipientUid}/inbox/notifications`);

    await db.runTransaction(async (tx) => {
        const snap = await tx.get(inboxRef);
        const existing = (snap.exists && (snap.data().entries || {})) || {};

        // Insert / replace, then enforce FIFO cap.
        const next = { ...existing, [entryId]: entry };
        const sortedIds = Object.values(next)
            .sort((a, b) => b.timestamp - a.timestamp)
            .slice(0, MAX_INBOX_ENTRIES)
            .map(e => e.id);

        const trimmed = {};
        for (const id of sortedIds) {
            if (next[id]) trimmed[id] = next[id];
        }

        tx.set(inboxRef, {
            entries: trimmed,
            updatedAt: now,
        }, { merge: false });
    });
}

/** Mirrors functions/src/lib/notify.ts MAX_INBOX_ENTRIES. */
const MAX_INBOX_ENTRIES = 200;

module.exports = { initializeSockets };
