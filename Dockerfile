# Dependencies resolve in their own layer so a source-only change does not refetch them.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI against the full suite; repeating them here would only slow the image.
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.title="Savonarola" \
      org.opencontainers.image.description="An LLM-based Telegram autonomous moderator" \
      org.opencontainers.image.source="https://github.com/stormino/savonarola"

# The bot holds a group's message history and a moderation record: it has no business
# running as root, and the data directory is the only path it needs to write.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home savonarola
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /data && chown -R savonarola:savonarola /data /app
USER savonarola

ENV SAV_DATA_DIR=/data
VOLUME ["/data"]
EXPOSE 8081

# Reports DOWN when judgments are failing, not merely when the process has died.
HEALTHCHECK --interval=60s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
