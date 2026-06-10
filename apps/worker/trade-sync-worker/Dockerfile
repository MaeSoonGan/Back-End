FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/worker/trade-sync-worker ./apps/worker/trade-sync-worker

RUN chmod +x gradlew
RUN ./gradlew :apps:worker:trade-sync-worker:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TRADE_SYNC_WORKER_PORT=8088
EXPOSE 8088

COPY --from=build /workspace/apps/worker/trade-sync-worker/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
