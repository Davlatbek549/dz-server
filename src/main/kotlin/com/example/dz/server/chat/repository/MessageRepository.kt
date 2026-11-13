package com.example.dz.server.chat.repository

import com.example.dz.server.chat.entity.Message
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface MessageRepository : JpaRepository<Message, UUID> {

    /** The full thread between two users, oldest first, regardless of who sent which message. */
    @Query(
        """
        select m from Message m
         where least(m.senderId, m.recipientId) = least(:userId, :friendId)
           and greatest(m.senderId, m.recipientId) = greatest(:userId, :friendId)
         order by m.createdAt asc
        """,
    )
    fun findThread(userId: UUID, friendId: UUID): List<Message>

    /** Same thread, newest first -- paired with a `PageRequest.of(0, 1)` for a chat-list preview. */
    @Query(
        """
        select m from Message m
         where least(m.senderId, m.recipientId) = least(:userId, :friendId)
           and greatest(m.senderId, m.recipientId) = greatest(:userId, :friendId)
         order by m.createdAt desc
        """,
    )
    fun findThreadDesc(userId: UUID, friendId: UUID, pageable: Pageable): List<Message>

    /** Opening a thread marks it read -- there's no separate explicit "mark read" endpoint. */
    @Modifying
    @Query(
        """
        update Message m set m.readAt = :now
         where m.senderId = :friendId and m.recipientId = :userId and m.readAt is null
        """,
    )
    fun markThreadRead(userId: UUID, friendId: UUID, now: Instant): Int

    fun countBySenderIdAndRecipientIdAndReadAtIsNull(senderId: UUID, recipientId: UUID): Long
}
