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
RUN useradd --system --uid 1001 --create-home savonarola
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /data && chown -R savonarola:savonarola /data /app
USER savonarola

ENV SAV_DATA_DIR=/data
VOLUME ["/data"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
