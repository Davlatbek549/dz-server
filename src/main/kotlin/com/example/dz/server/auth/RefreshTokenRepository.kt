package com.example.dz.server.auth

import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    /**
     * Signs the user out of every device at once.
     *
     * A bulk update bypasses the persistence context, so it flushes pending
     * changes first and clears cached entities after — otherwise a token loaded
     * earlier in the same transaction would still look unrevoked.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update RefreshToken t
           set t.revokedAt = :now
         where t.user.id = :userId
           and t.revokedAt is null
        """
    )
    fun revokeAllForUser(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}
