package com.ovi.where.core.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Idiomatic Firestore listener wrappers that bake in the cache-snapshot
 * guards every repository in this codebase needs.
 *
 * **Why this exists:**
 *   We had ~19 different `addSnapshotListener { snapshot, error -> ... }`
 *   call sites, each repeating the same five-line ritual:
 *     1. `if (error != null) return@`
 *     2. `if (snap.shouldSkipEmptyCache()) return@`
 *     3. parse
 *     4. write to Room
 *     5. emit
 *
 *   Drift was inevitable: some sites forgot the guard, some used the
 *   wrong guard, some logged the error, some swallowed it. Centralising
 *   the dance means there's one place to fix bugs and one place to add
 *   instrumentation.
 *
 * **Usage:**
 *
 *   ```kotlin
 *   val users: Flow<List<User>> = query.observeQuery(
 *       skipPolicy = SnapshotSkipPolicy.EMPTY_CACHE,
 *       parse = { it.toObjects(User::class.java) }
 *   )
 *   ```
 *
 *   Document equivalents and a "delete-on-server-snapshot reconcile" hook
 *   are below.
 *
 * Errors are logged via Timber and propagated by closing the Flow with
 * the original exception, so consumers using `catch { }` see the failure
 * type cleanly.
 */

/**
 * What to do when a snapshot's emptiness might be misleading.
 *
 * | Policy           | Empty cache snapshot | Empty server snapshot |
 * | ---------------- | -------------------- | --------------------- |
 * | [ALWAYS_EMIT]    | emit empty           | emit empty            |
 * | [EMPTY_CACHE]    | suppress             | emit empty            |
 * | [MISSING_DOC]    | suppress only when doc doesn't exist + cache | always emit |
 *
 * Use [EMPTY_CACHE] for "list of friends / conversations / locations"
 * style queries where an empty cache snapshot is a cold-start artifact,
 * not a real "you have no friends" signal.
 *
 * Use [MISSING_DOC] for single-document listeners (`users/{uid}`,
 * inbox docs) where the doc may legitimately not exist yet.
 *
 * Use [ALWAYS_EMIT] when the consumer wants to react to every snapshot,
 * including transient empties — e.g. when computing presence flags.
 */
enum class SnapshotSkipPolicy { ALWAYS_EMIT, EMPTY_CACHE, MISSING_DOC }

/**
 * Wraps a [Query] in a Flow that emits parsed values for each snapshot
 * the listener fires, applying the requested cache-snapshot guard.
 *
 * @param skipPolicy How to handle empty / missing-doc cache snapshots.
 * @param parse Synchronous mapping from snapshot to a value to emit.
 *   Throw to fail the Flow.
 */
inline fun <T> Query.observeQuery(
    skipPolicy: SnapshotSkipPolicy = SnapshotSkipPolicy.EMPTY_CACHE,
    crossinline parse: (QuerySnapshot) -> T,
): Flow<T> = callbackFlow {
    val reg: ListenerRegistration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            // PERMISSION_DENIED is the server's way of saying "you can no
            // longer read this query" — typically because the user was
            // removed from the group / conversation that gates the read.
            // We close the Flow *cleanly* (no error) so that consumers
            // observe a normal completion. The repository / VM that owns
            // the conversation listener will pick up the membership change
            // separately and swap the screen to NotParticipantBanner.
            //
            // Crashing the app on a legitimate authorization signal is
            // never the right behaviour — see the post-mortem for the
            // hardened-rules rollout.
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Timber.i("observeQuery permission denied — closing cleanly")
                close()
            } else {
                Timber.w(error, "observeQuery error")
                close(error)
            }
            return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener
        if (skipPolicy == SnapshotSkipPolicy.EMPTY_CACHE
            && snapshot.metadata.isFromCache
            && snapshot.isEmpty
        ) return@addSnapshotListener
        try {
            trySend(parse(snapshot))
        } catch (e: Exception) {
            Timber.e(e, "observeQuery parse failed")
            close(e)
        }
    }
    awaitClose { reg.remove() }
}

/**
 * Document-listener counterpart of [observeQuery]. Emits `null` when the
 * doc doesn't exist (and policy permits emitting that absence).
 */
inline fun <T> com.google.firebase.firestore.DocumentReference.observeDoc(
    skipPolicy: SnapshotSkipPolicy = SnapshotSkipPolicy.MISSING_DOC,
    crossinline parse: (DocumentSnapshot) -> T?,
): Flow<T?> = callbackFlow {
    val reg: ListenerRegistration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            // See observeQuery for rationale — PERMISSION_DENIED on a
            // single-doc listener means the caller no longer has read
            // access; close cleanly rather than crashing.
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Timber.i("observeDoc permission denied — closing cleanly")
                close()
            } else {
                Timber.w(error, "observeDoc error")
                close(error)
            }
            return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener
        if (skipPolicy == SnapshotSkipPolicy.MISSING_DOC
            && snapshot.metadata.isFromCache
            && !snapshot.exists()
        ) return@addSnapshotListener
        try {
            trySend(parse(snapshot))
        } catch (e: Exception) {
            Timber.e(e, "observeDoc parse failed")
            close(e)
        }
    }
    awaitClose { reg.remove() }
}
