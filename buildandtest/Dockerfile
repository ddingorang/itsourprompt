# 코드 빌드/실행 워커. backend/Dockerfile의 2단 빌드 구조를 따른다.

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
# 윈도우에서 체크아웃하면 gradlew가 CRLF라 셔뱅이 "/bin/sh\r"로 읽혀 not found가 된다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# 의존성만 먼저 받아서 레이어 캐싱 (소스 코드 변경 시에도 의존성은 재다운로드 안 하도록)
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ---- Run stage ----
# JRE가 아니라 JDK다 — 제출된 코드를 javac로 컴파일해야 한다.
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# 제출된 코드를 루트로 실행하지 않는다.
RUN useradd --create-home --uid 1000 worker
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown worker:worker /app/app.jar
USER worker

ENTRYPOINT ["java", "-jar", "app.jar"]
