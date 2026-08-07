package com.example.dz.server.collections.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/** One book's place in a collection, with enough detail to render it. */
@Entity
@Table(name = "collection_books")
class CollectionBook(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    var collection: BookCollection,

    @Column(name = "book_id", nullable = false)
    var bookId: String,

    @Column(nullable = false)
    var title: String,

    @Column
    var author: String? = null,

    @Column(name = "cover_url")
    var coverUrl: String? = null,

    /** Preserves the order the client sent, which the UI shows. */
    @Column(nullable = false)
    var position: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
