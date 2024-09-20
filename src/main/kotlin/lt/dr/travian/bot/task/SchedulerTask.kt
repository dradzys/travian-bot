package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TIMER
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.VILLAGE_LIST
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory

/**
 * A SchedulerTask is responsible:
 * 1. Reading list of villages and tribe value.
 * 2. Scheduling tasks for villages
 */
class SchedulerTask : RescheduledTimerTask() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (900_000L..950_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (6_666L..8_888L)
    }

    override fun isOnCoolDown(): Boolean = false

    override fun execute() {
        val villageIds = getVillageIds()
        LOGGER.info("VillageList: ${villageIds.joinToString()}")
        if (VILLAGE_LIST != villageIds) {
            scheduleTasks(villageIds)
        } else {
            LOGGER.info("VillageList un-changed")
        }
    }

    private fun getVillageIds(): Set<Int> {
        if (DRIVER.currentUrl != "$TRAVIAN_SERVER/dorf1.php") {
            DRIVER.get("$TRAVIAN_SERVER/dorf1.php")
        }
        FLUENT_WAIT.until {
            DRIVER.findElements(ByXPath("//div[@class=\"villageList\"]")).isNotEmpty()
        }
        return DRIVER.findElements(
            ByXPath("//div[@class=\"villageList\"]/div/div/span")
        ).mapNotNull { element ->
            val villageIdStr = element.getAttribute("data-did")
            if (!villageIdStr.isNullOrBlank()) {
                villageIdStr.toInt()
            } else null
        }.toSet()
    }

    /**
     * On first run schedule for all villages
     * On subsequent runs schedule only for newly added villages
     */
    private fun scheduleTasks(villageIds: Set<Int>) {
        val difference = villageIds.subtract(VILLAGE_LIST)
        VILLAGE_LIST = villageIds
        difference.forEach { villageId ->
            setOf(
                BuildingQueueTask(villageId),
                ArmyQueueTask(villageId),
                RaidTask(villageId),
            ).asSequence().shuffled().forEach {
                TIMER.schedule(it, 1000L)
            }
        }
    }

    override fun scheduleDelay(): Long =
        RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random() // ~15 minutes

    override fun clone(): RescheduledTimerTask {
        return SchedulerTask()
    }
}