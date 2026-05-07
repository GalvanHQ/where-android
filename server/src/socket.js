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
                
                // Update last message in conversation
                await db.collection('conversations').doc(conversationId).update({
                    lastMessageText: text.trim(),
                    lastMessageSenderId: uid,
                    lastMessageTimestamp: now
                });

                // Broadcast
                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, msgId, timestamp: now });

                // Send FCM push notifications to other participants
                sendFCM(conversationId, uid, userName, text.trim());
            } catch (err) {
                console.error('Error handling message:', err);
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
                
                await db.collection('conversations').doc(conversationId).update({
                    lastMessageText: '📍 Location',
                    lastMessageSenderId: uid,
                    lastMessageTimestamp: now
                });

                io.to(conversationId).emit('message', msgDto);
                socket.emit('ack', { tempId, msgId, timestamp: now });

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

        socket.on('read', async () => {
            if (!conversationId) return;
            // Handle read receipts
        });

        socket.on('disconnect', () => {
            // cleanup
        });
    });
};

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
