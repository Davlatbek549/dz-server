package com.example.dz.server.notifications.dto

import com.example.dz.server.notifications.entity.NotificationCategory
import java.time.Instant

/**
 * Wrapped rather than a bare JSON array so pagination can be added later
 * without changing the response's shape.
 */
data class NotificationsResponse(
    val notifications: List<NotificationResponse>,
)

data class NotificationResponse(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Instant,
)
