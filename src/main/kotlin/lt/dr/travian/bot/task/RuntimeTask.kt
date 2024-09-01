package lt.dr.travian.bot.task

import lt.dr.travian.bot.CheckSumUtils
import lt.dr.travian.bot.objectMapper
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
        instructionPath: String,
        Clazz: Class<T>
    ): OrderGroup<T>? {
        return kotlin.runCatching {
            val instructionFile = File(instructionPath)
            val checkSum = CheckSumUtils.calculateCheckSum(instructionFile)
            if (isQueueUnchanged(checkSum)) return this.orderGroup

            LOGGER.info("Fetching $instructionPath")
            val type = objectMapper.typeFactory.constructCollectionType(
                List::class.java,
                objectMapper.typeFactory.constructParametricType(
                    OrderGroup::class.java,
                    Clazz
                )
            )
            val orderGroups: List<OrderGroup<T>> = objectMapper.readValue(instructionFile, type)
            val orderGroup = orderGroups.firstOrNull { it.villageId == villageId }
            this.orderGroup = orderGroup
            this.lastQueueFileChecksum = checkSum
            orderGroup
        }.onFailure {
            if (it.cause is IOException) {
                LOGGER.error("Failed reading $instructionPath", it)
            } else {
                LOGGER.error("Failed parsing $instructionPath", it)
            }
        }.getOrNull()
    }

    private fun isQueueUnchanged(checkSum: String?): Boolean {
        return this.orderGroup != null && this.lastQueueFileChecksum == checkSum
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RuntimeTask::class.java)
    }
}

data class OrderGroup<T>(
    val villageId: Int,
    val orderQueue: Set<T>,
)
