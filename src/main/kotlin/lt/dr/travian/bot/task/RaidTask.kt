package lt.dr.travian.bot.task

import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.fluentWait
import lt.dr.travian.bot.task.RaidUnitType.OASIS
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

enum class RaidUnitType { OASIS, VILLAGE }

data class RaidUnit(
    val x: Int,
    val y: Int,
    val raidUnitType: RaidUnitType,
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
        return "RaidUnit(x=$x, y=$y)"
    }

}

data class RaidUnitGroup(val villageId: Int, val raidUnitSet: Set<RaidUnit>)

class RaidTask(
    private val driver: ChromeDriver,
    private val wait: Wait<ChromeDriver> = driver.fluentWait(),
    private val authService: AuthService,
    private val timer: Timer
) : RuntimeVariableTimerTask(authService, RaidTask::class.java) {

    override fun isOnCoolDown() = false

    // TODO: add support for village raiding
    // TODO: when checking oasis troops, if it has elephants or crocodiles add a log or smth. We can then claim good animals that are close to use.
    override fun doWork() {
        if (troopsMissing()) return

        RAID_UNIT_GROUPS.forEach {
            run breaking@{
                LOGGER.info("Raid farm list size: ${it.raidUnitSet.size}")
                it.raidUnitSet.sortedWith(compareBy(nullsFirst()) { it.lastSent })
                    .forEach { raidUnit ->
                        kotlin.runCatching {
                            val hasTroopsLeftOver = raid(raidUnit)
                            if (!hasTroopsLeftOver) {
                                return@breaking
                            }
                        }.onFailure {
                            LOGGER.error("Raid $$raidUnit failed, continuing with next raid", it)
                        }
                    }
            }
        }

    }

    override fun reSchedule() {
        val randomDelay = RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
        timer.schedule(
            RaidTask(driver = driver, authService = authService, timer = timer),
            randomDelay
        )
        LOGGER.info("${this::class.java.simpleName} scheduled at delay: $randomDelay")
    }

    private fun troopsMissing(): Boolean {
        val troopList = getTroops()
        return troopList.isEmpty() || troopList[3].second <= 0
    }

    private fun getTroops(): List<Pair<String, Int>> {
        driver.get("$TRAVIAN_SERVER/build.php?id=39&gid=16&tt=1&filter=3")
        val troopOverviewWebElement = driver.findElements(
            ByXPath("//*[@id=\"build\"]/div/table[1]/tbody[2]/tr/td")
        )
        wait.until { troopOverviewWebElement.firstOrNull { it.isDisplayed } ?: true }
        return troopOverviewWebElement.mapIndexed { index, webElement ->
            index.toString() to webElement.text.toInt()
        }
    }


    private fun raid(raidUnit: RaidUnit): Boolean {
        if (isOasisRaidable(raidUnit.x, raidUnit.y)) {
            findOasisRaidLink()?.let {
                it.click()
                val ttInputField = driver.findElement(
                    ByXPath("//*[@id=\"troops\"]/tbody/tr[1]/td[2]/input")
                )
                wait.until { ttInputField.isDisplayed }
                if (ttInputField.isEnabled) {
                    ttInputField.sendKeys("1")
                    val okButton = driver.findElement(ByXPath("//*[@id=\"ok\"]"))
                    wait.until { okButton.isDisplayed }
                    okButton.click()

                    val confirmButton =
                        driver.findElement(ByXPath("//*[@id=\"rallyPointButtonsContainer\"]/button[3]"))
                    wait.until { confirmButton.isDisplayed }
                    confirmButton.click()
                    LOGGER.info("$raidUnit raid is sent")
                    raidUnit.lastSent = LocalDateTime.now()
                } else {
                    // hasTroopsLeftOver
                    return false
                }
            }
        } else {
            LOGGER.info("$raidUnit has animals or is occupied, raiding skipped")
        }
        return true
    }

    private fun isOasisRaidable(x: Int, y: Int): Boolean {
        return kotlin.runCatching {
            driver.get("$TRAVIAN_SERVER/karte.php?x=$x&y=$y")
            val oasisTroopElement = driver.findElement(ByCssSelector("#troop_info td"))
            wait.until { oasisTroopElement.isDisplayed }
            return oasisTroopElement.text == "none"
        }.onFailure {
            LOGGER.error("Oasis check failed for ($x|$y) ${it.message}", it)
        }.getOrDefault(false)

    }

    private fun findOasisRaidLink(): WebElement? {
        return try {
            driver.findElement(
                ByXPath("//*[@id=\"tileDetails\"]/div[1]/div[1]/div[2]/a")
            )
        } catch (e: NoSuchElementException) {
            driver.findElement(
                ByXPath("//*[@id=\"tileDetails\"]/div[1]/div[1]/div[3]/a")
            )
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val FIRST_VILLAGE_RAID_UNITS = setOf(
            RaidUnit(-19, -49, OASIS),
            RaidUnit(-15, -51, OASIS),
            RaidUnit(-15, -52, OASIS),
            RaidUnit(-16, -50, OASIS),
            RaidUnit(-20, -45, OASIS),
            RaidUnit(-20, -46, OASIS),
            RaidUnit(-23, -46, OASIS),
            RaidUnit(-21, -57, OASIS),
            RaidUnit(-18, -48, OASIS),
            RaidUnit(-22, -52, OASIS),
            RaidUnit(-22, -49, OASIS),
            RaidUnit(-29, -50, OASIS),
            RaidUnit(-29, -55, OASIS),
            RaidUnit(-29, -56, OASIS),
            RaidUnit(-15, -59, OASIS),
            RaidUnit(-15, -60, OASIS),
            RaidUnit(-15, -45, OASIS),
            RaidUnit(-17, -42, OASIS),
            RaidUnit(-14, -60, OASIS),
            RaidUnit(-14, -46, OASIS),
            RaidUnit(-18, -53, OASIS),
            RaidUnit(-18, -55, OASIS),
            RaidUnit(-18, -42, OASIS),
            RaidUnit(-18, -41, OASIS),
            RaidUnit(-17, -53, OASIS),
            RaidUnit(-17, -47, OASIS),
            RaidUnit(-22, -53, OASIS),
            RaidUnit(-23, -53, OASIS),
            RaidUnit(-21, -54, OASIS),
            RaidUnit(-23, -39, OASIS),
            RaidUnit(-24, -39, OASIS),
            RaidUnit(-9, -51, OASIS),
            RaidUnit(-12, -60, OASIS),
            RaidUnit(-13, -62, OASIS),
            RaidUnit(-33, -48, OASIS),
            RaidUnit(-22, -55, OASIS),
            RaidUnit(-23, -56, OASIS),
            RaidUnit(-24, -55, OASIS),
            RaidUnit(-26, -57, OASIS),
            RaidUnit(-27, -54, OASIS),
            RaidUnit(-26, -51, OASIS),
            RaidUnit(-30, -46, OASIS),
        ).shuffled().toSet()

        private val RAID_UNIT_GROUPS = setOf(
            RaidUnitGroup(18614, FIRST_VILLAGE_RAID_UNITS)
        )

        // 17 to 21 min
        private val RESCHEDULE_RANGE_MILLIS = (102_0000L..1260_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..3333L)
    }
}
