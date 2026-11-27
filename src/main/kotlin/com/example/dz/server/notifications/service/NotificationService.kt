package com.example.dz.server.notifications.service

import com.example.dz.server.notifications.dto.NotificationResponse
import com.example.dz.server.notifications.dto.NotificationsResponse
import com.example.dz.server.notifications.entity.Notification
import com.example.dz.server.notifications.entity.NotificationCategory
import com.example.dz.server.notifications.exception.NotificationNotFoundException
import com.example.dz.server.notifications.mapper.toResponse
import com.example.dz.server.notifications.repository.NotificationRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationService(private val notifications: NotificationRepository) {

    @Transactional(readOnly = true)
    fun listNotifications(userId: UUID) = NotificationsResponse(
        notifications = notifications.findAllByUserIdOrderByCreatedAtDesc(userId).map { it.toResponse() },
    )

    fun markAsRead(userId: UUID, notificationId: UUID): NotificationResponse {
        val notification = notifications.findByIdAndUserId(notificationId, userId)
            .orElseThrow { NotificationNotFoundException(notificationId.toString()) }
        notification.isRead = true
        return notification.toResponse()
    }

    /** Called by other modules (friends, chat) to drop a row into someone's feed. */
    fun notify(userId: UUID, category: NotificationCategory, title: String, message: String) {
        notifications.save(Notification(userId = userId, category = category, title = title, message = message))
    }
}
