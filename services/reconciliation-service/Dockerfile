# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy root POM and shared lib POMs first for layer caching
COPY pom.xml .
COPY libs/common-domain/pom.xml      libs/common-domain/pom.xml
COPY libs/common-security/pom.xml    libs/common-security/pom.xml
COPY libs/common-kafka/pom.xml       libs/common-kafka/pom.xml
COPY libs/common-observability/pom.xml libs/common-observability/pom.xml
COPY services/reconciliation-service/pom.xml services/reconciliation-service/pom.xml

# Resolve dependencies without source (cache layer)
RUN mvn dependency:go-offline -pl services/reconciliation-service --also-make -q 2>/dev/null || true

# Copy source
COPY libs/                           libs/
COPY services/reconciliation-service/ services/reconciliation-service/

# Build only the reconciliation-service and its lib dependencies
RUN mvn clean package -pl services/reconciliation-service --also-make \
    -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Non-root user
RUN addgroup -S aegispay && adduser -S aegispay -G aegispay

WORKDIR /app

COPY --from=builder /workspace/services/reconciliation-service/target/*.jar app.jar

RUN chown aegispay:aegispay app.jar
USER aegispay

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
