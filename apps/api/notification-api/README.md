# MaeSoonGan Notification API

알림 서비스 Pod를 담당하는 Spring Boot 애플리케이션입니다.

## 주요 기능

- 로그인한 회원의 알림 목록 조회
- 미읽음 알림 개수 조회
- 단건 알림 읽음 처리
- 전체 알림 읽음 처리
- 알림 설정 조회
- 알림 설정 변경

## 로컬 실행 방법

RDS가 private subnet에 있으므로 애플리케이션 실행 전에 SSM 포트포워딩 터널을 먼저 열어야 합니다.

```powershell
aws ssm start-session `
  --region ap-northeast-2 `
  --target i-0b1d7681a12eb816c `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters host="database-1.cxmmsoys0vif.ap-northeast-2.rds.amazonaws.com",portNumber="3306",localPortNumber="3307"
```

위 명령어를 실행한 PowerShell 창은 닫지 않고 유지해야 합니다.

터널이 정상적으로 열렸는지 확인합니다.

```powershell
netstat -ano | findstr :3307
```

`LISTENING`이 보이면 정상입니다.

레포지토리 루트에 `.env.example`을 복사해서 `.env` 파일을 만들고, RDS 데이터베이스 계정 정보를 입력합니다.

```properties
SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3307/fisaschool?sslMode=trust&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=your-rds-username
SPRING_DATASOURCE_PASSWORD=your-rds-password
APP_JWT_SECRET=local-development-secret-change-me
```

주의: `SPRING_DATASOURCE_USERNAME`과 `SPRING_DATASOURCE_PASSWORD`는 AWS IAM 계정이 아니라 MariaDB 접속용 DB 계정입니다.

서비스를 실행합니다.

```powershell
.\gradlew.bat :apps:api:notification-api:bootRun
```

Swagger 주소:

```text
http://localhost:8086/swagger-ui/index.html
```

Health check:

```text
http://localhost:8086/api/health
http://localhost:8086/actuator/health
```

## Swagger 인증 방법

로컬 개발용 테스트 토큰은 아래 값을 사용합니다.

```text
user-token
```

Swagger 우측 상단 `Authorize` 버튼을 누른 뒤 입력칸에 `user-token`만 입력합니다.

Swagger는 `Bearer` prefix를 자동으로 붙입니다. 따라서 Swagger 입력칸에는 아래처럼 입력하면 안 됩니다.

```text
Bearer user-token
```

PowerShell이나 Postman처럼 직접 Authorization 헤더를 넣는 경우에는 전체 값을 넣어야 합니다.

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8086/api/notifications `
  -Headers @{Authorization='Bearer user-token'}
```

현재 로컬 개발용 `user-token`은 `member_id = 1` 사용자로 처리됩니다.

## API 목록

| Method | URL | 기능 |
| --- | --- | --- |
| GET | `/api/notifications` | 알림 목록 조회 |
| PATCH | `/api/notifications/{notificationId}/read` | 단건 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 처리 |
| GET | `/api/notifications/unread-count` | 미읽음 알림 개수 조회 |
| GET | `/api/notifications/settings` | 알림 설정 조회 |
| PATCH | `/api/notifications/settings` | 알림 설정 변경 |

## 알림 설정 변경 요청

`PATCH /api/notifications/settings`는 부분 업데이트를 지원합니다. 변경할 필드만 요청 바디에 포함하면 됩니다.

```json
{
  "tradeComplete": false,
  "orderCancel": true,
  "pendingOrder": false,
  "contestStart": true,
  "contestEnd": true,
  "rankChange": false,
  "marketOpen": false,
  "marketClose": false
}
```

알림 설정 필드:

- `tradeComplete`: 체결 완료 알림
- `orderCancel`: 주문 취소 알림
- `pendingOrder`: 미체결 주문 알림
- `contestStart`: 대회 시작 알림
- `contestEnd`: 대회 종료 알림
- `rankChange`: 순위 변동 알림
- `marketOpen`: 장 시작 알림
- `marketClose`: 장 마감 알림

## 데이터베이스

이 서비스는 SSM 터널을 통해 공유 RDS MariaDB 인스턴스에 연결합니다.

사용 테이블:

- `notification`
- `notification_setting`

`notification_setting` 테이블에는 아래 세부 설정 컬럼이 필요합니다.

- `trade_complete`
- `order_cancel`
- `pending_order`
- `contest_start`
- `contest_end`
- `rank_change`
- `market_open`
- `market_close`

스키마 참고 파일:

```text
src/main/resources/db/notification-api-schema.sql
```

이 SQL 파일은 참고용입니다. 애플리케이션 실행 시 자동으로 적용되지 않습니다.

## 테스트

```powershell
.\gradlew.bat :apps:api:notification-api:test
```

## 빌드

```powershell
.\gradlew.bat :apps:api:notification-api:bootJar
```
