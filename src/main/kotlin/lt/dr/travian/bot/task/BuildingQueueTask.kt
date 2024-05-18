package lt.dr.travian.bot.task

import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.WebElement
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

data class BuildingSlot(
    val id: Int?,
    val level: Int?,
    val isUpgradable: Boolean,
    val isUnderConstruction: Boolean,
)

sealed interface BuildQueueRequest {
    val wantedLevel: Int
    var wantedLevelReached: Boolean

    fun canLevelUp(buildingSlot: BuildingSlot): Boolean {
        if (buildingSlot.level != null && buildingSlot.level >= this.wantedLevel) {
            this.wantedLevelReached = true
        } else if (buildingSlot.isUnderConstruction && (buildingSlot.level != null && buildingSlot.level + 1 >= this.wantedLevel)) {
            this.wantedLevelReached = true
        }
        return buildingSlot.isUpgradable
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

    companion object {
        private val CAPITAL_VILLAGE_BUILD_ORDER = setOf(
            ResourceFieldQueueRequest(17, 7),
            ResourceFieldQueueRequest(18, 7),

            BuildingQueueRequest("Academy", 10),
            BuildingQueueRequest("Residence", 10),
            BuildingQueueRequest("Marketplace", 12),
            BuildingQueueRequest("Warehouse", 12),
            BuildingQueueRequest("Granary", 12),
            BuildingQueueRequest("Cranny", 10),

            ResourceFieldQueueRequest(1, 9),
            ResourceFieldQueueRequest(2, 9),
            ResourceFieldQueueRequest(3, 9),
            ResourceFieldQueueRequest(4, 9),
            ResourceFieldQueueRequest(5, 9),
            ResourceFieldQueueRequest(6, 9),
            ResourceFieldQueueRequest(7, 9),
            ResourceFieldQueueRequest(8, 9),
            ResourceFieldQueueRequest(9, 9),
            ResourceFieldQueueRequest(10, 9),
            ResourceFieldQueueRequest(11, 9),
            ResourceFieldQueueRequest(12, 9),
            ResourceFieldQueueRequest(13, 9),
            ResourceFieldQueueRequest(14, 9),
            ResourceFieldQueueRequest(15, 9),
            ResourceFieldQueueRequest(16, 9),
        )

        private val BUILD_ORDER_GROUPS = setOf(
            BuildOrderGroup(20217, CAPITAL_VILLAGE_BUILD_ORDER),
        )

        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (150_000L..250_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        BUILD_ORDER_GROUPS.forEach {
            LOGGER.info("Processing villageId: ${it.villageId}")
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
        buildOrderGroup.buildOrder.filter { !it.wantedLevelReached }.forEach { buildQueueRequest ->
            when (buildQueueRequest) {
                is BuildingQueueRequest -> upgradeBuilding(buildQueueRequest, buildOrderGroup)
                is ResourceFieldQueueRequest -> upgradeResourceField(
                    buildQueueRequest,
                    buildOrderGroup
                )
            }
        }
    }

    private fun isQueueRunning(villageId: Int): Boolean {
        // TODO update function to queue buildings if queue is not full,
        //  including case when one thing is under construction and another can be queued
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        return DRIVER.findElements(
            ByXPath("//div[@class=\"buildingList\"]")
        ).isNotEmpty()
    }

    private fun upgradeBuilding(
        buildingQueueRequest: BuildingQueueRequest,
        buildOrderGroup: BuildOrderGroup
    ) {
        DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${buildOrderGroup.villageId}")
        DRIVER.findElements(ByXPath("//div[@data-name=\"${buildingQueueRequest.name}\"]"))
            .map {
                val buildingSlotLink = it.findElement(ByCssSelector("a"))
                buildingSlotLink to BuildingSlot(
                    id = getBuildingId(buildingSlotLink),
                    level = buildingSlotLink.getAttribute("data-level")?.toInt(),
                    isUpgradable = isUpgradable(buildingSlotLink),
                    isUnderConstruction = isUnderConstruction(buildingSlotLink),
                )
            }
            .firstOrNull { (_, buildingSlot) ->
                buildingQueueRequest.canLevelUp(buildingSlot)
            }
            ?.let { (link, _) ->
                link.click()
                levelUpBuilding()
                LOGGER.info("${buildingQueueRequest.name} queued")
            }

    }

    private fun upgradeResourceField(
        resourceFieldQueueRequest: ResourceFieldQueueRequest,
        buildOrderGroup: BuildOrderGroup
    ) {
        val resourceField = getResourceFieldById(
            resourceFieldQueueRequest.id,
            buildOrderGroup.villageId,
        )
        if (!resourceFieldQueueRequest.canLevelUp(resourceField)) return
        DRIVER.get("$TRAVIAN_SERVER/build.php?newdid=${buildOrderGroup.villageId}&id=${resourceField.id}")
        levelUpBuilding()
        LOGGER.info("${resourceFieldQueueRequest.id} queued")
    }

    private fun getResourceFieldById(id: Int, villageId: Int): BuildingSlot {
        return getResourceFields(villageId).firstOrNull { it.id != null && it.id == id }
            ?: throw IllegalStateException("ResourceField id: $id, in villageId: $villageId not found")
    }

    private fun getResourceFields(villageId: Int): List<BuildingSlot> {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        val resourceFieldLinks = DRIVER.findElements(
            ByXPath("//div[@id=\"resourceFieldContainer\"]/a")
        )
        return resourceFieldLinks
            .filter { it.getAttribute("href").contains("/build.php?id=") }
            .map {
                BuildingSlot(
                    id = getBuildingId(it),
                    level = getResourceLevel(it),
                    isUpgradable = isUpgradable(it),
                    isUnderConstruction = isUnderConstruction(it),
                )
            }
            .filter { it.id != null && it.level != null }
    }

    private fun levelUpBuilding() {
        val upgradeButton = DRIVER.findElements(
            ByXPath("//div[@class=\"section1\"]/button")
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

    private fun getResourceLevel(resourceFieldLink: WebElement): Int? {
        return resourceFieldLink.findElements(ByCssSelector(".labelLayer"))
            .firstOrNull()?.let {
                if (it.text.isBlank()) {
                    // resource field with level 0, label level is just empty...
                    return 0
                }
                it.text.toInt()
            }
    }

    /**
     * Determine if buildingSlot is upgradable.
     * Cases in which it may be not available for an upgrade:
     * BuildQueue is full, lacking resources, slot level is maxed.
     */
    private fun isUpgradable(buildingWebElement: WebElement): Boolean {
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
}
