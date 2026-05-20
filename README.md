# MaeSoonGan Back-End

## Local MySQL

Create a local database first.

```sql
CREATE DATABASE mockdb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Create `src/main/resources/application-secret.properties`.

```properties
spring.datasource.password=your-local-password

app.jwt.secret=replace-with-long-random-secret

spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-google-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

For Naver Mail, use Naver SMTP settings instead of Gmail settings.

```properties
spring.mail.host=smtp.naver.com
spring.mail.port=465
spring.mail.username=your-naver-id@naver.com
spring.mail.password=your-naver-password-or-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.ssl.enable=true
```

`application-secret.properties` is ignored by git.

If `spring.mail.username` is empty, email codes are printed to the server log for local development.

```text
build/bootRun.out.log
```

## Run

```powershell
.\gradlew.bat bootRun
```

## Swagger

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Auth API v2

- `POST /api/auth/login`
- `GET /api/auth/check-id?userId=user123`
- `POST /api/auth/send-code`
- `POST /api/auth/verify-code`
- `GET /api/auth/check-nickname?nickname=tester`
- `POST /api/auth/register`
- `POST /api/auth/find-id`
- `POST /api/auth/verify-reset`
- `PATCH /api/auth/reset-password`

There is no phone/SMS verification API in v2. Email verification is shared by signup, find-id, and reset-password using `purpose`.
