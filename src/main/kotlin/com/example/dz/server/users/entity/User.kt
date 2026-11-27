package com.example.dz.server.users.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false)
    var email: String,

    /**
     * Null for an account that only exists through a provider. Login treats that as "no password
     * on this account", which is a refusal rather than a match.
     */
    @Column(name = "password_hash")
    var passwordHash: String?,

    @Column(nullable = false)
    var name: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column
    var bio: String? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column
    var language: String? = null,

    /** Daily reading goal in minutes, shown on the profile screen. */
    @Column(name = "current_goal_minutes")
    var currentGoalMinutes: Int? = null,

    /** Always lower case; unique case-insensitively (V11). */
    @Column
    var username: String? = null,

    @Column(name = "banner_url")
    var bannerUrl: String? = null,

    /** ISO 3166 alpha-2 code of the country [phoneNumber] was entered for, e.g. "GB". */
    @Column(name = "phone_country")
    var phoneCountry: String? = null,

    /** Can be set once; after that only support can change it. */
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,

    /** Can be set once; after that only support can change it. */
    @Enumerated(EnumType.STRING)
    @Column
    var gender: Gender? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "speciality_kind")
    var specialityKind: SpecialityKind? = null,

    @Column
    var speciality: String? = null,

    /** ISO 3166 alpha-2 code, e.g. "GB". */
    @Column(name = "address_country")
    var addressCountry: String? = null,

    @Column(name = "address_city")
    var addressCity: String? = null,

    @Column(name = "address_street")
    var addressStreet: String? = null,

    @Column(name = "address_postal_code")
    var addressPostalCode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false)
    var profileVisibility: ProfileVisibility = ProfileVisibility.OPEN,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    /** Cleared to lock an account out without deleting it. */
    @Column(nullable = false)
    var enabled: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /**
     * Set here rather than with `@CreationTimestamp`, which Hibernate only
     * applies when the INSERT runs — too late for a response built straight
     * after `save()`.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    /** Non-null once the row has been persisted. */
    fun requireId(): UUID = checkNotNull(id) { "User has not been persisted yet" }
}
