package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.WebElement
import org.slf4j.LoggerFactory

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
        if (buildingSlot.level != null && buildingSlot.level >= this.wantedLevel) {
            this.wantedLevelReached = true
        } else if (buildingSlot.isUnderConstruction && (buildingSlot.level != null && buildingSlot.level >= this.wantedLevel - 1)) {
            this.wantedLevelReached = true
        }
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

class BuildingQueueTask : RescheduledTimerTask() {
    override fun isOnCoolDown() = false

    override fun doWork() {
        BUILD_ORDER_GROUPS.forEach {
            LOGGER.info("Processing village:${it.villageId}")
            if (isQueueRunning(it.villageId)) {
                return@forEach
            }
            processBuildOrderGroup(it)
        }
    }

    override fun scheduleDelay(): Long = getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        return BuildingQueueTask()
    }

    private fun processBuildOrderGroup(buildOrderGroup: BuildOrderGroup) {
        buildOrderGroup.buildOrder.filter { !it.wantedLevelReached }.forEach {
            upgrade(it, buildOrderGroup)
        }
    }

    private fun isQueueRunning(villageId: Int): Boolean {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        return DRIVER.findElements(ByXPath("//*[@class=\"buildingList\"]")).isNotEmpty()
    }

    private fun upgrade(buildQueueRequest: BuildQueueRequest, buildOrderGroup: BuildOrderGroup) {
        when (buildQueueRequest) {
            is BuildingQueueRequest -> upgradeBuilding(buildQueueRequest, buildOrderGroup)
            is ResourceFieldQueueRequest -> upgradeResourceField(buildQueueRequest, buildOrderGroup)
        }
    }

    private fun upgradeBuilding(
        buildingQueueRequest: BuildingQueueRequest,
        buildOrderGroup: BuildOrderGroup
    ) {
        DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${buildOrderGroup.villageId}")
        val dataNameSelector = ByXPath("//*[@data-name=\"${buildingQueueRequest.name}\"]")
        DRIVER.findElements(dataNameSelector)
            .map { it.findElement(ByCssSelector("a")) }
            .firstOrNull { buildingSlotLink ->
                val buildingId = getBuildingId(buildingSlotLink)
                val level = buildingSlotLink.getAttribute("data-level")?.toInt()
                val hasEnoughResources = hasEnoughResources(buildingSlotLink)
                val isUnderConstruction = isUnderConstruction(buildingSlotLink)
                val buildingSlot = BuildingSlot(
                    buildingId, level, hasEnoughResources, isUnderConstruction
                )
                buildingQueueRequest.canLevelUp(buildingSlot)
            }?.let { link ->
                link.click()
                levelUpBuilding()
                LOGGER.info("${buildingQueueRequest.name} queued")
            }
    }

    private fun upgradeResourceField(
        resourceFieldQueueRequest: ResourceFieldQueueRequest,
        buildOrderGroup: BuildOrderGroup
    ) {
        val resourceField =
            getResourceFieldById(resourceFieldQueueRequest.id, buildOrderGroup.villageId)
        if (!resourceFieldQueueRequest.canLevelUp(resourceField)) return
        DRIVER.get("$TRAVIAN_SERVER/build.php?newdid=${buildOrderGroup.villageId}&id=${resourceField.id}")
        levelUpBuilding()
        LOGGER.info("${resourceFieldQueueRequest.id} queued")
    }

    private fun getResourceFieldById(id: Int, villageId: Int): BuildingSlot {
        return getResourceFields(villageId).firstOrNull { it.id != null && it.id == id }
            ?: throw IllegalStateException("NotFound")
    }

    private fun getResourceFields(villageId: Int): List<BuildingSlot> {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        val resourceFieldLinks = DRIVER.findElements(
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

    private fun levelUpBuilding() {
        val upgradeButton = DRIVER.findElements(
            ByXPath("//*[@class=\"section1\"]/button")
        ).firstOrNull() ?: return
        FLUENT_WAIT.until { upgradeButton.isDisplayed }
        upgradeButton.click()
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

    private fun getRandomDelay(): Long {
        return RESCHEDULE_RANGE_MILLIS.random() + RANDOM_ADDITIONAL_RANGE_MILLIS.random()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val FIRST_VILLAGE_BUILD_ORDER = setOf(
            BuildingQueueRequest("Warehouse", 16),
            ResourceFieldQueueRequest(1, 10),
            ResourceFieldQueueRequest(2, 10),
            ResourceFieldQueueRequest(3, 10),
            ResourceFieldQueueRequest(4, 10, wantedLevelReached = true),
            ResourceFieldQueueRequest(5, 10, wantedLevelReached = true),
            ResourceFieldQueueRequest(6, 10),
            ResourceFieldQueueRequest(7, 10),
            ResourceFieldQueueRequest(8, 10),
            ResourceFieldQueueRequest(9, 10),
            ResourceFieldQueueRequest(10, 10),
            ResourceFieldQueueRequest(11, 10),
            ResourceFieldQueueRequest(12, 10),
            ResourceFieldQueueRequest(13, 10),
            ResourceFieldQueueRequest(14, 10),
            ResourceFieldQueueRequest(15, 10),
            ResourceFieldQueueRequest(16, 10),
            ResourceFieldQueueRequest(17, 10),
            ResourceFieldQueueRequest(18, 10),
        )
        private val CAPITAL_VILLAGE_BUILD_ORDER = emptySet<BuildQueueRequest>()

        private val BUILD_ORDER_GROUPS = setOf(
            BuildOrderGroup(18614, FIRST_VILLAGE_BUILD_ORDER),
            BuildOrderGroup(22111, CAPITAL_VILLAGE_BUILD_ORDER),
        )

        // 7 to 15 minutes
        private val RESCHEDULE_RANGE_MILLIS = (450_000L..900_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }
}
