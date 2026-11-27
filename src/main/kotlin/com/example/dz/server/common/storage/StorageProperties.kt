package com.example.dz.server.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Where uploaded files (profile photos and banners) are kept.
 *
 * @param type [StorageType.LOCAL] keeps them in [localDirectory] and serves them from this server at
 *   `/files`. [StorageType.NONE] refuses uploads: the right answer on a host whose disk is wiped on
 *   every deploy, where a "successful" upload would vanish with the next release.
 * @param localDirectory relative paths resolve against the directory the server was started in.
 * @param publicBaseUrl the address files are fetched from, without a trailing slash. Stored photo
 *   addresses start with it, which is also how a stored address is recognised as one of ours.
 */
@ConfigurationProperties(prefix = "dz.storage")
data class StorageProperties(
    val type: StorageType = StorageType.LOCAL,
    val localDirectory: String = "data/uploads",
    val publicBaseUrl: String = "http://localhost:8080/files",
)

enum class StorageType { LOCAL, NONE }
