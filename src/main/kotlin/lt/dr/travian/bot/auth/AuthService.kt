package lt.dr.travian.bot.auth

import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.fluentWait
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory

class AuthService(
    private val driver: ChromeDriver,
    private val wait: Wait<ChromeDriver> = driver.fluentWait(),
    private val credentials: Credentials = CredentialService.getCredentials(),
) {

    private val loginButtonXpath = ByXPath("//button[@value=\"Login\"]")

    fun isUnAuthenticated(): Boolean {
        driver.get(TRAVIAN_SERVER)
        inputUsername()
        inputPassword()
        login()
        return hasAuthErrors()
    }

    fun authenticate() {
        if (this.isLoggedOut()) {
            this.reAuthenticate()
        }
    }

    private fun inputUsername() {
        val userNameInput = driver.findElement(
            ByXPath("//input[@name=\"name\"]")
        )
        wait.until { userNameInput.isDisplayed && userNameInput.isEnabled }
        userNameInput.clear()
        userNameInput.sendKeys(credentials.username)
    }

    private fun inputPassword() {
        val passwordInput = driver.findElement(
            ByXPath("//input[@name=\"password\"]")
        )
        wait.until { passwordInput.isDisplayed && passwordInput.isEnabled }
        passwordInput.clear()
        passwordInput.sendKeys(credentials.password)
    }

    private fun login() {
        val loginButton = driver.findElement(loginButtonXpath)
        wait.until { loginButton.isDisplayed && loginButton.isEnabled }
        loginButton.click()
    }

    private fun hasAuthErrors(): Boolean {
        return driver.findElements(ByXPath("//*[@id=\"error.LTR\"]")).isNotEmpty()
    }

    private fun isLoggedOut(): Boolean {
        return driver.currentUrl == TRAVIAN_SERVER || driver.findElements(loginButtonXpath)
            .isNotEmpty()
    }

    private fun reAuthenticate() {
        LOGGER.info("Attempting to re-authenticate")
        inputUsername()
        inputPassword()
        login()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }
}
