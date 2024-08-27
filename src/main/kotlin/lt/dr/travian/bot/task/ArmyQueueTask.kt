package lt.dr.travian.bot.task

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.core.type.TypeReference
import lt.dr.travian.bot.CheckSumUtils
import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.objectMapper
import lt.dr.travian.bot.task.ArmyQueueRequest.ArmyBuildingType.BARRACKS
import lt.dr.travian.bot.task.ArmyQueueRequest.ArmyBuildingType.STABLE
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
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

data class ArmyOrderGroup(
    val villageId: Int,
    val armyOrder: Set<ArmyQueueRequest>,
)

class ArmyQueueTask(private val villageId: Int) : RescheduledTimerTask() {

    private var armyOrderGroup: ArmyOrderGroup? = null
    private var lastArmyQueueFileCheckSum: String? = null

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (400_000L..450_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (6_666L..8_888L)
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        fetchArmyOrderGroup(this.villageId)?.let { processArmyOrderGroup(it) }
    }

    override fun scheduleDelay(): Long = getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        val armyQueueTask = ArmyQueueTask(this.villageId)
        armyQueueTask.armyOrderGroup = this.armyOrderGroup
        armyQueueTask.lastArmyQueueFileCheckSum = this.lastArmyQueueFileCheckSum
        return armyQueueTask
    }

    // todo: extract this method and reuse in all three tasks, since its basically the same
    private fun fetchArmyOrderGroup(villageId: Int): ArmyOrderGroup? {
        return kotlin.runCatching {
            val armyQueueFile = File("src/main/resources/army-queue.json")
            val checkSum = CheckSumUtils.calculateCheckSum(armyQueueFile)
            if (isArmyQueueUnchanged(checkSum)) return this.armyOrderGroup

            LOGGER.info("Fetching army-queue.json")
            val armyOrderGroup = objectMapper.readValue(
                armyQueueFile,
                object : TypeReference<List<ArmyOrderGroup>>() {}
            ).firstOrNull { it.villageId == villageId }
            this.armyOrderGroup = armyOrderGroup
            this.lastArmyQueueFileCheckSum = checkSum
            armyOrderGroup
        }.onFailure {
            if (it.cause is IOException) {
                LOGGER.error("Failed reading army-queue.json", it)
            } else {
                LOGGER.error("ArmyOrderGroup not found for villageId: $villageId", it)
            }
        }.getOrNull()
    }

    private fun isArmyQueueUnchanged(checkSum: String?): Boolean {
        return this.armyOrderGroup != null && this.lastArmyQueueFileCheckSum == checkSum
    }

    private fun processArmyOrderGroup(armyOrderGroup: ArmyOrderGroup) {
        armyOrderGroup.armyOrder.forEach { armyQueueRequest ->
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
