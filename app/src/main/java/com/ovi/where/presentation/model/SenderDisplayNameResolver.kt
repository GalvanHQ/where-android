package com.ovi.where.presentation.model

/**
 * Resolves the display name for a message sender.
 *
 * If the [senderName] is not blank (contains at least one non-whitespace character),
 * it is returned as-is. Otherwise, "Unknown" is returned as a fallback.
 *
 * The result is guaranteed to never be blank.
 *
 * @param senderName The raw sender name from the message model.
 * @return The resolved display name, or "Unknown" if the input is blank/whitespace-only.
 */
fun resolveSenderDisplayName(senderName: String): String {
    return senderName.ifBlank { "Unknown" }
}
