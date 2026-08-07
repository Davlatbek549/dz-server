package com.example.dz.server.collections.exception

/**
 * Also what another user's collection id looks like from here — "not yours" and
 * "does not exist" give the same answer on purpose.
 */
class CollectionNotFoundException(collectionId: String) :
    RuntimeException("No collection with id $collectionId for this user")
