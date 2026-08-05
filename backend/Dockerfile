FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 배포 스크립트가 컨테이너 내부에서 curl로 /actuator/health를 확인하는데,
# jre-alpine 베이스 이미지엔 curl이 기본으로 없다.
# ffmpeg는 타임랩스 워커(DefaultFfmpegProcessRunner)가 ProcessBuilder로 직접 실행하는
# 외부 바이너리 — jre-alpine 베이스에는 없어 별도로 설치해야 한다.
RUN apk add --no-cache curl ffmpeg
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
