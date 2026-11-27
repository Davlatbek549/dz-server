package com.example.dz.server.notifications.mapper

import com.example.dz.server.notifications.dto.NotificationResponse
import com.example.dz.server.notifications.entity.Notification

fun Notification.toResponse() = NotificationResponse(
    id = id.toString(),
    category = category,
    title = title,
    message = message,
    isRead = isRead,
    createdAt = createdAt,
)
