package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory


class FarmListSendTask : RescheduledTimerTask() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        // 25 to 30 minutes
        private val RESCHEDULE_RANGE_MILLIS = (1_250_000L..1_300_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }

    override fun isOnCoolDown() = false

    override fun execute() {
        DRIVER["$TRAVIAN_SERVER/build.php?id=39&gid=16&tt=99"]
        val startFarmListAllBtn =
            DRIVER.findElements(ByXPath("//*[@id=\"stickyWrapper\"]/button"))
                .firstOrNull { it.getAttribute("class").contains("startAllFarmLists") }
        startFarmListAllBtn?.let {
            FLUENT_WAIT.until { startFarmListAllBtn.isDisplayed && startFarmListAllBtn.isEnabled }
            startFarmListAllBtn.click()
        } ?: LOGGER.warn("start farm list btn not present")
    }

    override fun scheduleDelay(): Long {
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }

    override fun clone(): RescheduledTimerTask {
        return FarmListSendTask()
    }
}
