package lt.dr.travian.bot.task

import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.auth.AuthService
import lt.dr.travian.bot.fluentWait
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Wait
import org.slf4j.LoggerFactory
import java.util.*

data class BuildingSlot(
    val id: Int?,
    val level: Int?,
    val hasEnoughResources: Boolean,
    val isUnderConstruction: Boolean,
)

sealed interface BuildQueueRequest {
    val wantedLevel: Int
    var wantedLevelReached: Boolean

    fun canLevelUp(buildingSlot: BuildingSlot): Boolean {
        return buildingSlot.hasEnoughResources
                && !buildingSlot.isUnderConstruction
                && (buildingSlot.level != null && buildingSlot.level < this.wantedLevel)
    }
}

data class ResourceFieldQueueRequest(
    val id: Int,
    override val wantedLevel: Int,
    override var wantedLevelReached: Boolean = false
) : BuildQueueRequest

data class BuildingQueueRequest(
    val name: String,
    override val wantedLevel: Int,
    override var wantedLevelReached: Boolean = false
) : BuildQueueRequest

data class BuildOrderGroup(val villageId: Int, val buildOrder: Set<BuildQueueRequest>)

class BuildingQueueTask(
    private val driver: ChromeDriver,
    private val wait: Wait<ChromeDriver> = driver.fluentWait(),
    private val authService: AuthService,
    private val timer: Timer
) : RuntimeVariableTimerTask(authService, BuildingQueueTask::class.java) {

    override fun isOnCoolDown() = false

    override fun doWork() {
        driver.get("$TRAVIAN_SERVER/dorf1.php")
        BUILD_ORDER_GROUPS.forEach { processBuildOrderGroup(it) }
    }

    override fun reSchedule() {
        val delay = calculateDelayFromBuildQueueOrGetRandomDelay()
        timer.schedule(
            BuildingQueueTask(driver = driver, authService = authService, timer = timer),
            delay,
        )
        LOGGER.info("${this::class.java.simpleName} scheduled at delay: $delay")
    }

    private fun processBuildOrderGroup(buildOrderGroup: BuildOrderGroup) {
        buildOrderGroup.buildOrder.filter { !it.wantedLevelReached }.forEach {
            if (isQueueRunning()) {
                return@forEach
            }
            upgrade(it)
        }
    }

    private fun isQueueRunning(): Boolean = driver.findElements(
        ByXPath("//*[@class=\"buildingList\"]")
    ).isNotEmpty()

    private fun upgrade(buildQueueRequest: BuildQueueRequest) {
        when (buildQueueRequest) {
            is BuildingQueueRequest -> upgradeBuilding(buildQueueRequest)
            is ResourceFieldQueueRequest -> upgradeResourceField(buildQueueRequest)
        }
    }

    private fun upgradeBuilding(buildingQueueRequest: BuildingQueueRequest) {
        driver.get("$TRAVIAN_SERVER/dorf2.php")
        val dataNameSelector = ByXPath("//*[@data-name=\"${buildingQueueRequest.name}\"/a]")
        val buildingSlotLink = driver.findElements(dataNameSelector)
            .firstOrNull { buildingSlotLink ->
                val buildingId = getBuildingId(buildingSlotLink)
                val level = buildingSlotLink.getAttribute("data-level")?.toInt()
                val hasEnoughResources = hasEnoughResources(buildingSlotLink)
                val isUnderConstruction = isUnderConstruction(buildingSlotLink)
                val buildingSlot = BuildingSlot(
                    buildingId, level, hasEnoughResources, isUnderConstruction
                )
                buildingQueueRequest.canLevelUp(buildingSlot)
            }
        buildingSlotLink?.let { link ->
            link.click()
            levelUpBuilding(buildingQueueRequest)
        }
    }

    private fun upgradeResourceField(resourceFieldQueueRequest: ResourceFieldQueueRequest) {
        val resourceField = getResourceFieldById(resourceFieldQueueRequest.id)
        if (!resourceFieldQueueRequest.canLevelUp(resourceField)) return
        driver.get("$TRAVIAN_SERVER/build.php?id=${resourceField.id}")
        levelUpBuilding(resourceFieldQueueRequest)
        LOGGER.info("$resourceFieldQueueRequest queued")
    }

    private fun getResourceFieldById(id: Int): BuildingSlot {
        return getResourceFields().firstOrNull { it.id != null && it.id == id }
            ?: throw IllegalStateException("NotFound")
    }

    private fun getResourceFields(): List<BuildingSlot> {
        driver.get("$TRAVIAN_SERVER/dorf1.php")
        val resourceFieldLinks = driver.findElements(
            ByXPath("//*[@id=\"resourceFieldContainer\"]/a")
        )
        return resourceFieldLinks
            .filter { it.getAttribute("href").contains("/build.php?id=") }
            .map {
                val buildingId = getBuildingId(it)
                val resourceFieldLevel = getResourceLevel(it)
                val canLevelUpResourceField = hasEnoughResources(it)
                val isUnderConstruction = isUnderConstruction(it)
                BuildingSlot(
                    buildingId,
                    resourceFieldLevel,
                    canLevelUpResourceField,
                    isUnderConstruction
                )
            }
            .filter { it.id != null && it.level != null }
    }

    private fun levelUpBuilding(buildingQueueRequest: BuildQueueRequest) {
        val upgradeButton = driver.findElements(
            ByXPath("//*[@class=\"section1\"]/button")
        ).firstOrNull() ?: return
        wait.until { upgradeButton.isDisplayed }
        upgradeButton.click()
        buildingQueueRequest.wantedLevelReached = true
    }

    private fun getBuildingId(buildingLink: WebElement): Int? {
        return kotlin.runCatching {
            val linkQueryParams = buildingLink.getAttribute("href").split("?")[1]
            val hasMultipleQueryParams = linkQueryParams.contains("&")
            return if (hasMultipleQueryParams) {
                linkQueryParams.substring(0, linkQueryParams.indexOf("&")).split("=")[1].toInt()
            } else {
                linkQueryParams.split("=")[1].toInt()
            }
        }.getOrNull()
    }

    private fun getResourceLevel(resourceFieldWebElement: WebElement): Int? {
        return resourceFieldWebElement.findElements(ByCssSelector(".labelLayer"))
            .firstOrNull()
            ?.text
            ?.toInt()
    }

    private fun hasEnoughResources(buildingWebElement: WebElement): Boolean {
        return getClassAttributes(buildingWebElement).let { classAttributes ->
            classAttributes.any { it == "good" }
        }
    }

    private fun isUnderConstruction(buildingWebElement: WebElement): Boolean {
        return getClassAttributes(buildingWebElement).let { classAttributes ->
            classAttributes.any { it == "underConstruction" }
        }
    }

    private fun getClassAttributes(resourceFieldWebElement: WebElement): List<String> {
        return resourceFieldWebElement.getAttribute("class")?.split(" ") ?: emptyList()
    }

    private fun calculateDelayFromBuildQueueOrGetRandomDelay(): Long {
        val buildDurationElement = driver.findElements(
            ByXPath("//*[@class=\"buildDuration\"]")
        ).firstOrNull()
        return buildDurationElement?.let {
            val timerElement = it.findElements(ByCssSelector(".timer")).firstOrNull()
            val timerValueSeconds = timerElement?.getAttribute("value")?.toLong()
            timerValueSeconds?.times(1000L)
        } ?: getRandomDelay()
    }

    private fun getRandomDelay(): Long {
        LOGGER.info("Using random delay, instead of build queue end delay")
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val FIRST_VILLAGE_BUILD_ORDER = setOf(
            // crop to lvl 6
            ResourceFieldQueueRequest(15, 6),
            ResourceFieldQueueRequest(12, 6),
            ResourceFieldQueueRequest(13, 6),

            // clay to lvl 7
            ResourceFieldQueueRequest(5, 7),
            ResourceFieldQueueRequest(6, 7),
            ResourceFieldQueueRequest(16, 7),
            ResourceFieldQueueRequest(18, 7),

            // woodcutter to lvl 7
            ResourceFieldQueueRequest(1, 7),
            ResourceFieldQueueRequest(3, 7),
            ResourceFieldQueueRequest(14, 7),
            ResourceFieldQueueRequest(17, 7),

            // iron to lvl 7
            ResourceFieldQueueRequest(4, 7),
            ResourceFieldQueueRequest(7, 7),
            ResourceFieldQueueRequest(10, 7),
            ResourceFieldQueueRequest(11, 7),

            // crop to lvl 7
            ResourceFieldQueueRequest(2, 7),
            ResourceFieldQueueRequest(8, 7),
            ResourceFieldQueueRequest(9, 7),
            ResourceFieldQueueRequest(15, 7),
            ResourceFieldQueueRequest(12, 7),
            ResourceFieldQueueRequest(13, 7),
        )

        private val BUILD_ORDER_GROUPS = setOf(
            BuildOrderGroup(18614, FIRST_VILLAGE_BUILD_ORDER)
        )

        private val RESCHEDULE_RANGE_MILLIS = (600_000L..1200_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }
}
