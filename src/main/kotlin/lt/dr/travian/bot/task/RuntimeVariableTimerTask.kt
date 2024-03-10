package lt.dr.travian.bot.task

import lt.dr.travian.bot.auth.AuthService
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A specialized implementation of java.util.TimerTask, which
 * reschedules itself for varying period after each execution.
 */
abstract class RuntimeVariableTimerTask(
    private val authService: AuthService,
    private val clazz: Class<*>
) : TimerTask() {

    override fun run() {
        LOGGER.info("${clazz.simpleName} started")
        kotlin.runCatching {
            if (!isOnCoolDown()) {
                authenticate()
                doWork()
            } else {
                LOGGER.info("${clazz.simpleName} is on coolDown period")
            }
        }.onFailure { LOGGER.info("${clazz.simpleName} failed", it) }
        reSchedule()
        LOGGER.info("${clazz.simpleName} completed")
    }

    abstract fun isOnCoolDown(): Boolean

    abstract fun doWork()

    abstract fun reSchedule()

    private fun authenticate() {
        if (authService.isLoggedOut()) {
            authService.reAuthenticate()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RuntimeVariableTimerTask::class.java)
    }
}
