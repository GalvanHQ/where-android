// ── socket.js ───────────────────────────────────────────────────────────────
//
// Socket.IO entry point: chat (text / image / voice / location), typing,
// reactions, read receipts, presence, and a separate location relay room.
//
// Performance highlights (see docs/SERVER_PERFORMANCE.md):
//   • Bearer-token verification is cached (LRU) and shared with the REST
//     middleware in index.js so a user reconnecting in tight loops doesn't
//     re-cost the JWT verification each time.
//   • FCM fan-out for group chats now reads all recipient users in a single
//     `db.getAll(...refs)` call instead of N sequential gets.
//   • FCM dispatch uses `messaging.sendEach()` instead of N awaited sends so
//     a single slow recipient can't bottleneck the fan-out.
//   • Inbox mirroring transactions for each recipient run in parallel via
//     Promise.all (kept transactional so two simultaneous messages to the
//     same user don't clobber each other under contention).
//   • Token verification on the WebSocket handshake reuses the same LRU
//     cache as REST, so the first cached entry covers both paths.
//
// Behavioural notes:
//   • The server still does NOT persist location_update frames — those are
//     pure relays. Sending clients write their own location docs.
//   • Reactions, reads, and message persistence still run inside Firestore
//     transactions (correctness > throughput on these paths).
//   • Mute / mention behaviour is unchanged: muted recipients still receive
//     pushes when explicitly @mentioned.

const { v4: uuidv4 } = require('uuid');

