package com.example.dz.server.auth.repository

import com.example.dz.server.auth.entity.VerificationCode
import com.example.dz.server.auth.entity.VerificationPurpose
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface VerificationCodeRepository : JpaRepository<VerificationCode, UUID> {

    /**
     * The code a guess is checked against: the newest one issued for this person
     * and purpose. Older ones are consumed on issue, so at most one is live.
     */
    fun findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
        userId: UUID,
        purpose: VerificationPurpose,
    ): Optional<VerificationCode>

    /**
     * Retires every live code before a new one is issued, so requesting a fresh
     * code invalidates the previous mail rather than leaving both working.
     *
     * Bulk update, so it flushes first and clears after — otherwise a code
     * loaded earlier in the same transaction would still look unconsumed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update VerificationCode c
           set c.consumedAt = :now
         where c.user.id = :userId
           and c.purpose = :purpose
           and c.consumedAt is null
        """
    )
    fun consumeAllForUser(
        @Param("userId") userId: UUID,
        @Param("purpose") purpose: VerificationPurpose,
        @Param("now") now: Instant,
    ): Int
}
