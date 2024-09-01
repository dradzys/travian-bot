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
        private val RESCHEDULE_RANGE_MILLIS = (400_000L..450_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (6_666L..8_888L)
        private const val INSTRUCTION_FILE_PATH = "src/main/resources/army-queue.json"
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        fetchOrderGroup(villageId, INSTRUCTION_FILE_PATH, ArmyQueueRequest::class.java)?.let {
            processArmyOrderGroup(it)
        }
    }

    override fun scheduleDelay(): Long = getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        val armyQueueTask = ArmyQueueTask(this.villageId)
        armyQueueTask.orderGroup = this.orderGroup
        armyQueueTask.lastQueueFileChecksum = this.lastQueueFileChecksum
        return armyQueueTask
    }

    private fun processArmyOrderGroup(armyOrderGroup: OrderGroup<ArmyQueueRequest>) {
        armyOrderGroup.orderQueue.forEach { armyQueueRequest ->
            DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${armyOrderGroup.villageId}")
            val armyBuildingSlot = DRIVER.findElements(
                ByXPath("//*[@data-name=\"${armyQueueRequest.type.buildingName}\"]")
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
