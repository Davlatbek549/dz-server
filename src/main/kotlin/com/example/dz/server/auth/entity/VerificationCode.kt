package com.example.dz.server.auth.entity

import com.example.dz.server.users.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** What a code entitles its holder to do. See `V7__create_verification_codes_table.sql`. */
enum class VerificationPurpose {
    VerifyEmail,
    ResetPassword,
}

@Entity
@Table(name = "verification_codes")
class VerificationCode(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    /** BCrypt of the six digits — see the migration for why not SHA-256. */
    @Column(name = "code_hash", nullable = false)
    var codeHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var purpose: VerificationPurpose,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    fun isUsable(now: Instant = Instant.now()): Boolean =
        consumedAt == null && expiresAt.isAfter(now)
}
