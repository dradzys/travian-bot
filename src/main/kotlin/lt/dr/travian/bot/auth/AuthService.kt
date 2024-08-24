package lt.dr.travian.bot.auth

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByXPath
import org.slf4j.LoggerFactory

class AuthService private constructor() {

    companion object {
        @Volatile
        private var instance: AuthService? = null
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val credentials = CredentialService.getCredentials()

        fun getInstance(): AuthService {
            return instance ?: synchronized(this) {
                AuthService().also { instance = it }
            }
        }
    }

    private val loginButtonXpath = ByXPath("//*[text()=\"Login\"]")

    fun isUnAuthenticated(): Boolean {
        DRIVER.get(TRAVIAN_SERVER)
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
        val userNameInput = DRIVER.findElement(
            ByXPath("//input[@name=\"name\"]")
        )
        FLUENT_WAIT.until { userNameInput.isDisplayed && userNameInput.isEnabled }
        userNameInput.clear()
        userNameInput.sendKeys(credentials.username)
    }

    private fun inputPassword() {
        val passwordInput = DRIVER.findElement(
            ByXPath("//input[@name=\"password\"]")
        )
        FLUENT_WAIT.until { passwordInput.isDisplayed && passwordInput.isEnabled }
        passwordInput.clear()
        passwordInput.sendKeys(credentials.password)
    }

    private fun login() {
        val loginButton = DRIVER.findElement(loginButtonXpath)
        FLUENT_WAIT.until { loginButton.isDisplayed && loginButton.isEnabled }
        loginButton.click()
    }

    private fun hasAuthErrors(): Boolean {
        val errorDiv = DRIVER.findElements(ByXPath("//*[@class=\"errorSection\"]")).firstOrNull()
        return errorDiv?.let {
            !it.getAttribute("class").contains("hide")
        } ?: false
    }

    private fun isLoggedOut(): Boolean {
        return DRIVER.currentUrl == TRAVIAN_SERVER
                || DRIVER.findElements(loginButtonXpath).isNotEmpty()
    }

    private fun reAuthenticate() {
        LOGGER.info("Attempting to re-authenticate")
        inputUsername()
        inputPassword()
        login()
    }

}
