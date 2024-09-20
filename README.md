# travian-bot [![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=dradzys_travian-bot&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=dradzys_travian-bot) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=dradzys_travian-bot&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=dradzys_travian-bot) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=dradzys_travian-bot&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=dradzys_travian-bot)

* A selenium bot to automate `travian legends` tedious tasks:
    * building: `BuildingQueueTask.kt`
    * army recruitment: `ArmyQueueTask.kt`
    * farm list raiding: `FarmListSendTask.kt`
    * raiding: `RaidTask.kt`

# Getting started

##### Prerequisites to running

* JDK/JRE 17 installation

##### Configuration

* Set environmental variables:
    * `TRAVIAN_USERNAME`
    * `TRAVIAN_PASSWORD`
    * `TRAVIAN_SERVER`. Example.: `https://ts20.x2.europe.travian.com`
* Add instructions at:
  * windows: `C:\Users\<username>\AppData\Roaming\travian-bot`
  * linux/macos: `$HOME/.config/travian-bot`
  * examples available in `src/main/resources/travian-bot`

##### Running:

* Download latest [release](https://github.com/dradzys/travian-bot/releases)
  * via cli, recommended way to see logs in cli: `java -jar travian-bot-{version}.jar`
  * or just double-click the executable.
* Build and run locally:
    * Build:
        * unix based systems: `./gradlew clean build`
        * windows: `.\gradlew.bat clean build`
    * Run jar:
        * `java -jar ./build/libs/travian-bot.jar`

<hr>

##### Optional configuration:

* ChromeDriver options can be configured in `TravianBot.kt` file, under method `buildChromeDrive()`
    * You can add `"--headless=old"` to run in headless mode(No GUI).
* Each task can be further configured by changing:
    * `scheduleDelay()` - delay between each task execution.
    * `isOnCoolDown()` - optional cool-down mechanism. For example, you could provide a time
      window where a given task should not execute and be in cool-down state.

# TODO's:

* Add CI steps for docker image build and push
* Sonarqube code smell fixesß
* Alerting to some client to notify about attacks
* Build/Army queue order is lost during serialization. Add priority or some other mechanism.