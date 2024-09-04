package lt.dr.travian.bot.auth

object CredentialService {

    fun getCredentials(): Credentials {
        return Credentials(
            username = getEnvironmentVariable("TRAVIAN_USERNAME"),
            password = getEnvironmentVariable("TRAVIAN_PASSWORD"),
        )
    }

    fun getEnvironmentVariable(name: String): String = System.getenv(name)
        ?: throw RuntimeException("$name is missing. Make sure you set $name environment variable")
}

data class Credentials(
    val username: String,
    val password: String,
)
