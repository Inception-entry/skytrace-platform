FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
# 生产镜像不拉 test 依赖；Maven Central 502 时重试。
RUN --mount=type=cache,target=/root/.m2 \
    set -eu; \
    mvn_retry() { \
      i=0; \
      until "$@"; do \
        i=$((i + 1)); \
        [ "$i" -ge 5 ] && exit 1; \
        echo "Maven Central flake (attempt $i/5), retrying..." >&2; \
        sleep $((i * 8)); \
      done; \
    }; \
    mvn_retry mvn -B -ntp -Dmaven.test.skip=true -DexcludeScope=test dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    set -eu; \
    mvn_retry() { \
      i=0; \
      until "$@"; do \
        i=$((i + 1)); \
        [ "$i" -ge 5 ] && exit 1; \
        echo "Maven Central flake (attempt $i/5), retrying..." >&2; \
        sleep $((i * 8)); \
      done; \
    }; \
    mvn_retry mvn -B -ntp clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre

WORKDIR /app
# CVE-2025-68973 (gnupg) / CVE-2026-45447 (openssl): temurin 标签滞后于 Ubuntu 安全更新
RUN set -eux; \
    apt_retry() { apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 "$@"; }; \
    i=0; \
    until apt_retry update; do \
      i=$((i + 1)); \
      [ "$i" -ge 5 ] && exit 1; \
      sleep $((i * 8)); \
    done; \
    apt_retry upgrade -y --no-install-recommends; \
    apt_retry install -y --no-install-recommends curl; \
    rm -rf /var/lib/apt/lists/*; \
    rm -f /usr/bin/pebble
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
