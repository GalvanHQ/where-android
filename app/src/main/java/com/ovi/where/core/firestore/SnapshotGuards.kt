package com.ovi.where.core.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

/**
 * Helpers that decide whether a Firestore snapshot should be acted upon or
 * suppressed so it doesn't blank out a known-good local cache.
 *
 * Why this exists:
 *   Firestore's listener API delivers two flavours of snapshot:
 *     • **Cache snapshots** (`metadata.isFromCache == true`) — fast,
 *       possibly partial, fired before the network round-trips. They are
 *       authoritative for *additions* (the doc is at least this fresh)
 *       but not for *deletions* / *missing fields*.
 *     • **Server snapshots** (`metadata.isFromCache == false`) — confirmed
 *       by the backend; trustworthy in both directions.
 *
 *   Treating cache snapshots as fully authoritative caused several visible
 *   bugs (notification badge dropping to 0 on cold start, top-bar location
 *   chips vanishing during reconnects, meetup card disappearing, etc.).
 *
 *   Every new `addSnapshotListener` call site should run the appropriate
 *   guard from this file before reconciling Room or emitting empty state.
 */

/**
 * Returns `true` when an empty query snapshot should be suppressed because
 * it can't prove anything is gone. Empty cache snapshots are the canonical
 * "I don't know yet" signal — usually a cold start before sync.
 *
 * Use this when you'd otherwise emit `emptyList()` on missing data:
 *
 * ```kotlin
 * .addSnapshotListener { snap, err ->
 *     if (err != null) { close(err); return@addSnapshotListener }
 *     if (snap.shouldSkipEmptyCache()) return@addSnapshotListener
 *     trySend(snap?.toObjects(Foo::class.java) ?: emptyList())
 * }
 * ```
 */
fun QuerySnapshot?.shouldSkipEmptyCache(): Boolean =
    this != null && this.metadata.isFromCache && this.isEmpty

/**
 * Document-level analogue of [shouldSkipEmptyCache]. Returns `true` when
 * the snapshot is a cache miss (`exists() == false`) and came from cache
 * — meaning we don't know whether the doc really doesn't exist or just
 * hasn't synced yet.
 *
 * Use this when emitting `null` (or a missing-field default) would
 * temporarily blank out UI that's keyed on the doc's contents.
 */
fun DocumentSnapshot?.shouldSkipMissingCache(): Boolean =
    this != null && this.metadata.isFromCache && !this.exists()

/**
 * Returns `true` when the snapshot is from cache. Use sparingly — most
 * callers should reach for the empty-aware variants above. This raw
 * accessor exists for the rare case where the listener wants to keep
 * reading data from the cache snapshot but skip a destructive
 * reconcile / delete.
 */
fun QuerySnapshot?.isCacheSnapshot(): Boolean =
    this != null && this.metadata.isFromCache

fun DocumentSnapshot?.isCacheSnapshot(): Boolean =
    this != null && this.metadata.isFromCache
