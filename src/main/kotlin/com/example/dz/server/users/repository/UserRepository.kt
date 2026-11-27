package com.example.dz.server.users.repository

import com.example.dz.server.users.entity.User
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.Pageable
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

    /**
     * Candidates for the friends module's "suggested" list. [excludedIds] is the caller's own id
     * plus everyone already related to them (friends, pending either direction) -- always
     * non-empty, so `not in` never has to special-case an empty collection.
     */
    @Query("select u from User u where u.id not in :excludedIds and u.enabled = true order by u.createdAt desc")
    fun findDiscoverable(excludedIds: Collection<UUID>, pageable: Pageable): List<User>

    /**
     * Whether anyone other than [exceptId] holds [username]. Matched on `lower(username)` to line
     * up with V11's unique index, so the pre-save check and the constraint agree on what "taken"
     * means. The caller is excluded so keeping your own username is never a conflict.
     */
    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and u.id <> :exceptId")
    fun isUsernameTakenByOther(username: String, exceptId: UUID): Boolean
}
