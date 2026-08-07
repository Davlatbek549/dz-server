package com.example.dz.server.users

import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, UUID> {

    /**
     * Matched on `lower(email)` to line up with the unique index in V1 — a
     * plain `findByEmail` would let a differently-cased duplicate slip past the
     * pre-insert check and surface as a constraint violation instead.
     */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    fun findByEmailIgnoringCase(email: String): Optional<User>

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    fun existsByEmailIgnoringCase(email: String): Boolean
}
