package lt.dr.travian.bot.task

import lt.dr.travian.bot.TIMER
import lt.dr.travian.bot.auth.AuthService
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A specialized implementation of java.util.TimerTask, which
 * reschedules itself for varying period after each execution.
 */
abstract class RescheduledTimerTask(
    private val authService: AuthService = AuthService.getInstance()
) : TimerTask() {

    override fun run() {
        LOGGER.info("${this.javaClass.simpleName} started")
        kotlin.runCatching {
            if (!isOnCoolDown()) {
                authService.authenticate()
                execute()
                schedule(scheduleDelay())
            } else {
                LOGGER.info("${this.javaClass.simpleName} is on coolDown period")
            }
        }
            .onFailure { LOGGER.info("${this.javaClass.simpleName} failed", it) }
            .recover { schedule(recoverDelay()) }
        LOGGER.info("${this.javaClass.simpleName} completed")
    }

    abstract fun isOnCoolDown(): Boolean

    abstract fun execute()

    abstract fun scheduleDelay(): Long

    private fun recoverDelay(): Long =
        RECOVER_DELAY_RANGE.random() + RANDOM_ADDITIONAL_RECOVER_RANGE.random()

    abstract fun clone(): RescheduledTimerTask

    private fun schedule(delay: Long) {
        TIMER.schedule(clone(), delay)
        LOGGER.info("${this.javaClass.simpleName} scheduled at delay: $delay")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RescheduledTimerTask::class.java)
        private val RECOVER_DELAY_RANGE = (600_000L..1200_000L)
        private val RANDOM_ADDITIONAL_RECOVER_RANGE = (1111L..5555L)
    }
}
