package com.ovi.where.domain.usecase.chat

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Conversation
import com.ovi.where.domain.model.Message
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    operator fun invoke(): Flow<List<Conversation>> =
        conversationRepository.observeConversations()
}

class ObserveMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> =
        messageRepository.observeMessages(conversationId)
}

class GetOrCreateDirectConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(otherUserId: String): Resource<Conversation> =
        conversationRepository.getOrCreateDirectConversation(otherUserId)
}

class CreateGroupConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(groupId: String, name: String, memberIds: List<String>): Resource<Conversation> =
        conversationRepository.createGroupConversation(groupId, name, memberIds)
}

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(conversationId: String, text: String): Resource<Message> =
        messageRepository.sendMessage(conversationId, text)
}

class SendLocationMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(conversationId: String, latitude: Double, longitude: Double): Resource<Message> =
        messageRepository.sendLocationMessage(conversationId, latitude, longitude)
}

class MarkConversationReadUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(conversationId: String, userId: String): Resource<Unit> =
        conversationRepository.markAsRead(conversationId, userId)
}
