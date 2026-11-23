# syntax=docker/dockerfile:1

### Build stage — uses Maven + JDK 21 (no local Maven required) ###
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster rebuilds
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build the application (skip tests here — the integration test needs Docker,
# run `mvn test` separately on a host with Docker available)
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

### Runtime stage — slim JRE ###
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the container HEALTHCHECK (actuator)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

# Run as a non-root user
RUN useradd --system --uid 10001 appuser
USER appuser

EXPOSE 8080

# Container-aware heap sizing; JAVA_OPTS is overridable at runtime.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1

# exec form so the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

