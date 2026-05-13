const { auth, db, messaging } = require('./firebase');
const { v4: uuidv4 } = require('uuid');

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
                const { tempId, text } = data;
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

                await db.collection('messages').doc(msgId).set(msgDto);
                
                // Update last message in conversation and increment unread counts
                await updateConversationLastMessage(conversationId, uid, text.trim(), now);

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

                await db.collection('messages').doc(msgId).set(msgDto);

                // Update last message in conversation and increment unread counts
                await updateConversationLastMessage(conversationId, uid, '📷 Image', now);

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

                await db.collection('messages').doc(msgId).set(msgDto);
                
                // Update last message in conversation and increment unread counts
                await updateConversationLastMessage(conversationId, uid, '📍 Location', now);

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, id: msgId, timestamp: now });

                sendFCM(conversationId, uid, userName, '📍 Shared a location');
            } catch (err) {
                console.error('Error handling location message:', err);
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

                const msgRef = db.collection('messages').doc(messageId);
                const msgDoc = await msgRef.get();
                if (!msgDoc.exists) return;

                const msgData = msgDoc.data();
                const reactions = msgData.reactions || {};
                const emojiList = reactions[emoji] || [];

                // Only add if not already reacted with this emoji
                if (!emojiList.includes(uid)) {
                    emojiList.push(uid);
                    reactions[emoji] = emojiList;
                    await msgRef.update({ reactions });
                }

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

                const msgRef = db.collection('messages').doc(messageId);
                const msgDoc = await msgRef.get();
                if (!msgDoc.exists) return;

                const msgData = msgDoc.data();
                const reactions = msgData.reactions || {};
                const emojiList = reactions[emoji] || [];

                // Remove uid from the emoji's reactor list
                const index = emojiList.indexOf(uid);
                if (index !== -1) {
                    emojiList.splice(index, 1);
                    if (emojiList.length === 0) {
                        delete reactions[emoji];
                    } else {
                        reactions[emoji] = emojiList;
                    }
                    await msgRef.update({ reactions });
                }

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
 * Updates the conversation's last message fields and increments unread counts
 * for all participants except the sender.
 */
async function updateConversationLastMessage(conversationId, senderId, text, timestamp) {
    const convRef = db.collection('conversations').doc(conversationId);

    await db.runTransaction(async (transaction) => {
        const doc = await transaction.get(convRef);
        if (!doc.exists) return;

        const data = doc.data();
        const participants = data.participantIds || [];
        const unreadCounts = data.unreadCounts || {};

        // Increment unread count for all participants except sender
        for (const participantId of participants) {
            if (participantId !== senderId) {
                unreadCounts[participantId] = (unreadCounts[participantId] || 0) + 1;
            }
        }

        transaction.update(convRef, {
            lastMessageText: text,
            lastMessageSenderId: senderId,
            lastMessageTimestamp: timestamp,
            unreadCounts
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
