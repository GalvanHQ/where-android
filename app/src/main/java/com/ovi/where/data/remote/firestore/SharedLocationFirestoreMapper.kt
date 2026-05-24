package com.ovi.where.data.remote.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.ovi.where.domain.model.SharedLocation
import timber.log.Timber

/**
 * Canonical mapper between Firestore document data and the
 * [SharedLocation] domain model.
 *
 * **Why this exists:** Firestore's reflective `toObject<T>()` deserializer
 * silently fails on `Float` fields because Firestore stores all numbers as
 * `Double` / `Long`. The result is a partially-populated object — exactly
 * the kind of bug that's invisible until a user reports "the avatar row
 * never shows" and you spend three days tracing it.
 *
 * Centralising the manual mapper here means:
 *  • one place to fix the Float coercion logic
 *  • one place to add new fields (so they can't be forgotten on read)
 *  • one place to test the schema
 *
 * Producer-side (Cloud Run socket server, Cloud Functions) writes the
 * field shape this mapper expects. Keep them in lockstep — if the server
 * adds a field, the mapper should learn it; if a field is renamed, both
 * sides update together.
 */
object SharedLocationFirestoreMapper {

    /**
     * Parses a Firestore document into a [SharedLocation], returning null
     * on missing data or unrecoverable schema mismatch.
     *
     * Number coercions:
     *  • lat/lng/expiry/timestamp → coerced from `Number` to Double / Long
     *  • accuracy / speed / bearing → coerced from `Number` to Float
     *
     * The legacy single-`targetId` field still drives [SharedLocation.groupId]
     * and feeds [SharedLocation.targetIds] when present so old listener call
     * sites stay backward-compatible.
     */
    fun fromDocument(snapshot: DocumentSnapshot): SharedLocation? {
        val data = snapshot.data ?: return null
        return runCatching { fromMap(snapshot.id, data) }
            .onFailure { Timber.w(it, "SharedLocation parse failed: ${snapshot.id}") }
            .getOrNull()
    }

    /**
     * Variant that takes the (id, data) pair directly. Used in the legacy
     * call sites that pre-parse the doc into a Map. Prefer [fromDocument]
     * for new code.
     */
    fun fromMap(docId: String, data: Map<String, Any?>?): SharedLocation? {
        if (data == null) return null
        return SharedLocation(
            id = docId,
            userId = data["userId"] as? String ?: "",
            // groupId carries the legacy "where the share is targeted"
            // semantics. New code should read targetId / targetIds.
            groupId = data["targetId"] as? String
                ?: data["groupId"] as? String
                ?: "",
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            accuracy = (data["accuracy"] as? Number)?.toFloat() ?: 0f,
            speed = (data["speed"] as? Number)?.toFloat() ?: 0f,
            bearing = (data["bearing"] as? Number)?.toFloat() ?: 0f,
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
            isSharingActive = data["isSharingActive"] as? Boolean ?: false,
            sharingExpiresAt = (data["sharingExpiresAt"] as? Number)?.toLong() ?: 0L,
            targetType = data["targetType"] as? String ?: "group",
            targetId = data["targetId"] as? String ?: "",
            targetIds = (data["targetIds"] as? List<*>)
                ?.filterIsInstance<String>()
                ?: (data["targetId"] as? String)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }
                ?: emptyList(),
            targetExpiries = (data["targetExpiries"] as? Map<*, *>)
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = (v as? Number)?.toLong() ?: return@mapNotNull null
                    key to value
                }
                ?.toMap()
                ?: emptyMap(),
            visibleTo = (data["visibleTo"] as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList(),
            displayName = data["displayName"] as? String ?: "",
            photoUrl = data["photoUrl"] as? String,
            sharingStartedAt = (data["sharingStartedAt"] as? Number)?.toLong() ?: 0L,
        )
    }
}
