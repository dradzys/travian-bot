# Travian bot for personal use.

### Prerequisites

* JDK 17
* editor of your choice to configure various tasks of the bot.

### Configuration

* Set credential environmental variables:
    * `TRAVIAN_USERNAME`
    * `TRAVIAN_PASSWORD`
* Set travian server:
    * edit `TRAVIAN_SERVER` variable in `TravianBot.kt` file.

### How to run:

* Build jar:
  * unix based systems: `./gradlew clean build`
  * windows: `gradlew.bat clean build`
* Run jar:
  * `java -jar ./build/libs/TravianBot-1.0-SNAPSHOT.jar`

TODOs:

* [ ] implicit and explicit waits should not be used together at the same time
* [ ] add support for runtime addition to raid/army/build queue. Use redis? postgres?
* [ ] apply selenium best practices notes
    * [ ] selenium testing?
* [ ] detekt
* [ ] gradle task to build and run in single command?
* [x] update readme with how to configure and run