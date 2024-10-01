package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.task.RaidUnitType.OASIS
import lt.dr.travian.bot.task.RaidUnitType.VILLAGE
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ById
import org.openqa.selenium.By.ByLinkText
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.WebElement
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

enum class RaidUnitType { OASIS, VILLAGE }

data class RaidUnit(
    val x: Int,
    val y: Int,
    val raidUnitType: RaidUnitType,
    val troopId: String,
    val troopAmount: Int = 1,
    var lastSent: LocalDateTime? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RaidUnit

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    override fun toString(): String {
        return "RaidUnit(x=$x, y=$y, type=$raidUnitType, troopAmount=$troopAmount, troopId=$troopId)"
    }

}

/**
 * Manual raid task. Goes through the raid unit groups and raids the villages/oasis one-by-one.
 * Occupied oasis or oasis with animals will be skipped.
 * Villages that have turned into Natars will be skipped.
 */
class RaidTask(private val villageId: Int) : RuntimeTask<RaidUnit>() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (520_000L..720_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..3333L)
        private const val RAID_QUEUE_FILE_NAME = "raid-queue.json"
    }

    override fun isOnCoolDown() = false

    override fun execute() {
        fetchOrderGroup(this.villageId, RAID_QUEUE_FILE_NAME, RaidUnit::class.java)?.let { processRaidUnitGroup(it) }
    }

    override fun scheduleDelay() =
        RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()

    override fun clone(): RescheduledTimerTask {
        val raidTask = RaidTask(this.villageId)
        raidTask.orderGroup = this.orderGroup
        raidTask.lastQueueFileChecksum = this.lastQueueFileChecksum
        return raidTask
    }

    private fun processRaidUnitGroup(raidUnitGroup: OrderGroup<RaidUnit>) {
        val troopIds = raidUnitGroup.orderQueue.map { it.troopId }.toSet()
        if (troopsMissing(raidUnitGroup.villageId, troopIds)) return

        raidUnitGroup.orderQueue
            .sortedWith(compareBy(nullsFirst()) { it.lastSent })
            .forEach { raidUnit ->
                kotlin.runCatching {
                    val hasTroopsLeftOver = raid(raidUnit, raidUnitGroup.villageId)
                    if (!hasTroopsLeftOver) {
                        return
                    }
                }.onFailure {
                    LOGGER.error("$raidUnit failed, continuing with next raid", it)
                }
            }
    }

    private fun troopsMissing(villageId: Int, raidTroopIds: Set<String>): Boolean {
        val currentVillageTroops = getTroopMap(villageId)
        return raidTroopIds.all { troopId ->
            currentVillageTroops[troopId]?.let { amount -> amount <= 0 } ?: true
        }
    }

    private fun getTroopMap(villageId: Int): Map<String, Int> {
        DRIVER["$TRAVIAN_SERVER/build.php?newdid=$villageId&id=39&gid=16&tt=1&filter=3"]
        val troopOverviewWebElement = DRIVER.findElements(
            ByXPath("//tbody[@class=\"units last\"]/tr/td")
        )
        FLUENT_WAIT.until { troopOverviewWebElement.firstOrNull { it.isDisplayed } ?: true }
        var count = 0
        return troopOverviewWebElement.associate {
            "t${++count}" to it.text.toInt()
        }
    }


    private fun raid(raidUnit: RaidUnit, villageId: Int): Boolean {
        return when (raidUnit.raidUnitType) {
            OASIS -> raidOasis(raidUnit, villageId)
            VILLAGE -> raidVillage(raidUnit, villageId)
        }
    }

    private fun raidOasis(raidUnit: RaidUnit, villageId: Int): Boolean {
        if (isOasisRaidable(raidUnit.x, raidUnit.y, villageId)) {
            findOasisRaidLink()?.let { return raidAndGetTroopsLeftOver(it, raidUnit) }
                ?: LOGGER.info("$raidUnit is occupied, raid skipped.")
        } else {
            LOGGER.info("$raidUnit has animals or is occupied, raiding skipped")
        }
        return true
    }

    private fun raidVillage(raidUnit: RaidUnit, villageId: Int): Boolean {
        DRIVER["$TRAVIAN_SERVER/karte.php?x=${raidUnit.x}&y=${raidUnit.y}&newdid=$villageId"]
        val villageInfoElement = DRIVER.findElements(ById("village_info")).firstOrNull()
        villageInfoElement?.let {
            val villageTribe = villageInfoElement.findElements(
                ByCssSelector(".first td")
            ).firstOrNull()
            if (villageTribe == null) {
                LOGGER.info("Village Tribe information not present skipping...")
                return true
            }
            if (villageTribe.text == "Natars") {
                LOGGER.info("$raidUnit: Natars village found, skipping raiding. You should consider removing it from raid list")
                return true
            }
            val sendTroopsLink = DRIVER.findElements(ByLinkText("Send troops")).firstOrNull()
            if (sendTroopsLink != null && sendTroopsLink.isEnabled) {
                return raidAndGetTroopsLeftOver(sendTroopsLink, raidUnit)
            } else {
                LOGGER.warn("$raidUnit raid link is disabled. Either still under BP or has been banned")
            }
        }
        return true
    }

    private fun raidAndGetTroopsLeftOver(raidUnitLink: WebElement, raidUnit: RaidUnit): Boolean {
        raidUnitLink.click()
        val ttInputField = DRIVER.findElement(
            ByXPath("//input[@name=\"troop[${raidUnit.troopId}]\"]")
        )
        FLUENT_WAIT.until { ttInputField.isDisplayed }
        return if (ttInputField.isEnabled) {
            ttInputField.sendKeys(raidUnit.troopAmount.toString())
            val raidRadioInput = DRIVER.findElements(
                ByXPath("//input[@value=\"4\"]")
            ).firstOrNull()
            if (raidRadioInput == null) {
                LOGGER.warn("Raid radio input not found! $raidUnit")
                return true
            }
            raidRadioInput.click()
            val okButton = DRIVER.findElement(ByXPath("//button[@id=\"ok\"]"))
            FLUENT_WAIT.until { okButton.isDisplayed }
            okButton.click()

            val confirmButton = DRIVER.findElement(
                ByXPath("//button[@value=\"Confirm\"]")
            )
            FLUENT_WAIT.until { confirmButton.isDisplayed }
            confirmButton.click()
            LOGGER.info("$raidUnit sent")
            raidUnit.lastSent = LocalDateTime.now()
            true
        } else {
            // hasTroopsLeftOver
            false
        }
    }

    private fun isOasisRaidable(x: Int, y: Int, villageId: Int): Boolean {
        return kotlin.runCatching {
            DRIVER["$TRAVIAN_SERVER/karte.php?x=$x&y=$y&newdid=$villageId"]
            val oasisTroopElement = DRIVER.findElement(ByCssSelector("#troop_info td"))
            FLUENT_WAIT.until { oasisTroopElement.isDisplayed }
            return oasisTroopElement.text == "none"
        }.onFailure {
            LOGGER.error("Oasis check failed for ($x|$y) ${it.message}", it)
        }.getOrDefault(false)

    }

    private fun findOasisRaidLink(): WebElement? {
        return DRIVER.findElements(ByLinkText("Raid unoccupied oasis")).firstOrNull()
    }
}
