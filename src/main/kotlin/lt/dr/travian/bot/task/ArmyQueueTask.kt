package lt.dr.travian.bot.task

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.task.ArmyQueueRequest.ArmyBuildingType.BARRACKS
import lt.dr.travian.bot.task.ArmyQueueRequest.ArmyBuildingType.STABLE
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory
import java.time.LocalDateTime


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class ArmyQueueRequest(
    open val troopId: String,
    open val type: ArmyBuildingType,
    open val amount: Int,
) {

    @JsonTypeName("BARRACKS")
    data class BarrackQueue(override val troopId: String, override val amount: Int) :
        ArmyQueueRequest(troopId, BARRACKS, amount)

    @JsonTypeName("STABLE")
    data class StableQueue(override val troopId: String, override val amount: Int) :
        ArmyQueueRequest(troopId, STABLE, amount)

    enum class ArmyBuildingType(var buildingName: String) {
        BARRACKS("Barracks"),
        STABLE("Stable"),
    }
}

class ArmyQueueTask(private val villageId: Int) : RuntimeTask<ArmyQueueRequest>() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (1200_000L..1250_000L) // ~25 minutes
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (6_666L..8_888L)
        private const val INSTRUCTION_FILE_NAME = "army-queue.json"
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        fetchOrderGroup(
            villageId = villageId,
            instructionFileName = INSTRUCTION_FILE_NAME,
            clazz = ArmyQueueRequest::class.java
        )?.orderQueue?.groupBy { it.type }?.let {
            processArmyQueue(villageId, it)
        }
    }

    override fun scheduleDelay(): Long = getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        val armyQueueTask = ArmyQueueTask(this.villageId)
        armyQueueTask.orderGroup = this.orderGroup
        armyQueueTask.lastQueueFileChecksum = this.lastQueueFileChecksum
        return armyQueueTask
    }

    private fun processArmyQueue(
        villageId: Int,
        armyRequestByType: Map<ArmyQueueRequest.ArmyBuildingType, List<ArmyQueueRequest>>
    ) {
        armyRequestByType.forEach { (buildingType, requests) ->
            LOGGER.info("Processing ArmyQueue requests for $buildingType")
            DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${villageId}")
            val armyBuildingSlot = DRIVER.findElements(
                ByXPath("//*[@data-name=\"${buildingType.buildingName}\"]")
            ).firstOrNull()
            val armyBuildingLink = armyBuildingSlot?.findElements(
                ByCssSelector("a")
            )?.firstOrNull()
            armyBuildingLink?.click() ?: return
            inputArmyAmounts(requests)
            if (hasQueuedTroops()) {
                requests.forEach { LOGGER.info("$it queued") }
            }
        }
    }

    private fun inputArmyAmounts(armyQueueRequest: List<ArmyQueueRequest>) {
        armyQueueRequest.forEach {
            val troopInputElement = DRIVER.findElement(
                ByXPath("//input[@name=\"${it.troopId}\"]")
            )
            FLUENT_WAIT.until { troopInputElement.isDisplayed }
            if (troopInputElement.isEnabled) {
                troopInputElement.clear()
                troopInputElement.sendKeys(it.amount.toString())
            }
        }
    }

    private fun hasQueuedTroops(): Boolean {
        val trainArmyBtn = DRIVER.findElements(ByXPath("//button[@value=\"ok\"]"))
            .firstOrNull()
        return if (trainArmyBtn != null && trainArmyBtn.isDisplayed) {
            trainArmyBtn.click()
            true
        } else false
    }

    private fun getRandomDelay(): Long {
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }
}
