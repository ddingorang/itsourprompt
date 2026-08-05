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
# copyJunitConsole은 제출 코드의 테스트를 돌릴 콘솔 런처 팻자를 고정된 이름으로 꺼내 놓는다.
RUN ./gradlew clean bootJar copyJunitConsole --no-daemon -x test

# ---- Run stage ----
# JRE가 아니라 JDK다 — 제출된 코드를 javac로 컴파일해야 한다.
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# 제출된 코드를 루트로 실행하지 않는다.
RUN useradd --create-home --uid 1000 worker
COPY --from=build /app/build/libs/*.jar app.jar
# 문제마다 바뀌지 않는 고정 의존이라 이미지에 굽는다. 반대로 테스트 코드는 문제 저장소를 따라
# 수시로 바뀌므로 이미지가 아니라 run.request 메시지로 받는다(application.yml의 junit-console-jar).
COPY --from=build /app/build/junit-console/junit-platform-console-standalone.jar \
     /opt/junit/junit-platform-console-standalone.jar
RUN chown worker:worker /app/app.jar
USER worker

ENTRYPOINT ["java", "-jar", "app.jar"]
