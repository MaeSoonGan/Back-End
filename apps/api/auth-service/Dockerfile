FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/auth-service ./apps/api/auth-service

RUN chmod +x gradlew
RUN ./gradlew :apps:api:auth-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV AUTH_SERVICE_PORT=8081
EXPOSE 8081

COPY --from=build /workspace/apps/api/auth-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
