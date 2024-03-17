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
        return "RaidUnit(x=$x, y=$y, type=$raidUnitType, troopAmount=$troopAmount)"
    }

}

data class RaidUnitGroup(val villageId: Int, val raidUnitSet: Set<RaidUnit>)

class RaidTask : RescheduledTimerTask() {

    override fun isOnCoolDown() = false

    override fun doWork() {
        RAID_UNIT_GROUPS.forEach { raidUnitGroup ->
            LOGGER.info("Processing villageId: ${raidUnitGroup.villageId}")
            processRaidUnitGroup(raidUnitGroup)
        }
    }

    override fun scheduleDelay() =
        RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()

    override fun clone(): RescheduledTimerTask {
        return RaidTask()
    }

    private fun processRaidUnitGroup(raidUnitGroup: RaidUnitGroup) {
        if (troopsMissing(raidUnitGroup.villageId)) return

        raidUnitGroup.raidUnitSet
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

    private fun troopsMissing(villageId: Int): Boolean {
        val troopList = getTroops(villageId)
        return troopList.isEmpty() || troopList[3].second <= 0
    }

    private fun getTroops(villageId: Int): List<Pair<String, Int>> {
        DRIVER.get("$TRAVIAN_SERVER/build.php?newdid=$villageId&id=39&gid=16&tt=1&filter=3")
        val troopOverviewWebElement = DRIVER.findElements(
            ByXPath("//*[@id=\"build\"]/div/table[1]/tbody[2]/tr/td")
        )
        FLUENT_WAIT.until { troopOverviewWebElement.firstOrNull { it.isDisplayed } ?: true }
        return troopOverviewWebElement.mapIndexed { index, webElement ->
            index.toString() to webElement.text.toInt()
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
        DRIVER.get("$TRAVIAN_SERVER/karte.php?x=${raidUnit.x}&y=${raidUnit.y}&newdid=$villageId")
        val villageInfoElement = DRIVER.findElement(ById("village_info"))
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
            ByXPath("//input[@name=\"troop[t4]\"]")
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
            DRIVER.get("$TRAVIAN_SERVER/karte.php?x=$x&y=$y&newdid=$villageId")
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

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val FIRST_VILLAGE_RAID_UNITS = setOf(
            RaidUnit(-26, -58, VILLAGE, troopAmount = 10),
            RaidUnit(-29, -42, VILLAGE, troopAmount = 6),
            RaidUnit(-24, -37, VILLAGE, troopAmount = 4),
            RaidUnit(-37, -49, VILLAGE, troopAmount = 4),
            RaidUnit(-26, -56, VILLAGE, troopAmount = 4),
            RaidUnit(-19, -62, VILLAGE, troopAmount = 2),
            RaidUnit(-12, -58, VILLAGE, troopAmount = 2),
            RaidUnit(-21, -61, VILLAGE, troopAmount = 2),
            RaidUnit(-22, -26, VILLAGE, troopAmount = 3),
            RaidUnit(-19, -26, VILLAGE, troopAmount = 3),
            RaidUnit(-19, -49, OASIS, troopAmount = 2),
            RaidUnit(-16, -50, OASIS, troopAmount = 2),
            RaidUnit(-22, -49, OASIS, troopAmount = 2),
            RaidUnit(-15, -51, OASIS, troopAmount = 2),
            RaidUnit(-15, -52, OASIS, troopAmount = 2),
            RaidUnit(-20, -46, OASIS, troopAmount = 2),
            RaidUnit(-23, -46, OASIS, troopAmount = 2),
            RaidUnit(-18, -48, OASIS, troopAmount = 2),
            RaidUnit(-18, -55, OASIS, troopAmount = 2),
            RaidUnit(-21, -57, OASIS, troopAmount = 2),
            RaidUnit(-34, -58, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-22, -68, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-19, -55, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-26, -49, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-26, -54, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-17, -45, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-21, -45, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-20, -43, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-25, -44, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-25, -46, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-13, -50, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-27, -44, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-30, -41, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-33, -42, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-14, -42, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-14, -69, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-6, -49, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-12, -53, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-26, -62, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-27, -61, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-29, -62, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-23, -65, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-23, -76, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-17, -65, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-11, -59, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-10, -55, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-12, -53, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-6, -49, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-34, -54, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-30, -69, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-24, -74, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(3, -49, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-14, -39, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-16, -59, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-17, -50, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-17, -40, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-6, -64, VILLAGE, lastSent = LocalDateTime.now()),
            RaidUnit(-7, -65, VILLAGE, lastSent = LocalDateTime.now()),
        ).shuffled().toSet()

        private val CAPITAL_RAID_UNITS = setOf(
            RaidUnit(-29, -42, VILLAGE, troopAmount = 5),
            RaidUnit(-39, -70, VILLAGE, troopAmount = 5),
            RaidUnit(-67, -59, VILLAGE, troopAmount = 2),
            RaidUnit(-38, -43, VILLAGE, troopAmount = 3),
            RaidUnit(-55, -39, VILLAGE, troopAmount = 2),
            RaidUnit(-68, -54, VILLAGE, troopAmount = 2),
            RaidUnit(-37, -49, VILLAGE, troopAmount = 2),
            RaidUnit(-43, -61, VILLAGE, troopAmount = 2),
            RaidUnit(-45, -61, VILLAGE, troopAmount = 3),
            RaidUnit(-45, -65, VILLAGE, troopAmount = 2),
            RaidUnit(-39, -46, VILLAGE, troopAmount = 2),
            RaidUnit(-58, -48, VILLAGE, troopAmount = 2),
            RaidUnit(-46, -66, VILLAGE, troopAmount = 2),
            RaidUnit(-51, -64, VILLAGE, troopAmount = 2),
            RaidUnit(-64, -44, VILLAGE, troopAmount = 2),
            RaidUnit(-53, -69, VILLAGE, troopAmount = 2),
            RaidUnit(-48, -70, VILLAGE, troopAmount = 2),
            RaidUnit(-51, -69, VILLAGE, troopAmount = 2),
            RaidUnit(-51, -72, VILLAGE, troopAmount = 2),
            RaidUnit(-44, -73, VILLAGE, troopAmount = 2),
            RaidUnit(-62, -71, VILLAGE, troopAmount = 2),
            RaidUnit(-65, -69, VILLAGE, troopAmount = 2),
            RaidUnit(-39, -72, VILLAGE, troopAmount = 2),
            RaidUnit(-50, -74, VILLAGE, troopAmount = 2),
            RaidUnit(-36, -74, VILLAGE, troopAmount = 1),
            RaidUnit(-45, -54, OASIS, troopAmount = 2),
            RaidUnit(-44, -54, OASIS, troopAmount = 2),

            // low bounty raid unit, will enable once more troops available
//            RaidUnit(-52, -67, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-66, -68, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-69, -63, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-68, -64, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-32, -38, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-57, -66, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-44, -35, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-59, -51, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-53, -36, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-60, -57, VILLAGE, lastSent = LocalDateTime.now()),
//            RaidUnit(-67, -67, VILLAGE, lastSent = LocalDateTime.now()),
        ).shuffled().toSet()

        private val RAID_UNIT_GROUPS = setOf(
            RaidUnitGroup(18614, FIRST_VILLAGE_RAID_UNITS),
            RaidUnitGroup(22111, CAPITAL_RAID_UNITS),
        )

        private val RESCHEDULE_RANGE_MILLIS = (520_000L..720_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..3333L)
    }
}
