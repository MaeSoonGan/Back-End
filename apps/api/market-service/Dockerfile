FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps ./apps

RUN chmod +x gradlew
RUN ./gradlew :apps:api:market-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV MARKET_SERVICE_PORT=8085
EXPOSE 8085

COPY --from=build /workspace/apps/api/market-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
