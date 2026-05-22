package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Updates the calling user's free-form note ("custom status") on the
 * active meetup destination for [groupId]. An empty string clears the
 * note. Capped to 80 chars at the call site.
 *
 * Only the user's own entry is touched — Firestore rules + repository
 * use a dotted-path write, so other participants are unaffected.
 */
class UpdateMeetupParticipantNoteUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(groupId: String, note: String): Resource<Unit> {
        val trimmed = note.trim().take(MAX_NOTE_CHARS)
        return locationRepository.updateMeetupParticipantNote(groupId, trimmed)
    }

    companion object {
        const val MAX_NOTE_CHARS = 80
    }
}
