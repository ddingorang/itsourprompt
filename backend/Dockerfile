# 이 파일을 레포의 backend/Dockerfile 로 저장 (파일명은 반드시 "Dockerfile")
# Jenkinsfile.backend의 "docker build ..." 가 backend/ 디렉터리 안에서 이 파일을 찾음

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
# 의존성만 먼저 받아서 레이어 캐싱 (소스 코드 변경 시에도 의존성은 재다운로드 안 하도록)
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
