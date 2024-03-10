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

    fun isUnAuthenticated(): Boolean {
        driver.get(TRAVIAN_SERVER)
        inputUsername()
        inputPassword()
        return login()
    }

    fun isLoggedOut(): Boolean {
        return driver.currentUrl == TRAVIAN_SERVER || driver.findElements(
            ByXPath("//*[@id=\"loginForm\"]/tbody/tr[5]/td[2]/button")
        ).isNotEmpty()
    }

    fun reAuthenticate() {
        LOGGER.info("Attempting to reAuthenticate")
        inputUsername()
        inputPassword()
        login()
    }

    private fun inputUsername() {
        val userNameInput = driver.findElement(
            ByXPath("//*[@id=\"loginForm\"]/tbody/tr[1]/td[2]/input")
        )
        wait.until { userNameInput.isDisplayed && userNameInput.isEnabled }
        userNameInput.clear()
        userNameInput.sendKeys(credentials.username)
    }

    private fun inputPassword() {
        val passwordInput = driver.findElement(
            ByXPath("//*[@id=\"loginForm\"]/tbody/tr[2]/td[2]/input")
        )
        wait.until { passwordInput.isDisplayed && passwordInput.isEnabled }
        passwordInput.clear()
        passwordInput.sendKeys(credentials.password)
    }

    private fun login(): Boolean {
        val loginButton = driver.findElement(
            ByXPath("//*[@id=\"loginForm\"]/tbody/tr[5]/td[2]/button")
        )
        wait.until { loginButton.isDisplayed && loginButton.isEnabled }
        loginButton.click()
        return driver.findElements(ByXPath("//*[@id=\"error.LTR\"]")).isNotEmpty()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }
}
