package com.example.dz.server.chat.mapper

import com.example.dz.server.chat.dto.ChatFriendSummary
import com.example.dz.server.chat.dto.MessageResponse
import com.example.dz.server.chat.entity.Message
import com.example.dz.server.users.entity.User

fun Message.toResponse() = MessageResponse(
    id = requireId().toString(),
    senderId = senderId.toString(),
    recipientId = recipientId.toString(),
    text = text,
    createdAt = createdAt,
    readAt = readAt,
)

fun User.toChatFriendSummary() = ChatFriendSummary(
    id = requireId().toString(),
    name = name,
    avatarUrl = avatarUrl,
)
