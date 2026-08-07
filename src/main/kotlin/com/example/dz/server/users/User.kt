package com.example.dz.server.users

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var name: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    /** Cleared to lock an account out without deleting it. */
    @Column(nullable = false)
    var enabled: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant

    /** Non-null once the row has been persisted. */
    fun requireId(): UUID = checkNotNull(id) { "User has not been persisted yet" }
}
