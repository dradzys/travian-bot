package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.IS_PLUS_ACCOUNT
import lt.dr.travian.bot.TRAVIAN_SERVER
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
        val plusAccountEndsInMillis = getPlusAccountEndTimeMillis()
        IS_PLUS_ACCOUNT = plusAccountEndsInMillis != null && plusAccountEndsInMillis > 0
        // TODO: read and assign tribe value
        LOGGER.info("IS_PLUS_ACCOUNT: $IS_PLUS_ACCOUNT")
    }

    override fun scheduleDelay(): Long {
        return getPlusAccountEndTimeMillis()
            ?: (RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random())
    }

    private fun getPlusAccountEndTimeMillis(): Long? {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php")
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

    override fun clone(): RescheduledTimerTask {
        return AccountInfoReadTask()
    }
}