package com.example.dz.server.notifications.exception

/**
 * The caller has no such notification. Also what another user's notification
 * id looks like from here -- see [com.example.dz.server.library.exception.LibraryBookNotFoundException].
 */
class NotificationNotFoundException(id: String) :
    RuntimeException("No notification with id $id for this user")
