FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# CVE-2025-68973 (gnupg) / CVE-2026-45447 (openssl): temurin 标签滞后于 Ubuntu 安全更新
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