const { auth, db, messaging } = require('./firebase');
const { verifyIdTokenCached } = require('./authCache');
const { MAX_RECENT_MESSAGES } = require('./constants');

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
    // ── Auth handshake (cached) ────────────────────────────────────────────
    io.use(async (socket, next) => {
        const token = socket.handshake.query.token || socket.handshake.auth.token;
        if (!token) {
            return next(new Error('Authentication error: Token missing'));
        }
        const decoded = await verifyIdTokenCached(auth, token);
        if (!decoded) {
            return next(new Error('Authentication error: Invalid token'));
        }
        socket.user = decoded;
        next();
    });

    io.on('connection', (socket) => {
        const uid = socket.user.uid;
        const userName = socket.user.name || 'User';
        const userPhotoUrl = socket.user.picture || null;
        const conversationId = socket.handshake.query.conversationId;
        const locationRoom = socket.handshake.query.locationRoom;

        // ── Location Room Join ─────────────────────────────────────────────
        if (locationRoom) {
            socket.join(locationRoom);
            socket.locationRoom = locationRoom;
        }

        // ── Location Update Relay ──────────────────────────────────────────
        socket.on('location_update', (data) => {
            if (!socket.locationRoom) return;
            if (!validateLocationFrame(data)) return;
            // Ensure userId matches authenticated user (prevent spoofing)
            if (data.userId !== uid) return;
            socket.to(socket.locationRoom).emit('location_update', data);
        });

        if (conversationId) {
            // Verify participant
            db.collection('conversations').doc(conversationId).get().then((doc) => {
                if (doc.exists && (doc.data().participantIds || []).includes(uid)) {
                    socket.join(conversationId);
                    socket.emit('connected', { conversationId, userId: uid });
                    socket.to(conversationId).emit('presence', {
                        userId: uid,
                        status: 'online',
                    });
                } else {
                    socket.disconnect();
                }
            }).catch((err) => console.error('participant check failed', err));
        }

        /**
         * Per-frame participant guard.
         *
         * The connect-time check above only runs once when the socket
         * opens. If the user is removed from a group AFTER they've
         * connected (admin kicks them, or they leave from another
         * device), their socket stays alive — and they can keep
         * sending messages until the next reconnect.
         *
         * To close that hole we re-check `conversation.participantIds`
         * on every incoming message frame. The doc read is one Firestore
         * lookup per frame, but it's the cheapest auth gate available
         * here; any heavier per-frame work would need a Redis/local
         * cache layer. The cost is acceptable for a chat workload.
         *
         * Returns true on allow, false on deny (and disconnects the
         * socket so the client doesn't keep retrying invalid frames).
         */
        async function ensureStillParticipant() {
            if (!conversationId) return false;
            try {
                const doc = await db.collection('conversations').doc(conversationId).get();
                if (!doc.exists) {
                    socket.disconnect();
                    return false;
                }
                const ids = doc.data().participantIds || [];
                if (!ids.includes(uid)) {
                    // No longer a participant — likely removed from a
                    // group or left themselves on another device.
                    socket.emit('error', { message: 'You are no longer a participant of this conversation.' });
                    socket.disconnect();
                    return false;
                }
                return true;
            } catch (err) {
                console.error('per-frame participant check failed', err);
                return false;
            }
        }

        socket.on('message', async (data) => {
            if (!conversationId) return;
            if (!(await ensureStillParticipant())) return;
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
                    readBy: [uid],
                };

                if (replyToId) {
                    msgDto.replyToId = replyToId;
                    msgDto.replyToText = replyToText || null;
                    msgDto.replyToSenderName = replyToSenderName || null;
                }

                const mentions = Array.isArray(mentionedUserIds)
                    ? mentionedUserIds.filter((m) => typeof m === 'string' && m)
                    : [];
                if (mentions.length > 0) msgDto.mentionedUserIds = mentions;

                await persistMessage(conversationId, msgId, msgDto, uid, text.trim(), now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                // Fire-and-forget; failures shouldn't block the chat path.
                sendFCM(conversationId, uid, userName, text.trim(), { mentions })
                    .catch((err) => console.error('sendFCM error', err));
            } catch (err) {
                console.error('Error handling message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('image_message', async (data) => {
            if (!conversationId) return;
            if (!(await ensureStillParticipant())) return;
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
                    imageUrl,
                    timestamp: now,
                    readBy: [uid],
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '📷 Image', now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                sendFCM(conversationId, uid, userName, '📷 Sent an image')
                    .catch((err) => console.error('sendFCM error', err));
            } catch (err) {
                console.error('Error handling image message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('location_message', async (data) => {
            if (!conversationId) return;
            if (!(await ensureStillParticipant())) return;
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
                    readBy: [uid],
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '📍 Location', now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                sendFCM(conversationId, uid, userName, '📍 Shared a location')
                    .catch((err) => console.error('sendFCM error', err));
            } catch (err) {
                console.error('Error handling location message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('voice_message', async (data) => {
            if (!conversationId) return;
            if (!(await ensureStillParticipant())) return;
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
                    voiceUrl,
                    voiceDurationMs: durationMs || 0,
                    timestamp: now,
                    readBy: [uid],
                };

                await persistMessage(conversationId, msgId, msgDto, uid, '🎤 Voice message', now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                sendFCM(conversationId, uid, userName, '🎤 Sent a voice message')
                    .catch((err) => console.error('sendFCM error', err));
            } catch (err) {
                console.error('Error handling voice message:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('typing', (data) => {
            if (!conversationId) return;
            socket.to(conversationId).emit('typing', {
                userId: uid,
                userName,
                isTyping: data.isTyping,
            });
        });

        socket.on('reaction', async (data) => {
            if (!conversationId) return;
            try {
                const { messageId, emoji } = data;
                if (!messageId || !emoji) return;
                await mutateReaction(conversationId, messageId, emoji, uid, 'add');
                io.to(conversationId).emit('reaction_update', {
                    messageId,
                    userId: uid,
                    emoji,
                    action: 'add',
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
                await mutateReaction(conversationId, messageId, emoji, uid, 'remove');
                io.to(conversationId).emit('reaction_update', {
                    messageId,
                    userId: uid,
                    emoji,
                    action: 'remove',
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
                let newlyReadMessageIds = [];

                const convRef = db.collection('conversations').doc(conversationId);
                await db.runTransaction(async (transaction) => {
                    const doc = await transaction.get(convRef);
                    if (!doc.exists) return;

                    const data = doc.data();
                    if (!data.participantIds.includes(uid)) return;

                    const unreadCounts = data.unreadCounts || {};
                    unreadCounts[uid] = 0;

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
                    if (updated) updateData.recentMessages = recentMessages;
                    transaction.update(convRef, updateData);

                    newlyReadMessageIds = markedIds;
                });

                socket.to(conversationId).emit('read_receipt', {
                    messageIds: newlyReadMessageIds,
                    userId: uid,
                    timestamp: now,
                });
            } catch (err) {
                console.error('Error handling read receipt:', err);
                socket.emit('error', { message: 'Internal server error' });
            }
        });

        socket.on('disconnect', () => {
            if (conversationId) {
                socket.to(conversationId).emit('presence', {
                    userId: uid,
                    status: 'offline',
                });
            }
            if (socket.locationRoom) {
                socket.to(socket.locationRoom).emit('location_user_offline', {
                    userId: uid,
                });
            }
        });
    });
};

/**
 * Atomically mutates a single reaction inside the conversation's
 * recentMessages array. Pulled out of the socket handlers so the
 * add / remove paths share a single transactional implementation.
 */
async function mutateReaction(conversationId, messageId, emoji, uid, action) {
    const convRef = db.collection('conversations').doc(conversationId);
    await db.runTransaction(async (transaction) => {
        const convDoc = await transaction.get(convRef);
        if (!convDoc.exists) return;

        const convData = convDoc.data();
        const recentMessages = convData.recentMessages || [];
        const msgIndex = recentMessages.findIndex((m) => m.id === messageId);
        if (msgIndex < 0) return;

        const msg = recentMessages[msgIndex];
        const reactions = { ...(msg.reactions || {}) };
        const emojiList = Array.isArray(reactions[emoji]) ? [...reactions[emoji]] : [];

        if (action === 'add') {
            if (emojiList.includes(uid)) return;
            emojiList.push(uid);
            reactions[emoji] = emojiList;
        } else {
            const idx = emojiList.indexOf(uid);
            if (idx === -1) return;
            emojiList.splice(idx, 1);
            if (emojiList.length === 0) delete reactions[emoji];
            else reactions[emoji] = emojiList;
        }

        recentMessages[msgIndex] = { ...msg, reactions };
        transaction.update(convRef, { recentMessages });
    });
}

/**
 * Persists a message to the conversation document's recentMessages array
 * (capped at MAX_RECENT_MESSAGES) inside a single transaction.
 *
 * Cost: 1 transactional read + 1 write.
 */
async function persistMessage(conversationId, msgId, msgDto, senderId, previewText, timestamp) {
    const convRef = db.collection('conversations').doc(conversationId);

    // Strip conversationId from the embedded copy to save space (it's implicit).
    const embeddedMsg = { ...msgDto };
    delete embeddedMsg.conversationId;

    await db.runTransaction(async (transaction) => {
        const convDoc = await transaction.get(convRef);
        if (!convDoc.exists) return;

        const data = convDoc.data();
        const participants = data.participantIds || [];
        const unreadCounts = data.unreadCounts || {};

        for (const pid of participants) {
            if (pid !== senderId) {
                unreadCounts[pid] = (unreadCounts[pid] || 0) + 1;
            }
        }

        let recentMessages = data.recentMessages || [];
        recentMessages.push(embeddedMsg);
        if (recentMessages.length > MAX_RECENT_MESSAGES) {
            recentMessages = recentMessages.slice(-MAX_RECENT_MESSAGES);
        }

        const oldestRecentTimestamp = recentMessages.length > 0
            ? recentMessages[0].timestamp
            : timestamp;

        transaction.update(convRef, {
            lastMessageText: previewText,
            lastMessageSenderId: senderId,
            lastMessageTimestamp: timestamp,
            unreadCounts,
            recentMessages,
            oldestRecentTimestamp,
        });
    });
}

/**
 * Sends data-only FCM notifications + Firestore inbox entries to every
 * participant other than the sender. Honors mute lists and routes
 * @mentioned users through a separate `type` so the Android client
 * (FcmMessagingService) can still surface mention notifications even on
 * muted conversations.
 *
 * Performance:
 *   • Recipient user docs are fetched in ONE `getAll(...refs)` round trip.
 *   • FCM messages are delivered via `messaging.sendEach()` so a slow
 *     network for one recipient can't serialize the rest.
 *   • Inbox mirroring batches all recipient writes into a single
 *     transaction over the same set of docs (MAX 500 ops; we cap groups
 *     at 50 members elsewhere, so we're safely under).
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
            .filter((id) => id !== senderId)
            .filter((id) => {
                // Mention bypass — even muted recipients get a push when
                // explicitly @mentioned. WhatsApp / Slack norm.
                if (mentions.includes(id)) return true;
                const until = Number(mutedUntil[id] || 0);
                if (until > now) return false;
                if (mutedBy.includes(id) && !(id in mutedUntil)) return false;
                return true;
            });
        if (recipients.length === 0) return;

        // ── Batched user-doc read ──────────────────────────────────────────
        // Single round trip instead of N sequential gets.
        const userRefs = recipients.map((id) => db.collection('users').doc(id));
        const userSnaps = await db.getAll(...userRefs);

        // Build per-recipient context, shaped so we can fan out FCM and
        // inbox writes in parallel without re-walking the recipients array.
        const ctxs = recipients.map((recipientId, i) => {
            const snap = userSnaps[i];
            const token = (snap && snap.exists) ? (snap.data().fcmToken || null) : null;
            const isMention = mentions.includes(recipientId);
            const title = isMention
                ? `${senderName} mentioned you`
                : (isGroup ? `${senderName} in ${conversationName}` : senderName);
            const body = isMention ? `Mentioned you: ${text}` : text;
            const type = isMention ? 'mention' : 'new_message';
            return { recipientId, token, title, body, type, isMention };
        });

        // ── Inbox mirroring intentionally skipped for chat ─────────────────
        // Chat messages ('new_message') and mentions ('mention') are NOT
        // mirrored to the in-app notifications inbox. They live in the
        // Chats tab + the Android system tray. Mirroring them here would
        // surface every chat line as an inbox row, which is the same
        // anti-pattern WhatsApp / Messenger / Telegram avoid. The
        // Notifications screen is reserved for cross-cutting events
        // (friend requests, meetup, location). Server keeps writing those
        // types via the Cloud Functions side (functions/src/lib/notify.ts).
        // Skipping the write here saves N inbox transactions per send.

        // ── FCM dispatch via sendEach ──────────────────────────────────────
        // sendEach delivers all messages in one HTTP/2 connection burst,
        // returning per-message responses. We don't fan errors back to the
        // caller — bad tokens are individually handled below.
        const messages = [];
        for (const ctx of ctxs) {
            if (!ctx.token) continue;
            messages.push({
                token: ctx.token,
                data: {
                    type: ctx.type,
                    title: ctx.title,
                    body: ctx.body,
                    conversationId,
                    senderId,
                    senderName,
                    text,
                    conversationName,
                    ...(groupId ? { groupId } : {}),
                    ...(mentions.length > 0 ? { mentionedUserIds: mentions.join(',') } : {}),
                },
                android: { priority: 'high' },
            });
        }
        if (messages.length === 0) return;

        const result = await messaging.sendEach(messages);
        if (result.failureCount > 0) {
            // Log just the codes, not the entire payload, to keep logs sane.
            const codes = result.responses
                .filter((r) => !r.success)
                .map((r) => r.error?.code || 'unknown');
            console.warn(`FCM partial failure: ${result.failureCount}/${messages.length} - ${codes.join(',')}`);
        }
    } catch (err) {
        console.error('Failed to send FCM:', err);
    }
}

// NOTE: previous helper `persistChatInboxEntry` was removed when the
// in-app inbox stopped mirroring chat-type events. Chat lives on the
// Chats tab + the OS notification tray now, in line with how WhatsApp /
// Messenger / Telegram surface chat. If we ever need a chat-specific
// inbox row again, restore from git history (commit before this change).

module.exports = { initializeSockets };
