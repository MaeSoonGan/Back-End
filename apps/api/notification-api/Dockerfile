FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps ./apps

RUN chmod +x gradlew
RUN ./gradlew :apps:api:notification-api:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV NOTIFICATION_API_PORT=8086
EXPOSE 8086

COPY --from=build /workspace/apps/api/notification-api/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
