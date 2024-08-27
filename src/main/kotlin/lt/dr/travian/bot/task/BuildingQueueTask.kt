package lt.dr.travian.bot.task

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.core.type.TypeReference
import lt.dr.travian.bot.CheckSumUtils
import lt.dr.travian.bot.DRIVER
import lt.dr.travian.bot.FLUENT_WAIT
import lt.dr.travian.bot.TRAVIAN_SERVER
import lt.dr.travian.bot.objectMapper
import lt.dr.travian.bot.task.BuildQueueRequest.BuildType.BUILDING
import lt.dr.travian.bot.task.BuildQueueRequest.BuildType.RESOURCE_FIELD
import org.openqa.selenium.By.ByClassName
import org.openqa.selenium.By.ByCssSelector
import org.openqa.selenium.By.ByXPath
import org.openqa.selenium.WebElement
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

data class BuildingSlot(
    val id: Int?,
    val level: Int?,
    val isUpgradable: Boolean,
    val isUnderConstruction: Boolean,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
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

data class BuildOrderGroup(val villageId: Int, val buildOrder: List<BuildQueueRequest>)

class BuildingQueueTask(private val villageId: Int) : RescheduledTimerTask() {

    private var buildOrderGroup: BuildOrderGroup? = null
    private var lastBuildQueueFileCheckSum: String? = null

    companion object {
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val RESCHEDULE_RANGE_MILLIS = (300_000L..400_000L)
        private val RANDOM_ADDITIONAL_RANGE_MILLIS = (1111L..5555L)
    }

    override fun isOnCoolDown() = LocalDateTime.now().hour in 3 until 4

    override fun execute() {
        fetchBuildOrderGroup()?.let { processBuildOrderGroup(it) }
    }

    override fun scheduleDelay(): Long = getQueueTimeLeftInMillis() ?: getRandomDelay()

    override fun clone(): RescheduledTimerTask {
        val buildingQueueTask = BuildingQueueTask(this.villageId)
        buildingQueueTask.buildOrderGroup = this.buildOrderGroup
        buildingQueueTask.lastBuildQueueFileCheckSum = this.lastBuildQueueFileCheckSum
        return buildingQueueTask
    }

    private fun getQueueTimeLeftInMillis(): Long? {
        return kotlin.runCatching {
            DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
            DRIVER.findElements(
                ByXPath("//div[@class=\"buildingList\"]/ul/li[last()]")
            ).firstOrNull()?.let { buildQueueLastItem ->
                buildQueueLastItem.findElement(ByClassName("timer"))
                    ?.getAttribute("value")
                    ?.toLongOrNull()
                    ?.times(1000)
            }
        }.getOrNull()
    }

    private fun fetchBuildOrderGroup(): BuildOrderGroup? {
        return kotlin.runCatching {
            val buildQueueFile = File("src/main/resources/build-queue.json")
            val checkSum = CheckSumUtils.calculateCheckSum(buildQueueFile)
            if (isBuildQueueUnchanged(checkSum)) return buildOrderGroup

            LOGGER.info("Fetching build-queue.json")
            val buildOrderGroup = objectMapper.readValue(
                buildQueueFile,
                object : TypeReference<List<BuildOrderGroup>>() {}
            ).firstOrNull { it.villageId == villageId }
            this.buildOrderGroup = buildOrderGroup
            this.lastBuildQueueFileCheckSum = checkSum
            buildOrderGroup
        }.onFailure {
            if (it.cause is IOException) {
                LOGGER.error("Failed reading build-queue.json", it)
            } else {
                LOGGER.error("BuildOrderGroup not found for villageId: $villageId", it)
            }
        }.getOrNull()
    }

    private fun isBuildQueueUnchanged(checkSum: String?): Boolean {
        return this.buildOrderGroup != null && this.lastBuildQueueFileCheckSum == checkSum
    }

    private fun processBuildOrderGroup(buildOrderGroup: BuildOrderGroup) {
        run group@{
            buildOrderGroup.buildOrder
                .filter { !it.wantedLevelReached }
                .forEach { buildQueueRequest ->
                    if (isQueueRunning(buildOrderGroup.villageId)) {
                        return@group
                    }
                    LOGGER.info("Processing building request: $buildQueueRequest, in ${buildOrderGroup.villageId}")
                    when (buildQueueRequest) {
                        is BuildQueueRequest.BuildingRequest -> upgradeBuilding(
                            buildQueueRequest,
                            buildOrderGroup
                        )

                        is BuildQueueRequest.ResourceFieldRequest -> upgradeResourceField(
                            buildQueueRequest,
                            buildOrderGroup
                        )
                    }
                }
        }
    }

    private fun isQueueRunning(villageId: Int): Boolean {
        DRIVER.get("$TRAVIAN_SERVER/dorf1.php?newdid=$villageId")
        return DRIVER.findElements(
            ByXPath("//div[@class=\"buildingList\"]")
        ).isNotEmpty()
    }

    private fun upgradeBuilding(
        buildingQueueRequest: BuildQueueRequest.BuildingRequest,
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
        resourceFieldQueueRequest: BuildQueueRequest.ResourceFieldRequest,
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
                val slot = BuildingSlot(
                    id = getBuildingId(it),
                    level = getResourceLevel(it),
                    isUpgradable = isUpgradable(it),
                    isUnderConstruction = isUnderConstruction(it),
                )
                LOGGER.info("buildingSlot: $slot")
                slot
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
