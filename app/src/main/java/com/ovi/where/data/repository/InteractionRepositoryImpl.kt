package com.ovi.where.data.repository

import com.ovi.where.data.local.dao.InteractionDao
import com.ovi.where.data.local.entity.InteractionEntity
import com.ovi.where.domain.model.Interaction
import com.ovi.where.domain.model.InteractionType
import com.ovi.where.domain.repository.InteractionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRepositoryImpl @Inject constructor(
    private val dao: InteractionDao
) : InteractionRepository {

    override fun getRecentInteractions(limit: Int): Flow<List<Interaction>> {
        return dao.getRecent(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Failed to read recent interactions from DAO")
                emit(emptyList())
            }
    }

    override suspend fun recordInteraction(
        userId: String,
        displayName: String,
        photoUrl: String?,
        type: InteractionType
    ) {
        val entity = InteractionEntity(
            id = "${userId}_${type.name}",
            userId = userId,
            displayName = displayName,
            photoUrl = photoUrl,
            type = type.name,
            timestamp = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }

    private fun InteractionEntity.toDomain(): Interaction {
        return Interaction(
            userId = userId,
            displayName = displayName,
            photoUrl = photoUrl,
            type = InteractionType.valueOf(type),
            timestamp = timestamp
        )
    }
}
