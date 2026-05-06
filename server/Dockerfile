# Multi-stage Dockerfile for Ktor Chat Server
# Deployable to Google Cloud Run

# Stage 1: Build
FROM gradle:8.11.1-jdk21 AS builder

WORKDIR /app

# Copy only server-related files
COPY server/build.gradle.kts ./build.gradle.kts
COPY server/settings.gradle.kts ./settings.gradle.kts
COPY server/src ./src

# Copy gradle wrapper (not project gradle)
COPY gradle/wrapper ./gradle/wrapper
COPY gradle/libs.versions.toml ./gradle/libs.versions.toml

# Build the fat JAR
RUN gradle :server:shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -s /bin/sh -D appuser

COPY --from=builder /app/server/build/libs/*-all.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]