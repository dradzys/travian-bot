package lt.dr.travian.bot

import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.task.ArmyQueueTask
import lt.dr.travian.bot.task.BuildingQueueTask
import lt.dr.travian.bot.task.FarmListSendTask
import org.openqa.selenium.ElementNotInteractableException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.system.exitProcess

const val TRAVIAN_SERVER = "https://ts30.x3.international.travian.com"
val TIMER = Timer()
val DRIVER = buildChromeDrive()
val FLUENT_WAIT = DRIVER.fluentWait()
private val LOGGER = LoggerFactory.getLogger("TravianBot")

fun main() {
    if (AuthService.getInstance().isUnAuthenticated()) {
        LOGGER.error("Authentication Failure. Make sure you provide correct credentials and travian server.")
        DRIVER.quit()
        exitProcess(-1)
    }

    setOf(
        FarmListSendTask(),
        BuildingQueueTask(),
        ArmyQueueTask(),
    ).asSequence().shuffled().forEach {
        TIMER.schedule(it, 1000L)
    }
    Thread.currentThread().join()
}

private fun buildChromeDrive(): ChromeDriver {
    val options = ChromeOptions()
    options.addArguments(
        "start-maximized",
        "mute-audio",
        "no-default-browser-check",
        "disable-extensions",
        "disable-infobars",
    )
    options.setExperimentalOption("excludeSwitches", arrayOf("enable-automation"))
    options.setExperimentalOption(
        "prefs",
        mapOf(
            "credentials_enable_service" to false,
            "profile.password_manager_enabled" to false
        )
    )

    val driver = ChromeDriver(options)
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2))
    return driver
}

private fun ChromeDriver.fluentWait(): Wait<ChromeDriver> {
    return FluentWait(this)
        .withTimeout(Duration.ofSeconds(5))
        .pollingEvery(Duration.ofMillis((300L..500L).random()))
        .ignoring(ElementNotInteractableException::class.java)
}
