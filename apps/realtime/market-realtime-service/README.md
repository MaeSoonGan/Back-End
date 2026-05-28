# MaeSoonGan Market Realtime Service

한국투자증권 KIS 실시간 WebSocket API에서 국내 주식 현재가와 호가를 수집하고, Redis/ElastiCache와 MTS WebSocket Gateway로 전달하는 실시간 시세 처리 서비스입니다.

Kafka 연동은 추후 온프레미스 Kafka 준비 이후 붙일 수 있도록 포트만 분리해두었고, 현재 구현은 KIS 연동, Redis 적재, REST 조회, WebSocket 구독/푸시까지 포함합니다.

## 역할

- KIS 실시간 현재가 TR 수신
- KIS 실시간 호가 TR 수신
- 최신 현재가/호가를 Redis 또는 ElastiCache에 저장
- MTS 클라이언트가 `/ws/market`으로 구독한 종목의 시세를 실시간 push
- 초기 화면이나 내부 서비스가 REST API로 최신 시세 조회
- 온프레미스 체결엔진이나 계정계가 Redis에서 최신 시세 조회 가능
- KIS 종목 마스터 `.mst` 파일을 Redis에 적재해 종목코드와 종목명을 매핑

## 현재 구현 상태

- Spring Boot 서비스 모듈 구성
- Dockerfile 추가
- Health, 상태 조회 API 추가
- KIS approval key 발급 클라이언트 구현
- KIS 실시간 WebSocket 연결 구현
- KIS 현재가 `H0STCNT0` 구독/해제 구현
- KIS 호가 `H0STASP0` 구독/해제 구현
- KIS `|`, `^` 구분 payload 파싱 구현
- Redis/ElastiCache 최신 시세 저장 구현
- Redis 기반 최신 시세 REST 조회 구현
- `/ws/market` WebSocket Gateway 구현
- 클라이언트 세션별 종목 구독/해제 처리
- 종목별 첫 구독 시 KIS 구독, 마지막 구독 해제 시 KIS 해제 처리
- KIS 종목 마스터 파일 기반 Redis 종목명 캐시 적재 구현
- mock 시세 소스 구현
- 테스트 코드 추가

## Redis를 종목 마스터 저장소처럼 사용하는 기준

현재 종목 수는 코스피/코스닥 합산 수천 건 수준이라 Redis에 TTL 없이 적재해도 메모리 부담은 크지 않습니다. 그래서 지금 단계에서는 아래 방식으로 운영해도 됩니다.

- `stock:names`: 종목코드 -> 종목명 Hash
- `stock:meta:{stockCode}`: 종목별 상세 메타 JSON
- `stock:master:status`: 마지막 종목 마스터 적재 결과

단, Redis만 기준으로 사용할 경우 다음 사항은 관리해야 합니다.

- Redis 데이터가 초기화되면 앱 재기동 또는 재적재가 필요합니다.
- `.mst` 파일이 갱신되면 다시 적재해야 최신 종목명이 반영됩니다.
- 적재 실패 시 서비스는 기본적으로 계속 기동되고, 종목명은 종목코드로 fallback 됩니다.
- 운영에서 적재 실패를 바로 장애로 보고 싶으면 `STOCK_MASTER_FAIL_FAST=true`로 설정합니다.

장기적으로는 RDS의 `stock` 테이블을 기준 데이터로 두고 Redis는 런타임 캐시로 쓰는 구성이 더 명확합니다. 다만 지금 개발 단계에서는 Redis 적재 방식으로 충분히 진행 가능합니다.

## API

### Health Check

```http
GET /api/health
GET /actuator/health
```

서비스 실행 상태를 확인합니다.

### Realtime Status

```http
GET /api/realtime/status
```

시세 수집 상태를 반환합니다.

주요 필드:

- `quoteSourceMode`: `mock`, `kis`, `noop`
- `ingestionStatus`: `NOT_STARTED`, `RUNNING`, `STOPPED`, `FAILED`
- `quoteSourceConnected`: KIS 또는 mock 소스 연결 여부
- `redisEnabled`: Redis 저장 활성 여부
- `kafkaEnabled`: Kafka 발행 활성 여부
- `lastReceivedAt`: 마지막 시세 수신 시각
- `priceEventCount`: 현재가 이벤트 처리 건수
- `orderbookEventCount`: 호가 이벤트 처리 건수
- `lastError`: 마지막 오류 메시지

### Redis Cache Status

```http
GET /api/realtime/cache/status
GET /api/realtime/cache/price/{stockCode}
GET /api/realtime/cache/orderbook/{stockCode}
GET /api/realtime/cache/stock-master
```

Redis 연결 상태, 최신 현재가/호가, 종목 마스터 적재 상태를 확인합니다.

### Market REST API

```http
GET /api/market/price/{stockCode}
GET /api/market/prices?codes=005930,000660
GET /api/market/status
```

- 단일 종목 최신 시세 조회
- 여러 종목 최신 시세 일괄 조회
- 서버 시간 기준 장 상태 조회

### WebSocket

```text
ws://localhost:8087/ws/market
```

구독 요청:

```json
{"action":"SUBSCRIBE","stockCodes":["005930","000660"]}
```

구독 해제 요청:

```json
{"action":"UNSUBSCRIBE","stockCodes":["005930"]}
```

서버 push 예시:

