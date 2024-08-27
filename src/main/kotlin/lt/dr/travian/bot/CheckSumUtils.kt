package lt.dr.travian.bot

import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

object CheckSumUtils {

    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    fun calculateCheckSum(file: File): String? {
        return kotlin.runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val fileBytes = file.readBytes()
            val hashBytes = digest.digest(fileBytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        }.onFailure {
            LOGGER.error("Failed calculating checksum", it)
        }.getOrNull()
    }

}
