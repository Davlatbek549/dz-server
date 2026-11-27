package com.example.dz.server.chat.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A direct message between two users. The pair of ids is the thread -- there's no separate conversation entity. */
@Entity
@Table(name = "messages")
class Message(

    @Column(name = "sender_id", nullable = false, updatable = false)
    var senderId: UUID,

    @Column(name = "recipient_id", nullable = false, updatable = false)
    var recipientId: UUID,

    @Column(nullable = false)
    var text: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "read_at")
    var readAt: Instant? = null

    fun requireId(): UUID = checkNotNull(id) { "Message has not been persisted yet" }
}
