package lt.dr.travian.bot.task

import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.fluentWait
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.util.*

sealed interface ArmyQueueRequest {
    val troopId: String
    val buildingName: String
    val amount: Int
}

data class BarrackQueue(
    override val troopId: String,
    override val buildingName: String = "Barracks",
    override val amount: Int,
) : ArmyQueueRequest

data class StableQueue(
    override val troopId: String,
    override val buildingName: String = "Stable",
    override val amount: Int,
) : ArmyQueueRequest

class ArmyQueueTask(
    private val driver: ChromeDriver,
    private val wait: Wait<ChromeDriver> = driver.fluentWait(),
    private val authService: AuthService,
    private val timer: Timer
) : RuntimeVariableTimerTask(authService, ArmyQueueTask::class.java) {

    override fun isOnCoolDown() = false

    override fun doWork() {
        ARMY_ORDER.forEach { armyQueueRequest ->
            driver.get("$TRAVIAN_SERVER/dorf2.php")
            val armyBuildingSlot = driver.findElements(
                ByXPath("//*[@data-name=\"${armyQueueRequest.buildingName}\"]")
            ).firstOrNull()
            val armyBuildingLink = armyBuildingSlot?.findElements(
                ByCssSelector("a")
            )?.firstOrNull()
            armyBuildingLink?.click() ?: return
            queueArmy(armyQueueRequest)
        }
    }

    override fun reSchedule() {
        val delay = getTroopCreationTimeInMillis("t1") ?: getRandomDelay()
        timer.schedule(
            ArmyQueueTask(driver = driver, authService = authService, timer = timer),
            delay
        )
        LOGGER.info("${this::class.java.simpleName} scheduled at delay: $delay")
    }

    private fun queueArmy(armyQueueRequest: ArmyQueueRequest) {
        val troopInputElement = driver.findElement(
            ByXPath("//*[@name=\"${armyQueueRequest.troopId}\"]")
        )
        wait.until { troopInputElement.isDisplayed }
        if (troopInputElement.isEnabled) {
            troopInputElement.clear()
            troopInputElement.sendKeys(armyQueueRequest.amount.toString())
            val trainButton = driver.findElement(ByXPath("//button[@value=\"ok\"]"))
            trainButton.click()
            LOGGER.info("$armyQueueRequest queued")
        } else {
            LOGGER.info("Not enough resource to queue $armyQueueRequest")
        }
    }

    private fun getTroopCreationTimeInMillis(troopId: String): Long? {
        return kotlin.runCatching {
            val troopCreationTime = driver.findElement(ByXPath("//*[@data-troopid=\"$troopId\"]"))
                .findElement(ByCssSelector(".duration span")).text
            timeToMillis(troopCreationTime)
        }.getOrNull()
    }

    /**
     * HH:MM:ss conversion to Millis
     */
    private fun timeToMillis(time: String): Long {
        val timeSplit = time.split(":")
        val localTime = LocalTime.of(
            timeSplit[0].toInt(),
            timeSplit[1].toInt(),
            timeSplit[2].toInt()
        )
        return getTotalSeconds(localTime) * 1000L
    }

    private fun getTotalSeconds(localTime: LocalTime): Int {
        return (((localTime.hour * 60) + localTime.minute) * 60) + localTime.second
    }

    private fun getRandomDelay(): Long {
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val ARMY_ORDER = setOf(
            BarrackQueue(troopId = "t1", amount = 1), // phalanx
//            StableQueue(troopId = "t4", amount = 1), // tt
        )

        private val RESCHEDULE_RANGE_MILLIS = (400_000L..800_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (66_666L..111_111L)
    }
}
