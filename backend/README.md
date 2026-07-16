# Backend

Spring Boot 기반 Hello World API 서버.

## 기술 스택

- Java 21
- Spring Boot 4.1.0
- Gradle 9.1 (Wrapper 포함)
- Spring Web
- Spring Data JPA
- jOOQ (`spring-boot-starter-jooq`)
- PostgreSQL 18.4

## 사전 준비

1. **JDK 21** 설치 후 `java -version`으로 확인 (Gradle Wrapper가 포함되어 별도 Gradle 설치는 불필요)
2. **PostgreSQL 18.4** 실행. 프로젝트 루트의 docker-compose를 사용하면 DB와 `hello` 데이터베이스가 자동 생성됩니다.

   ```bash
   # 프로젝트 루트에서
   docker compose up -d
   ```

   > Docker를 쓰지 않는다면 PostgreSQL을 직접 설치하고 `CREATE DATABASE hello;`를 실행하세요.

   접속 정보는 `src/main/resources/application.yml`에 있으며 `docker-compose.yml`과 동일하게 맞춰져 있습니다.

   | 항목     | 값                                      |
   | -------- | --------------------------------------- |
   | url      | `jdbc:postgresql://localhost:5432/hello`|
   | username | `postgres`                              |
   | password | `postgres`                              |

   > 환경에 맞게 `application.yml`을 수정하세요.

## 실행

```bash
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다.

## 확인

```bash
curl http://localhost:8080/api/hello
# -> Hello World
```

## 구조

```
backend/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
└── src/main/
    ├── java/com/example/backend/
    │   ├── BackendApplication.java   # 애플리케이션 진입점
    │   └── HelloController.java      # GET /api/hello
    └── resources/
        └── application.yml            # DB / JPA / jOOQ 설정
```
