# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy parent POM and module descriptor first so Maven can resolve the reactor
COPY pom.xml /workspace/pom.xml

# Copy the module source
COPY services/data-pipeline/pom.xml /workspace/services/data-pipeline/pom.xml

# Pre-fetch dependencies (layer-caching optimisation)
RUN mvn -f /workspace/services/data-pipeline/pom.xml \
        dependency:go-offline -B --no-transfer-progress || true

# Copy full source tree for the module
COPY services/data-pipeline/src /workspace/services/data-pipeline/src

# Copy shared libs source (needed for local builds inside Docker)
COPY libs /workspace/libs

# Build only the data-pipeline module (and its lib dependencies)
RUN mvn -f /workspace/pom.xml \
        -pl libs/common-domain,libs/common-observability,services/data-pipeline \
        -am install -DskipTests -B --no-transfer-progress

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="AegisPay Data Pipeline"
LABEL org.opencontainers.image.vendor="AegisPay"
LABEL org.opencontainers.image.description="Kafka Streams analytics pipeline → ClickHouse"

# Non-root user for security
RUN addgroup -g 1001 aegispay && adduser -u 1001 -G aegispay -H -D aegispay

WORKDIR /app

ARG JAR_FILE=target/data-pipeline-*.jar
COPY --from=build /workspace/services/data-pipeline/${JAR_FILE} app.jar

RUN chown -R aegispay:aegispay /app
USER aegispay

EXPOSE 8090

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O- http://localhost:8090/actuator/health || exit 1
