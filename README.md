# travian-bot

<hr>

* A bot to automate `travian legends` tedious tasks like `raiding`, `building`, `training troops`.
* Not very user-friendly, since you need to configure the bot by manually entering values in the
  code.

#### Prerequisites

* JDK 17
* Editor of your choice to configure tasks. I recommend `IntelliJ IDEA` for syntax highlighting and
  code completion.

# Getting started

<hr>

##### Configuration

* Set credentials via environmental variables:
    * `TRAVIAN_USERNAME`
    * `TRAVIAN_PASSWORD`
* Set travian server url:
    * set `TRAVIAN_SERVER` value in `TravianBot.kt` file.
* Configure tasks:
    * `ArmyQueue.kt` for training troops.
  ```kotlin
          private val FIRST_VILLAGE_ARMY_ORDER = setOf(
              BarrackQueue(troopId = "t1", amount = 15),
              StableQueue(troopId = "t4", amount = 2)
          )
  
          private val CAPITAL_VILLAGE_ARMY_ORDER = setOf(
              BarrackQueue(troopId = "t1", amount = 2),
          )
  
          private val ARMY_ORDER_GROUPS = setOf(
              ArmyOrderGroup(villageId = 18614, armyOrder = FIRST_VILLAGE_ARMY_ORDER),
              ArmyOrderGroup(villageId = 22111, armyOrder = CAPITAL_VILLAGE_ARMY_ORDER),
          )
    ```
    * `BuildingQueueTask.kt` for resource field and building queue.
        * Note, that resource field queue requests are different from building queue requests. For
          resource field provide id, and for building provide name.
  ```kotlin
          private val FIRST_VILLAGE_BUILD_ORDER = setOf(
              BuildingQueueRequest("Warehouse", 20),
              BuildingQueueRequest("Main Building", 20),
          )
          private val CAPITAL_VILLAGE_BUILD_ORDER = setOf(
              ResourceFieldQueueRequest(1, 9),
              ResourceFieldQueueRequest(2, 9),
              ResourceFieldQueueRequest(3, 9),
              BuildingQueueRequest("Barracks", 12),
              BuildingQueueRequest("Warehouse", 18),
          )
  
          private val BUILD_ORDER_GROUPS = setOf(
              BuildOrderGroup(18614, FIRST_VILLAGE_BUILD_ORDER),
              BuildOrderGroup(22111, CAPITAL_VILLAGE_BUILD_ORDER),
          )
  ```
    * `RaidQueueTask.kt` for raiding oasis and inactive villages manually by providing coordinates.
        * Note, that you must provide correct type of raid unit.
        * For oasis, if they are occupied, it will not be raided.
        * For village, if it has been captured by Natars, it will not be raided.
  ```kotlin
        private val FIRST_VILLAGE_RAID_UNITS = setOf(
            RaidUnit(-26, -58, VILLAGE, troopAmount = 10, troopId = "t4"),
            RaidUnit(-21, -57, OASIS, troopAmount = 2, troopId = "t4"),
        ).shuffled().toSet()

        private val CAPITAL_RAID_UNITS = setOf(
            RaidUnit(-29, -42, VILLAGE, troopAmount = 8, troopId = "t4"),
            RaidUnit(-44, -50, OASIS, troopAmount = 2, troopId = "t4"),
        ).shuffled().toSet()

        private val RAID_UNIT_GROUPS = setOf(
            RaidUnitGroup(18614, FIRST_VILLAGE_RAID_UNITS),
            RaidUnitGroup(22111, CAPITAL_RAID_UNITS),
        )
  ```
    * `FarmListSendTask.kt` - raiding using gold clubs farm list feature. Just configure the farm
      list in travian, and it will send all of them
* Schedule tasks:
    ```kotlin
    setOf(
        FarmListSendTask(),
        BuildingQueueTask(),
        ArmyQueueTask(),
    ).asSequence().shuffled().forEach {
        TIMER.schedule(it, 1000L)
    }
    ```

##### Running:

* Build jar:
    * unix based systems: `./gradlew clean build`
    * windows: `gradlew.bat clean build`
* Run jar:
    * `java -jar ./build/libs/travian-bot-1.0.0.jar`

<hr>

##### Optional configuration:

* ChromeDriver options can be configured in `TravianBot.kt` file, under method `buildChromeDrive()`
    * You can add `"--headless=old"` to run in headless mode(No GUI).
* Additionally, you can remove, tasks that are not relevant for you from `TravianBot.kt` main
  method, just after authentication logic, like so:

```kotlin
// Will run only raid task
setOf(
    RaidTask()
).asSequence().shuffled().forEach {
    TIMER.schedule(it, 1000L)
}
```

* Each task can be further configured by changing:
    * `scheduleDelay()` - delay between each task execution.
    * `isOnCoolDown()` - optional cool-down mechanism. For example, you could provide a time
      window where a given task should not execute and be in cool-down state.

<hr>

# Known issues and TODOs

* [ ] Would be nice to add runtime configuration for tasks, so that you can update without
  recompiling the code.
* [ ] We need a lot more manual testing to ensure that the bot is working as expected.
    * [ ] different tribes, different buildings, different troops.
    * [ ] different bot detection preventions mechanisms(random click intervals, task execution
      intervals, cool-downs)
* [ ] BuildingQueueTask for palisade/wall is not working. The travian-ui for palisade is not a
  clickable `<a>` tag, but rather a `<svg>` element.
* [ ] BuildingQueueTask for a building that is not already placed is not supported.

<hr>