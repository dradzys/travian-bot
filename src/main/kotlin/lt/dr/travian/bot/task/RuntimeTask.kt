package lt.dr.travian.bot.task

import lt.dr.travian.bot.objectMapper
import lt.dr.travian.bot.utils.CheckSumUtils
import lt.dr.travian.bot.utils.ExternalInstructionUtils.loadExternalInstruction
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Runtime configurable task, fetches a json instruction file and stores in-memory.
 * If files checksum has changed it will update in-memory value.
 */
abstract class RuntimeTask<T> : RescheduledTimerTask() {

    protected var orderGroup: OrderGroup<T>? = null
    protected var lastQueueFileChecksum: String? = null

    protected fun fetchOrderGroup(
        villageId: Int,
        instructionFileName: String,
        Clazz: Class<T>
    ): OrderGroup<T>? {
        return kotlin.runCatching {
            val file = loadExternalInstruction(instructionFileName) ?: return null
            if (!file.exists()) return null
            val checkSum = CheckSumUtils.calculateCheckSum(file)
            if (isQueueUnchanged(checkSum)) return this.orderGroup

            LOGGER.info("Fetching $instructionFileName")
            val orderGroup = getOrderGroups(file, Clazz).firstOrNull {
                it.villageId == villageId
            }
            this.orderGroup = orderGroup
            this.lastQueueFileChecksum = checkSum
            orderGroup
        }.onFailure {
            if (it.cause is IOException) {
                LOGGER.error("Failed reading $instructionFileName", it)
            } else {
                LOGGER.error("Failed parsing $instructionFileName", it)
            }
        }.getOrNull()
    }

    private fun isQueueUnchanged(checkSum: String?): Boolean {
        return this.orderGroup != null && this.lastQueueFileChecksum == checkSum
    }

    private fun getOrderGroups(file: File, clazz: Class<T>): List<OrderGroup<T>> {
        val type = objectMapper.typeFactory.constructCollectionType(
            List::class.java,
            objectMapper.typeFactory.constructParametricType(
                OrderGroup::class.java,
                clazz
            )
        )
        return objectMapper.readValue(file, type)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RuntimeTask::class.java)
    }
}

data class OrderGroup<T>(
    val villageId: Int,
    val orderQueue: Set<T>,
)
