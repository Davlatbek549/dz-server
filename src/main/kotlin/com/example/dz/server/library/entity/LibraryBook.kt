package com.example.dz.server.library.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.UpdateTimestamp

/**
 * The owner is stored as a plain [userId] column rather than a `@ManyToOne` to
 * `User`: every query here already filters by user, and nothing in this module
 * needs the user's other fields, so a relation would only add a join and a lazy
 * proxy. The foreign key and its cascade still live in the database.
 */
@Entity
@Table(name = "library_books")
class LibraryBook(

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    /** Identifier from the upstream source (Gutendex), not one we mint. */
    @Column(name = "book_id", nullable = false, updatable = false)
    var bookId: String,

    @Column(nullable = false)
    var title: String,

    @Column
    var author: String? = null,

    @Column(name = "cover_url")
    var coverUrl: String? = null,

    @Column
    var description: String? = null,

    @Column(name = "text_url")
    var textUrl: String? = null,

    @Column(name = "is_free", nullable = false)
    var isFree: Boolean = true,

    @Column(name = "is_favorite", nullable = false)
    var isFavorite: Boolean = false,

    @Column(name = "progress_percent", nullable = false)
    var progressPercent: Int = 0,

    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /**
     * Set here rather than with `@CreationTimestamp`, which Hibernate only
     * applies when the INSERT runs. A response built straight after `save()`
     * happens before that flush, so a generated value would still be missing.
     */
    @Column(name = "added_at", nullable = false, updatable = false)
    var addedAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
