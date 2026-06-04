FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/contest-service ./apps/api/contest-service

RUN chmod +x gradlew
RUN ./gradlew :apps:api:contest-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV CONTEST_SERVICE_PORT=8082
EXPOSE 8082

COPY --from=build /workspace/apps/api/contest-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
