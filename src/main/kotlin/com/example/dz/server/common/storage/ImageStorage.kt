package com.example.dz.server.common.storage

import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory

/**
 * Keeps uploaded images somewhere the app can fetch them from by address.
 *
 * Keys are made by the server, never taken from a request, so a client cannot choose where a file
 * lands or overwrite someone else's.
 */
interface ImageStorage {

    /** False where uploads are switched off; callers refuse the upload before doing any work. */
    val acceptsUploads: Boolean

    /** Stores [bytes] under [key] and returns the public address to fetch them from. */
    fun put(key: String, bytes: ByteArray, contentType: String): String

    /**
     * Deletes the file behind [url] if this storage holds it. Addresses from elsewhere, like a
     * Google profile photo, are left alone. Never throws: a file that could not be deleted is
     * logged, because failing the request over it would leave the caller worse off.
     */
    fun deleteQuietly(url: String)
}

/** Files in a folder on this machine, served by this server. For development. */
class LocalImageStorage(
    private val directory: Path,
    private val publicBaseUrl: String,
) : ImageStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override val acceptsUploads: Boolean = true

    override fun put(key: String, bytes: ByteArray, contentType: String): String {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        return "$publicBaseUrl/$key"
    }

    override fun deleteQuietly(url: String) {
        val prefix = "$publicBaseUrl/"
        if (!url.startsWith(prefix)) return
        try {
            Files.deleteIfExists(resolve(url.removePrefix(prefix)))
        } catch (failure: Exception) {
            log.warn("Could not delete stored file {}", url, failure)
        }
    }

    /** Refuses any key that would land outside [directory], such as one holding "../". */
    private fun resolve(key: String): Path {
        val path = directory.resolve(key).normalize()
        require(path.startsWith(directory)) { "Key escapes the storage folder" }
        return path
    }
}

/** Where no storage is set up: uploads are refused, and there is never anything to delete. */
object UnavailableImageStorage : ImageStorage {
    override val acceptsUploads: Boolean = false

    override fun put(key: String, bytes: ByteArray, contentType: String): String =
        error("Uploads are switched off; check acceptsUploads first")

    override fun deleteQuietly(url: String) = Unit
}
