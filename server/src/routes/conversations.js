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

        const conversations = snapshot.docs
            .filter(doc => {
                // Filter out conversations soft-deleted by this user
                const deletedBy = doc.data().deletedBy || [];
                return !deletedBy.includes(uid);
            })
            .filter(doc => {
                // Filter out conversations archived by this user
                const archivedBy = doc.data().archivedBy || [];
                return !archivedBy.includes(uid);
            })
            .map(doc => {
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
// NOTE: This is now redundant since unread counts are embedded in GET /api/conversations.
// Kept for backward compatibility.
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
// Optimized: Returns embedded recentMessages from conversation doc (1 read)
// for initial load. Falls back to archive collection for pagination.
router.get('/:conversationId/messages', async (req, res) => {
    try {
        const { conversationId } = req.params;
        const uid = req.user.uid;

        // Always need the conversation doc for participant verification
        const convDoc = await db.collection('conversations').doc(conversationId).get();
        if (!convDoc.exists) {
            return res.status(404).json({ error: 'Conversation not found' });
        }
        const convData = convDoc.data();
        if (!convData.participantIds.includes(uid)) {
            return res.status(403).json({ error: 'Not a participant' });
        }

        const { before, after, limit } = req.query;
        const messageLimit = parseInt(limit) || 30;

        // ── Catch-up: messages after a timestamp (reconnection) ──
        if (after) {
            const afterTimestamp = parseInt(after);
            // First check if embedded messages cover this range
            const recentMessages = convData.recentMessages || [];
            const catchUpFromRecent = recentMessages
                .filter(m => m.timestamp > afterTimestamp)
                .map(m => ({ ...m, conversationId })); // Re-add conversationId

            if (catchUpFromRecent.length > 0 && catchUpFromRecent.length < messageLimit) {
                // Embedded messages cover the range — 0 extra reads!
                return res.json(catchUpFromRecent);
            }

            // Fall back to archive query
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

        // ── Pagination: messages before a cursor ──
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

        // ── Initial load: use embedded recentMessages (0 extra reads!) ──
        if (req.query.limit) {
            const recentMessages = convData.recentMessages || [];

            if (recentMessages.length > 0) {
                // Re-add conversationId (stripped during embedding to save space)
                const messages = recentMessages.map(m => ({
                    ...m,
                    conversationId
                }));

                // Already sorted by timestamp asc (appended in order)
                // Take the last `messageLimit` messages
                const sliced = messages.length > messageLimit
                    ? messages.slice(-messageLimit)
                    : messages;

                // Determine if there's more history in the archive
                const oldestRecentTs = convData.oldestRecentTimestamp || 0;
                const hasMore = recentMessages.length >= messageLimit;

                const nextCursor = hasMore && sliced.length > 0
                    ? String(sliced[0].timestamp)
                    : null;

                return res.json({
                    messages: sliced,
                    nextCursor,
                    hasMore
                });
            }

            // Fallback: no embedded messages yet, query archive
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

        // ── Legacy: no params, return embedded recentMessages or all from archive ──
        const recentMessages = convData.recentMessages || [];
        if (recentMessages.length > 0) {
            const messages = recentMessages.map(m => ({
                ...m,
                conversationId
            }));
            return res.json(messages);
        }

        // Fallback to archive
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

        // Create new direct conversation with empty recentMessages
        const newConvRef = db.collection('conversations').doc();
        const newConv = {
            type: 'direct',
            participantIds: [uid, otherUserId],
            createdAt: Date.now(),
            lastMessageTimestamp: Date.now(),
            recentMessages: [],
            oldestRecentTimestamp: 0
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
            lastMessageTimestamp: Date.now(),
            recentMessages: [],
            oldestRecentTimestamp: 0
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

/**
 * POST /api/conversations/migrate-recent
 * One-time migration: backfills recentMessages for all conversations
 * belonging to the authenticated user. Run once after deploying the
 * single-doc optimization. Safe to re-run (idempotent).
 */
router.post('/migrate-recent', async (req, res) => {
    try {
        const uid = req.user.uid;

        const convSnapshot = await db.collection('conversations')
            .where('participantIds', 'array-contains', uid)
            .get();

        let migrated = 0;
        for (const convDoc of convSnapshot.docs) {
            const data = convDoc.data();
            // Skip if already migrated
            if (data.recentMessages && data.recentMessages.length > 0) continue;

            // Fetch last 50 messages from archive
            const msgSnapshot = await db.collection('messages')
                .where('conversationId', '==', convDoc.id)
                .orderBy('timestamp', 'desc')
                .limit(50)
                .get();

            if (msgSnapshot.empty) continue;

            const recentMessages = msgSnapshot.docs.map(doc => {
                const m = { id: doc.id, ...doc.data() };
                delete m.conversationId; // Strip to save space
                return m;
            }).reverse(); // Oldest first

            const oldestRecentTimestamp = recentMessages[0].timestamp;

            await convDoc.ref.update({
                recentMessages,
                oldestRecentTimestamp
            });
            migrated++;
        }

        res.json({ success: true, migratedConversations: migrated });
    } catch (error) {
        console.error('Error migrating recent messages:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
