package com.example.dz.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ChatsResponse(
    val chats: List<ChatResponse>,
)

data class ChatResponse(
    val friend: ChatFriendSummary,
    val lastMessage: MessageResponse?,
    val unreadCount: Int,
)

/** A small duplicate of `friends.dto.FriendSummary` -- each feature owns its own minimal summary shape. */
data class ChatFriendSummary(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

data class MessagesResponse(
    val messages: List<MessageResponse>,
)

data class MessageResponse(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val createdAt: Instant,
    val readAt: Instant?,
)

data class SendMessageRequest(
    @field:NotBlank(message = "Message text is required")
    @field:Size(max = 5000, message = "Message is too long")
    val text: String,
)
