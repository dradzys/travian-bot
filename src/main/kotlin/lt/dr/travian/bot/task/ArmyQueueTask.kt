package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory

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

    companion object {
        private val CAPITAL_VILLAGE_ARMY_ORDER = setOf(
            BarrackQueue(troopId = "t2", amount = 1),
        )

        private val ARMY_ORDER_GROUPS = setOf(
            ArmyOrderGroup(villageId = 20217, armyOrder = CAPITAL_VILLAGE_ARMY_ORDER),
        )

        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (300_000L..360_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (6_666L..8_888L)
    }

    override fun isOnCoolDown() = false

    override fun execute() {
        ARMY_ORDER_GROUPS.forEach {
            LOGGER.info("Processing villageId: ${it.villageId}")
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
            ByXPath("//input[@name=\"${armyQueueRequest.troopId}\"]")
        )
        FLUENT_WAIT.until { troopInputElement.isDisplayed }
        if (troopInputElement.isEnabled) {
            troopInputElement.clear()
            troopInputElement.sendKeys(armyQueueRequest.amount.toString())
            val trainButton = DRIVER.findElement(ByXPath("//button[@value=\"ok\"]"))
            trainButton.click()
            LOGGER.info("$armyQueueRequest queued")
        } else {
            LOGGER.info("Not enough resources to queue $armyQueueRequest")
        }
    }

    private fun getRandomDelay(): Long {
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }
}
