# 🤝MaeSoonGan Back-End

## ✍️프로젝트 한 줄 소개

- 사용자와 관리자가 증권 투자 대회 서비스를 이용할 수 있도록 Spring Boot 기반 API, 실시간 시세, 주문 이벤트 처리, 인증, 알림, 운영 관리 기능을 구현한 백엔드 애플리케이션입니다.

<br />

## 🛫레포지토리 개요

- 이 레포지토리는 MaeSoonGan 서비스의 백엔드 API 서버, 실시간 서비스, 비동기 워커, 배포 설정을 담당합니다.

- 사용자 API에서는 회원가입, 로그인, 프로필 관리, 종목 조회, 관심종목 관리, 매수 및 매도 주문, 포트폴리오 조회, 대회 목록 조회, 대회 참가, 랭킹 조회, 알림 및 공지사항 조회 기능을 제공합니다. 관리자 API에서는 관리자 로그인, 대시보드, 회원 관리, 계정 정지 및 해제, 시드머니 지급, 대회 관리, 랭킹 관리, 공지 관리, 점검 모드, 감사 로그, 운영 모니터링 기능을 제공합니다.

- Back-End는 Front-End 요청을 처리하고 MariaDB, Redis, Kafka, 외부 증권 API, AWS S3와 연동합니다. 또한 Docker, Kubernetes, Argo CD, GitHub Actions 기반 설정을 통해 AWS EKS 환경에 배포할 수 있도록 구성했습니다.

<br />

## 🛠️주요 기능

### 사용자 기능

* 회원가입
* 로그인
* ID/PW 찾기
* 회원 프로필 조회 및 수정
* 프로필 이미지 업로드용 S3 Presigned URL 발급
* 종목 검색
* 종목 상세 조회
* 실시간 시세 조회
* 관심종목 관리
* 매수 및 매도 주문
* 주문 취소 요청
* 보유 자산 및 포트폴리오 조회
* 대회 목록 조회
* 대회 상세 조회
* 대회 참가
* 대회 랭킹 조회
* 알림 조회 및 읽음 처리
* 공지사항 조회
* 사용자 실시간 이벤트 구독

### 관리자 기능

* 관리자 로그인
* 대시보드 조회
* 회원 목록 조회
* 회원 상세 조회
* 회원 계정 정지 및 해제
* 회원 검색 및 CSV 내보내기
* 시드머니 지급
* 시드머니 지급 이력 조회
* 대회 생성 및 관리
* 대회 상세 조회
* 대회 종료 및 취소
* 대회 결과 조회
* 랭킹 조회 및 제외/복구
* 랭킹 CSV 내보내기
* 공지사항 등록 및 관리
* 점검 모드 제어
* 감사 로그 조회
* 운영 모니터링 화면 조회

### 시스템 기능

* JWT 기반 인증 및 권한 처리
* Redis 기반 인증/주문/실시간 데이터 캐싱
* Kafka 기반 주문 요청 및 체결 이벤트 연동
* 한국투자증권 API 연동
* MariaDB 기반 영속 데이터 관리
* OpenAPI/Swagger 문서 제공
* Actuator 헬스 체크 제공
* Prometheus, OpenTelemetry, Logstash 기반 관측성 설정
* Kubernetes, External Secrets, Argo CD 배포 설정

<br />

## 📚기술 스택

| 구분              | 기술                                           |
| ----------------- | ---------------------------------------------- |
| Language          | Java 17                                        |
| Framework         | Spring Boot 4, Spring MVC                      |
| Build Tool        | Gradle                                         |
| Persistence       | Spring Data JPA, Spring JDBC                   |
| Database          | MariaDB                                        |
| Cache             | Redis                                          |
| Messaging         | Apache Kafka, Spring Kafka                     |
| Security          | Spring Security, JWT                           |
| Validation        | Spring Validation                              |
| API Docs          | springdoc-openapi, Swagger UI                  |
| Realtime          | WebSocket, SSE                                 |
| Cloud             | AWS S3, AWS EKS, Amazon ECR                    |
| Observability     | Spring Actuator, Micrometer, Prometheus, OpenTelemetry, Logstash |
| Test              | JUnit 5, Spring Boot Test, Spring Security Test, Spring Kafka Test |
| Deploy            | Docker, Kubernetes, Argo CD, External Secrets  |
| CI/CD             | GitHub Actions                                 |
| Version Control   | GitHub                                         |

