# 한국제일 프롬프트 도장

2학기 공통 프로젝트 진행을 위한 백엔드 / 프론트엔드 기초 프로젝트. - 서울 5반 A505팀

각각 Hello World만 표시하는 최소 구성입니다.

## 구성

| 디렉터리    | 스택                                                                      |
| ----------- | ------------------------------------------------------------------------- |
| `backend/`  | Java 21, Spring Boot 4.1.0, Spring Data JPA, jOOQ, PostgreSQL 18.4         |
| `frontend/` | Node 24, TypeScript 6.0.x, React 19.2.7, Vite 7                            |

각 디렉터리의 `README.md`에서 상세 실행 방법을 확인하세요.

## 빠른 시작

**Frontend**

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

**Infra** (Docker 필요)

```bash
docker compose up -d
```

| 서비스     | 이미지                        | 포트          | 접속 정보                          |
| ---------- | ----------------------------- | ------------- | ---------------------------------- |
| PostgreSQL | `postgres:18.4`               | 5432          | db `hello` / `postgres` / `postgres` |
| Redis      | `redis:8-alpine`              | 6379          | (auth 없음)                        |
| RabbitMQ   | `rabbitmq:4-management-alpine`| 5672 / 15672  | `rabbitmq` / `rabbitmq` · UI `:15672` |

> Redis·RabbitMQ는 향후 큐잉 도입을 위한 인프라입니다. Spring 연동 의존성
> (`spring-boot-starter-data-redis`, `spring-boot-starter-amqp`)은 실제 구현 시점에 추가하세요.

**Backend** (JDK 21 필요 — Gradle Wrapper 포함)

```bash
cd backend
./gradlew bootRun   # http://localhost:8080/api/hello
```

DB 접속 정보는 `docker-compose.yml`과 `backend/src/main/resources/application.yml`이 동일하게 맞춰져 있습니다.
