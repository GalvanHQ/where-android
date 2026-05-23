// ── authCache.js ────────────────────────────────────────────────────────────
//
// Shared LRU cache for verified Firebase ID tokens.
//
// Why this exists:
//   Both the REST middleware (index.js) and the Socket.IO handshake
//   (socket.js) call `auth.verifyIdToken(token)` on every request /
//   connection. That call performs a remote validation against Google's
//   public key set the first time it sees a key, and even on warm paths
//   it still costs a few hundred microseconds of crypto + JSON parsing.
//
//   For an app where a single user opens a chat (1 socket) AND fires
//   several REST calls (list conversations, mark read, fetch messages)
//   in the same minute, that's 3-5 wasted verifications per active user.
//
//   We cache the decoded claims, keyed by the *raw* token string, with
//   a TTL bounded by the token's own `exp` claim. If verifyIdToken would
//   have rejected the token (signature / expiry), we never cache it.
//
// Capacity notes:
//   max=10_000 entries × ~600 bytes ≈ 6 MB worst case. Cloud Run instance
//   sees ~80 concurrent connections, so 10k easily covers a herd of users
//   plus their stale-but-valid tokens within the 1h window.
//
// Eviction:
//   • LRU eviction when size exceeds `max`.
//   • TTL eviction at `min(token.exp - 30s, now + maxTtlMs)`.
//   • Manual `bust(token)` on auth failures so a stale negative result
//     doesn't pin a bad token in the cache.

const { LRUCache } = require('lru-cache');

// 1 hour cap — Firebase ID tokens themselves expire after 1h, so anything
// longer would never be reached. The 30s safety margin avoids racing the
// expiry boundary between the cache hit and the downstream Firestore call.
const MAX_TTL_MS = 60 * 60 * 1000;
const SAFETY_MARGIN_MS = 30 * 1000;

const cache = new LRUCache({
    max: 10_000,
    ttl: MAX_TTL_MS,
    ttlAutopurge: false,
    updateAgeOnGet: false,
});

/**
 * Resolves the decoded ID-token claims for a raw bearer string. Returns
 * `null` if the token is missing, malformed, or rejected by Firebase.
 *
 * @param {import('firebase-admin').auth.Auth} auth — Firebase Admin auth handle
 * @param {string} token — raw ID token (no "Bearer " prefix)
 * @returns {Promise<import('firebase-admin').auth.DecodedIdToken | null>}
 */
async function verifyIdTokenCached(auth, token) {
    if (!token || typeof token !== 'string') return null;

    const cached = cache.get(token);
    if (cached) return cached;

    try {
        const decoded = await auth.verifyIdToken(token);
        // exp is in seconds; convert to ms and clamp to MAX_TTL_MS.
        const expMs = (Number(decoded.exp) || 0) * 1000;
        const ttl = Math.max(
            1_000,
            Math.min(MAX_TTL_MS, expMs - Date.now() - SAFETY_MARGIN_MS)
        );
        cache.set(token, decoded, { ttl });
        return decoded;
    } catch (err) {
        // Don't cache failures — a transient network blip on the cert
        // refresh would otherwise lock the user out for an hour.
        return null;
    }
}

/** Manually evict a token. */
function bust(token) {
    if (token) cache.delete(token);
}

module.exports = { verifyIdTokenCached, bust };
