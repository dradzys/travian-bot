# travian-bot

<hr>

* A selenium bot to automate `travian legends` tedious tasks like `raiding`, `building`
  , `training troops`.

#### Prerequisites

* JDK 17 installation

# Getting started

<hr>

##### Configuration

* Set credentials via environmental variables:
    * `TRAVIAN_USERNAME`
    * `TRAVIAN_PASSWORD`
* Set travian server url:
    * set `TRAVIAN_SERVER` value in `TravianBot.kt` file.
* Configure instructions for tasks at `src/main/resources`.
* Schedule tasks:
    * add/remove as per your needs
    ```kotlin
    setOf(
        FarmListSendTask(),
        BuildingQueueTask(19421),
        ArmyQueueTask(19421),
        BuildingQueueTask(21287),
        ArmyQueueTask(21287),
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
setOf(BuildingQueueTask(19421)).asSequence().shuffled().forEach { TIMER.schedule(it, 1000L) }
```

* Each task can be further configured by changing:
    * `scheduleDelay()` - delay between each task execution.
    * `isOnCoolDown()` - optional cool-down mechanism. For example, you could provide a time
      window where a given task should not execute and be in cool-down state.

<hr>

# Known issues and TODOs

* [x] ~~Would be nice to add runtime configuration for tasks, so that you can update without~~
  recompiling the code.
* [ ] We need a lot more manual testing to ensure that the bot is working as expected.
    * [ ] different tribes, different buildings, different troops.
    * [ ] different bot detection preventions mechanisms(random click intervals, task execution
      intervals, cool-downs)
* [x] ~~BuildingQueueTask for palisade/wall is not working. The travian-ui for palisade is not a
  clickable `<a>` tag, but rather a `<svg>` element.~~
* [x] ~~BuildingQueueTask for a building that is not already placed is not supported.~~

<hr>