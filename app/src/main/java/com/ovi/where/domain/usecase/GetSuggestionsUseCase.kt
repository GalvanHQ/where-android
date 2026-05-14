package com.ovi.where.domain.usecase

import com.ovi.where.domain.repository.InteractionRepository
import com.ovi.where.presentation.common.search.SuggestionUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Retrieves recent interactions and maps them to suggestion UI models.
 *
 * Suggestions are ordered by most recent interaction first (timestamp DESC,
 * preserved from the repository) and capped at [limit] entries (default 15).
 * The [SuggestionUiModel.isOnline] field uses the interaction's online status,
 * which defaults to false when presence data is unavailable.
 */
class GetSuggestionsUseCase @Inject constructor(
    private val interactionRepository: InteractionRepository
) {

    operator fun invoke(limit: Int = 15): Flow<List<SuggestionUiModel>> {
        return interactionRepository.getRecentInteractions(limit)
            .map { interactions ->
                interactions.take(limit).map { interaction ->
                    SuggestionUiModel(
                        userId = interaction.userId,
                        displayName = interaction.displayName,
                        photoUrl = interaction.photoUrl,
                        isOnline = interaction.isOnline
                    )
                }
            }
    }
}
