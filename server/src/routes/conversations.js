const express = require('express');
const { db } = require('../firebase');

const router = express.Router();

// Middleware to verify Firebase Auth Token
const requireAuth = (req, res, next) => {
    // We expect the auth user to be attached by a global middleware, 
    // or we verify the token here.
    if (!req.user) {
        return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
};

router.use(requireAuth);

// GET /api/conversations — List all conversations for the authenticated user
router.get('/', async (req, res) => {
    try {
        const uid = req.user.uid;

        const snapshot = await db.collection('conversations')
            .where('participantIds', 'array-contains', uid)
            .orderBy('lastMessageTimestamp', 'desc')
            .get();

        const conversations = snapshot.docs.map(doc => {
            const data = doc.data();
            const unreadCounts = data.unreadCounts || {};
            return {
                id: doc.id,
                type: data.type || 'direct',
                participantIds: data.participantIds || [],
                groupId: data.groupId || null,
                name: data.name || '',
                photoUrl: data.photoUrl || null,
                lastMessageText: data.lastMessageText || '',
                lastMessageSenderId: data.lastMessageSenderId || '',
                lastMessageTimestamp: data.lastMessageTimestamp || 0,
                unreadCount: unreadCounts[uid] || 0,
                createdAt: data.createdAt || 0
            };
        });

        res.json(conversations);
    } catch (error) {
        console.error('Error fetching conversations:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// GET /api/conversations/unread-counts — Get unread counts for all user's conversations
router.get('/unread-counts', async (req, res) => {
    try {
        const uid = req.user.uid;

        const snapshot = await db.collection('conversations')
            .where('participantIds', 'array-contains', uid)
            .get();

        const unreadCounts = snapshot.docs.map(doc => {
            const data = doc.data();
            const counts = data.unreadCounts || {};
            return {
                conversationId: doc.id,
                unreadCount: counts[uid] || 0
            };
        });

        res.json(unreadCounts);
    } catch (error) {
        console.error('Error fetching unread counts:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// GET /api/conversations/:conversationId/messages
router.get('/:conversationId/messages', async (req, res) => {
    try {
        const { conversationId } = req.params;
        const uid = req.user.uid;

        // Verify participant
        const convDoc = await db.collection('conversations').doc(conversationId).get();
        if (!convDoc.exists) {
            return res.status(404).json({ error: 'Conversation not found' });
        }
        if (!convDoc.data().participantIds.includes(uid)) {
            return res.status(403).json({ error: 'Not a participant' });
        }

        const { before, after, limit } = req.query;
        const messageLimit = parseInt(limit) || 30;

        // If 'after' param is provided, return messages after that timestamp (for reconnection catch-up)
        if (after) {
            const afterTimestamp = parseInt(after);
            const snapshot = await db.collection('messages')
                .where('conversationId', '==', conversationId)
                .where('timestamp', '>', afterTimestamp)
                .orderBy('timestamp', 'asc')
                .limit(messageLimit)
                .get();

            const messages = snapshot.docs.map(doc => ({
                id: doc.id,
                ...doc.data()
            }));

            return res.json(messages);
        }

        // If 'before' param is provided, return paginated response
        if (before) {
            const beforeTimestamp = parseInt(before);
            const snapshot = await db.collection('messages')
                .where('conversationId', '==', conversationId)
                .where('timestamp', '<', beforeTimestamp)
                .orderBy('timestamp', 'desc')
                .limit(messageLimit + 1)
                .get();

            const docs = snapshot.docs;
            const hasMore = docs.length > messageLimit;
            const resultDocs = hasMore ? docs.slice(0, messageLimit) : docs;

            const messages = resultDocs.map(doc => ({
                id: doc.id,
                ...doc.data()
            }));

            // Reverse to return in ascending order
            messages.reverse();

            const nextCursor = hasMore && messages.length > 0
                ? String(messages[0].timestamp)
                : null;

            return res.json({
                messages,
                nextCursor,
                hasMore
            });
        }

        // Default: if limit param is explicitly provided (without before), return paginated from latest
        if (req.query.limit) {
            const snapshot = await db.collection('messages')
                .where('conversationId', '==', conversationId)
                .orderBy('timestamp', 'desc')
                .limit(messageLimit + 1)
                .get();

            const docs = snapshot.docs;
            const hasMore = docs.length > messageLimit;
            const resultDocs = hasMore ? docs.slice(0, messageLimit) : docs;

            const messages = resultDocs.map(doc => ({
                id: doc.id,
                ...doc.data()
            }));

            // Reverse to return in ascending order
            messages.reverse();

            const nextCursor = hasMore && messages.length > 0
                ? String(messages[0].timestamp)
                : null;

            return res.json({
                messages,
                nextCursor,
                hasMore
            });
        }

        // Backward compatible: no params, return all messages as flat array
        const snapshot = await db.collection('messages')
            .where('conversationId', '==', conversationId)
            .orderBy('timestamp', 'asc')
            .get();

        const messages = snapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
        }));

        res.json(messages);
    } catch (error) {
        console.error('Error fetching messages:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// POST /api/conversations/direct
router.post('/direct', async (req, res) => {
    try {
        const { otherUserId } = req.body;
        const uid = req.user.uid;

        if (!otherUserId) {
            return res.status(400).json({ error: 'otherUserId is required' });
        }

        // Check if conversation already exists
        const snapshot = await db.collection('conversations')
            .where('type', '==', 'direct')
            .where('participantIds', 'array-contains', uid)
            .get();

        let existingConv = null;
        for (const doc of snapshot.docs) {
            const data = doc.data();
            if (data.participantIds.includes(otherUserId) && data.participantIds.length === 2) {
                existingConv = { id: doc.id, ...data };
                break;
            }
        }

        if (existingConv) {
            return res.json(existingConv);
        }

        // Create new direct conversation
        const newConvRef = db.collection('conversations').doc();
        const newConv = {
            type: 'direct',
            participantIds: [uid, otherUserId],
            createdAt: Date.now(),
            lastMessageTimestamp: Date.now()
        };

        await newConvRef.set(newConv);
        res.json({ id: newConvRef.id, ...newConv });

    } catch (error) {
        console.error('Error creating direct conversation:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// POST /api/conversations/group
router.post('/group', async (req, res) => {
    try {
        const { groupId, name, memberIds } = req.body;
        const uid = req.user.uid;

        if (!name || !memberIds) {
            return res.status(400).json({ error: 'name and memberIds are required' });
        }

        const participants = Array.from(new Set([uid, ...memberIds]));

        const newConvRef = db.collection('conversations').doc();
        const newConv = {
            type: 'group',
            groupId: groupId || null,
            name: name,
            participantIds: participants,
            createdAt: Date.now(),
            lastMessageTimestamp: Date.now()
        };

        await newConvRef.set(newConv);
        res.json({ id: newConvRef.id, ...newConv });

    } catch (error) {
        console.error('Error creating group conversation:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// PATCH /api/conversations/:conversationId/read
router.patch('/:conversationId/read', async (req, res) => {
    try {
        const { conversationId } = req.params;
        const uid = req.user.uid;

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

        res.json({ success: true });
    } catch (error) {
        console.error('Error marking as read:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
