FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps ./apps

RUN chmod +x gradlew
RUN ./gradlew :apps:realtime:user-realtime-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV USER_REALTIME_SERVICE_PORT=8087
EXPOSE 8087

COPY --from=build /workspace/apps/realtime/user-realtime-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
