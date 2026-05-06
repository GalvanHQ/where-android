# Multi-stage Dockerfile for Ktor Chat Server
# Deployable to Google Cloud Run

# Stage 1: Build
FROM gradle:8.11.1-jdk21 AS builder

WORKDIR /app

# Copy dependencies first for better caching
COPY server/gradle ./gradle
COPY server/build.gradle.kts ./build.gradle.kts
COPY server/settings.gradle.kts ./settings.gradle.kts
COPY gradle/libs.versions.toml ./gradle/libs.versions.toml
COPY gradle/wrapper ./gradle/wrapper

# Copy source
COPY server/src ./src

# Build the fat JAR
RUN gradle :server:shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user for Cloud Run security
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -s /bin/sh -D appuser

# Copy built artifact
COPY --from=builder /app/server/build/libs/*-all.jar app.jar

# Switch to non-root user
USER appuser

# Expose port (Cloud Run provides PORT env var)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# Run the server
ENTRYPOINT ["java", "-jar", "app.jar"]