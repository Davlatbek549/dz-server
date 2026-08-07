package com.example.dz.server.collections.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.UpdateTimestamp

/**
 * Named `BookCollection` rather than `Collection` so it cannot be confused with
 * — or accidentally shadow — `kotlin.collections.Collection`.
 */
@Entity
@Table(name = "collections")
class BookCollection(

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    /** The id the app generated; what callers use in the URL. */
    @Column(name = "external_id", nullable = false, updatable = false)
    var externalId: String,

    @Column(nullable = false)
    var title: String,

    @Column
    var description: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /**
     * Cascading with orphan removal makes membership a plain list operation:
     * clearing the list deletes the rows, adding to it inserts them.
     */
    @OneToMany(
        mappedBy = "collection",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position")
    var books: MutableList<CollectionBook> = mutableListOf()

    /** See [com.example.dz.server.users.entity.User.createdAt] for why this is not `@CreationTimestamp`. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
