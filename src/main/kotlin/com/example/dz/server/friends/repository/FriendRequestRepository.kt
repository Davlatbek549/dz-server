package com.example.dz.server.friends.repository

import com.example.dz.server.friends.entity.FriendRequest
import com.example.dz.server.friends.entity.FriendRequestStatus
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FriendRequestRepository : JpaRepository<FriendRequest, UUID> {

    /**
     * The request or friendship between two users in the given [status],
     * regardless of who sent it. Used both for the "already friends"/
     * "already pending" guards before inserting a new request, and for the
     * chat module's friendship gate.
     */
    @Query(
        """
        select r from FriendRequest r
         where least(r.requesterId, r.addresseeId) = least(:userId, :otherUserId)
           and greatest(r.requesterId, r.addresseeId) = greatest(:userId, :otherUserId)
           and r.status = :status
        """,
    )
    fun findBetween(userId: UUID, otherUserId: UUID, status: FriendRequestStatus): Optional<FriendRequest>

    /** Every row in [status] where the user is on either side -- friends list, or chat's friend list. */
    @Query(
        """
        select r from FriendRequest r
         where r.status = :status
           and (r.requesterId = :userId or r.addresseeId = :userId)
        """,
    )
    fun findAllForUser(userId: UUID, status: FriendRequestStatus): List<FriendRequest>

    /** Requests sent *to* this user -- the accept/decline inbox. */
    fun findAllByAddresseeIdAndStatusOrderByCreatedAtDesc(addresseeId: UUID, status: FriendRequestStatus): List<FriendRequest>

    /** Scopes accept/decline to the addressee, same shape as `NotificationRepository.findByIdAndUserId`. */
    fun findByIdAndAddresseeId(id: UUID, addresseeId: UUID): Optional<FriendRequest>
}
