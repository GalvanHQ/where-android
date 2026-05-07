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
