# Stage 1: extract Spring Boot layers
FROM eclipse-temurin:21-jre-jammy AS builder
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2: production image
FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.title="AegisPay User Service"
LABEL org.opencontainers.image.vendor="AegisPay"

RUN groupadd --gid 1001 aegispay \
 && useradd --uid 1001 --gid aegispay --no-create-home aegispay

WORKDIR /app

COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

RUN chown -R aegispay:aegispay /app
USER aegispay

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1
