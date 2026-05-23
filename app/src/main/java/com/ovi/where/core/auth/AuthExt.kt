package com.ovi.where.core.auth

import com.google.firebase.auth.FirebaseAuth

/**
 * Convenience accessor for the current user's UID.
 *
 * The expression `firebaseAuth.currentUser?.uid` was duplicated in nine
 * ViewModels and repositories. Each copy was identical, but the local
 * naming drifted (`currentUid`, `currentUserId`, `uid` getters,
 * inline-`val`s). This extension lets the call site stay short and
 * readable without each class needing its own one-liner getter.
 *
 * Returns `null` when the user is signed out.
 */
val FirebaseAuth.uid: String?
    get() = currentUser?.uid
