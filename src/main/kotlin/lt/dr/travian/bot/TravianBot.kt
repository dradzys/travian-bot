package lt.dr.travian.bot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.auth.CredentialService.getEnvironmentVariable
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

val TRAVIAN_SERVER = getEnvironmentVariable("TRAVIAN_SERVER")
val TIMER = Timer()
var DRIVER = buildChromeDrive()
var FLUENT_WAIT = DRIVER.fluentWait()
val objectMapper = jacksonObjectMapper()
private val LOGGER = LoggerFactory.getLogger("TravianBot")

fun main() {
    if (AuthService.getInstance().isUnAuthenticated()) {
        LOGGER.error("Authentication Failure. Make sure you provide correct credentials and travian server.")
        DRIVER.quit()
        exitProcess(-1)
    }

    setOf(
        FarmListSendTask(),
        BuildingQueueTask(19421),
        ArmyQueueTask(19421),
        BuildingQueueTask(21287),
        ArmyQueueTask(21287),
        BuildingQueueTask(23311),
    ).asSequence().shuffled().forEach {
        TIMER.schedule(it, 1000L)
    }
    Thread.currentThread().join()
}

fun buildChromeDrive(): ChromeDriver {
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

fun ChromeDriver.fluentWait(): Wait<ChromeDriver> {
    return FluentWait(this)
        .withTimeout(Duration.ofSeconds(5))
        .pollingEvery(Duration.ofMillis((1000L..2500L).random()))
        .ignoring(ElementNotInteractableException::class.java)
}
