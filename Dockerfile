FROM selenium/standalone-chromium:latest
COPY /build/libs/travian-bot.jar travian-bot.jar

CMD ["java", "-jar", "/travian-bot.jar"]