package lt.dr.travian.bot

import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.task.ArmyQueueTask
import lt.dr.travian.bot.task.BuildingQueueTask
import lt.dr.travian.bot.task.RaidTask
import org.openqa.selenium.ElementNotInteractableException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.system.exitProcess


const val TRAVIAN_SERVER = "https://ts20.x2.europe.travian.com"

fun main() {
    val driver = configureChromeDriver()
    val authService = AuthService(driver)
    if (authService.isUnAuthenticated()) {
        LOGGER.error("Authentication Failure. Make sure you provide correct credentials and server is not under maintenance")
        driver.quit()
        exitProcess(-1)
    }

    val timer = Timer()
    setOf(
        RaidTask(driver = driver, authService = authService, timer = timer),
        BuildingQueueTask(driver = driver, authService = authService, timer = timer),
        ArmyQueueTask(driver = driver, authService = authService, timer = timer),
    ).asSequence().shuffled().forEach {
        timer.schedule(it, 1000L)
    }

    Thread.currentThread().join()
}

private fun configureChromeDriver(): ChromeDriver {
    val options = ChromeOptions()
//    options.addArguments("--headless=new")
    options.addArguments("start-maximized")
    options.addArguments("mute-audio")
    options.addArguments("no-default-browser-check")
    options.addArguments("disable-extensions")
    options.addArguments("disable-infobars")
    options.setExperimentalOption("excludeSwitches", arrayOf("enable-automation"))
    options.setExperimentalOption(
        "prefs",
        mapOf("credentials_enable_service" to false, "profile.password_manager_enabled" to false)
    )

    val driver = ChromeDriver(options)
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2))
    return driver
}

fun ChromeDriver.fluentWait(): Wait<ChromeDriver> {
    return FluentWait(this)
        .withTimeout(Duration.ofSeconds(5))
        .pollingEvery(Duration.ofMillis((300L..500L).random()))
        .ignoring(ElementNotInteractableException::class.java)
}

private val LOGGER = LoggerFactory.getLogger("TravianBot")
