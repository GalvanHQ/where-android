const { auth, db, messaging, admin } = require('./firebase');
const { v4: uuidv4 } = require('uuid');

/** Max number of recent messages embedded inside a conversation doc. */
const MAX_RECENT_MESSAGES = 50;

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
        const conversationId = socket.handshake.query.conversationId;

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
                const { tempId, text, replyToId, replyToText, replyToSenderName } = data;
                if (!text || text.trim() === '') return;

                const msgId = uuidv4();
                const now = Date.now();

                const msgDto = {
                    id: msgId,
                    conversationId,
                    senderId: uid,
                    senderName: userName,
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

                await persistMessage(conversationId, msgId, msgDto, uid, text.trim(), now);

                // Broadcast
                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                // Send FCM push notifications to other participants
                sendFCM(conversationId, uid, userName, text.trim());
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

                // Reset unread count for this user in the conversation
                const convRef = db.collection('conversations').doc(conversationId);
                await db.runTransaction(async (transaction) => {
                    const doc = await transaction.get(convRef);
                    if (!doc.exists) return;

                    const data = doc.data();
                    if (!data.participantIds.includes(uid)) return;

                    const unreadCounts = data.unreadCounts || {};
                    unreadCounts[uid] = 0;
                    transaction.update(convRef, { unreadCounts });
                });

                // Broadcast read receipt to room
                socket.to(conversationId).emit('read_receipt', {
                    messageId: 'latest',
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

async function sendFCM(conversationId, senderId, senderName, text) {
    try {
        const convDoc = await db.collection('conversations').doc(conversationId).get();
        if (!convDoc.exists) return;
        
        const participants = convDoc.data().participantIds || [];
        const recipients = participants.filter(id => id !== senderId);

        if (recipients.length === 0) return;

        // Fetch FCM tokens for recipients from 'users' collection
        const tokens = [];
        for (const recipientId of recipients) {
            const userDoc = await db.collection('users').doc(recipientId).get();
            if (userDoc.exists && userDoc.data().fcmToken) {
                tokens.push(userDoc.data().fcmToken);
            }
        }

        if (tokens.length > 0) {
            const payload = {
                notification: {
                    title: senderName,
                    body: text
                },
                data: {
                    conversationId: conversationId
                },
                tokens: tokens
            };
            await messaging.sendEachForMulticast(payload);
        }
    } catch (err) {
        console.error('Failed to send FCM:', err);
    }
}

module.exports = { initializeSockets };
