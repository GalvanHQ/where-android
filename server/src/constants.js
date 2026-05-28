// ── server/src/constants.js ─────────────────────────────────────────────────
//
// Server-side constants that were previously inlined or duplicated across
// `socket.js` and `routes/conversations.js`. Pull them in from here so a
// limit change happens in one place.
//
// These values must stay in sync with their TypeScript twins in
// `functions/src/lib/notify.ts` (Cloud Functions can't share JS modules
// with the Node socket server). Whenever you change a value here, mirror
// it there and bump both deploys together.

/** Max number of recent messages embedded in a conversation document. */
const MAX_RECENT_MESSAGES = 50;

/** Max number of inbox entries per recipient (FIFO eviction). */
const MAX_INBOX_ENTRIES = 200;

/** Default branding emitted at conversation creation. Mirrors the Android
 *  `ConversationThemeColors.DEFAULT` / `DEFAULT_EMOJI_SHORTCUT`. */
const DEFAULT_THEME_COLOR = '#F9DF4D';
const DEFAULT_EMOJI_SHORTCUT = '👍';

module.exports = {
    MAX_RECENT_MESSAGES,
    MAX_INBOX_ENTRIES,
    DEFAULT_THEME_COLOR,
    DEFAULT_EMOJI_SHORTCUT,
};