<br />

## 📋디렉터리 구조

| 경로               | 설명                         |
| ------------------ | ---------------------------- |
| `src`              | 루트 Spring Boot 애플리케이션 및 공통 샘플 소스 |
| `apps/api`         | 사용자 및 관리자 REST API 서비스 모듈 |
| `apps/api/auth-service` | 회원가입, 로그인, 이메일 인증, 프로필 API |
| `apps/api/admin-service` | 관리자 인증, 회원, 대회, 공지, 시스템 운영 API |
| `apps/api/contest-service` | 대회 목록, 상세, 참가, 랭킹 API |
| `apps/api/market-service` | 종목, 시장, 관심종목 API |
| `apps/api/order-service` | 주문, 주문 취소, 포트폴리오 API 및 Kafka 발행 |
| `apps/api/notification-api` | 알림, 공지사항, 내부 알림 생성 API |
| `apps/realtime`    | 실시간 시세 및 사용자 실시간 이벤트 서비스 |
| `apps/realtime/market-realtime-service` | 시장 실시간 데이터 수집, 캐시, WebSocket API |
| `apps/realtime/user-realtime-service` | 사용자 대상 SSE 실시간 이벤트 API |
| `apps/worker`      | 비동기 이벤트 처리 워커 모듈 |
| `apps/worker/trade-sync-worker` | Kafka 체결 이벤트를 주문/포트폴리오 데이터로 동기화 |
| `libs`             | 공통 라이브러리, 이벤트 계약, 보안 공통 모듈 영역 |
| `config`           | 공통 관측성 설정 |
| `docs`             | 문서 파일 |
| `infra/k8s`        | Kubernetes 매니페스트 |
| `infra/argocd`     | Argo CD Application 설정 |
| `infra/helm`       | Helm 차트 영역 |
| `infra/terraform`  | AWS 인프라 구성 영역 |
| `.github`          | GitHub Actions 워크플로우 및 이슈/PR 템플릿 |

<br />

## 🚀로컬 실행

### 사전 준비

* Java 17
* MariaDB
* Redis
* Kafka
* Gradle Wrapper

### 로컬 데이터베이스 생성

```sql
CREATE DATABASE fisaschool
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 주요 환경 변수

```properties
SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3307/fisaschool?sslMode=trust&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=your-local-username
SPRING_DATASOURCE_PASSWORD=your-local-password

APP_JWT_SECRET=replace-with-long-random-secret

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

KIS_BASE_URL=https://openapivts.koreainvestment.com:29443
KIS_APP_KEY=your-kis-app-key
KIS_APP_SECRET=your-kis-app-secret
KIS_ACCOUNT_NUMBER=your-account-number

AWS_REGION=ap-northeast-2
PROFILE_IMAGE_BUCKET=maesoongan-profile-images
```

### 서비스별 실행

```powershell
.\gradlew.bat :apps:api:admin-service:bootRun
.\gradlew.bat :apps:api:auth-service:bootRun
.\gradlew.bat :apps:api:contest-service:bootRun
.\gradlew.bat :apps:api:market-service:bootRun
.\gradlew.bat :apps:api:notification-api:bootRun
.\gradlew.bat :apps:api:order-service:bootRun
.\gradlew.bat :apps:realtime:market-realtime-service:bootRun
.\gradlew.bat :apps:realtime:user-realtime-service:bootRun
.\gradlew.bat :apps:worker:trade-sync-worker:bootRun
```

### 기본 포트

| 서비스 | 포트 |
| ------ | ---- |
| `admin-service` | `8080` |
| `auth-service` | `8081` |
| `contest-service` | `8082` |
| `order-service` | `8084` |
| `market-service` | `8085` |
| `notification-api` | `8086` |
| `market-realtime-service` | `8087` |
| `user-realtime-service` | `8087` |
| `trade-sync-worker` | `8088` |

### 테스트

```powershell
.\gradlew.bat test
```

특정 모듈만 테스트할 경우 다음과 같이 실행합니다.

```powershell
.\gradlew.bat :apps:api:auth-service:test
```

### Swagger

```text
http://localhost:{port}/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:{port}/v3/api-docs
```

<br />

