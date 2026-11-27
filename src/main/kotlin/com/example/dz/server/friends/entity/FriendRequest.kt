package com.example.dz.server.friends.entity

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

/** A friendship is just a request that reached [Accepted] -- there is no separate friendship row. */
enum class FriendRequestStatus { Pending, Accepted, Declined }

@Entity
@Table(name = "friend_requests")
class FriendRequest(

    @Column(name = "requester_id", nullable = false, updatable = false)
    var requesterId: UUID,

    @Column(name = "addressee_id", nullable = false, updatable = false)
    var addresseeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FriendRequestStatus = FriendRequestStatus.Pending,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "responded_at")
    var respondedAt: Instant? = null

    fun requireId(): UUID = checkNotNull(id) { "FriendRequest has not been persisted yet" }
}
