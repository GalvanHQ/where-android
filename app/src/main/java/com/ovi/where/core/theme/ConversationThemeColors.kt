package com.ovi.where.core.theme

/**
 * Canonical palette of conversation theme colors.
 *
 * **Why this exists:** the same eight hex strings used to live in three
 * places:
 *   • `ConversationInfoScreen.kt` (with display names),
 *   • `GroupInfoScreen.kt` (without names),
 *   • `ConversationRepositoryImpl` companion + `server/src/routes/conversations.js`
 *     (just the default).
 *
 * Drift between those copies turned into UX bugs: one screen offered an
 * "Indigo" option, the other rendered `#5170FF` with no label, and the
 * server defaulted to whichever value you happened to read first. This
 * object is now the single source of truth for both Android pickers and
 * the repository default.
 *
 * The server (`server/src/routes/conversations.js`) still inlines the
 * default hex separately — it can't import Kotlin — but that's the only
 * cross-runtime duplicate and it's documented there.
 *
 * Each entry pairs a hex token with a short display name so screens that
 * surface the choice can show "Indigo" instead of `#5170FF`.
 */
object ConversationThemeColors {

    /**
     * Ordered list of `(hex, displayName)` pairs. Picker UIs iterate this
     * directly. The first entry is the default — keep [DEFAULT] in sync if
     * you reorder.
     */
    val OPTIONS: List<Pair<String, String>> = listOf(
        "#5170FF" to "Indigo",
        "#006878" to "Teal",
        "#8E3A8C" to "Purple",
        "#6B5E00" to "Mustard",
        "#006E2C" to "Forest",
        "#BA1A1A" to "Crimson",
        "#006491" to "Ocean",
        "#8B4513" to "Sienna",
    )

    /**
     * Just the hex strings, in the same order as [OPTIONS]. Provided for
     * legacy callers (e.g. the group info picker) that didn't carry names.
     * New code should prefer [OPTIONS] so the label travels with the value.
     */
    val HEX_VALUES: List<String> = OPTIONS.map { it.first }

    /**
     * Default theme color applied at conversation creation and used as a
     * fallback when reading legacy rows that have `themeColor: null`.
     * Mirrors `DEFAULT_THEME_COLOR` in `server/src/routes/conversations.js`
     * — change in lockstep if the brand palette shifts.
     */
    const val DEFAULT: String = "#5170FF"

    /**
     * Default emoji shortcut used as the per-conversation quick reaction
     * when the user hasn't customized one. Matches the server-side default
     * in `server/src/routes/conversations.js`.
     */
    const val DEFAULT_EMOJI_SHORTCUT: String = "👍"
}
