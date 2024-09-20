package lt.dr.travian.bot.task

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.IS_PLUS_ACCOUNT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.TRIBE
import lt.dr.travian.bot.Tribe
import lt.dr.travian.bot.task.BuildQueueRequest.BuildType.BUILDING
import lt.dr.travian.bot.task.BuildQueueRequest.BuildType.RESOURCE_FIELD
import lt.dr.travian.bot.task.BuildQueueRequest.BuildingRequest
import lt.dr.travian.bot.task.BuildQueueRequest.ResourceFieldRequest
import org.openqa.selenium.By
import org.openqa.selenium.By.ByClassName
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = BuildingRequest::class, name = "BUILDING"),
    JsonSubTypes.Type(value = ResourceFieldRequest::class, name = "RESOURCE_FIELD")
)
sealed class BuildQueueRequest(
    open val type: BuildType,
    open val wantedLevel: Int,
    open var wantedLevelReached: Boolean
) {

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

    @JsonTypeName("RESOURCE_FIELD")
    data class ResourceFieldRequest(
        val id: Int,
        override val wantedLevel: Int,
        override var wantedLevelReached: Boolean = false
    ) : BuildQueueRequest(RESOURCE_FIELD, wantedLevel, wantedLevelReached)

    @JsonTypeName("BUILDING")
    data class BuildingRequest(
        val name: String,
        override val wantedLevel: Int,
        override var wantedLevelReached: Boolean = false
    ) : BuildQueueRequest(BUILDING, wantedLevel, wantedLevelReached)

    enum class BuildType {
        BUILDING, RESOURCE_FIELD
    }
}

class BuildingQueueTask(private val villageId: Int) : RuntimeTask<BuildQueueRequest>() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (300_000L..400_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
        private val buildingCategoryIds = (1..3)
        private const val BUILD_QUEUE_FILE_NAME = "build-queue.json"
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        fetchOrderGroup(
            villageId = this.villageId,
            instructionFileName = BUILD_QUEUE_FILE_NAME,
            clazz = BuildQueueRequest::class.java
        )?.let { processBuildOrderGroup(it) }
    }

    override fun scheduleDelay(): Long = getQueueTimeLeftInMillis() ?: getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        val buildingQueueTask = BuildingQueueTask(this.villageId)
        buildingQueueTask.orderGroup = this.orderGroup
        buildingQueueTask.lastQueueFileChecksum = this.lastQueueFileChecksum
        return buildingQueueTask
    }

    private fun getQueueTimeLeftInMillis(): Long? {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        return if (TRIBE == Tribe.ROMANS) {
            getQueueTimeLeft("//div[@class=\"buildingList\"]/ul/li[1]")
        } else {
            getQueueTimeLeft("//div[@class=\"buildingList\"]/ul/li[last()]")
        }
    }

    private fun getQueueTimeLeft(xpath: String): Long? {
        return kotlin.runCatching {
            DRIVER.findElements(ByXPath(xpath)).firstOrNull()?.let { buildQueueLastItem ->
                buildQueueLastItem.findElement(ByClassName("timer"))
                    ?.getAttribute("value")
                    ?.toLongOrNull()
                    ?.times(1000)
            }
        }.getOrNull()
    }

    private fun processBuildOrderGroup(buildOrderGroup: OrderGroup<BuildQueueRequest>) {
        run group@{
            buildOrderGroup.orderQueue
                .filter { !it.wantedLevelReached }
                .forEach { buildQueueRequest ->
                    if (isBuildingQueueFull(buildOrderGroup.villageId)) {
                        return@group
                    }
                    LOGGER.info("Processing building request: $buildQueueRequest, in ${buildOrderGroup.villageId}")
                    when (buildQueueRequest) {
                        is BuildingRequest -> upgradeBuilding(
                            buildQueueRequest,
                            buildOrderGroup
                        )

                        is ResourceFieldRequest -> upgradeResourceField(
                            buildQueueRequest,
                            buildOrderGroup
                        )
                    }
                }
        }
    }

    private fun isBuildingQueueFull(villageId: Int): Boolean {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        val buildList = DRIVER.findElements(
            ByXPath("//div[@class=\"buildingList\"]/ul/li")
        )
        return when {
            TRIBE == Tribe.ROMANS && IS_PLUS_ACCOUNT -> {
                buildList.size == 3
            }

            IS_PLUS_ACCOUNT -> {
                buildList.size == 2
            }

            TRIBE == Tribe.ROMANS && !IS_PLUS_ACCOUNT -> {
                buildList.size == 2
            }

            else -> {
                buildList.isNotEmpty()
            }
        }
    }

    private fun upgradeBuilding(
        buildingQueueRequest: BuildingRequest,
        buildOrderGroup: OrderGroup<BuildQueueRequest>
    ) {
        if (DRIVER.currentUrl != "$TRAVIAN_SERVER/dorf2.php?newdid=${buildOrderGroup.villageId}") {
            DRIVER.get("$TRAVIAN_SERVER/dorf2.php?newdid=${buildOrderGroup.villageId}")
        }
        val requestedBuildings = DRIVER.findElements(
            ByXPath("//div[@data-name=\"${buildingQueueRequest.name}\"]")
        )
        when {
            requestedBuildings.isBuildingNotYetPlaced() -> {
                DRIVER.findElements(
                    ByXPath("//div[@data-gid=\"0\"]")
                ).firstOrNull()?.let { freeBuildingSlot ->
                    freeBuildingSlot.findElements(By.cssSelector("svg path")).firstOrNull()
                        ?.let { freeBuildingSlotLink ->
                            freeBuildingSlotLink.click()
                            buildingCategoryIds.takeWhile { categoryId ->
                                !wasQueuedInGivenCategory(categoryId, buildingQueueRequest)
                            }
                        }
                } ?: LOGGER.warn("No free building slot found")
            }

            requestedBuildings.isNotEmpty() -> {
                // upgrade existing building
                requestedBuildings
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
                        if (link.getAttribute("href").isNullOrBlank()) {
                            // city wall is special case, it has no href attribute
                            // going directly to city wall upgrade page
                            // its always with id 40 on all tribes
                            DRIVER.get("$TRAVIAN_SERVER/build.php?newdid=${buildOrderGroup.villageId}&id=40")
                        } else {
                            link.click()
                        }
                        levelUpBuilding()
                        LOGGER.info("${buildingQueueRequest.name} queued")
                    }
            }
        }
    }

    private fun wasQueuedInGivenCategory(
        categoryId: Int,
        buildingQueueRequest: BuildingRequest
    ): Boolean {
        var hasQueuedBuilding = false
        if (categoryId != 1) {
            // category 1 is open by default, once you click on empty build field
            DRIVER.get(DRIVER.currentUrl + "&category=$categoryId")
        }
        DRIVER.findElements(
            ByXPath("//h2[text()=\"${buildingQueueRequest.name}\"]/following-sibling::div[2]/div[@class='contractLink']/button")
        )
            .firstOrNull {
                it.getAttribute("class")?.contains("green") ?: false
            }?.let {
                hasQueuedBuilding = true
                it.click()
                LOGGER.info("${buildingQueueRequest.name} queued")
            }
        return hasQueuedBuilding
    }

    private fun List<WebElement>.isBuildingNotYetPlaced() = this.isEmpty()

    private fun upgradeResourceField(
        resourceFieldQueueRequest: ResourceFieldRequest,
        buildOrderGroup: OrderGroup<BuildQueueRequest>
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
        if (DRIVER.currentUrl != "$TRAVIAN_SERVER/dorf1.php?newdid=$villageId") {
            DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        }
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
