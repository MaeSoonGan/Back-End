FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/order-service ./apps/api/order-service

RUN chmod +x gradlew
RUN ./gradlew :apps:api:order-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV ORDER_SERVICE_PORT=8084
EXPOSE 8084

COPY --from=build /workspace/apps/api/order-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
