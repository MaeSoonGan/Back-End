# MaeSoonGan Admin Service

This module is the Spring Boot application for the admin service pod.

## Run locally

Open the SSM port-forwarding tunnel to RDS first.

```powershell
aws ssm start-session `
  --target EC2_INSTANCE_ID `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=RDS_ENDPOINT,portNumber=3306,localPortNumber=3307"
```

Create `.env` in the repository root.

```properties
SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3307/fisaschool?sslMode=trust&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=your-rds-username
SPRING_DATASOURCE_PASSWORD=your-rds-password
```

Run the service.

```powershell
.\gradlew.bat :apps:api:admin-service:bootRun
```

Swagger URL:

```text
http://localhost:8080/swagger-ui/index.html
```

Health check:

```text
http://localhost:8080/api/health
http://localhost:8080/actuator/health
```

## Build

```powershell
.\gradlew.bat :apps:api:admin-service:bootJar
```

## Docker image

Run this command from the repository root.

```powershell
docker build -f apps/api/admin-service/Dockerfile -t maesoongan-admin-service .
```
