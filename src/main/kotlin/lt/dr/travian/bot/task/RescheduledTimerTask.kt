package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TIMER
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.buildChromeDrive
import lt.dr.travian.bot.fluentWait
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * A specialized implementation of java.util.TimerTask, which
 * reschedules itself for varying period after each execution.
 */
abstract class RescheduledTimerTask(
    private val authService: AuthService = AuthService.getInstance()
) : TimerTask() {

    private val failedTaskCount = AtomicInteger(0)
    protected var recoverThreshold = 3

    override fun run() {
        LOGGER.info("${this.javaClass.simpleName} started")
        kotlin.runCatching {
            if (!isOnCoolDown()) {
                authService.authenticate()
                execute()
                failedTaskCount.set(0)
            } else {
                LOGGER.info("${this.javaClass.simpleName} is on coolDown period")
            }
            schedule(scheduleDelay())
        }.onFailure {
            failedTaskCount.incrementAndGet()
            LOGGER.info("${this.javaClass.simpleName} failed", it)
            if (isBrowserClosed()) {
                reOpenBrowser()
                DRIVER.get(TRAVIAN_SERVER)
                authService.authenticate()
            }
        }.recover {
            if (shouldRecoverFailedTask()) {
                schedule(recoverDelay())
            } else {
                LOGGER.warn("${this.javaClass.simpleName} failed too many times. Stopping further recovery.")
            }
        }
        LOGGER.info("${this.javaClass.simpleName} completed")
    }

    abstract fun isOnCoolDown(): Boolean

    abstract fun execute()

    abstract fun scheduleDelay(): Long

    private fun recoverDelay(): Long =
        RECOVER_DELAY_RANGE.random() + RANDOM_ADDITIONAL_RECOVER_RANGE.random()

    private fun shouldRecoverFailedTask() = failedTaskCount.get() < recoverThreshold

    abstract fun clone(): RescheduledTimerTask

    private fun schedule(delay: Long) {
        TIMER.schedule(clone(), delay)
        LOGGER.info("${this.javaClass.simpleName} scheduled at delay: $delay")
    }

    private fun isBrowserClosed(): Boolean {
        return kotlin.runCatching {
            DRIVER.windowHandles.isNullOrEmpty()
        }.onFailure {
            LOGGER.warn("Closed browser detected, will attempt to open new instance")
        }.getOrDefault(true)
    }

    private fun reOpenBrowser() {
        DRIVER = buildChromeDrive()
        FLUENT_WAIT = DRIVER.fluentWait()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RescheduledTimerTask::class.java)
        private val RECOVER_DELAY_RANGE = (600_000L..1200_000L)
        private val RANDOM_ADDITIONAL_RECOVER_RANGE = (1111L..5555L)
    }
}
