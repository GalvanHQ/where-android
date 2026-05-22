package com.ovi.where.core.config

/**
 * Remote Config key constants for feature flags.
 */
object FeatureFlags {
    const val KEY_USE_NEW_FRIENDSHIP_MODEL = "useNewFriendshipModel"

    /**
     * Whether to render system messages (group renames, member adds, etc.) as
     * the dedicated centered grey info line. When `false`, SYSTEM messages
     * fall through to the generic chat bubble using `Message.text` as the
     * body — useful as a remote kill switch if the renderer ships a bug.
     *
     * See `.kiro/specs/group-system-messages/`.
     */
    const val KEY_SHOW_SYSTEM_MESSAGES = "showSystemMessages"
    const val DEFAULT_SHOW_SYSTEM_MESSAGES = true
}
