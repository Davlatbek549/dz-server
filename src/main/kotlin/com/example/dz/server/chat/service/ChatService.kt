package com.example.dz.server.chat.service

import com.example.dz.server.chat.dto.ChatResponse
import com.example.dz.server.chat.dto.ChatsResponse
import com.example.dz.server.chat.dto.MessageResponse
import com.example.dz.server.chat.dto.MessagesResponse
import com.example.dz.server.chat.entity.Message
import com.example.dz.server.chat.mapper.toChatFriendSummary
import com.example.dz.server.chat.mapper.toResponse
import com.example.dz.server.chat.repository.MessageRepository
import com.example.dz.server.friends.entity.FriendRequestStatus
import com.example.dz.server.friends.exception.FriendNotFoundException
import com.example.dz.server.friends.repository.FriendRequestRepository
import com.example.dz.server.notifications.entity.NotificationCategory
import com.example.dz.server.notifications.service.NotificationService
import com.example.dz.server.users.exception.UserNotFoundException
import com.example.dz.server.users.repository.UserRepository
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChatService(
    private val messages: MessageRepository,
    private val friendRequests: FriendRequestRepository,
    private val users: UserRepository,
    private val notificationService: NotificationService,
) {

    @Transactional(readOnly = true)
    fun listChats(userId: UUID): ChatsResponse {
        val friendIds = friendRequests.findAllForUser(userId, FriendRequestStatus.Accepted)
            .map { if (it.requesterId == userId) it.addresseeId else it.requesterId }
        val friendsById = users.findAllById(friendIds).associateBy { it.requireId() }

        val chats = friendIds.mapNotNull { friendId ->
            val friend = friendsById[friendId] ?: return@mapNotNull null
            val lastMessage = messages.findThreadDesc(userId, friendId, PageRequest.of(0, 1)).firstOrNull()
            val unread = messages.countBySenderIdAndRecipientIdAndReadAtIsNull(friendId, userId)
            ChatResponse(
                friend = friend.toChatFriendSummary(),
                lastMessage = lastMessage?.toResponse(),
                unreadCount = unread.toInt(),
            )
        }
        return ChatsResponse(chats = chats)
    }

    /**
     * Not read-only: opening a thread marks the friend's messages read as a
     * side effect, which Postgres refuses inside a read-only transaction.
     */
    fun getMessages(userId: UUID, friendId: UUID): MessagesResponse {
        requireFriendship(userId, friendId)
        // Marked read before the fetch, so the response reflects the state it just set.
        messages.markThreadRead(userId, friendId, Instant.now())
        val thread = messages.findThread(userId, friendId)
        return MessagesResponse(messages = thread.map { it.toResponse() })
    }

    fun sendMessage(userId: UUID, friendId: UUID, text: String): MessageResponse {
        requireFriendship(userId, friendId)
        val sender = users.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val message = messages.save(Message(senderId = userId, recipientId = friendId, text = text))

        notificationService.notify(
            userId = friendId,
            category = NotificationCategory.Chat,
            title = sender.name,
            message = text,
        )

        return message.toResponse()
    }

    private fun requireFriendship(userId: UUID, friendId: UUID) {
        friendRequests.findBetween(userId, friendId, FriendRequestStatus.Accepted)
            .orElseThrow { FriendNotFoundException(friendId.toString()) }
    }
}
