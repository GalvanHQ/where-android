package com.ovi.where.domain.usecase.chat

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Use case for sending a voice message.
 *
 * Takes the conversation ID and the local file path of the recorded audio,
 * delegates to MessageRepository for upload and message creation.
 *
 * Requirements: 11.4, 11.6, 11.7
 */
class SendVoiceMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        conversationId: String,
        audioFilePath: String,
        durationMs: Long
    ): Resource<Message> {
        return messageRepository.sendVoiceMessage(conversationId, audioFilePath, durationMs)
    }
}
