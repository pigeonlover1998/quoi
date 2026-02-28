package quoi.utils.ui.rendering

import quoi.utils.WebUtils.setupConnection
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/ui/rendering/Image.kt
 */
class Image(
    val identifier: String,
    var isSVG: Boolean = false,
    var stream: InputStream = getStream(identifier),
    private var buffer: ByteBuffer? = null
) {

    init {
        isSVG = identifier.endsWith(".svg", true)
    }

    fun buffer(): ByteBuffer {
        if (buffer == null) {
            val bytes = stream.readBytes()
            buffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip() as ByteBuffer
            stream.close()
        }
        return buffer ?: throw IllegalStateException("Image has no data")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Image) return false
        return identifier == other.identifier
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    companion object {

        private fun getStream(path: String): InputStream {
            val trimmedPath = path.trim()
            return if (trimmedPath.startsWith("http")) setupConnection(trimmedPath)
            else {
                val file = File(trimmedPath)
                if (file.exists() && file.isFile) Files.newInputStream(file.toPath())
                else this::class.java.getResourceAsStream(trimmedPath) ?: throw FileNotFoundException(trimmedPath)
            }
        }
    }
}