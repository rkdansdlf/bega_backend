# Stage 1: Build - 빌드 전용 환경
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

# [수정된 부분] gradlew 파일의 줄 끝 문자를 Windows(CRLF)에서 Linux(LF)로 변환합니다.
# apt-get을 통한 dos2unix 설치 대신 기본 설치된 sed를 사용하여 빌드 속도를 대폭 개선합니다.
RUN sed -i 's/\r$//' gradlew

RUN chmod +x gradlew

# 소스 코드를 복사하기 전에 의존성을 먼저 다운로드하여 Docker 캐시 효율을 높입니다.
# build.gradle이 변경되지 않으면 이 단계는 캐시된 레이어를 사용합니다.
RUN ./gradlew dependencies

COPY src src

RUN ./gradlew clean build -x test

# Stage 2: Runtime - 실행 전용 환경
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 빌더 스테이지에서 빌드된 JAR 파일만 복사합니다.
COPY --from=builder /build/build/libs/*.jar app.jar

# 보안 강화를 위해 non-root 사용자를 생성하고 해당 사용자로 애플리케이션을 실행합니다.
RUN useradd -m -u 1000 appuser && \
    mkdir -p /app/logs && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# 컨테이너 실행 시 JAR 파일을 실행합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]