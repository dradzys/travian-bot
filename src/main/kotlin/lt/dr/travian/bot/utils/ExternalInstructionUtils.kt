package lt.dr.travian.bot.utils

import lt.dr.travian.bot.DRIVER
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

object ExternalInstructionUtils {

    private val LOGGER = LoggerFactory.getLogger(this.javaClass)
    private const val EXTERNAL_INSTRUCTION_DIR = "travian-bot"

    /**
     * Validate external instruction directory presence, attempt to create if missing.
     * Other-wise exit out with IllegalArgumentException.
     */
    fun validateExternalInstructionDir() {
        kotlin.runCatching {
            val externalInstructionPath = resolveExternalInstructionDir()
            val configFile = File(externalInstructionPath)
            if (configFile.exists()) return

            if (!configFile.mkdirs()) {
                LOGGER.error("Unable to create external instruction file, please create manually.")
                DRIVER.quit()
                exitProcess(1)
            }
        }.onFailure {
            LOGGER.error("Unable to check or create external instruction file", it)
        }
    }

    fun loadExternalInstruction(fileName: String): File? {
        return kotlin.runCatching {
            return File(resolveExternalInstructionFile(fileName))
        }.getOrNull()
    }

    /**
     * Resolve os-agnostic external instruction path.
     * This configuration is system-wide.
     */
    private fun resolveExternalInstructionDir(): String {
        return if (System.getProperty("os.name").contains("Windows")) {
            Paths.get(System.getenv("APPDATA"), EXTERNAL_INSTRUCTION_DIR).toString()
        } else {
            Paths.get(System.getProperty("user.home"), ".config", EXTERNAL_INSTRUCTION_DIR)
                .toString()
        }
    }


    private fun resolveExternalInstructionFile(fileName: String): String {
        return if (System.getProperty("os.name").contains("Windows")) {
            Paths.get(System.getenv("APPDATA"), EXTERNAL_INSTRUCTION_DIR, fileName).toString()
        } else {
            Paths.get(
                System.getProperty("user.home"),
                ".config",
                EXTERNAL_INSTRUCTION_DIR,
                fileName
            ).toString()
        }
    }
}
