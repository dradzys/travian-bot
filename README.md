# Travian bot for personal use.

### How to run:

* `./gradlew clean build`
* `java -jar ./build/libs/TravianBot-1.0-SNAPSHOT.jar`

TODOs:

* [ ] implicit and explicit waits should not be used together at the same time
* [ ] add support for runtime addition to raid/army/build queue. Use redis? postgres?
* [ ] apply selenium best practices notes
    * [ ] selenium testing?
* [ ] detekt
* [ ] gradle task to build and run in single command?
* [ ] update readme with how to configure and run