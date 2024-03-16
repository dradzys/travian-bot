package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory
import java.time.LocalTime

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

data class ArmyOrderGroup(
    val villageId: Int,
    val armyOrder: Set<ArmyQueueRequest>,
)

class ArmyQueueTask : RescheduledTimerTask() {

    override fun isOnCoolDown() = false

    override fun doWork() {
        ARMY_ORDER_GROUPS.forEach {
            LOGGER.info("Processing ${it.villageId}")
            processArmyOrderGroup(it)
        }
    }

    override fun scheduleDelay(): Long = getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        return ArmyQueueTask()
    }

    private fun processArmyOrderGroup(armyOrderGroup: ArmyOrderGroup) {
        armyOrderGroup.armyOrder.forEach { armyQueueRequest ->
            DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${armyOrderGroup.villageId}")
            val armyBuildingSlot = DRIVER.findElements(
                ByXPath("//*[@data-name=\"${armyQueueRequest.buildingName}\"]")
            ).firstOrNull()
            val armyBuildingLink = armyBuildingSlot?.findElements(
                ByCssSelector("a")
            )?.firstOrNull()
            armyBuildingLink?.click() ?: return
            queueArmy(armyQueueRequest)
        }
    }

    private fun queueArmy(armyQueueRequest: ArmyQueueRequest) {
        val troopInputElement = DRIVER.findElement(
            ByXPath("//*[@name=\"${armyQueueRequest.troopId}\"]")
        )
        FLUENT_WAIT.until { troopInputElement.isDisplayed }
        if (troopInputElement.isEnabled) {
            troopInputElement.clear()
            troopInputElement.sendKeys(armyQueueRequest.amount.toString())
            val trainButton = DRIVER.findElement(ByXPath("//button[@value=\"ok\"]"))
            trainButton.click()
            LOGGER.info("$armyQueueRequest queued")
        } else {
            LOGGER.info("Not enough resource to queue $armyQueueRequest")
        }
    }

    private fun getTroopCreationTimeInMillis(troopId: String): Long {
        return kotlin.runCatching {
            val troopCreationTime = DRIVER.findElement(ByXPath("//*[@data-troopid=\"$troopId\"]"))
                .findElement(ByCssSelector(".duration span")).text
            timeToMillis(troopCreationTime)
        }.getOrNull() ?: getRandomDelay()
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
        private val FIRST_VILLAGE_ARMY_ORDER = setOf(
            BarrackQueue(troopId = "t1", amount = 4), // phalanx
        )

        private val CAPITAL_VILLAGE_ARMY_ORDER = setOf(
            BarrackQueue(troopId = "t1", amount = 1), // phalanx
        )

        private val ARMY_ORDER_GROUPS = setOf(
            ArmyOrderGroup(villageId = 18614, armyOrder = FIRST_VILLAGE_ARMY_ORDER),
            ArmyOrderGroup(villageId = 22111, armyOrder = CAPITAL_VILLAGE_ARMY_ORDER),
        )

        private val RESCHEDULE_RANGE_MILLIS = (500_000L..600_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (66_666L..88_888L)
    }
}
