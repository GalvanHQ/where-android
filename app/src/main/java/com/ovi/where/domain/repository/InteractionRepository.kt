package com.ovi.where.domain.repository

import com.ovi.where.domain.model.Interaction
import com.ovi.where.domain.model.InteractionType
import kotlinx.coroutines.flow.Flow

interface InteractionRepository {
    fun getRecentInteractions(limit: Int): Flow<List<Interaction>>
    suspend fun recordInteraction(userId: String, displayName: String, photoUrl: String?, type: InteractionType)
    suspend fun clearAll()
}
