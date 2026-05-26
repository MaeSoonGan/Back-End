# MaeSoonGan Market Service

종목, 시세, 관심종목, 시장 정보를 제공하는 Spring Boot 애플리케이션입니다.

## 주요 기능

- 종목 현재가/등락/거래량 조회
- 종목 일별 기본 정보 조회
- 종목 호가 스냅샷 조회
- 종목 검색
- 관심종목 목록 조회
- 관심종목 추가/삭제
- KOSPI/KOSDAQ 지수 조회
- 장 상태 조회
- 실시간 종목 순위 조회

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
.\gradlew.bat :apps:api:market-service:bootRun
```

Swagger 주소:

```text
http://localhost:8085/swagger-ui/index.html
```

Health check:

```text
http://localhost:8085/api/health
http://localhost:8085/actuator/health
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
  -Uri http://localhost:8085/api/stocks/005930/price `
  -Headers @{Authorization='Bearer user-token'}
```

현재 로컬 개발용 `user-token`은 `member_id = 1` 사용자로 처리됩니다.

## API 목록

| Method | URL | 인증 | 기능 |
| --- | --- | --- | --- |
| GET | `/api/stocks/{code}/price` | 필요 | 현재가/등락/거래량 조회 |
| GET | `/api/stocks/{code}/daily-info` | 필요 | 일별 기본 정보 조회 |
| GET | `/api/stocks/{code}/orderbook` | 필요 | 호가 초기 스냅샷 조회 |
| GET | `/api/stocks/search` | 필요 | 종목 검색 |
| GET | `/api/watchlist` | 필요 | 관심종목 목록 조회 |
| POST | `/api/watchlist/{stockCode}` | 필요 | 관심종목 추가 |
| DELETE | `/api/watchlist/{stockCode}` | 필요 | 관심종목 해제 |
| GET | `/api/market/index` | 불필요 | KOSPI/KOSDAQ 지수 조회 |
| GET | `/api/market/status` | 불필요 | 장 상태 조회 |
| GET | `/api/market/ranking` | 불필요 | 실시간 종목 순위 조회 |

## 요청 예시

현재가 조회:

```text
GET /api/stocks/005930/price
Authorization: Bearer user-token
```

종목 검색:

```text
GET /api/stocks/search?keyword=삼성&market=KOSPI
Authorization: Bearer user-token
```

관심종목 목록 조회:

```text
GET /api/watchlist?market=domestic
Authorization: Bearer user-token
```

시장 지수 조회:

```text
GET /api/market/index?market=KOSPI
```

랭킹 조회:

```text
GET /api/market/ranking?type=거래대금
GET /api/market/ranking?type=상승
GET /api/market/ranking?type=하락
```

## 데이터베이스

기존 사용 테이블:

- `stock`
- `watchlist`
- `member_snapshot`

market-service 전용 테이블:

- `stock_price_snapshot`
- `stock_daily_price`
- `stock_orderbook_snapshot`
- `market_index_snapshot`
- `market_ranking_snapshot`

스키마 생성 SQL:

```text
src/main/resources/db/market-service-schema.sql
```

테스트용 seed SQL:

```text
src/main/resources/db/market-service-seed.sql
```

적용 순서:

1. `market-service-schema.sql` 실행
2. `market-service-seed.sql` 실행

위 SQL 파일들은 참고 및 수동 적용용입니다. 애플리케이션 실행 시 자동으로 적용되지 않습니다.

## 테스트 데이터

seed SQL에는 아래 테스트 데이터가 포함되어 있습니다.

- 삼성전자 `005930`
- SK하이닉스 `000660`
- 삼성물산 `028260`
- NAVER `035420`
- 삼성전자 호가 5단계
- KOSPI/KOSDAQ 지수
- 거래대금/상승/하락 랭킹

## 테스트 결과

RDS 터널 연결 후 아래 API를 확인했습니다.

```text
GET    /api/health
GET    /api/stocks/005930/price
GET    /api/stocks/005930/daily-info
GET    /api/stocks/005930/orderbook
GET    /api/stocks/search?keyword=삼성&market=KOSPI
GET    /api/watchlist?market=domestic
POST   /api/watchlist/005930
DELETE /api/watchlist/005930
GET    /api/market/index?market=KOSPI
GET    /api/market/index?market=KOSDAQ
GET    /api/market/status
GET    /api/market/ranking
GET    /api/market/ranking?type=상승
GET    /api/market/ranking?type=하락
```

확인한 예외 응답:

```text
GET /api/stocks/NOPE/price                  -> 404 NOT_FOUND
GET /api/stocks/search?keyword=test&market=INVALID -> 400 BAD_REQUEST
GET /api/stocks/005930/price 인증 없이 호출   -> 401 UNAUTHORIZED
GET /api/market/ranking?type=invalid        -> 400 BAD_REQUEST
```

## 테스트

```powershell
.\gradlew.bat :apps:api:market-service:test
```

## 빌드

```powershell
.\gradlew.bat :apps:api:market-service:bootJar
```
