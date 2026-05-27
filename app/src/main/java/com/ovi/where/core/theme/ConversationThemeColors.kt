package com.ovi.where.core.theme

/**
 * Canonical palette of conversation theme colors.
 *
 * **Why this exists:** the same hex strings used to live in three places
 * (`ConversationInfoScreen.kt`, `GroupInfoScreen.kt`, repository defaults +
 * server defaults). Drift between those copies caused subtle UX bugs.
 * This object is now the single source of truth for both Android pickers
 * and the repository default.
 *
 * The palette deliberately starts with the brand magenta so every new
 * conversation opens on-message; the rest of the palette spans the full
 * brand sunset gradient (purple → pink → orange) plus a few neutral
 * accents so users have meaningful choice without leaving the brand.
 *
 * The server (`server/src/routes/conversations.js`) still inlines the
 * default hex separately — it can't import Kotlin — but that's the only
 * cross-runtime duplicate and it's documented there.
 */
object ConversationThemeColors {

    /**
     * Ordered list of `(hex, displayName)` pairs. Picker UIs iterate this
     * directly. The first entry is the default — keep [DEFAULT] in sync if
     * you reorder.
     *
     * Naming follows the brand's gradient language: indigo violet at the
     * top, drifting through violet / magenta / coral / orange (the full
     * where_logo_v2 sunburst) and finishing with two cool earth tones for
     * users who want to mute the chat chrome.
     */
    val OPTIONS: List<Pair<String, String>> = listOf(
        "#6E60CF" to "Indigo",    // brand primary — default
        "#5B538D" to "Slate",     // brand secondary
        "#A53D00" to "Sunset",    // brand tertiary
        "#C2298A" to "Magenta",   // mid-gradient accent
        "#FF610B" to "Tangerine", // bottom-of-gradient accent
        "#1976D2" to "Azure",
        "#006E2C" to "Forest",
        "#006878" to "Teal",
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
     * Mirrors `DEFAULT_THEME_COLOR` in `server/src/constants.js` —
     * change in lockstep if the brand palette shifts.
     */
    const val DEFAULT: String = "#6E60CF"

    /**
     * Default emoji shortcut used as the per-conversation quick reaction
     * when the user hasn't customized one. Matches the server-side default
     * in `server/src/constants.js`.
     */
    const val DEFAULT_EMOJI_SHORTCUT: String = "👍"
}
