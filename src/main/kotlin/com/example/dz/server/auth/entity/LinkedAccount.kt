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

/** Identity providers DZ accepts. Stored as the name, so adding one needs no migration. */
enum class AuthProvider {
    Google,
}

/**
 * A provider identity bound to a DZ account.
 *
 * [subject] is the provider's immutable id for the person, not their address — addresses get
 * changed and reassigned, and matching on one would eventually hand an account to a stranger.
 */
@Entity
@Table(name = "linked_accounts")
class LinkedAccount(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: AuthProvider,

    @Column(nullable = false)
    var subject: String,

    /** What the provider reported at link time. Kept for support, never used for lookup. */
    @Column
    var email: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