```json
{"type":"PRICE_UPDATE","data":{"stockCode":"005930","stockName":"삼성전자","currentPrice":74500}}
```

Swagger에서는 WebSocket을 직접 테스트할 수 없습니다. Postman WebSocket, 브라우저 콘솔, `wscat` 같은 WebSocket 클라이언트로 테스트합니다.

## Redis Key

시세 데이터는 TTL이 있습니다.

- 현재가: `stock:{stockCode}:price`
- 호가: `stock:{stockCode}:orderbook`
- 시장 상태: `market:status`

종목 마스터 데이터는 TTL 없이 유지합니다.

- 종목명 매핑: `stock:names`
- 종목 메타: `stock:meta:{stockCode}`
- 적재 상태: `stock:master:status`

## 환경 변수

`.env.example`을 복사해 `.env`로 사용하거나, EKS에서는 Secret/ConfigMap으로 주입합니다.

```properties
REALTIME_QUOTE_INGESTOR_PORT=8087

QUOTE_SOURCE_MODE=mock
QUOTE_INGESTION_AUTO_START=true
QUOTE_INGESTION_STOCK_CODES=
QUOTE_MOCK_EMIT_INTERVAL_MILLIS=1000

STOCK_MASTER_LOAD_ENABLED=false
STOCK_MASTER_KOSPI_PATH=
STOCK_MASTER_KOSDAQ_PATH=
STOCK_MASTER_FAIL_FAST=false

KIS_APP_KEY=
KIS_APP_SECRET=
KIS_BASE_URL=https://openapivts.koreainvestment.com:29443
KIS_ENVIRONMENT=demo
KIS_APPROVAL_URL=https://openapivts.koreainvestment.com:29443/oauth2/Approval
KIS_WEBSOCKET_URL=ws://ops.koreainvestment.com:31000
KIS_PRICE_TR_ID=H0STCNT0
KIS_ORDERBOOK_TR_ID=H0STASP0
KIS_CUSTOMER_TYPE=P
KIS_EXCHANGE_ID=KRX

REDIS_ENABLED=false
REDIS_HOST=
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_TLS_ENABLED=false
REDIS_SSL_VERIFY_PEER=true
REDIS_QUOTE_TTL_SECONDS=300
REDIS_MARKET_STATUS_TTL_SECONDS=60
REDIS_CONNECT_TIMEOUT=3s
REDIS_COMMAND_TIMEOUT=3s

KAFKA_BOOTSTRAP_SERVERS=
KAFKA_SECURITY_PROTOCOL=
KAFKA_TOPIC_PRICE=market.price
KAFKA_TOPIC_ORDERBOOK=market.orderbook
```

SSM 로컬 포트포워딩으로 TLS ElastiCache를 테스트할 때는 인증서 호스트명이 `127.0.0.1`과 맞지 않을 수 있으므로 로컬에서만 `REDIS_SSL_VERIFY_PEER=false`를 사용할 수 있습니다.

## 종목 마스터 적재

KIS 종목정보 파일을 받은 뒤 아래처럼 설정합니다.

```properties
STOCK_MASTER_LOAD_ENABLED=true
STOCK_MASTER_KOSPI_PATH=C:/path/to/kospi_code.mst
STOCK_MASTER_KOSDAQ_PATH=C:/path/to/kosdaq_code.mst
STOCK_MASTER_FAIL_FAST=false
```

앱 기동 시 `.mst` 파일을 MS949 고정폭 형식으로 파싱해 Redis에 저장합니다. 적재가 끝나면 다음 API로 상태를 확인할 수 있습니다.

```http
GET /api/realtime/cache/stock-master
```

예상 Redis 값:

```json
{
  "totalCount": 3386,
  "kospiCount": 969,
  "kosdaqCount": 2417,
  "loadedAt": "2026-05-28T10:30:00"
}
```

## KIS 연동

실제 KIS를 시세 소스로 사용하려면 다음처럼 설정합니다.

```properties
QUOTE_SOURCE_MODE=kis
KIS_APP_KEY=...
KIS_APP_SECRET=...
KIS_APPROVAL_URL=https://openapivts.koreainvestment.com:29443/oauth2/Approval
KIS_WEBSOCKET_URL=ws://ops.koreainvestment.com:31000
KIS_PRICE_TR_ID=H0STCNT0
KIS_ORDERBOOK_TR_ID=H0STASP0
KIS_CUSTOMER_TYPE=P
```

서비스는 기동 시 approval key를 발급받고 KIS WebSocket 연결을 유지합니다. `/ws/market`에서 종목을 구독하면 KIS 현재가/호가도 함께 구독하고, 수신한 시세를 Redis 저장과 WebSocket push 흐름으로 전달합니다.

## 로컬 실행

레포지토리 루트에서 실행합니다.

```powershell
.\gradlew.bat :apps:realtime:market-realtime-service:bootRun
```

기본 포트는 `8087`입니다.

```text
http://localhost:8087/api/health
http://localhost:8087/api/realtime/status
http://localhost:8087/actuator/health
```

## Docker 이미지 빌드

레포지토리 루트에서 실행합니다.

```powershell
docker build -f apps/realtime/market-realtime-service/Dockerfile -t maesoongan-market-realtime-service .
```

## 테스트

```powershell
.\gradlew.bat :apps:realtime:market-realtime-service:test
```
