package com.example.dz.server.users.service

import com.example.dz.server.users.exception.ProfileErrorCode
import com.example.dz.server.users.exception.ProfileException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import org.springframework.stereotype.Component

/**
 * Turns an uploaded picture into the JPEG that is actually stored: checked, trimmed to the exact
 * shape, resized to the [ProfileImageKind]'s size and written out fresh.
 *
 * Writing a new file rather than keeping the upload is what makes this safe to publish. Nothing
 * from the original survives except its pixels: not the camera's hidden location data, not
 * anything crafted into the file to trip up whoever opens it next.
 *
 * Checks run cheapest first, and the picture's size is read from its header before a single pixel
 * is decoded, so a small file claiming to be enormous is refused before it can fill the memory.
 */
@Component
class ProfileImageProcessor {

    fun process(kind: ProfileImageKind, bytes: ByteArray): ByteArray {
        if (bytes.size > MAX_UPLOAD_BYTES) throw tooLarge()
        val format = formatOf(bytes) ?: throw unreadable()
        val reader = ImageIO.getImageReadersByFormatName(format).asSequence().firstOrNull() ?: throw unreadable()

        val decoded = try {
            MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                reader.setInput(input, true, true)
                checkSize(kind, reader.getWidth(0), reader.getHeight(0))
                reader.read(0)
            }
        } catch (refused: ProfileException) {
            throw refused
        } catch (damaged: Exception) {
            // Decoders throw all sorts on a broken file, not only IOException.
            throw unreadable()
        } finally {
            reader.dispose()
        }

        val sized = resize(trimToShape(decoded, kind.aspectRatio), kind.width, kind.height)
        return encodeJpeg(sized)
    }

    private fun checkSize(kind: ProfileImageKind, width: Int, height: Int) {
        if (width > MAX_SIDE || height > MAX_SIDE || width.toLong() * height > MAX_PIXELS) throw tooLarge()
        val off = abs(width.toDouble() / height - kind.aspectRatio) / kind.aspectRatio
        if (off > SHAPE_TOLERANCE) {
            throw ProfileException(
                ProfileErrorCode.ImageWrongShape,
                if (kind == ProfileImageKind.Avatar) "A profile photo has to be square" else "A banner has to be three times as wide as it is tall",
            )
        }
        if (width < kind.minWidth || height < kind.minHeight) {
            throw ProfileException(ProfileErrorCode.ImageTooSmall, "This picture is too small to look sharp")
        }
    }

    /** Cuts the few pixels a shape within [SHAPE_TOLERANCE] is off by, evenly from both sides. */
    private fun trimToShape(image: BufferedImage, aspectRatio: Double): BufferedImage {
        val wanted = image.height * aspectRatio
        return when {
            image.width > wanted + 0.5 -> {
                val width = wanted.roundToInt()
                image.getSubimage((image.width - width) / 2, 0, width, image.height)
            }
            image.width < wanted - 0.5 -> {
                val height = (image.width / aspectRatio).roundToInt()
                image.getSubimage(0, (image.height - height) / 2, image.width, height)
            }
            else -> image
        }
    }

    /**
     * Halves the picture while it is still more than twice the target, then makes the last step.
     * Bilinear filtering is only sharp over steps of up to 2x; one big jump would alias. Drawing
     * onto white also flattens transparency, which a JPEG cannot hold.
     */
    private fun resize(image: BufferedImage, width: Int, height: Int): BufferedImage {
        var current = image
        while (current.width / 2 >= width && current.height / 2 >= height) {
            current = draw(current, current.width / 2, current.height / 2)
        }
        return if (current.width == width && current.height == height && current.type == BufferedImage.TYPE_INT_RGB) {
            current
        } else {
            draw(current, width, height)
        }
    }

    private fun draw(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.drawImage(image, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        val bytes = ByteArrayOutputStream()
        try {
            MemoryCacheImageOutputStream(bytes).use { output ->
                writer.output = output
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return bytes.toByteArray()
    }

    /** Decided by the file's first bytes. Its name and declared type are the client's say-so. */
    private fun formatOf(bytes: ByteArray): String? = when {
        bytes.startsWith(JPEG_SIGNATURE) -> "jpeg"
        bytes.startsWith(PNG_SIGNATURE) -> "png"
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun unreadable() = ProfileException(ProfileErrorCode.ImageUnreadable, "Use a JPEG or PNG picture")

    private fun tooLarge() = ProfileException(ProfileErrorCode.ImageTooLarge, "This picture is too large")

    companion object {
        /** Matches `spring.servlet.multipart.max-file-size`, which stops bigger files sooner. */
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024

        /**
         * The server runs in about 350 MB of heap. Eight million pixels decode to about 32 MB,
         * which leaves room for a few uploads at once. The app sends 0.75 million at most.
         */
        private const val MAX_SIDE = 4000
        private const val MAX_PIXELS = 8_000_000L

        /** Two percent: enough for rounding in a client's crop, not enough to notice when trimmed. */
        private const val SHAPE_TOLERANCE = 0.02

        private const val JPEG_QUALITY = 0.85f

        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
