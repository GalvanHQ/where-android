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
 * The palette deliberately starts with the brand yellow so every new
 * conversation opens on-message; the rest of the palette mirrors the
 * accent vocabulary used by [AvatarColors] (gold, teal, azure, magenta,
 * forest, violet, sienna) plus a couple of warm sunset stops drawn from
 * [BrandGradient] so users have meaningful choice without leaving the
 * brand system.
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
     * Hexes are picked so the chat chrome (send button, your bubble,
     * accent strokes) reads with enough contrast against both light and
     * dark surfaces. Pure brand yellow leads because that's the colour
     * users associate with the app; the rest of the row gives them a
     * curated set of accents from the same palette.
     */
    val OPTIONS: List<Pair<String, String>> = listOf(
        "#F9DF4D" to "Canary",    // brand yellow — default
        "#BD9300" to "Gold",      // deep gold (Primary60)
        "#DD6500" to "Tangerine", // BrandGradient warm orange
        "#006B69" to "Teal",      // brand tertiary
        "#2D6CDF" to "Azure",
        "#C4407A" to "Magenta",
        "#007A38" to "Forest",
        "#6B5BB8" to "Violet",
        "#B23B23" to "Sienna",
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
    const val DEFAULT: String = "#F9DF4D"

    /**
     * Default emoji shortcut used as the per-conversation quick reaction
     * when the user hasn't customized one. Matches the server-side default
     * in `server/src/constants.js`.
     */
    const val DEFAULT_EMOJI_SHORTCUT: String = "👍"
}
