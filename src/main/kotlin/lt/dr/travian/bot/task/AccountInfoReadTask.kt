package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.IS_PLUS_ACCOUNT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.TRIBE
import lt.dr.travian.bot.Tribe
import lt.dr.travian.bot.toTribe
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory

class AccountInfoReadTask : RescheduledTimerTask() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AccountInfoReadTask::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (900_000L..950_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }

    override fun isOnCoolDown(): Boolean = false

    override fun execute() {
        assignPlusAccount()
        if (TRIBE == null) {
            assignTribe()
        }
    }

    override fun scheduleDelay(): Long {
        return getPlusAccountEndTimeMillis()
            ?: (RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random())
    }

    override fun clone(): RescheduledTimerTask {
        return AccountInfoReadTask()
    }

    private fun assignPlusAccount() {
        val plusAccountEndsInMillis = getPlusAccountEndTimeMillis()
        IS_PLUS_ACCOUNT = plusAccountEndsInMillis != null && plusAccountEndsInMillis > 0
        LOGGER.info("IS_PLUS_ACCOUNT: $IS_PLUS_ACCOUNT")
    }

    private fun getPlusAccountEndTimeMillis(): Long? {
        DRIVER["$TRAVIAN_SERVER/dorf1.php"]
        DRIVER.findElements(ByXPath("//a[@class=\"shop\"]")).firstOrNull()?.click()
        FLUENT_WAIT.until {
            DRIVER.findElements(ByXPath("//div[@class=\"paymentImage\"]")).isNotEmpty()
        }
        DRIVER.findElements(ByXPath("//a[@data-tabname=\"pros\"]")).firstOrNull()?.click()
            ?: LOGGER.warn("Advantages tab not found")
        return DRIVER.findElements(ByXPath("//*[@class=\"bonusEndsSoon\"]/span"))
            .firstOrNull()?.let {
                it.getAttribute("value")?.toLongOrNull()?.times(1000)
            }
    }

    private fun assignTribe() {
        DRIVER["$TRAVIAN_SERVER/dorf2.php"]
        val tribe = Tribe.values().map { it.id }.firstOrNull { tribeId ->
            DRIVER.findElements(ByXPath("//*[@class=\"building g10 $tribeId\"]")).isNotEmpty()
        }?.toTribe()
        if (tribe == null) {
            LOGGER.warn("Cannot resolve Tribe value!")
        } else {
            TRIBE = tribe
            LOGGER.info("Tribe: $TRIBE")
        }
    }
}