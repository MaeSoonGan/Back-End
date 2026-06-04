FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/admin-service ./apps/api/admin-service

RUN chmod +x gradlew
RUN ./gradlew :apps:api:admin-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV SERVER_PORT=8080
EXPOSE 8080

COPY --from=build /workspace/apps/api/admin-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
