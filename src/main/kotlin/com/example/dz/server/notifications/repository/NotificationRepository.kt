package com.example.dz.server.notifications.repository

import com.example.dz.server.notifications.entity.Notification
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Every lookup takes a `userId`, so another user's notification id resolves
 * to nothing here -- same guarantee as [com.example.dz.server.library.repository.LibraryBookRepository].
 */
interface NotificationRepository : JpaRepository<Notification, UUID> {

    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<Notification>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Notification>
}
