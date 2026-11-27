package com.example.dz.server.notifications.controller

import com.example.dz.server.notifications.dto.NotificationResponse
import com.example.dz.server.notifications.dto.NotificationsResponse
import com.example.dz.server.notifications.service.NotificationService
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun listNotifications(@AuthenticationPrincipal userId: UUID): NotificationsResponse =
        notificationService.listNotifications(userId)

    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable notificationId: UUID,
    ): NotificationResponse = notificationService.markAsRead(userId, notificationId)
}
