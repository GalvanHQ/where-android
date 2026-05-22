package com.ovi.where.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.MeetupParticipant
import com.ovi.where.domain.model.MeetupParticipantStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Room-cached meetup destination row.
 *
 * Single Source of Truth: this table is the only thing the UI reads from.
 * The repository layer subscribes to Firestore and mirrors snapshots into
 * Room; the UI flows off Room's [androidx.room.Query] returning a
 * `Flow<MeetupDestinationEntity?>`. Keeps the meetup feature consistent
 * with the rest of the app's "Room-as-SSOT" pattern (see
 * `SharedLocationEntity`, `MessageEntity`, etc.).
 *
 * Participants are serialized as a JSON blob — they're read together with
 * the destination, never updated independently in Room (Firestore writes
 * the whole sub-map back), so JSON keeps the schema simple without the
 * cost of a relational shape.
 */
@Entity(tableName = "meetup_destination")
data class MeetupDestinationEntity(
    /** Group id this meetup belongs to — primary key ensures one row per group. */
    @PrimaryKey
    val groupId: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String,
    val setBy: String,
    val setAt: Long,
    val isActive: Boolean,
    /** JSON-serialized [MeetupParticipantsBlob.participants]. */
    val participantsJson: String
)

/** Serialization shape for the participants map. */
@Serializable
private data class MeetupParticipantsBlob(
    val participants: Map<String, ParticipantBlob>
)

@Serializable
private data class ParticipantBlob(
    val status: String,
    val updatedAt: Long,
    val note: String = ""
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun MeetupDestinationEntity.toDomain(): MeetupDestination {
    val parsed = runCatching {
        json.decodeFromString(MeetupParticipantsBlob.serializer(), participantsJson)
    }.getOrNull()
    val participants = parsed?.participants
        ?.mapValues { (_, blob) ->
            MeetupParticipant(
                status = MeetupParticipantStatus.fromString(blob.status),
                updatedAt = blob.updatedAt,
                note = blob.note
            )
        }
        ?: emptyMap()
    return MeetupDestination(
        latitude = latitude,
        longitude = longitude,
        name = name,
        address = address,
        setBy = setBy,
        setAt = setAt,
        isActive = isActive,
        participants = participants
    )
}

fun MeetupDestination.toEntity(groupId: String): MeetupDestinationEntity {
    val blob = MeetupParticipantsBlob(
        participants = participants.mapValues { (_, p) ->
            ParticipantBlob(
                status = p.status.name,
                updatedAt = p.updatedAt,
                note = p.note
            )
        }
    )
    return MeetupDestinationEntity(
        groupId = groupId,
        latitude = latitude,
        longitude = longitude,
        name = name,
        address = address,
        setBy = setBy,
        setAt = setAt,
        isActive = isActive,
        participantsJson = json.encodeToString(MeetupParticipantsBlob.serializer(), blob)
    )
}
