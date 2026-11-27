package com.example.dz.server.notifications.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What a notification is about, and so what tapping it should open. `Chat` is
 * its own value rather than folded into `Friends`: both list under the app's
 * Friends filter, but only a `Chat` row opens the thread instead of the
 * friend's profile. See `V8__create_notifications_table.sql`.
 */
enum class NotificationCategory { Friends, Chat, Goals, Store, Other }

@Entity
@Table(name = "notifications")
class Notification(

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: NotificationCategory,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var message: String,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
